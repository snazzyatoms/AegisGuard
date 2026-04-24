package com.aegisguard.selection;

import org.bukkit.Location;

/**
 * Selection - Stores two corner locations for claim creation/resizing.
 *
 * @since 1.2.6
 */
public class Selection {

    private Location l1;
    private Location l2;

    public Selection() {
        this.l1 = null;
        this.l2 = null;
    }

    public Selection(Location l1, Location l2) {
        this.l1 = l1;
        this.l2 = l2;
    }

    /**
     * Get the first corner location
     */
    public Location getL1() {
        return l1;
    }

    /**
     * Set the first corner location
     */
    public void setL1(Location l1) {
        this.l1 = l1;
    }

    /**
     * Get the second corner location
     */
    public Location getL2() {
        return l2;
    }

    /**
     * Set the second corner location
     */
    public void setL2(Location l2) {
        this.l2 = l2;
    }

    /**
     * Check if both corners are set
     */
    public boolean isComplete() {
        return l1 != null && l2 != null;
    }

    /**
     * Check if both corners are in the same world
     */
    public boolean isSameWorld() {
        if (!isComplete()) return false;
        if (l1.getWorld() == null || l2.getWorld() == null) return false;
        return l1.getWorld().equals(l2.getWorld());
    }

    /**
     * Clear both corners
     */
    public void clear() {
        this.l1 = null;
        this.l2 = null;
    }

    /**
     * Get the calculated area of the selection
     */
    public int getArea() {
        if (!isComplete()) return 0;

        int minX = Math.min(l1.getBlockX(), l2.getBlockX());
        int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
        int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());

        int width = (maxX - minX) + 1;
        int depth = (maxZ - minZ) + 1;

        return width * depth;
    }

    @Override
    public String toString() {
        return "Selection{" +
                "l1=" + (l1 != null ? l1.getBlockX() + "," + l1.getBlockZ() : "null") +
                ", l2=" + (l2 != null ? l2.getBlockX() + "," + l2.getBlockZ() : "null") +
                '}';
    }
}
