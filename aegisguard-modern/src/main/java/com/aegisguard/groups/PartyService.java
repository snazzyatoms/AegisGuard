package com.aegisguard.groups;

import com.aegisguard.AegisGuard;
import com.aegisguard.arena.ArenaParty;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns general-purpose parties and presents a unified view of "the group this
 * player is in right now" — a general party, or an arena party while grouped
 * for a run. The sidebar renders that view; arena logic stays untouched.
 *
 * <p>Membership survives relogs (members show as offline) so a party feels
 * like a real group, not a session lease. Explicit leave/kick, or the last
 * member going away, ends a party.
 */
public final class PartyService implements Listener {

    public enum JoinResult { JOINED, NO_INVITE, PARTY_FULL, ALREADY_IN_PARTY }

    private final AegisGuard plugin;
    private final Map<UUID, Party> partyByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInviteAt = new ConcurrentHashMap<>();
    private final java.io.File storeFile;
    /** If a load failed we refuse to overwrite the file with partial state. */
    private volatile boolean storeHealthy = true;
    /** Presence learned from remote rosters: memberId → where they are. */
    private final Map<UUID, RemoteMember> remotePresence = new ConcurrentHashMap<>();
    /** Last entity-thread-captured location per online member (tracker never reads
     *  another entity's state off its own region). */
    private final Map<UUID, org.bukkit.Location> lastPositions = new ConcurrentHashMap<>();
    private final Map<UUID, String> memberNames = new ConcurrentHashMap<>();
    /** Per-player preferences: see the tracker / hide own position. */
    private final Map<UUID, Boolean> trackPref = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> hidePref = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPingAt = new ConcurrentHashMap<>();
    private volatile Object syncTicker;
    private volatile Object trackerTicker;
    private PartySidebarService sidebar;

    /** What the sidebar needs about a member who lives on another backend. */
    public record RemoteMember(String name, String server, boolean online, long seenAt) { }

    public PartyService(AegisGuard plugin) {
        this.plugin = plugin;
        this.storeFile = new java.io.File(plugin.getDataFolder(), "parties.yml");
        load();
    }

    public void setSidebar(PartySidebarService sidebar) { this.sidebar = sidebar; }

    private boolean networkSyncOn() {
        return enabled() && plugin.getConfig().getBoolean("party.network_sync", true)
                && plugin.network() != null && plugin.network().isNetworked()
                && plugin.networkChat() != null;
    }

    /** Periodic roster republication — self-heals backends that joined late. */
    public void start() {
        if (syncTicker == null) {
            long period = Math.max(20L, plugin.getConfig().getLong("party.sync_interval_seconds", 60L));
            syncTicker = plugin.scheduler().runAsyncRepeating(() -> {
                if (!networkSyncOn()) return;
                for (Party party : parties.values()) publishRoster(party);
            }, period, period);
        }
        if (trackerTicker == null) {
            long ticks = Math.max(10L, plugin.getConfig().getLong("party.tracker_interval_ticks", 40L));
            trackerTicker = plugin.scheduler().runGlobalRepeating(this::trackerTick, ticks, ticks);
        }
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("party.enabled", true);
    }

    public int maxSize() {
        return Math.max(2, plugin.getConfig().getInt("party.max_size", 8));
    }

    public long inviteTtlMillis() {
        return Math.max(10L, plugin.getConfig().getLong("party.invite_expire_seconds", 60L)) * 1000L;
    }

    // ------------------------------------------------------------------
    // Lifecycle

    public Party createParty(Player leader, String name) {
        if (leader == null) return null;
        Party existing = partyByPlayer.get(leader.getUniqueId());
        if (existing != null) return existing;
        Party party = new Party(leader.getUniqueId(), cleanName(name, leader.getName()));
        parties.put(party.getPartyId(), party);
        partyByPlayer.put(leader.getUniqueId(), party);
        refreshSidebar();
        saveAsync();
        publishRoster(party);
        return party;
    }

