package com.aegisguard.arena;

/**
 * Pure scoring helpers.
 */
public final class ArenaScoreService {

    private ArenaScoreService() {}

    public static int killScore(ArenaRarity rarity) {
        if (rarity == null) return 10;
        return switch (rarity) {
            case COMMON -> 10;
            case UNCOMMON -> 18;
            case RARE -> 30;
            case EPIC -> 50;
            case LEGENDARY -> 90;
        };
    }

    public static int waveClearScore(int waveIndex) {
        return 25 + Math.max(0, waveIndex) * 5;
    }

    public static int bossScore(boolean finalBoss) {
        return finalBoss ? 250 : 120;
    }

    public static int applyScoreMultiplier(int base, double multiplier) {
        return (int) Math.round(Math.max(0, base) * Math.max(0.0D, multiplier));
    }

    /** Tie-break: deeper wave wins; then higher score; then faster time. */
    public static int compareRecords(int waveA, int scoreA, long timeA, int waveB, int scoreB, long timeB) {
        if (waveA != waveB) return Integer.compare(waveB, waveA);
        if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
        return Long.compare(timeA, timeB);
    }
}
