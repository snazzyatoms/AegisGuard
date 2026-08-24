package com.aegisguard.alliance;

import com.aegisguard.AegisGuard;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Milestone 7 - persistence and membership for alliances ({@code alliances.yml}).
 * Completely separate from {@link com.aegisguard.groups.GroupManager} (co-ownership).
 */
public class AllianceManager {

    private final AegisGuard plugin;
    private final File file;
    private final Map<UUID, Alliance> alliancesById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToAlliance = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();
    private volatile boolean dirty;
    private volatile boolean storageReady;
    private volatile boolean storageWriteWarningLogged;

    public AllianceManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "alliances.yml");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("alliance_access.enabled", true);
    }

    public int maxMembers() {
        return Math.max(2, plugin.getConfig().getInt("alliance_access.max_members", 20));
    }

    /** Minutes before invites expire. {@code 0} keeps legacy never-expire behavior. */
    public long inviteExpireMinutes() {
        return Math.max(0L, plugin.getConfig().getLong("alliance_access.invite_expire_minutes", 0L));
    }

    public boolean isInviteExpired(long invitedAtMillis) {
        long minutes = inviteExpireMinutes();
        if (minutes <= 0L || invitedAtMillis <= 0L) return false;
        return System.currentTimeMillis() - invitedAtMillis > minutes * 60_000L;
    }

    public boolean isToggleDisallowed(String key) {
        if (key == null || key.isBlank()) return false;
        String normalized = key.equalsIgnoreCase("friendly_pvp") || key.equalsIgnoreCase("pvp")
                ? "friendly_pvp" : key.toLowerCase(java.util.Locale.ROOT);
        return plugin.getConfig().getBoolean("alliance_access.disallow." + normalized, false);
    }

    public Collection<Alliance> all() {
        return List.copyOf(alliancesById.values());
    }

    public Alliance get(UUID id) {
        return id == null ? null : alliancesById.get(id);
    }

    public Alliance getByPlayer(UUID playerId) {
        if (playerId == null) return null;
        UUID allianceId = playerToAlliance.get(playerId);
        return allianceId == null ? null : alliancesById.get(allianceId);
    }

    public Alliance create(String name, UUID leaderId) {
        if (leaderId == null) return null;
        if (playerToAlliance.containsKey(leaderId)) return null;
        Alliance alliance = Alliance.create(name, leaderId);
        alliancesById.put(alliance.getId(), alliance);
        playerToAlliance.put(leaderId, alliance.getId());
        dirty = true;
        save();
        return alliance;
    }

    public String invite(Alliance alliance, UUID targetId) {
        if (alliance == null || targetId == null) return "alliance_invalid";
        if (alliance.isMember(targetId)) return "alliance_already_member";
        if (playerToAlliance.containsKey(targetId)) return "alliance_target_in_other";
        if (alliance.size() >= maxMembers()) return "alliance_full";
        alliance.addInvite(targetId, System.currentTimeMillis());
        dirty = true;
        saveAsync();
        return null;
    }

    public String accept(UUID playerId) {
        return accept(playerId, null);
    }

    /** Accept an invitation from a specific alliance when chosen in the roster UI. */
    public String accept(UUID playerId, UUID allianceId) {
        if (playerId == null) return "alliance_invalid";
        if (playerToAlliance.containsKey(playerId)) return "alliance_already_member";

        pruneExpiredInvites();

        Alliance invited = null;
        for (Alliance alliance : alliancesById.values()) {
            if (allianceId != null && !allianceId.equals(alliance.getId())) continue;
            if (!alliance.isInvited(playerId)) continue;
            Long invitedAt = alliance.getInvites().get(playerId);
            if (invitedAt != null && isInviteExpired(invitedAt)) {
                alliance.removeInvite(playerId);
                dirty = true;
                continue;
            }
            invited = alliance;
            break;
        }
        if (invited == null) return "alliance_no_invite";
        if (invited.size() >= maxMembers()) return "alliance_full";

        invited.addMember(playerId, System.currentTimeMillis());
        playerToAlliance.put(playerId, invited.getId());
        dirty = true;
        saveAsync();
        return null;
    }

    /** Leader-only cancellation of a pending invitation. */
    public String removeInvite(UUID leaderId, UUID allianceId, UUID targetId) {
        Alliance alliance = get(allianceId);
        if (alliance == null || leaderId == null || targetId == null) return "alliance_invalid";
        if (!alliance.isLeader(leaderId)) return "alliance_not_leader";
        if (!alliance.removeInvite(targetId)) return "alliance_no_invite";
        dirty = true;
        saveAsync();
        return null;
    }

    /** Decline a specific invitation without requiring alliance leadership. */
    public String decline(UUID playerId, UUID allianceId) {
        Alliance alliance = get(allianceId);
        if (alliance == null || playerId == null) return "alliance_invalid";
        if (!alliance.removeInvite(playerId)) return "alliance_no_invite";
        dirty = true;
        saveAsync();
        return null;
    }

    public int pruneExpiredInvites() {
        if (inviteExpireMinutes() <= 0L) return 0;
        int removed = 0;
        for (Alliance alliance : alliancesById.values()) {
            for (Map.Entry<UUID, Long> entry : List.copyOf(alliance.getInvites().entrySet())) {
                if (isInviteExpired(entry.getValue())) {
                    if (alliance.removeInvite(entry.getKey())) {
                        removed++;
                        dirty = true;
                    }
                }
            }
        }
        if (removed > 0) saveAsync();
        return removed;
    }

    public String leave(UUID playerId) {
        if (playerId == null) return "alliance_invalid";
        Alliance alliance = getByPlayer(playerId);
        if (alliance == null) return "alliance_not_member";
        if (alliance.isLeader(playerId)) return "alliance_leader_must_disband";

        alliance.removeMember(playerId);
        playerToAlliance.remove(playerId);
        dirty = true;
        saveAsync();
        return null;
    }

    public String disband(UUID leaderId) {
        Alliance alliance = getByPlayer(leaderId);
        if (alliance == null) return "alliance_not_member";
        if (!alliance.isLeader(leaderId)) return "alliance_not_leader";

        for (UUID member : new ArrayList<>(alliance.getMemberIds())) {
            playerToAlliance.remove(member);
        }
        alliancesById.remove(alliance.getId());
        dirty = true;
        saveAsync();
        return null;
    }

    public boolean isDirty() { return dirty; }

    public void load() {
        synchronized (ioLock) {
            if (!prepareStorageFile()) return;

            FileConfiguration data = YamlConfiguration.loadConfiguration(file);
            alliancesById.clear();
            playerToAlliance.clear();
            ConfigurationSection root = data.getConfigurationSection("alliances");
            if (root == null) {
                dirty = false;
                return;
            }

            for (String key : root.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    ConfigurationSection sec = root.getConfigurationSection(key);
                    if (sec == null) continue;
                    UUID leader = UUID.fromString(sec.getString("leader"));
                    Alliance alliance = new Alliance(id, sec.getString("name", "Alliance"),
                            leader, sec.getLong("created-at", System.currentTimeMillis()));

                    ConfigurationSection members = sec.getConfigurationSection("members");
                    if (members != null) {
                        for (String memberKey : members.getKeys(false)) {
                            UUID memberId = UUID.fromString(memberKey);
                            alliance.addMember(memberId, members.getLong(memberKey, alliance.getCreatedAt()));
                            playerToAlliance.put(memberId, id);
                        }
                    }
                    if (!alliance.isMember(leader)) {
                        alliance.addMember(leader, alliance.getCreatedAt());
                        playerToAlliance.put(leader, id);
                    }

                    ConfigurationSection invites = sec.getConfigurationSection("invites");
                    if (invites != null) {
                        for (String inviteKey : invites.getKeys(false)) {
                            alliance.addInvite(UUID.fromString(inviteKey), invites.getLong(inviteKey));
                        }
                    }
                    alliancesById.put(id, alliance);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to load alliance " + key + ": " + ex.getMessage());
                }
            }
            dirty = false;
            plugin.console().info("log_alliances_loaded", "Loaded {COUNT} alliance(s).", "COUNT", String.valueOf(alliancesById.size()));
        }
    }

    private boolean prepareStorageFile() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create plugin data directory " + parent);
            }
            if (!file.exists() && !file.createNewFile()) {
                throw new IOException("Could not create " + file);
            }
            if (!file.isFile() || !file.canRead()) {
                throw new IOException(file + " is not a readable file");
            }
            storageReady = true;
            storageWriteWarningLogged = false;
            return true;
        } catch (IOException error) {
            storageReady = false;
            plugin.getLogger().log(Level.SEVERE,
                    "Could not prepare alliances.yml; existing in-memory alliance state was retained and will not be replaced by an empty load.",
                    error);
            return false;
        }
    }

    public void save() {
        synchronized (ioLock) {
            if (!storageReady) {
                if (!storageWriteWarningLogged) {
                    storageWriteWarningLogged = true;
                    plugin.getLogger().severe("Refusing to save alliances.yml because alliance storage did not "
                            + "initialize successfully. Repair the data directory or file permissions first.");
                }
                return;
            }
            if (!dirty && file.exists()) return;
            YamlConfiguration out = new YamlConfiguration();
            for (Alliance alliance : alliancesById.values()) {
                String base = "alliances." + alliance.getId();
                out.set(base + ".name", alliance.getName());
                out.set(base + ".leader", alliance.getLeaderId().toString());
                out.set(base + ".created-at", alliance.getCreatedAt());
                for (Map.Entry<UUID, Long> entry : alliance.getMembers().entrySet()) {
                    out.set(base + ".members." + entry.getKey(), entry.getValue());
                }
                for (Map.Entry<UUID, Long> entry : alliance.getInvites().entrySet()) {
                    out.set(base + ".invites." + entry.getKey(), entry.getValue());
                }
            }
            try {
                out.save(file);
                dirty = false;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to save alliances.yml. Alliance changes remain dirty and will be retried.", e);
            }
        }
    }

    public void saveAsync() {
        dirty = true;
        plugin.runGlobalAsync(this::save);
    }
}
