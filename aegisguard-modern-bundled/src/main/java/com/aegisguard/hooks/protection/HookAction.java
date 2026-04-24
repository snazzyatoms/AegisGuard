package com.aegisguard.hooks.protection;

/**
 * Describes what AegisGuard is trying to do, so hook logic can be more intentional.
 * Even if hooks ignore it, ProtectionManager needs the enum to compile.
 */
public enum HookAction {
    CLAIM_CREATE,
    CLAIM_EDIT,

    PVP,

    MOB_SPAWN,
    MOB_TARGET,
    MOB_DAMAGE_PLAYER,
    MOB_DAMAGE_ANIMAL,

    ANIMAL_DAMAGE,
    ANIMAL_INTERACT,

    REDSTONE_INTERACT,
    VEHICLE_ENTER,

    CONTAINER_INTERACT,
    BLOCK_BREAK,
    BLOCK_PLACE,

    OTHER
}
