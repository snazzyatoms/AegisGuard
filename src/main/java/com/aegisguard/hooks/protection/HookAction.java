package com.aegisguard.hooks.protection;

/**
 * HookAction
 * Identifies what AegisGuard is trying to do at a location so external protection hooks
 * can decide whether AegisGuard should yield (bypass) to another plugin.
 *
 * This is NOT a result enum (ALLOW/DENY). It’s an "action category" enum.
 */
public enum HookAction {

    // --- Combat ---
    PVP,

    // --- Mobs ---
    MOB_SPAWN,
    MOB_TARGET,
    MOB_DAMAGE_PLAYER,
    MOB_DAMAGE_ANIMAL,

    // --- Animals / Pets ---
    ANIMAL_DAMAGE,
    ANIMAL_INTERACT,

    // --- Redstone / Mechanisms ---
    REDSTONE_INTERACT,

    // --- Vehicles ---
    VEHICLE_ENTER

    // Add more as you expand hooks (BUILD_BREAK, BUILD_PLACE, CONTAINER_OPEN, etc.)
}
