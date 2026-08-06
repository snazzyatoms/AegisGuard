package com.aegisguard.arena;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-player runtime record inside a shared ArenaRun.
 */
public final class ArenaParticipant {

    private final UUID playerId;
    private volatile ParticipantState state = ParticipantState.FIGHTING;
    private volatile String snapshotPath;
    private volatile boolean snapshotRestored;
    private volatile boolean eliminatedHandled;
    private volatile long fightingMillis;
    private volatile long disconnectedSince;
    private volatile int kills;
    private volatile int bossKills;
    private volatile double damageDealt;
    private volatile int eliminations;
    private volatile int personalScore;
    private volatile boolean rewardEligible = true;
    private final AtomicBoolean recoveryComplete = new AtomicBoolean(false);

    public ArenaParticipant(UUID playerId) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
    }

    public UUID getPlayerId() { return playerId; }

    public ParticipantState getState() { return state; }
    public void setState(ParticipantState state) {
        this.state = state == null ? ParticipantState.FIGHTING : state;
    }

    public String getSnapshotPath() { return snapshotPath; }
    public void setSnapshotPath(String snapshotPath) { this.snapshotPath = snapshotPath; }

    public boolean isSnapshotRestored() { return snapshotRestored; }
    public void setSnapshotRestored(boolean snapshotRestored) { this.snapshotRestored = snapshotRestored; }

    public boolean isEliminatedHandled() { return eliminatedHandled; }
    public void setEliminatedHandled(boolean eliminatedHandled) { this.eliminatedHandled = eliminatedHandled; }

    public long getFightingMillis() { return fightingMillis; }
    public void addFightingMillis(long delta) { this.fightingMillis += Math.max(0L, delta); }

    public long getDisconnectedSince() { return disconnectedSince; }
    public void setDisconnectedSince(long disconnectedSince) { this.disconnectedSince = disconnectedSince; }

    public int getKills() { return kills; }
    public void addKill() { kills++; }
    public int getBossKills() { return bossKills; }
    public void addBossKill() { bossKills++; }
    public double getDamageDealt() { return damageDealt; }
    public void addDamage(double amount) { if (amount > 0) damageDealt += amount; }
    public int getEliminations() { return eliminations; }
    public void addElimination() { eliminations++; }
    public int getPersonalScore() { return personalScore; }
    public void addScore(int amount) { personalScore += Math.max(0, amount); }

    public boolean isRewardEligible() { return rewardEligible; }
    public void setRewardEligible(boolean rewardEligible) { this.rewardEligible = rewardEligible; }

    public boolean markRecoveryComplete() { return recoveryComplete.compareAndSet(false, true); }
    public boolean isRecoveryComplete() { return recoveryComplete.get(); }

    public boolean isFighting() { return state == ParticipantState.FIGHTING; }
}
