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
    private PartySidebarService sidebar;

    public PartyService(AegisGuard plugin) {
        this.plugin = plugin;
        this.storeFile = new java.io.File(plugin.getDataFolder(), "parties.yml");
        load();
    }

    public void setSidebar(PartySidebarService sidebar) { this.sidebar = sidebar; }

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
        return true;
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
        save();
        parties.clear();
        partyByPlayer.clear();
        lastInviteAt.clear();
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
        try {
            out.save(storeFile);
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not save parties.yml; changes remain in memory: " + e.getMessage());
        }
    }

    /** Immutable render model for the sidebar: title + member set + leader. */
    public record PartyView(String title, UUID leaderId, Set<UUID> memberIds) { }
}
