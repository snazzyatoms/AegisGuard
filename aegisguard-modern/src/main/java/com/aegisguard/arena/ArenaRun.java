package com.aegisguard.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One shared party/solo dungeon run.
 */
public final class ArenaRun {

    private final UUID runId;
    private final String arenaId;
    private final ArenaMode mode;
    private volatile ArenaRunState state = ArenaRunState.LOBBY;
    private volatile ArenaEndReason endReason;
    private volatile UUID leaderId;
    private volatile UUID reservedLeaderId;
    private volatile long leaderDisconnectAt;
    private final Map<UUID, ArenaParticipant> participants = new ConcurrentHashMap<>();
    private final Set<UUID> activeMobIds = ConcurrentHashMap.newKeySet();
    private final List<String> journal = new ArrayList<>();
    private volatile int waveIndex;
    private volatile int deepestWave;
    private volatile int bossesDefeated;
    private volatile int partyKills;
    private volatile int partyScore;
    private volatile long startedAt;
    private volatile long endedAt;
    private volatile ArenaScalingTable.Row lockedScale = ArenaScalingTable.Row.identity();
    private volatile int startFighterCount = 1;
    private volatile long runSeed;
    private final AtomicBoolean cleanupDone = new AtomicBoolean(false);
    private final AtomicBoolean leadershipTransferToken = new AtomicBoolean(false);
    private volatile String pendingLeadershipTransferReason;

    public ArenaRun(UUID runId, String arenaId, ArenaMode mode, UUID leaderId) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.arenaId = Objects.requireNonNull(arenaId, "arenaId");
        this.mode = mode == null ? ArenaMode.PVE_WAVES : mode;
        this.leaderId = leaderId;
        this.startedAt = System.currentTimeMillis();
        this.runSeed = runId.getMostSignificantBits() ^ runId.getLeastSignificantBits();
    }

    public UUID getRunId() { return runId; }
    public String getArenaId() { return arenaId; }
    public ArenaMode getMode() { return mode; }

    public ArenaRunState getState() { return state; }
    public void setState(ArenaRunState state) {
        this.state = state == null ? ArenaRunState.LOBBY : state;
        journal("state=" + this.state);
    }

    public ArenaEndReason getEndReason() { return endReason; }
    public void setEndReason(ArenaEndReason endReason) { this.endReason = endReason; }

    public UUID getLeaderId() { return leaderId; }
    public void setLeaderId(UUID leaderId) { this.leaderId = leaderId; }

    public UUID getReservedLeaderId() { return reservedLeaderId; }
    public void setReservedLeaderId(UUID reservedLeaderId) { this.reservedLeaderId = reservedLeaderId; }

    public long getLeaderDisconnectAt() { return leaderDisconnectAt; }
    public void setLeaderDisconnectAt(long leaderDisconnectAt) { this.leaderDisconnectAt = leaderDisconnectAt; }

    public Map<UUID, ArenaParticipant> getParticipants() { return participants; }

    public ArenaParticipant getOrCreate(UUID playerId) {
        return participants.computeIfAbsent(playerId, ArenaParticipant::new);
    }

    public ArenaParticipant getParticipant(UUID playerId) {
        return playerId == null ? null : participants.get(playerId);
    }

    public Set<UUID> getActiveMobIds() { return activeMobIds; }

    public int getWaveIndex() { return waveIndex; }
    public void setWaveIndex(int waveIndex) {
        this.waveIndex = Math.max(0, waveIndex);
        this.deepestWave = Math.max(deepestWave, this.waveIndex + 1);
    }

    public int getDeepestWave() { return deepestWave; }
    public int getBossesDefeated() { return bossesDefeated; }
    public void addBossDefeated() { bossesDefeated++; }
    public int getPartyKills() { return partyKills; }
    public void addPartyKill() { partyKills++; }
    public int getPartyScore() { return partyScore; }
    public void addPartyScore(int amount) { partyScore += Math.max(0, amount); }

    public long getStartedAt() { return startedAt; }
    public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
    public long getEndedAt() { return endedAt; }
    public void setEndedAt(long endedAt) { this.endedAt = endedAt; }

    public ArenaScalingTable.Row getLockedScale() { return lockedScale; }
    public void setLockedScale(ArenaScalingTable.Row lockedScale) {
        this.lockedScale = lockedScale == null ? ArenaScalingTable.Row.identity() : lockedScale;
    }

    public int getStartFighterCount() { return startFighterCount; }
    public void setStartFighterCount(int startFighterCount) {
        this.startFighterCount = Math.max(1, startFighterCount);
    }

    public long getRunSeed() { return runSeed; }
    public void setRunSeed(long runSeed) { this.runSeed = runSeed; }

    public boolean markCleanupDone() { return cleanupDone.compareAndSet(false, true); }
    public boolean isCleanupDone() { return cleanupDone.get(); }

    public boolean tryBeginLeadershipTransfer(String reason) {
        if (!leadershipTransferToken.compareAndSet(false, true)) return false;
        pendingLeadershipTransferReason = reason;
        return true;
    }

    public void completeLeadershipTransfer(UUID newLeader) {
        this.leaderId = newLeader;
        this.reservedLeaderId = null;
        this.leaderDisconnectAt = 0L;
        journal("leadership_changed to=" + newLeader + " reason=" + pendingLeadershipTransferReason);
        pendingLeadershipTransferReason = null;
        leadershipTransferToken.set(false);
    }

    public void cancelLeadershipTransfer() {
        pendingLeadershipTransferReason = null;
        leadershipTransferToken.set(false);
    }

    public int countFighting() {
        int n = 0;
        for (ArenaParticipant p : participants.values()) {
            if (p.isFighting()) n++;
        }
        return n;
    }

    public List<UUID> memberIds() {
        return List.copyOf(participants.keySet());
    }

    public synchronized void journal(String line) {
        journal.add(System.currentTimeMillis() + " " + line);
        if (journal.size() > 200) {
            journal.subList(0, journal.size() - 200).clear();
        }
    }

    public synchronized List<String> journalCopy() {
        return List.copyOf(journal);
    }

    public long durationMillis() {
        long end = endedAt > 0 ? endedAt : System.currentTimeMillis();
        return Math.max(0L, end - startedAt);
    }

    public boolean isSoloRun() {
        return participants.size() == 1;
    }

    public Map<String, Object> snapshotPublic() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("runId", runId.toString());
        map.put("arenaId", arenaId);
        map.put("state", state.name());
        map.put("wave", deepestWave);
        map.put("partySize", participants.size());
        map.put("fighters", countFighting());
        return map;
    }
}
