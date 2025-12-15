package com.aegisguard.hooks.protection;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Implementations should be lightweight and safe:
 * - return ABSTAIN if the plugin isn't enabled or anything is uncertain
 * - never throw; fail open as ABSTAIN (manager decides)
 */
public interface ProtectionHook {

    /**
     * Display name of the hooked plugin ("WorldGuard", "GriefPrevention", etc.)
     */
    String getName();

    /**
     * Whether this hook is currently usable (plugin present/enabled + dependencies satisfied).
     */
    boolean isAvailable();

    /**
     * Check if a player can build (place/break) at a location.
     */
    ProtectionResult canBuild(Player player, Location location);

    /**
     * Optional: check if player can interact (doors, chests, buttons).
     */
    default ProtectionResult canInteract(Player player, Location location) {
        return ProtectionResult.ABSTAIN;
    }

    /**
     * Optional: check if PvP is allowed at a location.
     */
    default ProtectionResult canPvp(Player attacker, Player victim, Location location) {
        return ProtectionResult.ABSTAIN;
    }
}
