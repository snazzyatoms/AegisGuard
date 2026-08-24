package com.aegisguard.snapshots;

import com.aegisguard.data.Plot;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * WorldEdit-neutral boundary for optional plot build backups.
 * Implementations must remain safe when WorldEdit or FAWE is not installed.
 */
public interface BuildBackupService {
    boolean isConfiguredOn();
    boolean isReady();
    PlotBuildBackup.IntegrationInfo integrationInfo();
    CompletableFuture<PlotBuildBackup.BackupInspection> inspectAsync(UUID snapshotId);
    CompletableFuture<PlotBuildBackup.BackupInspection> inspectAsync(ClaimSnapshot snapshot);
    CompletableFuture<Map<UUID, PlotBuildBackup.BackupInspection>> inspectBatchAsync(
            Collection<ClaimSnapshot> snapshots);
    PlotBuildBackup.CaptureResult preview(ClaimSnapshot snapshot);
    PlotBuildBackup.CaptureResult queueCapture(Plot plot, ClaimSnapshot snapshot);
    CompletableFuture<PlotBuildBackup.CaptureResult> captureAsync(Plot plot, ClaimSnapshot snapshot);
    CompletableFuture<PlotBuildBackup.CaptureResult> captureAsync(
            Plot plot, ClaimSnapshot snapshot, boolean featureEnabled);
    Set<UUID> activeCaptureIds();
    CompletableFuture<PlotBuildBackup.TrackedRestoreResult> restoreTracked(
            ClaimSnapshot snapshot, Set<String> alreadyCompleted,
            BiFunction<String, Boolean, CompletableFuture<Void>> checkpoint);
    CompletableFuture<List<String>> expectedBuildTilesAsync(UUID snapshotId);
    void deleteSchematic(UUID snapshotId);
    CompletableFuture<PlotBuildBackup.StorageReport> maintainStorageAsync(boolean dryRun);
    CompletableFuture<Boolean> linkCaptureOperationAsync(UUID snapshotId, UUID operationId);
    CompletableFuture<Boolean> linkRestoreOperationAsync(UUID snapshotId, UUID operationId);
}
