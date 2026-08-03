package com.aegisguard.routes;

import java.util.Objects;
import java.util.UUID;

/**
 * Milestone 6 (Routes and Checkpoints) - a single ordered stop on a staff-authored route.
 * Pure data; no Bukkit dependency so unit tests can exercise distance/progress logic directly.
 */
public final class Checkpoint {

    private final UUID id;
    private final String name;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final double radius;

    public Checkpoint(UUID id, String name, String world, double x, double y, double z, double radius) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.name = (name == null || name.isBlank()) ? "Checkpoint" : name.trim();
        this.world = world == null ? "world" : world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = Math.max(1.0D, radius);
    }

    public static Checkpoint at(String name, String world, double x, double y, double z, double radius) {
        return new Checkpoint(UUID.randomUUID(), name, world, x, y, z, radius);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getRadius() { return radius; }

    public double distanceSquared(String worldName, double px, double py, double pz) {
        if (worldName == null || !worldName.equalsIgnoreCase(world)) return Double.POSITIVE_INFINITY;
        double dx = px - x;
        double dy = py - y;
        double dz = pz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean isWithinRange(String worldName, double px, double py, double pz) {
        double r = radius;
        return distanceSquared(worldName, px, py, pz) <= (r * r);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Checkpoint that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
