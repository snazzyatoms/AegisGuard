package com.aegisguard.util;

import org.bukkit.Particle;

import java.util.Locale;
import java.util.Map;

/** Resolves particle names across the legacy and current Bukkit enum naming schemes. */
public final class CompatParticle {

    private static final Map<String, String> MODERN_ALIASES = Map.of(
            "VILLAGER_HAPPY", "HAPPY_VILLAGER",
            "FIREWORKS_SPARK", "FIREWORK",
            "SMOKE_LARGE", "LARGE_SMOKE",
            "SMOKE_NORMAL", "SMOKE"
    );

    private CompatParticle() {
    }

    public static Particle match(String name) {
        if (name == null || name.isBlank()) return null;
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        Particle direct = valueOf(normalized);
        if (direct != null) return direct;

        String modern = MODERN_ALIASES.get(normalized);
        if (modern != null) return valueOf(modern);

        for (Map.Entry<String, String> alias : MODERN_ALIASES.entrySet()) {
            if (alias.getValue().equals(normalized)) return valueOf(alias.getKey());
        }
        return null;
    }

    private static Particle valueOf(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
