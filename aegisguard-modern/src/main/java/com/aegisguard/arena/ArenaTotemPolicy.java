package com.aegisguard.arena;

/**
 * Explicit per-arena totem-of-undying policy. Do not rely on vanilla timing.
 */
public enum ArenaTotemPolicy {
    /** Prevent totem activation; eliminate via protected arena death. */
    DISABLE,
    /** Allow totem consume, then still eliminate into spectator/lobby. */
    CONSUME_AND_ELIMINATE,
    /** Totem save keeps the player fighting (changes fairness — document clearly). */
    ALLOW;

    public static ArenaTotemPolicy fromConfig(String raw, ArenaTotemPolicy fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