    /** First non-null of the player's general party, else their arena party view. */
    public Party partyOf(UUID playerId) {
        return partyByPlayer.get(playerId);
    }

    /** Unified snapshot for the sidebar: general party, else arena party. */
    public PartyView viewFor(UUID playerId) {
        Party party = partyByPlayer.get(playerId);
        if (party != null) {
            return new PartyView(title(party), party.getLeaderId(), party.getMembers());
        }
        if (plugin.arena() != null) {
            ArenaParty arena = plugin.arena().getParty(playerId);
            if (arena != null && arena.size() > 1) {
                return new PartyView("Arena Party", arena.getLeaderId(), arena.getMembers());
            }
        }
        return null;
    }

    public boolean invite(Player inviter, Player target) {
        if (inviter == null || target == null || inviter.equals(target)) return false;
        Party party = partyOf(inviter.getUniqueId());
        if (party == null) party = createParty(inviter, null);
        if (party == null) return false;
        if (!party.isLeader(inviter.getUniqueId())) return false;
        if (party.size() >= maxSize() || party.isMember(target.getUniqueId())) return false;
        long now = System.currentTimeMillis();
        Long last = lastInviteAt.get(inviter.getUniqueId());
        if (last != null && now - last < 3000L) return false;
        lastInviteAt.put(inviter.getUniqueId(), now);
        party.invite(target.getUniqueId(), now + inviteTtlMillis());
        return true;
    }

    public JoinResult join(Player player) {
        if (player == null) return JoinResult.NO_INVITE;
        if (partyByPlayer.containsKey(player.getUniqueId())) return JoinResult.ALREADY_IN_PARTY;
        for (Party party : parties.values()) {
            party.purgeExpiredInvites();
            if (!party.hasInvite(player.getUniqueId())) continue;
            if (party.size() >= maxSize()) return JoinResult.PARTY_FULL;
            if (party.acceptInvite(player.getUniqueId())) {
                partyByPlayer.put(player.getUniqueId(), party);
                refreshSidebar();
                saveAsync();
                publishRoster(party);
                return JoinResult.JOINED;
            }
        }
        return JoinResult.NO_INVITE;
    }

    /** Declines a pending invite; returns true if one existed. */
    public boolean decline(Player player) {
        if (player == null) return false;
        boolean had = false;
        for (Party party : parties.values()) {
            if (party.hasInvite(player.getUniqueId())) {
                party.declineInvite(player.getUniqueId());
                had = true;
            }
        }
        return had;
    }

    /** Explicit leave or kick. Returns the affected party (may be disbanded). */
    public Party removeMember(UUID playerId) {
        Party party = partyByPlayer.remove(playerId);
        if (party == null) return null;
        party.removeMember(playerId);
        if (party.isEmpty()) {
            parties.remove(party.getPartyId());
        }
        refreshSidebar();
        saveAsync();
        publishRoster(party);
        return party;
    }

    public boolean kick(Player leader, UUID targetId) {
        Party party = partyOf(leader == null ? null : leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) return false;
        if (leader.getUniqueId().equals(targetId) || !party.isMember(targetId)) return false;
        removeMember(targetId);
        return true;
    }

    public boolean promote(Player leader, UUID targetId) {
        Party party = partyOf(leader == null ? null : leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) return false;
        if (targetId == null || !party.isMember(targetId)) return false;
        party.setLeaderId(targetId);
        refreshSidebar();
        saveAsync();
        publishRoster(party);
        return true;
    }

    // ------------------------------------------------------------------
    // Network roster sync — mirrors of parties that span backends.
    // ------------------------------------------------------------------

