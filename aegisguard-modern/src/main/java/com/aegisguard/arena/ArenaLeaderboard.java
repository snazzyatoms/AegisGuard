package com.aegisguard.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory leaderboard store with solo/party separation.
 */
public final class ArenaLeaderboard {

    private final Map<String, List<ArenaLeaderboardRecord>> boards = new ConcurrentHashMap<>();

    private static String key(ArenaLeaderboardRecord.Board board, String arenaId, ArenaMode mode) {
        return board.name() + ":" + arenaId + ":" + (mode == null ? "PVE_WAVES" : mode.name());
    }

    public synchronized void submit(ArenaLeaderboardRecord record, int topN) {
        if (record == null) return;
        // Fastest boards only for successful clears (caller must pass CLEAR with time)
        String k = key(record.getBoard(), record.getArenaId(), record.getMode());
        List<ArenaLeaderboardRecord> list = boards.computeIfAbsent(k, x -> new ArrayList<>());
        list.add(record);
        list.sort(comparatorFor(record.getBoard()));
        while (list.size() > Math.max(1, topN)) {
            list.remove(list.size() - 1);
        }
    }

    public List<ArenaLeaderboardRecord> top(ArenaLeaderboardRecord.Board board, String arenaId, ArenaMode mode, int n) {
        List<ArenaLeaderboardRecord> list = boards.getOrDefault(key(board, arenaId, mode), List.of());
        return list.stream().limit(Math.max(1, n)).collect(Collectors.toList());
    }

    public Map<String, List<ArenaLeaderboardRecord>> asMap() {
        return Map.copyOf(boards);
    }

    public void putAll(String key, List<ArenaLeaderboardRecord> records) {
        if (key == null) return;
        boards.put(key, records == null ? new ArrayList<>() : new ArrayList<>(records));
    }

    private static Comparator<ArenaLeaderboardRecord> comparatorFor(ArenaLeaderboardRecord.Board board) {
        if (board == ArenaLeaderboardRecord.Board.SOLO_FASTEST
                || board == ArenaLeaderboardRecord.Board.PARTY_FASTEST) {
            return Comparator.comparingLong(ArenaLeaderboardRecord::getClearTimeMillis)
                    .thenComparing(ArenaLeaderboardRecord::getWave, Comparator.reverseOrder())
                    .thenComparing(ArenaLeaderboardRecord::getScore, Comparator.reverseOrder());
        }
        if (board == ArenaLeaderboardRecord.Board.PARTY_BOSS_KILLS
                || board == ArenaLeaderboardRecord.Board.GROUP_INDIVIDUAL_BOSS_KILLS) {
            return Comparator.comparingInt(ArenaLeaderboardRecord::getBossKills).reversed()
                    .thenComparingInt(ArenaLeaderboardRecord::getWave).reversed()
                    .thenComparingInt(ArenaLeaderboardRecord::getScore).reversed();
        }
        if (board == ArenaLeaderboardRecord.Board.GROUP_INDIVIDUAL_KILLS) {
            return Comparator.comparingInt(ArenaLeaderboardRecord::getScore).reversed()
                    .thenComparingInt(ArenaLeaderboardRecord::getWave).reversed();
        }
        // score / wave boards: wave → score → time
        return (a, b) -> ArenaScoreService.compareRecords(
                a.getWave(), a.getScore(), a.getClearTimeMillis(),
                b.getWave(), b.getScore(), b.getClearTimeMillis());
    }
}
