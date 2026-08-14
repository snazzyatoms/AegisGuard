package com.aegisguard.snapshots;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.snapshots.ClaimSnapshot.SnapshotType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages claim snapshots for rollback functionality.
 * Takes snapshots before risky operations (merge, expansion) and allows rollback.
 */
public class SnapshotManager {
    
    private final AegisGuard plugin;
    private final File file;
    private FileConfiguration data;
    
    // In-memory cache: snapshotId -> snapshot
    private final Map<UUID, ClaimSnapshot> snapshots = new ConcurrentHashMap<>();
    
    private volatile boolean isDirty = false;
    
    public SnapshotManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claim-snapshots.yml");
    }
    
    /**
     * Take a snapshot of a plot before a risky operation.
     * @return The created snapshot
     */
    public ClaimSnapshot createSnapshot(Plot plot, SnapshotType type, String reason, UUID triggeredBy) {
        ClaimSnapshot snapshot = new ClaimSnapshot(plot, type, reason, triggeredBy);
        snapshots.put(snapshot.getSnapshotId(), snapshot);
        setDirty(true);
        
        plugin.console().info("log_snapshots_created",
                "[Snapshots] Created snapshot {ID} for plot {PLOT} ({TYPE})",
                "ID", String.valueOf(snapshot.getSnapshotId()),
                "PLOT", String.valueOf(plot.getPlotId()),
                "TYPE", String.valueOf(type));
        
        // Auto-cleanup old snapshots if limit exceeded
        pruneOldSnapshots();
        
        return snapshot;
    }

    public int createSnapshotsForServerZones(UUID triggeredBy, String reason, SnapshotType type) {
        if (plugin.store() == null) return 0;
        int count = 0;
        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null || !plot.isServerZone()) continue;
            createSnapshot(plot, type == null ? SnapshotType.MANUAL : type, reason, triggeredBy);
            count++;
        }
        return count;
    }

    public boolean isScheduledEnabled() {
        return plugin.getConfig().getBoolean("snapshots.enabled", true)
                && plugin.getConfig().getBoolean("snapshots.scheduled.enabled", false);
    }

    public int getScheduledIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("snapshots.scheduled.interval_minutes", 360));
    }

    /**
     * Cycle Off → 15 → 60 → 360 → 1440 → Off. Returns next interval minutes, or 0 when off.
     */
    public int cycleScheduledInterval() {
        boolean enabled = plugin.getConfig().getBoolean("snapshots.scheduled.enabled", false);
        int current = plugin.getConfig().getInt("snapshots.scheduled.interval_minutes", 360);
        int[] steps = {15, 60, 360, 1440};
        int nextMinutes = 0;
        boolean nextEnabled = false;
        if (!enabled) {
            nextEnabled = true;
            nextMinutes = 15;
        } else {
            int idx = -1;
            for (int i = 0; i < steps.length; i++) {
                if (steps[i] == current) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                nextEnabled = true;
                nextMinutes = 15;
            } else if (idx + 1 < steps.length) {
                nextEnabled = true;
                nextMinutes = steps[idx + 1];
            } else {
                nextEnabled = false;
                nextMinutes = 360;
            }
        }
        plugin.getConfig().set("snapshots.scheduled.enabled", nextEnabled);
        plugin.getConfig().set("snapshots.scheduled.interval_minutes", nextMinutes);
        plugin.getConfig().set("snapshots.scheduled.targets", "server_zones");
        if (plugin.cfg() != null && plugin.cfg().raw() != null && plugin.cfg().raw() != plugin.getConfig()) {
            plugin.cfg().raw().set("snapshots.scheduled.enabled", nextEnabled);
            plugin.cfg().raw().set("snapshots.scheduled.interval_minutes", nextMinutes);
            plugin.cfg().raw().set("snapshots.scheduled.targets", "server_zones");
        }
        return nextEnabled ? nextMinutes : 0;
    }

    public int runScheduledPass() {
        if (!isScheduledEnabled()) return 0;
        return createSnapshotsForServerZones(null, "Scheduled server-zone snapshot", SnapshotType.SCHEDULED);
    }
    
    /**
     * Rollback a plot to a previous snapshot.
     * @return true if rollback succeeded
     */
    public boolean rollback(UUID snapshotId) {
        ClaimSnapshot snapshot = snapshots.get(snapshotId);
        if (snapshot == null) {
            plugin.console().warning("log_snapshots_rollback_missing",
                    "[Snapshots] Cannot rollback: snapshot {ID} not found",
                    "ID", String.valueOf(snapshotId));
            return false;
        }

        Plot currentPlot = plugin.store().getPlot(snapshot.getOwner(), snapshot.getPlotId());
        if (currentPlot == null) {
            currentPlot = new Plot(
                    snapshot.getPlotId(),
                    snapshot.getOwner(),
                    snapshot.getOwnerName(),
                    snapshot.getWorldName(),
                    snapshot.getX1(),
                    snapshot.getZ1(),
                    snapshot.getX2(),
                    snapshot.getZ2()
            );
        } else {
            plugin.store().removePlot(currentPlot.getOwner(), currentPlot.getPlotId());
        }

        restorePlotState(currentPlot, snapshot);

        plugin.store().addPlot(currentPlot);
        plugin.store().savePlotSync(currentPlot);
        plugin.store().setDirty(true);
        
        plugin.console().info("log_snapshots_rolled_back",
                "[Snapshots] Rolled back plot {PLOT} to snapshot {ID}",
                "PLOT", String.valueOf(currentPlot.getPlotId()),
                "ID", String.valueOf(snapshotId));
        
        return true;
    }
    
    /**
     * Get all snapshots for a specific plot.
     */
    public List<ClaimSnapshot> getSnapshotsForPlot(UUID plotId) {
        return snapshots.values().stream()
                .filter(s -> s.getPlotId().equals(plotId))
                .sorted(Comparator.comparingLong(ClaimSnapshot::getTimestamp).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Get all snapshots (for admin GUI).
     */
    public List<ClaimSnapshot> getAllSnapshots() {
        return new ArrayList<>(snapshots.values()).stream()
                .sorted(Comparator.comparingLong(ClaimSnapshot::getTimestamp).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Get a specific snapshot by ID.
     */
    public ClaimSnapshot getSnapshot(UUID snapshotId) {
        return snapshots.get(snapshotId);
    }
    
    /**
     * Delete a snapshot.
     */
    public boolean deleteSnapshot(UUID snapshotId) {
        ClaimSnapshot removed = snapshots.remove(snapshotId);
        if (removed != null) {
            setDirty(true);
            plugin.console().info("log_snapshots_deleted",
                "[Snapshots] Deleted snapshot {ID}",
                "ID", String.valueOf(snapshotId));
            return true;
        }
        return false;
    }
    
    /**
     * Prune old snapshots based on config limits.
     */
    private void pruneOldSnapshots() {
        int maxSnapshots = plugin.getConfig().getInt("snapshots.max_snapshots", 100);
        long keepMinutes = plugin.getConfig().getLong("snapshots.keep_minutes", 10080); // 7 days default
        
        if (snapshots.size() <= maxSnapshots && keepMinutes <= 0) return;
        
        long cutoff = System.currentTimeMillis() - (keepMinutes * 60_000L);
        List<ClaimSnapshot> toRemove = new ArrayList<>();
        
        // Remove by age
        if (keepMinutes > 0) {
            for (ClaimSnapshot snapshot : snapshots.values()) {
                if (snapshot.getTimestamp() < cutoff) {
                    toRemove.add(snapshot);
                }
            }
        }
        
        // Remove oldest if over limit
        if (snapshots.size() > maxSnapshots) {
            List<ClaimSnapshot> sorted = snapshots.values().stream()
                    .sorted(Comparator.comparingLong(ClaimSnapshot::getTimestamp))
                    .collect(Collectors.toList());
            
            int excess = snapshots.size() - maxSnapshots;
            for (int i = 0; i < excess && i < sorted.size(); i++) {
                if (!toRemove.contains(sorted.get(i))) {
                    toRemove.add(sorted.get(i));
                }
            }
        }
        
        // Remove
        for (ClaimSnapshot snapshot : toRemove) {
            snapshots.remove(snapshot.getSnapshotId());
        }
        
        if (!toRemove.isEmpty()) {
            setDirty(true);
            plugin.console().info("log_snapshots_pruned",
                "[Snapshots] Pruned {COUNT} old snapshots",
                "COUNT", String.valueOf(toRemove.size()));
        }
    }
    
    // ============================================================
    // Persistence
    // ============================================================
    
    public synchronized void load() {
        try {
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                file.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create claim-snapshots.yml", e);
        }
        
        data = YamlConfiguration.loadConfiguration(file);
        snapshots.clear();
        
        if (data.isConfigurationSection("snapshots")) {
            for (String key : data.getConfigurationSection("snapshots").getKeys(false)) {
                try {
                    String path = "snapshots." + key;
                    
                    UUID snapshotId = UUID.fromString(key);
                    UUID plotId = UUID.fromString(data.getString(path + ".plotId"));
                    UUID owner = UUID.fromString(data.getString(path + ".owner"));
                    String worldName = data.getString(path + ".world", "world");
                    
                    int x1 = data.getInt(path + ".x1");
                    int z1 = data.getInt(path + ".z1");
                    int x2 = data.getInt(path + ".x2");
                    int z2 = data.getInt(path + ".z2");
                    
                    long timestamp = data.getLong(path + ".timestamp");
                    SnapshotType type = SnapshotType.valueOf(data.getString(path + ".type", "MANUAL"));
                    String reason = data.getString(path + ".reason", "");
                    
                    UUID triggeredBy = null;
                    String actorStr = data.getString(path + ".triggeredBy", "");
                    if (!actorStr.isEmpty()) {
                        try { triggeredBy = UUID.fromString(actorStr); } catch (Throwable ignored) {}
                    }
                    
                    Map<String, Boolean> flags = new HashMap<>();
                    if (data.isConfigurationSection(path + ".flags")) {
                        ConfigurationSection flagsSec = data.getConfigurationSection(path + ".flags");
                        if (flagsSec != null) {
                            for (String flagKey : flagsSec.getKeys(false)) {
                                flags.put(flagKey, flagsSec.getBoolean(flagKey));
                            }
                        }
                    }

                    Map<UUID, String> members = new HashMap<>();
                    if (data.isConfigurationSection(path + ".members")) {
                        ConfigurationSection membersSec = data.getConfigurationSection(path + ".members");
                        if (membersSec != null) {
                            for (String memberKey : membersSec.getKeys(false)) {
                                try {
                                    members.put(UUID.fromString(memberKey), membersSec.getString(memberKey, "visitor"));
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }

                    List<UUID> bannedPlayers = new ArrayList<>();
                    for (String bannedKey : data.getStringList(path + ".banned")) {
                        try {
                            bannedPlayers.add(UUID.fromString(bannedKey));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    
                    ClaimSnapshot snapshot = new ClaimSnapshot(
                            snapshotId, plotId, owner, worldName,
                            x1, z1, x2, z2, timestamp,
                            type, reason, triggeredBy,
                            data.getString(path + ".ownerName"),
                            data.getString(path + ".plotName"),
                            data.getString(path + ".description"),
                            data.getString(path + ".welcomeMessage"),
                            data.getString(path + ".farewellMessage"),
                            data.getString(path + ".entryTitle"),
                            data.getString(path + ".entrySubtitle"),
                            data.getString(path + ".customBiome"),
                            data.getString(path + ".plotStatus"),
                            data.getBoolean(path + ".serverWarp", false),
                            data.getBoolean(path + ".groupPlot", false),
                            data.getDouble(path + ".treasuryBalance", 0.0),
                            parseUuid(data.getString(path + ".groupId")),
                            data.getString(path + ".groupName"),
                            flags, members, bannedPlayers
                    );
                    
                    snapshots.put(snapshotId, snapshot);
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load snapshot: " + key, e);
                }
            }
        }
        
        plugin.console().info("log_snapshots_loaded",
                "[Snapshots] Loaded {COUNT} snapshots",
                "COUNT", String.valueOf(snapshots.size()));
        setDirty(false);
    }
    
    public synchronized void save() {
        if (data == null) return;
        
        data.set("snapshots", null);
        
        for (ClaimSnapshot snapshot : snapshots.values()) {
            String path = "snapshots." + snapshot.getSnapshotId();
            
            data.set(path + ".plotId", snapshot.getPlotId().toString());
            data.set(path + ".owner", snapshot.getOwner().toString());
            data.set(path + ".world", snapshot.getWorldName());
            
            data.set(path + ".x1", snapshot.getX1());
            data.set(path + ".z1", snapshot.getZ1());
            data.set(path + ".x2", snapshot.getX2());
            data.set(path + ".z2", snapshot.getZ2());
            
            data.set(path + ".timestamp", snapshot.getTimestamp());
            data.set(path + ".type", snapshot.getType().name());
            data.set(path + ".reason", snapshot.getReason());
            data.set(path + ".triggeredBy", snapshot.getTriggeredBy() == null ? "" : snapshot.getTriggeredBy().toString());
            data.set(path + ".ownerName", snapshot.getOwnerName());
            data.set(path + ".plotName", snapshot.getPlotName());
            data.set(path + ".description", snapshot.getDescription());
            data.set(path + ".welcomeMessage", snapshot.getWelcomeMessage());
            data.set(path + ".farewellMessage", snapshot.getFarewellMessage());
            data.set(path + ".entryTitle", snapshot.getEntryTitle());
            data.set(path + ".entrySubtitle", snapshot.getEntrySubtitle());
            data.set(path + ".customBiome", snapshot.getCustomBiome());
            data.set(path + ".plotStatus", snapshot.getPlotStatus());
            data.set(path + ".serverWarp", snapshot.isServerWarp());
            data.set(path + ".groupPlot", snapshot.isGroupPlot());
            data.set(path + ".treasuryBalance", snapshot.getTreasuryBalance());
            data.set(path + ".groupId", snapshot.getGroupId() == null ? null : snapshot.getGroupId().toString());
            data.set(path + ".groupName", snapshot.getGroupName());

            data.set(path + ".flags", null);
            for (Map.Entry<String, Boolean> flag : snapshot.getFlags().entrySet()) {
                data.set(path + ".flags." + flag.getKey(), flag.getValue());
            }

            data.set(path + ".members", null);
            for (Map.Entry<UUID, String> member : snapshot.getMembers().entrySet()) {
                if (member.getKey() == null) continue;
                data.set(path + ".members." + member.getKey(), member.getValue());
            }

            List<String> banned = snapshot.getBannedPlayers().stream()
                    .filter(Objects::nonNull)
                    .map(UUID::toString)
                    .toList();
            data.set(path + ".banned", banned);
        }
        
        try {
            data.save(file);
            isDirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save claim-snapshots.yml", e);
        }
    }

    public ClaimSnapshot getLatestSnapshotForPlot(UUID plotId) {
        List<ClaimSnapshot> matches = getSnapshotsForPlot(plotId);
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Applies every tracked field of {@code snapshot} onto {@code plot}. Package-visible and static
     * (touches no {@code SnapshotManager} instance state) so rollback correctness can be unit tested
     * directly, without a live plugin/data-store instance.
     */
    static void restorePlotState(Plot plot, ClaimSnapshot snapshot) {
        plot.setOwner(snapshot.getOwner());
        plot.setOwnerName(snapshot.getOwnerName());
        plot.setWorld(snapshot.getWorldName());
        plot.setBounds(snapshot.getX1(), snapshot.getZ1(), snapshot.getX2(), snapshot.getZ2());
        plot.setPlotName(snapshot.getPlotName());
        plot.setDescription(snapshot.getDescription());
        plot.setWelcomeMessage(snapshot.getWelcomeMessage());
        plot.setFarewellMessage(snapshot.getFarewellMessage());
        plot.setEntryTitle(snapshot.getEntryTitle());
        plot.setEntrySubtitle(snapshot.getEntrySubtitle());
        plot.setCustomBiome(snapshot.getCustomBiome());
        plot.setPlotStatus(snapshot.getPlotStatus());
        plot.setServerWarp(snapshot.isServerWarp());
        plot.setGroupPlot(snapshot.isGroupPlot());
        plot.setTreasuryBalance(snapshot.getTreasuryBalance());
        plot.setGroupId(snapshot.getGroupId());
        plot.setGroupName(snapshot.getGroupName());

        plot.getFlags().clear();
        plot.getFlags().putAll(snapshot.getFlags());

        plot.getPlayerRoles().clear();
        for (Map.Entry<UUID, String> member : snapshot.getMembers().entrySet()) {
            plot.setRole(member.getKey(), member.getValue());
        }

        plot.getBannedPlayers().clear();
        for (UUID banned : snapshot.getBannedPlayers()) {
            plot.addBan(banned);
        }
    }
    
    public boolean isDirty() { return isDirty; }
    public void setDirty(boolean dirty) { this.isDirty = dirty; }
    public void saveSync() { save(); }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