    /** Publish this party's full roster to the network event bus. */
    public void publishRoster(Party party) {
        if (party == null || party.isEmpty() || !networkSyncOn()) return;
        Map<String, String> fields = new java.util.HashMap<>();
        fields.put("party", party.getPartyId().toString());
        fields.put("title", party.getName() == null ? "" : party.getName());
        fields.put("leader", party.getLeaderId().toString());
        fields.put("origin", plugin.network().serverName());
        StringBuilder csv = new StringBuilder();
        for (UUID m : party.getMembers()) {
            if (csv.length() > 0) csv.append(',');
            Player online = Bukkit.getPlayer(m);
            RemoteMember known = remotePresence.get(m);
            String name = online != null ? online.getName()
                    : known != null ? known.name()
                    : nameCache(m);
            boolean on = online != null && online.isOnline();
            csv.append(m).append('~').append(name == null ? "?" : name).append('~').append(on ? 1 : 0);
        }
        fields.put("members", csv.toString());
        plugin.networkChat().publishParty(fields);
    }

    /**
     * Merge a roster event from another backend. Full-replace semantics per
     * party: the publisher's view wins for that party id; concurrent adds on
     * two backends self-heal on the next republication. A local mirror is
     * materialized whenever a roster names an online player here, so chat,
     * leave, and the sidebar all just work.
     */
    public void applyRemoteRoster(Map<String, String> fields) {
        if (fields == null || !enabled()) return;
        UUID partyId, leaderId;
        try {
            partyId = UUID.fromString(String.valueOf(fields.get("party")));
            leaderId = UUID.fromString(String.valueOf(fields.get("leader")));
        } catch (Throwable t) { return; }
        String title = fields.get("title");
        String origin = fields.get("origin");

        Set<UUID> memberIds = new java.util.HashSet<>();
        String csv = fields.get("members");
        if (csv != null) {
            for (String entry : csv.split(",")) {
                String[] parts = entry.split("~");
                try {
                    UUID id = UUID.fromString(parts[0]);
                    String name = parts.length > 1 ? parts[1] : "?";
                    boolean on = parts.length > 2 && "1".equals(parts[2]);
                    memberIds.add(id);
                    remotePresence.put(id, new RemoteMember(name, origin, on, System.currentTimeMillis()));
                } catch (Throwable ignored) { }
            }
        }
        if (memberIds.isEmpty()) return;

        Party mirror = parties.get(partyId);
        boolean anyLocalOnline = memberIds.stream().anyMatch(id -> {
            Player p = Bukkit.getPlayer(id);
            return p != null && p.isOnline();
        });
        if (mirror == null && anyLocalOnline) {
            mirror = new Party(leaderId, title, partyId);
            parties.put(partyId, mirror);
        }
        if (mirror == null) return;
        mirror.setName(title);
        mirror.setLeaderId(leaderId);
        for (UUID id : new java.util.HashSet<>(mirror.getMembers())) {
            if (!memberIds.contains(id)) {
                mirror.removeMember(id);
                partyByPlayer.remove(id, mirror);
            }
        }
        for (UUID id : memberIds) {
            mirror.addMember(id);
            partyByPlayer.put(id, mirror);
        }
        remotePresence.entrySet().removeIf(e ->
                !memberIds.contains(e.getKey()) && e.getValue().seenAt() < System.currentTimeMillis() - 300_000L);
        refreshSidebar();
    }

    /** Remote presence for the sidebar: name/server for members not online here. */
    public RemoteMember remoteStatus(UUID memberId) {
        return memberId == null ? null : remotePresence.get(memberId);
    }

    private String nameCache(UUID id) {
        try {
            String name = Bukkit.getOfflinePlayer(id).getName();
            return name == null ? "?" : name;
        } catch (Throwable t) { return "?"; }
    }

    // ------------------------------------------------------------------
    // Rally — leader plants a point, members teleport to it on their own call.

    /** Leader plants the party's rally point at their feet. */
    public Party plantRally(Player leader) {
        Party party = partyOf(leader == null ? null : leader.getUniqueId());
        if (party == null || !party.isLeader(leader.getUniqueId())) return null;
        long ttl = Math.max(15L, plugin.getConfig().getLong("party.rally_expire_seconds", 120L)) * 1000L;
        party.rally(leader.getLocation(), System.currentTimeMillis() + ttl);
        return party;
    }

