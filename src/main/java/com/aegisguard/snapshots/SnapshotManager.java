package com.aegisguard.snapshots;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.snapshots.ClaimSnapshot.SnapshotType;
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
        
        plugin.getLogger().info("[Snapshots] Created snapshot " + snapshot.getSnapshotId() + 
                " for plot " + plot.getPlotId() + " (" + type + ")");
        
        // Auto-cleanup old snapshots if limit exceeded
        pruneOldSnapshots();
        
        return snapshot;
    }
    
    /**
     * Rollback a plot to a previous snapshot.
     * @return true if rollback succeeded
     */
    public boolean rollback(UUID snapshotId) {
        ClaimSnapshot snapshot = snapshots.get(snapshotId);
        if (snapshot == null) {
            plugin.getLogger().warning("[Snapshots] Cannot rollback: snapshot " + snapshotId + " not found");
            return false;
        }
        
        // Get current plot
        Plot currentPlot = plugin.store().getPlot(snapshot.getOwner(), snapshot.getPlotId());
        if (currentPlot == null) {
            plugin.getLogger().warning("[Snapshots] Cannot rollback: plot " + snapshot.getPlotId() + " no longer exists");
            return false;
        }
        
        // Remove from store
        plugin.store().removePlot(currentPlot.getOwner(), currentPlot.getPlotId());
        
        // Restore bounds
        currentPlot.setX1(snapshot.getX1());
        currentPlot.setZ1(snapshot.getZ1());
        currentPlot.setX2(snapshot.getX2());
        currentPlot.setZ2(snapshot.getZ2());
        
        // TODO: Restore flags and members if you stored them
        // Example:
        // for (Map.Entry<String, Boolean> flag : snapshot.getFlags().entrySet()) {
        //     currentPlot.setFlag(flag.getKey(), flag.getValue());
        // }
        
        // Add back to store
        plugin.store().addPlot(currentPlot);
        plugin.store().setDirty(true);
        
        plugin.getLogger().info("[Snapshots] Rolled back plot " + currentPlot.getPlotId() + 
                " to snapshot " + snapshotId);
        
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
            plugin.getLogger().info("[Snapshots] Deleted snapshot " + snapshotId);
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
            plugin.getLogger().info("[Snapshots] Pruned " + toRemove.size() + " old snapshots");
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
                    
                    // Load flags and members (optional)
                    Map<String, Boolean> flags = new HashMap<>();
                    Map<UUID, String> members = new HashMap<>();
                    
                    ClaimSnapshot snapshot = new ClaimSnapshot(
                            snapshotId, plotId, owner, worldName,
                            x1, z1, x2, z2, timestamp,
                            type, reason, triggeredBy,
                            flags, members
                    );
                    
                    snapshots.put(snapshotId, snapshot);
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load snapshot: " + key, e);
                }
            }
        }
        
        plugin.getLogger().info("[Snapshots] Loaded " + snapshots.size() + " snapshots");
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
            
            // TODO: Save flags and members if needed
        }
        
        try {
            data.save(file);
            isDirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save claim-snapshots.yml", e);
        }
    }
    
    public boolean isDirty() { return isDirty; }
    public void setDirty(boolean dirty) { this.isDirty = dirty; }
    public void saveSync() { save(); }
}
