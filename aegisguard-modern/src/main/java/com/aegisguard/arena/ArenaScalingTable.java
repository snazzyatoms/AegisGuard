package com.aegisguard.arena;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Start-of-run party-size scaling multipliers. Locked at run start for MVP.
 */
public final class ArenaScalingTable {

    public static final class Row {
        public final double mobCount;
        public final double mobHealth;
        public final double mobDamage;
        public final double bossHealth;
        public final double bossMinions;
        public final double spawnPacing;
        public final double waveTimer;
        public final double rewardMultiplier;
        public final double scoreMultiplier;

        public Row(double mobCount, double mobHealth, double mobDamage, double bossHealth,
                   double bossMinions, double spawnPacing, double waveTimer,
                   double rewardMultiplier, double scoreMultiplier) {
            this.mobCount = mobCount;
            this.mobHealth = mobHealth;
            this.mobDamage = mobDamage;
            this.bossHealth = bossHealth;
            this.bossMinions = bossMinions;
            this.spawnPacing = spawnPacing;
            this.waveTimer = waveTimer;
            this.rewardMultiplier = rewardMultiplier;
            this.scoreMultiplier = scoreMultiplier;
        }

        public static Row identity() {
            return new Row(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0);
        }
    }

    private final Map<Integer, Row> byPartySize;

    public ArenaScalingTable(Map<Integer, Row> byPartySize) {
        Map<Integer, Row> copy = new LinkedHashMap<>();
        if (byPartySize != null) copy.putAll(byPartySize);
        this.byPartySize = Collections.unmodifiableMap(copy);
    }

    public static ArenaScalingTable lavaDungeonDefaults() {
        Map<Integer, Row> map = new LinkedHashMap<>();
        map.put(1, new Row(1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00));
        map.put(2, new Row(1.40, 1.35, 1.20, 1.50, 1.25, 0.95, 1.05, 1.25, 1.10));
        map.put(3, new Row(1.80, 1.70, 1.35, 2.00, 1.50, 0.90, 1.10, 1.50, 1.20));
        map.put(4, new Row(2.20, 2.05, 1.50, 2.50, 1.75, 0.85, 1.15, 1.75, 1.30));
        return new ArenaScalingTable(map);
    }

    public Row forPartySize(int partySize) {
        int size = Math.max(1, partySize);
        Row row = byPartySize.get(size);
        if (row != null) return row;
        // Clamp to nearest defined size
        int best = 1;
        for (Integer key : byPartySize.keySet()) {
            if (key <= size) best = key;
        }
        return Objects.requireNonNullElse(byPartySize.get(best), Row.identity());
    }

    public Map<Integer, Row> rows() {
        return byPartySize;
    }
}