    /** Active rally point for the player's party (null if none/expired). */
    public Location rallyPointFor(UUID playerId) {
        Party party = partyOf(playerId);
        return party == null ? null : party.rallyPoint();
    }

    // ------------------------------------------------------------------
    // Live tracker — MMO-style compass HUD plus a shared ping marker.
    // Positions are only ever read on each member's own entity thread and
    // cached; the HUD composes from that cache, so no off-region access.

    public boolean trackingEnabled() {
        return enabled() && plugin.getConfig().getBoolean("party.tracking_enabled", true);
    }

    public boolean showsTracker(UUID playerId) {
        Boolean pref = trackPref.get(playerId);
        return pref == null || pref;
    }

    /** Whether this member hides their position from the party tracker + sidebar distance. */
    public boolean isHidden(UUID playerId) {
        Boolean pref = hidePref.get(playerId);
        return pref != null && pref;
    }

    /**
     * Last entity-thread-captured position of a member, or null when offline, unreported,
     * or hidden. Reading this never touches another entity off its region.
     */
    public org.bukkit.Location positionOf(UUID playerId) {
        return playerId == null ? null : lastPositions.get(playerId);
    }

    /** Cached display name captured on the member's own thread (null if never seen). */
    public String nameOf(UUID playerId) {
        return playerId == null ? null : memberNames.get(playerId);
    }

    public boolean toggleTrack(UUID playerId) {
        boolean next = !showsTracker(playerId);
        trackPref.put(playerId, next);
        saveAsync();
        return next;
    }

    public boolean toggleHide(UUID playerId) {
        boolean next = !isHidden(playerId);
        hidePref.put(playerId, next);
        if (next) lastPositions.remove(playerId);
        saveAsync();
        return next;
    }

    private void trackerTick() {
        boolean tracking = trackingEnabled();
        for (Party party : parties.values()) {
            if (tracking) {
                org.bukkit.Location ping = party.pingPoint();
                if (ping != null && ping.getWorld() != null
                        && plugin.getConfig().getBoolean("party.ping_beam", true)) {
                    plugin.scheduler().runAt(ping, () -> spawnPingBeam(ping));
                }
            }
            for (UUID id : party.getMembers()) {
                Player member = Bukkit.getPlayer(id);
                if (member == null || !member.isOnline()) continue;
                plugin.scheduler().runEntity(member, () -> tickMember(party, member, tracking), () -> { });
            }
        }
    }

    /** Runs on the member's own entity thread. Position capture always runs (the sidebar
     *  distance and party map both read the cache); only the HUD honors the toggle. */
    private void tickMember(Party party, Player member, boolean tracking) {
        memberNames.put(member.getUniqueId(), member.getName());
        if (!isHidden(member.getUniqueId())) {
            lastPositions.put(member.getUniqueId(), member.getLocation().clone());
        }
        if (!tracking || !showsTracker(member.getUniqueId())) return;
        String hud = trackerHud(party, member);
        if (hud != null) com.aegisguard.util.Text.actionBar(member, hud);
    }

