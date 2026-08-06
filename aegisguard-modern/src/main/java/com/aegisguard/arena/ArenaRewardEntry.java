package com.aegisguard.arena;

import java.util.Objects;
import java.util.UUID;

/**
 * Durable reward ledger entry. Stable key: runId + playerId + rewardKey.
 */
public final class ArenaRewardEntry {

    private final String entryId;
    private final UUID runId;
    private final UUID playerId;
    private final String rewardKey;
    private volatile ArenaRewardStatus status;
    private volatile String detail;
    private volatile long updatedAt;

    public ArenaRewardEntry(UUID runId, UUID playerId, String rewardKey) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.rewardKey = Objects.requireNonNull(rewardKey, "rewardKey");
        this.entryId = stableId(runId, playerId, rewardKey);
        this.status = ArenaRewardStatus.PENDING;
        this.updatedAt = System.currentTimeMillis();
    }

    public static String stableId(UUID runId, UUID playerId, String rewardKey) {
        return runId + ":" + playerId + ":" + rewardKey;
    }

    public String getEntryId() { return entryId; }
    public UUID getRunId() { return runId; }
    public UUID getPlayerId() { return playerId; }
    public String getRewardKey() { return rewardKey; }

    public ArenaRewardStatus getStatus() { return status; }
    public void setStatus(ArenaRewardStatus status) {
        this.status = status == null ? ArenaRewardStatus.FAILED : status;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public long getUpdatedAt() { return updatedAt; }
}
