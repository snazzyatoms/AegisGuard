package com.aegisguard.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One wave or boss milestone in an arena definition.
 */
public final class ArenaWaveSpec {

    public enum Type { WAVE, BOSS }

    private final Type type;
    private final String id;
    private final int mobCount;
    private final ArenaRarity maxRarity;
    private final boolean finalBoss;
    private final String checkpointRewardKey;
    private final List<String> difficultyModifiers;
    private final String unlockPhaseId;
    private final List<String> mobTemplateIds;
    private final long timeLimitMillis;

    public ArenaWaveSpec(Type type, String id, int mobCount, ArenaRarity maxRarity, boolean finalBoss,
                         String checkpointRewardKey, List<String> difficultyModifiers,
                         String unlockPhaseId, List<String> mobTemplateIds, long timeLimitMillis) {
        this.type = Objects.requireNonNullElse(type, Type.WAVE);
        this.id = id == null ? "wave" : id;
        this.mobCount = Math.max(0, mobCount);
        this.maxRarity = Objects.requireNonNullElse(maxRarity, ArenaRarity.COMMON);
        this.finalBoss = finalBoss;
        this.checkpointRewardKey = checkpointRewardKey;
        this.difficultyModifiers = difficultyModifiers == null ? List.of() : List.copyOf(difficultyModifiers);
        this.unlockPhaseId = unlockPhaseId;
        this.mobTemplateIds = mobTemplateIds == null ? List.of() : List.copyOf(mobTemplateIds);
        this.timeLimitMillis = Math.max(0L, timeLimitMillis);
    }

    public Type type() { return type; }
    public String id() { return id; }
    public int mobCount() { return mobCount; }
    public ArenaRarity maxRarity() { return maxRarity; }
    public boolean finalBoss() { return finalBoss; }
    public String checkpointRewardKey() { return checkpointRewardKey; }
    public List<String> difficultyModifiers() { return difficultyModifiers; }
    public String unlockPhaseId() { return unlockPhaseId; }
    public List<String> mobTemplateIds() { return mobTemplateIds; }
    public long timeLimitMillis() { return timeLimitMillis; }

    public boolean isBoss() { return type == Type.BOSS; }

    public static ArenaWaveSpec wave(String id, int count, ArenaRarity max) {
        return new ArenaWaveSpec(Type.WAVE, id, count, max, false, null, List.of(), null, List.of("skeleton", "zombie"), 0L);
    }

    public static ArenaWaveSpec milestoneBoss(String id, ArenaRarity max, String rewardKey) {
        return new ArenaWaveSpec(Type.BOSS, id, 1, max, false, rewardKey, List.of("harder"), "phase2", List.of("blaze"), 0L);
    }

    public static ArenaWaveSpec finalBoss(String id) {
        return new ArenaWaveSpec(Type.BOSS, id, 1, ArenaRarity.LEGENDARY, true, "clear", List.of(), null, List.of("wither_skeleton"), 0L);
    }

    public static Type typeFrom(String raw) {
        if (raw == null) return Type.WAVE;
        try {
            return Type.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Type.WAVE;
        }
    }

    public ArenaWaveSpec withMobCount(int count) {
        return new ArenaWaveSpec(type, id, count, maxRarity, finalBoss, checkpointRewardKey,
                difficultyModifiers, unlockPhaseId, mobTemplateIds, timeLimitMillis);
    }
}
