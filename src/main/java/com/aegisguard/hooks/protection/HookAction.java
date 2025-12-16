package com.aegisguard.hooks.protection;

/**
 * Describes what AegisGuard is trying to do at a location.
 * Hooks can decide whether AegisGuard should yield to an external protection plugin.
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

    // --- Interaction ---
    REDSTONE_INTERACT,
    VEHICLE_ENTER,

    // --- Generic / fallback ---
    OTHER
}
