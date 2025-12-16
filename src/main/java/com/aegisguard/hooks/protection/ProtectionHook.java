package com.aegisguard.hooks.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Generic "other protection plugin" adapter.
 * Use reflection unless the plugin offers a stable public API dependency.
 */
public interface ProtectionHook {

    /** Human/plugin identifier (ex: "WorldGuard", "GriefPrevention"). */
    String id();

    /** Hook ordering. Higher runs first. */
    default int priority() {
        return 0;
    }

    /** True if the hooked plugin is present AND the hook initialized successfully. */
    boolean isActive();

    /**
     * True if THIS location is inside another plugin's protected area/claim/region.
     * This is used to prevent AegisGuard from overlapping or "fighting" other protection systems.
     */
    boolean isProtectedElsewhere(Location location);

    /**
     * Action-aware bypass decision used by ProtectionManager.
     *
     * Default behavior (safe + backwards compatible):
     * If active AND protected elsewhere, AegisGuard yields.
     *
     * Hooks can override for action-specific behavior.
     */
    default boolean shouldBypass(Location location, @Nullable Player actor, HookAction action) {
        if (!isActive() || location == null) return false;
        return isProtectedElsewhere(location);
    }

    /**
     * Optional area scan helper for claim creation checks.
     * Hooks can override for precision (API-based region queries).
     */
    default boolean isAreaProtectedElsewhere(String world, int x1, int z1, int x2, int z2) {
        return false;
    }
}
