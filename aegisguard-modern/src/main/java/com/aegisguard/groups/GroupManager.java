package com.aegisguard.groups;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GroupManager {

    private final AegisGuard plugin;
    private final File file;
    private final Map<UUID, PlotGroup> groupsById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToGroup = new ConcurrentHashMap<>();
    private final Object ioLock = new Object();
    private volatile boolean dirty;
    private volatile boolean storageReady;
    private volatile boolean storageWriteWarningLogged;

    public GroupManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "groups.yml");
    }

    public void load() {
        synchronized (ioLock) {
            if (!prepareStorageFile()) return;

            FileConfiguration data = YamlConfiguration.loadConfiguration(file);
            groupsById.clear();
            playerToGroup.clear();
            ConfigurationSection groupsSec = data.getConfigurationSection("groups");
            if (groupsSec == null) {
                dirty = false;
                return;
            }

            for (String key : groupsSec.getKeys(false)) {
                try {
                    UUID groupId = UUID.fromString(key);
                    ConfigurationSection sec = groupsSec.getConfigurationSection(key);
                    if (sec == null) continue;

                    String name = sec.getString("name", "Group");
                    UUID leader = UUID.fromString(sec.getString("leader"));
                    long createdAt = sec.getLong("created-at", System.currentTimeMillis());
                    UUID linkedPlotId = null;
                    String linkedPlotRaw = sec.getString("linked-plot");
                    if (linkedPlotRaw != null && !linkedPlotRaw.isBlank()) {
                        linkedPlotId = UUID.fromString(linkedPlotRaw);
                    }

                    PlotGroup group = new PlotGroup(
                            groupId,
                            name,
                            leader,
                            createdAt,
                            sec.getDouble("treasury-balance", 0.0D),
                            linkedPlotId,
                            sec.getBoolean("starter-claim.used", false),
                            sec.getLong("starter-claim.claimed-at", 0L),
                            sec.getLong("starter-claim.removal-lock-until", 0L)
                    );

                    ConfigurationSection membersSec = sec.getConfigurationSection("members");
                    if (membersSec != null) {
                        for (String memberKey : membersSec.getKeys(false)) {
                            try {
                                UUID memberId = UUID.fromString(memberKey);
                                long joinedAt = membersSec.getLong(memberKey, createdAt);
                                group.addMember(memberId, joinedAt);
                                playerToGroup.put(memberId, groupId);
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }

                    if (!group.isMember(leader)) {
                        group.addMember(leader, createdAt);
                        playerToGroup.put(leader, groupId);
                    }

                    ConfigurationSection invitesSec = sec.getConfigurationSection("invites");
                    if (invitesSec != null) {
                        for (String inviteKey : invitesSec.getKeys(false)) {
                            try {
                                group.addInvite(UUID.fromString(inviteKey), invitesSec.getLong(inviteKey, System.currentTimeMillis()));
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }

                    groupsById.put(groupId, group);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to load group " + key + ": " + ex.getMessage());
                }
            }

            dirty = false;
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
                    "Could not prepare groups.yml; existing in-memory group state was retained and will not be replaced by an empty load.",
                    error);
            return false;
        }
    }

    public void save() {
        synchronized (ioLock) {
            if (!storageReady) {
                if (!storageWriteWarningLogged) {
                    storageWriteWarningLogged = true;
                    plugin.getLogger().severe("Refusing to save groups.yml because group storage did not "
                            + "initialize successfully. Repair the data directory or file permissions first.");
                }
                return;
            }
            FileConfiguration data = new YamlConfiguration();
            for (PlotGroup group : groupsById.values()) {
                String path = "groups." + group.getId();
                data.set(path + ".name", group.getName());
                data.set(path + ".leader", group.getLeader().toString());
                data.set(path + ".created-at", group.getCreatedAt());
                data.set(path + ".treasury-balance", group.getTreasuryBalance());
                data.set(path + ".linked-plot", group.getLinkedPlotId() == null ? null : group.getLinkedPlotId().toString());
                data.set(path + ".starter-claim.used", group.isStarterClaimUsed());
                data.set(path + ".starter-claim.claimed-at", group.getStarterClaimedAt());
                data.set(path + ".starter-claim.removal-lock-until", group.getStarterRemovalLockUntil());

                for (Map.Entry<UUID, Long> member : group.getMembers().entrySet()) {
                    if (member.getKey() != null) {
                        data.set(path + ".members." + member.getKey(), member.getValue());
                    }
                }

                for (Map.Entry<UUID, Long> invite : group.getPendingInvites().entrySet()) {
                    if (invite.getKey() != null) {
                        data.set(path + ".invites." + invite.getKey(), invite.getValue());
                    }
                }
            }

            try {
                data.save(file);
                dirty = false;
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to save groups.yml. Group changes remain dirty and will be retried.", ex);
            }
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public PlotGroup getGroup(UUID groupId) {
        return groupId == null ? null : groupsById.get(groupId);
    }

    public PlotGroup getGroupForPlayer(UUID playerId) {
        if (playerId == null) return null;
        UUID groupId = playerToGroup.get(playerId);
        return groupId == null ? null : groupsById.get(groupId);
    }

    public PlotGroup getGroupByName(String name) {
        if (name == null || name.isBlank()) return null;
        String needle = normalizeName(name);
        for (PlotGroup group : groupsById.values()) {
            if (needle.equals(normalizeName(group.getName()))) {
                return group;
            }
        }
        return null;
    }

    public Collection<PlotGroup> getAllGroups() {
        return groupsById.values();
    }

    public boolean isInGroup(UUID playerId) {
        return getGroupForPlayer(playerId) != null;
    }

    public PlotGroup createGroup(UUID leader, String name) {
        if (leader == null || name == null || name.isBlank()) return null;
        if (isInGroup(leader) || getGroupByName(name) != null) return null;

        long now = System.currentTimeMillis();
        PlotGroup group = new PlotGroup(UUID.randomUUID(), name.trim(), leader, now);
        group.addMember(leader, now);
        groupsById.put(group.getId(), group);
        playerToGroup.put(leader, group.getId());
        dirty = true;
        return group;
    }

    public boolean invitePlayer(PlotGroup group, UUID target) {
        if (group == null || target == null || group.isMember(target)) return false;
        group.addInvite(target, System.currentTimeMillis());
        dirty = true;
        return true;
    }

    public boolean acceptInvite(PlotGroup group, UUID playerId) {
        if (group == null || playerId == null || !group.hasInvite(playerId) || isInGroup(playerId)) return false;
        group.removeInvite(playerId);
        group.addMember(playerId, System.currentTimeMillis());
        playerToGroup.put(playerId, group.getId());
        dirty = true;
        return true;
    }

    public boolean leaveGroup(PlotGroup group, UUID playerId) {
        if (group == null || playerId == null || !group.isMember(playerId)) return false;
        if (Objects.equals(group.getLeader(), playerId)) return false;
        group.removeMember(playerId);
        playerToGroup.remove(playerId);
        cleanupIfEmpty(group);
        dirty = true;
        return true;
    }

    public boolean kickMember(PlotGroup group, UUID target) {
        if (group == null || target == null || !group.isMember(target) || Objects.equals(group.getLeader(), target)) return false;
        group.removeMember(target);
        playerToGroup.remove(target);
        cleanupIfEmpty(group);
        dirty = true;
        return true;
    }

    public void disbandGroup(PlotGroup group) {
        if (group == null) return;
        groupsById.remove(group.getId());
        for (UUID member : new ArrayList<>(group.getMemberIds())) {
            playerToGroup.remove(member);
        }
        dirty = true;
    }

    public void cleanupMissingPlotLinks() {
        for (PlotGroup group : groupsById.values()) {
            UUID plotId = group.getLinkedPlotId();
            if (plotId == null) continue;
            boolean exists = plugin.store().getAllPlots().stream().anyMatch(plot -> plot != null && plotId.equals(plot.getPlotId()));
            if (!exists) {
                group.setLinkedPlotId(null);
                dirty = true;
            }
        }
    }

    public int getEligibleStarterMemberCount(PlotGroup group) {
        if (group == null) return 0;
        long now = System.currentTimeMillis();
        long minAgeMillis = Math.max(0L, plugin.getConfig().getLong("group_plots.starter.member_age_minutes", 30L)) * 60_000L;
        int count = 0;
        for (Map.Entry<UUID, Long> entry : group.getMembers().entrySet()) {
            if (entry.getKey() == null) continue;
            if (now - entry.getValue() >= minAgeMillis) {
                count++;
            }
        }
        return count;
    }

    public int getStarterMaxFreeMembers() {
        return Math.max(1, plugin.getConfig().getInt("group_plots.max_members_for_free_bonus", 5));
    }

    public int getStarterMaxArea(PlotGroup group) {
        int eligible = getEligibleStarterMemberCount(group);
        int baseArea = Math.max(1, plugin.getConfig().getInt("group_plots.starter.base_area", 625));
        int bonusPerMember = Math.max(0, plugin.getConfig().getInt("group_plots.starter.area_per_member", 225));
        int maxArea = Math.max(baseArea, plugin.getConfig().getInt("group_plots.starter.max_area", 2500));
        if (eligible <= 0) return baseArea;
        int effective = Math.min(eligible, getStarterMaxFreeMembers());
        return Math.min(maxArea, baseArea + Math.max(0, effective - 1) * bonusPerMember);
    }

    public boolean qualifiesForFreeStarterClaim(PlotGroup group, int area) {
        if (group == null || area <= 0 || group.isStarterClaimUsed()) return false;
        int minMembers = Math.max(1, plugin.getConfig().getInt("group_plots.min_members_to_claim", 2));
        int eligible = getEligibleStarterMemberCount(group);
        if (eligible < minMembers) return false;
        if (eligible > getStarterMaxFreeMembers()) return false;
        return area <= getStarterMaxArea(group);
    }

    public void markStarterClaimUsed(PlotGroup group) {
        if (group == null) return;
        long now = System.currentTimeMillis();
        group.setStarterClaimUsed(true);
        group.setStarterClaimedAt(now);
        long lockMinutes = Math.max(0L, plugin.getConfig().getLong("group_plots.starter.removal_lock_minutes", 120L));
        group.setStarterRemovalLockUntil(now + (lockMinutes * 60_000L));
        dirty = true;
    }

    public boolean canRemoveMemberNow(PlotGroup group) {
        return group == null || !group.isStarterRemovalLocked(System.currentTimeMillis());
    }

    public String describeStarterLockRemaining(PlotGroup group) {
        if (group == null) return "0";
        long remaining = Math.max(0L, group.getStarterRemovalLockUntil() - System.currentTimeMillis());
        long minutes = Math.max(1L, (long) Math.ceil(remaining / 60_000.0D));
        return String.valueOf(minutes);
    }

    public void attachPlot(PlotGroup group, Plot plot) {
        if (group == null || plot == null) return;
        group.setLinkedPlotId(plot.getPlotId());
        dirty = true;
    }

    public String getMemberName(UUID uuid) {
        if (uuid == null) return "Unknown";
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name == null || name.isBlank() ? uuid.toString() : name;
    }

    public List<String> getSortedMemberNames(PlotGroup group) {
        if (group == null) return List.of();
        List<String> names = new ArrayList<>();
        for (UUID memberId : group.getMemberIds()) {
            names.add(getMemberName(memberId));
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public double getRequiredClaimCost(int area) {
        if (area <= 0) return 0.0D;
        double perBlock = Math.max(0.0D, plugin.getConfig().getDouble("group_plots.economy.cost_per_block", 1.0D));
        return area * perBlock;
    }

    public String normalizeName(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    private void cleanupIfEmpty(PlotGroup group) {
        if (group != null && group.size() <= 0) {
            groupsById.remove(group.getId());
        }
    }
}