    private String trackerHud(Party party, Player viewer) {
        org.bukkit.Location from = viewer.getLocation();
        record Entry(double distance, String text) { }
        List<Entry> entries = new ArrayList<>();
        for (UUID otherId : party.getMembers()) {
            if (otherId.equals(viewer.getUniqueId()) || isHidden(otherId)) continue;
            org.bukkit.Location target = lastPositions.get(otherId);
            String name = memberNames.get(otherId);
            if (target == null || name == null) continue;
            boolean sameWorld = target.getWorld() != null && target.getWorld().equals(from.getWorld());
            double distance = sameWorld ? from.distance(target) : Double.MAX_VALUE;
            entries.add(new Entry(distance, "§f" + name + "§7" + (sameWorld
                    ? " " + compassArrow(from, target) + " " + (int) Math.round(distance) + "m"
                    : " §8·other realm")));
        }
        org.bukkit.Location ping = party.pingPoint();
        if (ping != null && ping.getWorld() != null && ping.getWorld().equals(from.getWorld())) {
            double distance = from.distance(ping);
            entries.add(new Entry(distance, "§d◎ ping§7 " + compassArrow(from, ping)
                    + " " + (int) Math.round(distance) + "m"));
        }
        if (entries.isEmpty()) return null;
        int max = Math.max(1, plugin.getConfig().getInt("party.tracker_max_entries", 3));
        entries.sort(Comparator.comparingDouble(Entry::distance));
        StringBuilder hud = new StringBuilder("§e⚑ ");
        for (int i = 0; i < Math.min(max, entries.size()); i++) {
            if (i > 0) hud.append("  §8•  ");
            hud.append(entries.get(i).text());
        }
        return hud.toString();
    }

    /** 8-sector arrow for the bearing from viewer to target, relative to the viewer's yaw. */
    private String compassArrow(org.bukkit.Location from, org.bukkit.Location to) {
        double bearing = Math.toDegrees(Math.atan2(-(to.getX() - from.getX()),
                to.getZ() - from.getZ()));
        double rel = ((bearing - from.getYaw()) % 360 + 540) % 360 - 180;
        if (rel >= -22.5 && rel < 22.5) return "↑";
        if (rel >= 22.5 && rel < 67.5) return "↗";
        if (rel >= 67.5 && rel < 112.5) return "→";
        if (rel >= 112.5 && rel < 157.5) return "↘";
        if (rel >= -67.5 && rel < -22.5) return "↖";
        if (rel >= -112.5 && rel < -67.5) return "←";
        if (rel >= -157.5 && rel < -112.5) return "↙";
        return "↓";
    }

    /** Members can mark a spot the whole party sees on the tracker + a light beam. */
    public Party plantPing(Player member) {
        if (member == null || !trackingEnabled()
                || !plugin.getConfig().getBoolean("party.ping_enabled", true)) return null;
        Party party = partyOf(member.getUniqueId());
        if (party == null) return null;
        long now = System.currentTimeMillis();
        Long last = lastPingAt.get(member.getUniqueId());
        if (last != null && now - last < 5000L) return null;
        lastPingAt.put(member.getUniqueId(), now);
        org.bukkit.block.Block looked = member.getTargetBlockExact(64);
        org.bukkit.Location point = looked != null
                ? looked.getLocation().add(0.5, 1.0, 0.5) : member.getLocation();
        long ttl = Math.max(10L, plugin.getConfig().getLong("party.ping_expire_seconds", 90L)) * 1000L;
        party.ping(point, now + ttl);
        return party;
    }

