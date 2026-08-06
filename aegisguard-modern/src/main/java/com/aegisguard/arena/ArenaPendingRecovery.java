package com.aegisguard.arena;

import java.util.Objects;
import java.util.UUID;

/**
 * Durable offline crash-recovery record. Idempotent login restore.
 */
public final class ArenaPendingRecovery {

    public enum Status { PENDING, COMPLETE }

    private final UUID playerId;
    private final UUID runId;
    private final String snapshotPath;
    private final ArenaSpawnPoint lobbyDestination;
    private volatile Status status = Status.PENDING;
    private volatile String appliedToken;

    public ArenaPendingRecovery(UUID playerId, UUID runId, String snapshotPath, ArenaSpawnPoint lobbyDestination) {
        this.playerId = Objects.requireNonNull(playerId);
        this.runId = Objects.requireNonNull(runId);
        this.snapshotPath = Objects.requireNonNull(snapshotPath);
        this.lobbyDestination = lobbyDestination;
    }

    public UUID getPlayerId() { return playerId; }
    public UUID getRunId() { return runId; }
    public String getSnapshotPath() { return snapshotPath; }
    public ArenaSpawnPoint getLobbyDestination() { return lobbyDestination; }
    public Status getStatus() { return status; }

    public boolean tryComplete(String token) {
        if (status == Status.COMPLETE) return false;
        if (appliedToken != null && appliedToken.equals(token)) return false;
        this.appliedToken = token;
        this.status = Status.COMPLETE;
        return true;
    }

    public void setStatus(Status status) {
        this.status = status == null ? Status.PENDING : status;
    }

    public String getAppliedToken() { return appliedToken; }
    public void setAppliedToken(String appliedToken) { this.appliedToken = appliedToken; }

    public boolean isPending() { return status == Status.PENDING; }
}
