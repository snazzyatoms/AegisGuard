package com.aegisguard.arena;

public enum ArenaRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY;

    public boolean isAtMost(ArenaRarity max) {
        return max != null && ordinal() <= max.ordinal();
    }

    public static ArenaRarity fromConfig(String raw, ArenaRarity fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
