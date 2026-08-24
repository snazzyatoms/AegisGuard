package com.aegisguard.snapshots;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Persisted restore lifecycle used for duplicate prevention and crash recovery. */
public final class RestoreOperation {

    public enum Status {
        PREFLIGHT,
        RESCUE_CREATING,
        DATA_RESTORING,
        BUILD_QUEUED,
        BUILD_RUNNING,
        COMPLETE,
        PARTIAL,
        FAILED,
        PAUSED_REVIEW,
        RELEASED;

        public boolean terminal() {
            return this == COMPLETE || this == PARTIAL || this == FAILED
                    || this == PAUSED_REVIEW || this == RELEASED;
        }
    }

    private final UUID operationId;
    private final UUID snapshotId;
    private final UUID plotId;
    private final UUID actorId;
    private final long startedAt;
    private final EnumSet<RestoreScope> scopes;
    private volatile Status status;
    private volatile UUID rescueSnapshotId;
    private volatile long updatedAt;
    private volatile String detail;
    private volatile boolean dataRestored;
    private volatile String dataResult;
    private volatile String buildResult;
    private final LinkedHashSet<String> completedBuildTiles = new LinkedHashSet<>();
    private final LinkedHashSet<String> pendingBuildTiles = new LinkedHashSet<>();
    private final LinkedHashSet<String> failedBuildTiles = new LinkedHashSet<>();

    public RestoreOperation(UUID operationId, UUID snapshotId, UUID plotId, UUID actorId,
                            Set<RestoreScope> scopes, Status status, UUID rescueSnapshotId,
                            long startedAt, long updatedAt, String detail) {
        this.operationId = operationId == null ? UUID.randomUUID() : operationId;
        this.snapshotId = snapshotId;
        this.plotId = plotId;
        this.actorId = actorId;
        this.scopes = RestoreScope.normalize(scopes);
        this.status = status == null ? Status.PREFLIGHT : status;
        this.rescueSnapshotId = rescueSnapshotId;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
        this.detail = detail == null ? "" : detail;
        this.dataResult = "PENDING";
        this.buildResult = "PENDING";
    }

    public RestoreOperation(UUID operationId, UUID snapshotId, UUID plotId, UUID actorId,
                            Set<RestoreScope> scopes, Status status, UUID rescueSnapshotId,
                            long startedAt, long updatedAt, String detail, boolean dataRestored,
                            String dataResult, String buildResult, Set<String> completedBuildTiles,
                            Set<String> pendingBuildTiles, Set<String> failedBuildTiles) {
        this(operationId, snapshotId, plotId, actorId, scopes, status, rescueSnapshotId,
                startedAt, updatedAt, detail);
        this.dataRestored = dataRestored;
        this.dataResult = dataResult == null ? "PENDING" : dataResult;
        this.buildResult = buildResult == null ? "PENDING" : buildResult;
        if (completedBuildTiles != null) this.completedBuildTiles.addAll(completedBuildTiles);
        if (pendingBuildTiles != null) this.pendingBuildTiles.addAll(pendingBuildTiles);
        if (failedBuildTiles != null) this.failedBuildTiles.addAll(failedBuildTiles);
    }

    public static RestoreOperation start(UUID snapshotId, UUID plotId, UUID actorId,
                                         Set<RestoreScope> scopes, long now) {
        return new RestoreOperation(UUID.randomUUID(), snapshotId, plotId, actorId, scopes,
                Status.PREFLIGHT, null, now, now, "Preflight accepted");
    }

    public synchronized void transition(Status next, String detail, long now) {
        if (next == null) throw new IllegalArgumentException("next status");
        if (status.terminal()) {
            throw new IllegalStateException("Restore operation is already terminal: " + status);
        }
        status = next;
        updatedAt = now;
        this.detail = detail == null ? "" : detail;
    }

    public synchronized void setRescueSnapshotId(UUID rescueSnapshotId, long now) {
        this.rescueSnapshotId = rescueSnapshotId;
        this.updatedAt = now;
    }

