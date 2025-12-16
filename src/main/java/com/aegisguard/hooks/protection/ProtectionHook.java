package com.aegisguard.hooks.protection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Generic "other protection plugin" adapter.
 * Use reflection unless the plugin offers a stable public API dependency.
 */
public interface ProtectionHook {

    /** Human/plugin identifier (ex: "WorldGuard", "GriefPrevention"). */
    String id();

    /** True if the hooked plugin is present AND the hook initialized successfully. */
    boolean isActive();

    /**
     * True if THIS location is inside another plugin's protected area/claim/region.
     * This is used to prevent AegisGuard from overlapping or "fighting" other protection systems.
     */
    boolean isProtectedElsewhere(Location location);

    /**
     * Fine-grained bypass check used by ProtectionManager.
     *
     * Default behavior (safe + backwards compatible):
     * If the hook is active AND the location is protected elsewhere, AegisGuard yields.
     *
     * Hooks may override this to allow/deny specific actions (PVP, MOB_SPAWN, REDSTONE, etc).
     */
    default boolean shouldBypass(Location location, @Nullable Player actor, HookAction action) {
        if (!isActive() || location == null) return false;
        return isProtectedElsewhere(location);
    }

    /**
     * Area scan helper: returns true if ANY sampled points in the area are protected elsewhere.
     * Used for "can I claim here?" checks.
     *
     * Default implementation: conservative sampling at corners + center.
     * Hooks can override for better precision (API-based region queries).
     */
    default boolean isAreaProtectedElsewhere(String world, int x1, int z1, int x2, int z2) {
        if (!isActive() || world == null || world.isEmpty()) return false;

        World w = Bukkit.getWorld(world);
        if (w == null) return false;

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        int cx = (minX + maxX) / 2;
        int cz = (minZ + maxZ) / 2;

        // Avoid getHighestBlockYAt() here (can force chunk loads); use a stable Y.
        int y = Math.max(1, w.getSeaLevel());

        int[][] points = new int[][]{
                {minX, minZ},
                {minX, maxZ},
                {maxX, minZ},
                {maxX, maxZ},
                {cx, cz}
        };

        for (int[] p : points) {
            Location sample = new Location(w, p[0] + 0.5, y, p[1] + 0.5);
            try {
                if (isProtectedElsewhere(sample)) return true;
            } catch (Throwable ignored) {
            }
        }

        return false;
    }
}
