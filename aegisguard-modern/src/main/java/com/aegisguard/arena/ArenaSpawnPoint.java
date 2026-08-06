package com.aegisguard.arena;

import java.util.Objects;
import java.util.UUID;

/**
 * Stored spawn location with durable world identity.
 */
public final class ArenaSpawnPoint {

    private final UUID worldId;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public ArenaSpawnPoint(UUID worldId, String worldName, double x, double y, double z, float yaw, float pitch) {
        this.worldId = worldId;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public UUID worldId() { return worldId; }
    public String worldName() { return worldName; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    public boolean hasWorldIdentity() {
        return worldId != null || (worldName != null && !worldName.isBlank());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArenaSpawnPoint that)) return false;
        return Double.compare(that.x, x) == 0 && Double.compare(that.y, y) == 0
                && Double.compare(that.z, z) == 0
                && Float.compare(that.yaw, yaw) == 0 && Float.compare(that.pitch, pitch) == 0
                && Objects.equals(worldId, that.worldId) && Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, worldName, x, y, z, yaw, pitch);
    }
}
