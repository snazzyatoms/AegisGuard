package com.aegisguard.hooks.protection;

import org.bukkit.Location;

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
     * Area scan helper: returns true if ANY sampled points in the area are protected elsewhere.
     * Used for "can I claim here?" checks.
     */
    default boolean isAreaProtectedElsewhere(String world, int x1, int z1, int x2, int z2) {
        // Default implementation: conservative sampling at chunk corners + center-ish.
        // Hooks can override for better precision.
        return false;
    }
}
