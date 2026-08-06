package com.aegisguard.arena;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weighted rarity pick with wave-gated maximum tier.
 */
public final class ArenaRarityGate {

    private final Map<ArenaRarity, Integer> weights;

    public ArenaRarityGate(Map<ArenaRarity, Integer> weights) {
        this.weights = new EnumMap<>(ArenaRarity.class);
        if (weights != null) {
            this.weights.putAll(weights);
        }
        for (ArenaRarity r : ArenaRarity.values()) {
            this.weights.putIfAbsent(r, defaultWeight(r));
        }
    }

    public static ArenaRarityGate defaults() {
        return new ArenaRarityGate(null);
    }

    private static int defaultWeight(ArenaRarity r) {
        return switch (r) {
            case COMMON -> 60;
            case UNCOMMON -> 25;
            case RARE -> 10;
            case EPIC -> 4;
            case LEGENDARY -> 1;
        };
    }

    public ArenaRarity roll(ArenaRarity maxAllowed) {
        return roll(maxAllowed, ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE));
    }

    /** Deterministic roll for shared party fairness (same seed → same rarity). */
    public ArenaRarity roll(ArenaRarity maxAllowed, int seed) {
        Objects.requireNonNull(maxAllowed, "maxAllowed");
        int total = 0;
        for (ArenaRarity r : ArenaRarity.values()) {
            if (r.isAtMost(maxAllowed)) {
                total += Math.max(0, weights.getOrDefault(r, 0));
            }
        }
        if (total <= 0) return ArenaRarity.COMMON;
        int pick = Math.floorMod(seed, total);
        int cursor = 0;
        for (ArenaRarity r : ArenaRarity.values()) {
            if (!r.isAtMost(maxAllowed)) continue;
            cursor += Math.max(0, weights.getOrDefault(r, 0));
            if (pick < cursor) return r;
        }
        return ArenaRarity.COMMON;
    }

    public Map<ArenaRarity, Integer> weights() {
        return Map.copyOf(weights);
    }
}