    /** A short column of light at the ping — spawned on the ping's own region tick. */
    private void spawnPingBeam(org.bukkit.Location point) {
        try {
            point.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, point, 30,
                    0.25, 4.5, 0.25, 0.005);
        } catch (Throwable ignored) { }
    }

    /** All online member UUIDs of the player's party (for notifications). */
    public List<Player> onlineMembers(UUID playerId) {
        Party party = partyOf(playerId);
        List<Player> out = new ArrayList<>();
        if (party == null) return out;
        for (UUID id : party.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Events

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Membership persists through relog — the sidebar shows them offline.
        refreshSidebar();
    }

    private String title(Party party) {
        String name = party.getName();
        return (name == null || name.isBlank()) ? "Party" : name;
    }

    private String cleanName(String name, String fallback) {
        if (name == null || name.isBlank()) return fallback + "'s Party";
        String stripped = name.replaceAll("[^\\p{L}\\p{N} _'-]", "").trim();
        return stripped.isEmpty() ? fallback + "'s Party"
                : stripped.substring(0, Math.min(stripped.length(), 24));
    }

    private void refreshSidebar() {
        if (sidebar != null) sidebar.requestRefresh();
    }

    /** Persists once, then clears all parties (plugin disable / module off). */
    public void shutdown() {
        if (syncTicker != null) {
            try { plugin.scheduler().cancel(syncTicker); } catch (Throwable ignored) { }
            syncTicker = null;
        }
        if (trackerTicker != null) {
            try { plugin.scheduler().cancel(trackerTicker); } catch (Throwable ignored) { }
            trackerTicker = null;
        }
        save();
        parties.clear();
        partyByPlayer.clear();
        lastInviteAt.clear();
        remotePresence.clear();
        lastPositions.clear();
        memberNames.clear();
        lastPingAt.clear();
    }

    // ------------------------------------------------------------------
    // Persistence — parties.yml, mirroring alliances.yml's safe-write style.

    private void load() {
        if (!storeFile.isFile()) return;
        YamlConfiguration data;
        try {
            data = YamlConfiguration.loadConfiguration(storeFile);
        } catch (Throwable t) {
            storeHealthy = false;
            plugin.getLogger().severe("Could not read parties.yml; keeping it untouched: " + t.getMessage());
            return;
        }
        var list = data.getMapList("parties");
        for (Map<?, ?> row : list) {
            try {
                UUID id = UUID.fromString(String.valueOf(row.get("id")));
                UUID leader = UUID.fromString(String.valueOf(row.get("leader")));
                String name = row.get("name") == null ? null : String.valueOf(row.get("name"));
                Party party = new Party(leader, name, id);
                Object rawMembers = row.get("members");
                if (rawMembers instanceof java.util.List<?> memberList) {
                    for (Object m : memberList) {
                        try {
                            party.addMember(UUID.fromString(String.valueOf(m)));
                        } catch (Throwable ignored) { }
                    }
                }
                if (party.isEmpty()) continue;
                parties.put(party.getPartyId(), party);
                for (UUID member : party.getMembers()) partyByPlayer.put(member, party);
            } catch (Throwable skipped) {
                plugin.getLogger().warning("Skipping malformed parties.yml row: " + skipped.getMessage());
            }
        }
        for (Map<?, ?> row : data.getMapList("prefs")) {
            try {
                UUID id = UUID.fromString(String.valueOf(row.get("id")));
                trackPref.put(id, Boolean.parseBoolean(String.valueOf(row.get("track"))));
                hidePref.put(id, Boolean.parseBoolean(String.valueOf(row.get("hide"))));
            } catch (Throwable ignored) { }
        }
    }

    private void saveAsync() {
        plugin.scheduler().runAsync(this::save);
    }

    /** Writes parties.yml; refuses to clobber a file we failed to load. */
    public synchronized void save() {
        if (!storeHealthy || !enabled()) return;
        YamlConfiguration out = new YamlConfiguration();
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (Party party : parties.values()) {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", party.getPartyId().toString());
            row.put("name", party.getName());
            row.put("leader", party.getLeaderId().toString());
            java.util.List<String> members = new java.util.ArrayList<>();
            for (UUID m : party.getMembers()) members.add(m.toString());
            row.put("members", members);
            rows.add(row);
        }
        out.set("parties", rows);
        java.util.List<Map<String, Object>> prefRows = new java.util.ArrayList<>();
        Set<UUID> prefIds = new java.util.HashSet<>(trackPref.keySet());
        prefIds.addAll(hidePref.keySet());
        for (UUID id : prefIds) {
            Map<String, Object> row = new java.util.HashMap<>();
            row.put("id", id.toString());
            row.put("track", showsTracker(id));
            row.put("hide", isHidden(id));
            prefRows.add(row);
        }
        out.set("prefs", prefRows);
        try {
            out.save(storeFile);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not save parties.yml; changes remain in memory: " + e.getMessage());
        }
    }

    /** Immutable render model for the sidebar: title + member set + leader. */
    public record PartyView(String title, UUID leaderId, Set<UUID> memberIds) { }
}
