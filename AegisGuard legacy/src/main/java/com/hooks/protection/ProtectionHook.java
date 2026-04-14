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
     * Priority for choosing which external plugin "wins" first when multiple hooks claim protection.
     * Higher = checked earlier.
     */
    default int priority() {
        return 0;
    }

    /**
     * True if THIS location is inside another plugin's protected area/claim/region.
     * This is used to prevent AegisGuard from overlapping or "fighting" other protection systems.
     */
    boolean isProtectedElsewhere(Location location);

    /**
     * Area scan helper: returns true if ANY sampled points in the area are protected elsewhere.
     * Used for "can I claim here?" checks.
     *
     * Hooks can override for better precision.
     */
    default boolean isAreaProtectedElsewhere(String world, int x1, int z1, int x2, int z2) {
        return false;
    }
}
