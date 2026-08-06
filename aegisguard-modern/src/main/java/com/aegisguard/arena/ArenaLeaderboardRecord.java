package com.aegisguard.arena;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Leaderboard row. Solo and party boards never mix.
 */
public final class ArenaLeaderboardRecord {

    public enum Board {
        SOLO_SCORE,
        SOLO_WAVE,
        SOLO_FASTEST,
        PARTY_SCORE,
        PARTY_WAVE,
        PARTY_FASTEST,
        PARTY_BOSS_KILLS,
        GROUP_INDIVIDUAL_KILLS,
        GROUP_INDIVIDUAL_BOSS_KILLS
    }

    private final Board board;
    private final String arenaId;
    private final ArenaMode mode;
    private final String difficultyLabel;
    private final int partySize;
    private final List<UUID> members;
    private final int score;
    private final int wave;
    private final long clearTimeMillis;
    private final int bossKills;
    private final long recordedAt;
    private final String seasonId;
    private final UUID runId;

    public ArenaLeaderboardRecord(Board board, String arenaId, ArenaMode mode, String difficultyLabel,
                                  int partySize, List<UUID> members, int score, int wave,
                                  long clearTimeMillis, int bossKills, UUID runId, String seasonId) {
        this.board = Objects.requireNonNull(board);
        this.arenaId = arenaId;
        this.mode = mode == null ? ArenaMode.PVE_WAVES : mode;
        this.difficultyLabel = difficultyLabel == null ? "default" : difficultyLabel;
        this.partySize = Math.max(1, partySize);
        this.members = members == null ? List.of() : List.copyOf(members);
        this.score = score;
        this.wave = wave;
        this.clearTimeMillis = clearTimeMillis;
        this.bossKills = bossKills;
        this.runId = runId;
        this.seasonId = seasonId == null ? "lifetime" : seasonId;
        this.recordedAt = System.currentTimeMillis();
    }

    public Board getBoard() { return board; }
    public String getArenaId() { return arenaId; }
    public ArenaMode getMode() { return mode; }
    public String getDifficultyLabel() { return difficultyLabel; }
    public int getPartySize() { return partySize; }
    public List<UUID> getMembers() { return members; }
    public int getScore() { return score; }
    public int getWave() { return wave; }
    public long getClearTimeMillis() { return clearTimeMillis; }
    public int getBossKills() { return bossKills; }
    public long getRecordedAt() { return recordedAt; }
    public String getSeasonId() { return seasonId; }
    public UUID getRunId() { return runId; }

    public static boolean beats(ArenaLeaderboardRecord existing, int wave, int score, long time) {
        if (existing == null) return true;
        return ArenaScoreService.compareRecords(wave, score, time,
                existing.wave, existing.score, existing.clearTimeMillis) < 0;
    }
}
