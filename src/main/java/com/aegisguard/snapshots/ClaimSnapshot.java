package com.aegisguard.snapshots;

import com.aegisguard.data.Plot;
import java.util.*;

/**
 * Immutable snapshot of a Plot's state at a specific moment.
 * Used for rollback functionality after risky operations (merge, expansion).
 */
public class ClaimSnapshot {
    
    public enum SnapshotType {
        PRE_EXPANSION,
        PRE_MERGE,
        MANUAL
    }
    
    private final UUID snapshotId;
    private final UUID plotId;
    private final UUID owner;
    private final String worldName;
    
    // Bounds
    private final int x1, z1, x2, z2;
    
    // Metadata
    private final long timestamp;
    private final SnapshotType type;
    private final String reason; // e.g., "Before expansion +20 radius" or "Before merging with plot ABC"
    private final UUID triggeredBy; // Admin who approved the change
    
    // Optional: Store flags, members, etc. for full state restoration
    private final Map<String, Boolean> flags;
    private final Map<UUID, String> members; // UUID -> role
    
    public ClaimSnapshot(Plot plot, SnapshotType type, String reason, UUID triggeredBy) {
        this.snapshotId = UUID.randomUUID();
        this.plotId = plot.getPlotId();
        this.owner = plot.getOwner();
        this.worldName = plot.getWorld();
        
        this.x1 = plot.getX1();
        this.z1 = plot.getZ1();
        this.x2 = plot.getX2();
        this.z2 = plot.getZ2();
        
        this.timestamp = System.currentTimeMillis();
        this.type = type;
        this.reason = reason == null ? "" : reason;
        this.triggeredBy = triggeredBy;
        
        // Deep copy flags and members (if you store them in Plot)
        this.flags = new HashMap<>();
        this.members = new HashMap<>();
        
        // TODO: If your Plot has getFlags() and getMembers(), copy them here
        // Example:
        // if (plot.getFlags() != null) this.flags.putAll(plot.getFlags());
        // if (plot.getMembers() != null) this.members.putAll(plot.getMembers());
    }
    
    // Full constructor for loading from storage
    public ClaimSnapshot(UUID snapshotId, UUID plotId, UUID owner, String worldName,
                         int x1, int z1, int x2, int z2, long timestamp,
                         SnapshotType type, String reason, UUID triggeredBy,
                         Map<String, Boolean> flags, Map<UUID, String> members) {
        this.snapshotId = snapshotId;
        this.plotId = plotId;
        this.owner = owner;
        this.worldName = worldName;
        this.x1 = x1;
        this.z1 = z1;
        this.x2 = x2;
        this.z2 = z2;
        this.timestamp = timestamp;
        this.type = type;
        this.reason = reason;
        this.triggeredBy = triggeredBy;
        this.flags = flags == null ? new HashMap<>() : new HashMap<>(flags);
        this.members = members == null ? new HashMap<>() : new HashMap<>(members);
    }
    
    // Getters
    public UUID getSnapshotId() { return snapshotId; }
    public UUID getPlotId() { return plotId; }
    public UUID getOwner() { return owner; }
    public String getWorldName() { return worldName; }
    public int getX1() { return x1; }
    public int getZ1() { return z1; }
    public int getX2() { return x2; }
    public int getZ2() { return z2; }
    public long getTimestamp() { return timestamp; }
    public SnapshotType getType() { return type; }
    public String getReason() { return reason; }
    public UUID getTriggeredBy() { return triggeredBy; }
    public Map<String, Boolean> getFlags() { return new HashMap<>(flags); }
    public Map<UUID, String> getMembers() { return new HashMap<>(members); }
    
    public int getRadius() {
        return Math.max(0, (x2 - x1) / 2);
    }
    
    public long getAgeMillis() {
        return System.currentTimeMillis() - timestamp;
    }
    
    @Override
    public String toString() {
        return "ClaimSnapshot{" +
                "id=" + snapshotId +
                ", plotId=" + plotId +
                ", owner=" + owner +
                ", type=" + type +
                ", timestamp=" + timestamp +
                ", bounds=[" + x1 + "," + z1 + " -> " + x2 + "," + z2 + "]" +
                '}';
    }
}
