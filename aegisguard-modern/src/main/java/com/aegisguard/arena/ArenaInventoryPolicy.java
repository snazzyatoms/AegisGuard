package com.aegisguard.arena;

public enum ArenaInventoryPolicy {
    SAVE_AND_RESTORE,
    ARENA_LOADOUT,
    DROP_ON_DEATH;

    public boolean isProtectedInventory() {
        return this == SAVE_AND_RESTORE || this == ARENA_LOADOUT;
    }

    public static ArenaInventoryPolicy fromConfig(String raw, ArenaInventoryPolicy fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