    public synchronized void markReleased(String detail, long now) {
        if (status != Status.PARTIAL && status != Status.PAUSED_REVIEW) {
            throw new IllegalStateException("Only partial or paused operations can be released: " + status);
        }
        status = Status.RELEASED;
        updatedAt = now;
        this.detail = detail == null ? "Maintenance lock released" : detail;
    }

    public synchronized void reopenForReview(String detail, long now) {
        if (status != Status.RELEASED) {
            throw new IllegalStateException("Only a pending release can be reopened: " + status);
        }
        status = Status.PAUSED_REVIEW;
        updatedAt = now;
        this.detail = detail == null ? "Release was not durable; review remains required" : detail;
    }

    public synchronized void reopenAfterCompletionPersistenceFailure(String detail, long now) {
        if (status != Status.COMPLETE) {
            throw new IllegalStateException("Only a non-durable completion can be reopened: " + status);
        }
        status = Status.PAUSED_REVIEW;
        updatedAt = now;
        this.detail = detail == null ? "Completion was not durable; review required" : detail;
    }

    public synchronized boolean pauseForReviewIfActive(String detail, long now) {
        if (status.terminal()) return false;
        status = Status.PAUSED_REVIEW;
        updatedAt = now;
        this.detail = detail == null ? "Paused for staff review" : detail;
        return true;
    }

    public synchronized void markDataRestored(String result, long now) {
        dataRestored = true;
        dataResult = result == null ? "RESTORED" : result;
        updatedAt = now;
    }

    public synchronized void initializeBuildTiles(java.util.Collection<String> tileIds, long now) {
        if (tileIds != null) {
            for (String id : tileIds) {
                if (id == null || id.isBlank() || completedBuildTiles.contains(id)) continue;
                pendingBuildTiles.add(id);
            }
        }
        failedBuildTiles.clear();
        buildResult = pendingBuildTiles.isEmpty() ? "NO_PENDING_TILES" : "RUNNING";
        updatedAt = now;
    }

    public synchronized void checkpointBuildTile(String tileId, boolean success, long now) {
        if (tileId == null || tileId.isBlank()) return;
        pendingBuildTiles.remove(tileId);
        failedBuildTiles.remove(tileId);
        if (success) completedBuildTiles.add(tileId);
        else failedBuildTiles.add(tileId);
        updatedAt = now;
    }

    public synchronized void markBuildResult(String result, long now) {
        buildResult = result == null ? "UNKNOWN" : result;
        updatedAt = now;
    }

    public synchronized void resumeForRetry(String detail, long now) {
        if (status != Status.PARTIAL && status != Status.PAUSED_REVIEW && status != Status.FAILED) {
            throw new IllegalStateException("Operation is not retryable: " + status);
        }
        pendingBuildTiles.addAll(failedBuildTiles);
        failedBuildTiles.clear();
        status = dataRestored && scopes.contains(RestoreScope.BUILD)
                ? Status.BUILD_RUNNING : Status.DATA_RESTORING;
        updatedAt = now;
        this.detail = detail == null ? "Staff retry started" : detail;
    }

    public UUID operationId() { return operationId; }
    public UUID snapshotId() { return snapshotId; }
    public UUID plotId() { return plotId; }
    public UUID actorId() { return actorId; }
    public EnumSet<RestoreScope> scopes() { return EnumSet.copyOf(scopes); }
    public Status status() { return status; }
    public UUID rescueSnapshotId() { return rescueSnapshotId; }
    public long startedAt() { return startedAt; }
    public long updatedAt() { return updatedAt; }
    public String detail() { return detail; }
    public boolean dataRestored() { return dataRestored; }
    public String dataResult() { return dataResult; }
    public String buildResult() { return buildResult; }
    public synchronized Set<String> completedBuildTiles() { return Set.copyOf(completedBuildTiles); }
    public synchronized Set<String> pendingBuildTiles() { return Set.copyOf(pendingBuildTiles); }
    public synchronized Set<String> failedBuildTiles() { return Set.copyOf(failedBuildTiles); }
}
