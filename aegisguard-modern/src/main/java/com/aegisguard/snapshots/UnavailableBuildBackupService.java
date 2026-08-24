package com.aegisguard.snapshots;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/** No-op backend used when the optional WorldEdit API is unavailable. */
final class UnavailableBuildBackupService implements BuildBackupService {
    private final AegisGuard plugin;

    UnavailableBuildBackupService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isConfiguredOn() {
        return plugin.getConfig().getBoolean("snapshots.build_backup.enabled", false);
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public PlotBuildBackup.IntegrationInfo integrationInfo() {
        return new PlotBuildBackup.IntegrationInfo("None", "", false, false,
                "WorldEdit or FastAsyncWorldEdit is not installed");
    }

    private PlotBuildBackup.BackupInspection unavailableInspection() {
        return new PlotBuildBackup.BackupInspection(false, 0L, 0,
                PlotBuildBackup.IntegrityStatus.NONE, false, "", "", "None", "",
                "WorldEdit or FastAsyncWorldEdit is not installed");
    }

    @Override
    public CompletableFuture<PlotBuildBackup.BackupInspection> inspectAsync(UUID snapshotId) {
        return CompletableFuture.completedFuture(unavailableInspection());
    }

    @Override
    public CompletableFuture<PlotBuildBackup.BackupInspection> inspectAsync(ClaimSnapshot snapshot) {
        return CompletableFuture.completedFuture(unavailableInspection());
    }

    @Override
    public CompletableFuture<Map<UUID, PlotBuildBackup.BackupInspection>> inspectBatchAsync(
            Collection<ClaimSnapshot> snapshots) {
        Map<UUID, PlotBuildBackup.BackupInspection> result = new LinkedHashMap<>();
        if (snapshots != null) {
            for (ClaimSnapshot snapshot : snapshots) {
                if (snapshot != null) result.put(snapshot.getSnapshotId(), unavailableInspection());
            }
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public PlotBuildBackup.CaptureResult preview(ClaimSnapshot snapshot) {
        return PlotBuildBackup.CaptureResult.DISABLED;
    }

    @Override
    public PlotBuildBackup.CaptureResult queueCapture(Plot plot, ClaimSnapshot snapshot) {
        return PlotBuildBackup.CaptureResult.DISABLED;
    }

    @Override
    public CompletableFuture<PlotBuildBackup.CaptureResult> captureAsync(Plot plot, ClaimSnapshot snapshot) {
        return CompletableFuture.completedFuture(PlotBuildBackup.CaptureResult.DISABLED);
    }

    @Override
    public CompletableFuture<PlotBuildBackup.CaptureResult> captureAsync(
            Plot plot, ClaimSnapshot snapshot, boolean featureEnabled) {
        return CompletableFuture.completedFuture(PlotBuildBackup.CaptureResult.DISABLED);
    }

    @Override
    public Set<UUID> activeCaptureIds() {
        return Set.of();
    }

    @Override
    public CompletableFuture<PlotBuildBackup.TrackedRestoreResult> restoreTracked(
            ClaimSnapshot snapshot, Set<String> alreadyCompleted,
            BiFunction<String, Boolean, CompletableFuture<Void>> checkpoint) {
        return CompletableFuture.completedFuture(new PlotBuildBackup.TrackedRestoreResult(
                PlotBuildBackup.RestoreQueueResult.NO_BACKUP,
                alreadyCompleted == null ? Set.of() : alreadyCompleted, Set.of(), Set.of()));
    }

    @Override
    public CompletableFuture<List<String>> expectedBuildTilesAsync(UUID snapshotId) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public void deleteSchematic(UUID snapshotId) {
        // No WorldEdit-backed files can be created by this backend.
    }

    @Override
    public CompletableFuture<PlotBuildBackup.StorageReport> maintainStorageAsync(boolean dryRun) {
        return CompletableFuture.completedFuture(new PlotBuildBackup.StorageReport(
                0L, 0L, 0, 0, 0, 0, 0, 0, 0, 0, dryRun, List.of()));
    }

    @Override
    public CompletableFuture<Boolean> linkCaptureOperationAsync(UUID snapshotId, UUID operationId) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> linkRestoreOperationAsync(UUID snapshotId, UUID operationId) {
        return CompletableFuture.completedFuture(false);
    }
}
