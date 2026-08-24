package com.aegisguard.snapshots;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.snapshots.ClaimSnapshot.SnapshotType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Manages claim snapshots for rollback functionality.
 * Takes snapshots before risky operations (merge, expansion) and allows rollback.
 */
public class SnapshotManager {
    
    private final AegisGuard plugin;
    private final BuildBackupService plotBuildBackup;
    private final AutomaticPlayerBackupService automaticPlayerBackups;
    private final RestoreOperationStore operationStore;
    private final File file;
    private FileConfiguration data;
    
    // In-memory cache: snapshotId -> snapshot
    private final Map<UUID, ClaimSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Set<CompletableFuture<RestoreResult>> inFlightRestores = ConcurrentHashMap.newKeySet();
    private final Map<UUID, RestoreOperation> restoreOperations = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeRestoreByPlot = new ConcurrentHashMap<>();
    
    private volatile boolean isDirty = false;
    
    public SnapshotManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.plotBuildBackup = createBuildBackupService(plugin);
        this.automaticPlayerBackups = new AutomaticPlayerBackupService(plugin, this);
        this.file = new File(plugin.getDataFolder(), "claim-snapshots.yml");
        this.operationStore = new RestoreOperationStore(
                new File(plugin.getDataFolder(), "restore-operations.yml"));
    }

    public BuildBackupService buildBackup() {
        return plotBuildBackup;
    }

    private static BuildBackupService createBuildBackupService(AegisGuard plugin) {
        try {
            Class.forName("com.sk89q.worldedit.WorldEdit", false, plugin.getClass().getClassLoader());
            return new PlotBuildBackup(plugin);
        } catch (Throwable unavailable) {
            plugin.getLogger().info("WorldEdit/FAWE is not installed; optional plot build backups are unavailable.");
            return new UnavailableBuildBackupService(plugin);
        }
    }

    public AutomaticPlayerBackupService automaticPlayerBackups() {
        return automaticPlayerBackups;
    }

    public record AutomaticSnapshotResult(UUID snapshotId, boolean dataSaved,
                                          PlotBuildBackup.CaptureResult buildResult, String detail) { }

    /** Called from the plot's owning region after eligibility and change detection pass. */
    CompletableFuture<AutomaticSnapshotResult> createAutomaticSnapshot(
            Plot plot, ClaimSnapshot snapshot, boolean captureBuild,
            int retentionPerPlot, long retentionDays) {
        if (plot == null || snapshot == null || !plot.getPlotId().equals(snapshot.getPlotId())) {
            return CompletableFuture.completedFuture(new AutomaticSnapshotResult(
                    snapshot == null ? null : snapshot.getSnapshotId(), false,
                    PlotBuildBackup.CaptureResult.FAILED, "Invalid automatic snapshot target"));
        }
        if (isRestoreLocked(plot.getPlotId())) {
            return CompletableFuture.completedFuture(new AutomaticSnapshotResult(
                    snapshot.getSnapshotId(), false, PlotBuildBackup.CaptureResult.FAILED,
                    "Plot is maintenance-locked by a restore"));
        }
        snapshots.put(snapshot.getSnapshotId(), snapshot);
        setDirty(true);
        CompletableFuture<PlotBuildBackup.CaptureResult> build = captureBuild
                ? plotBuildBackup.captureAsync(plot, snapshot, true)
                : CompletableFuture.completedFuture(PlotBuildBackup.CaptureResult.DISABLED);
        pruneAutomaticSnapshots(plot.getPlotId(), snapshot.getType(), retentionPerPlot, retentionDays,
                System.currentTimeMillis(), snapshot.getSnapshotId());
        pruneOldSnapshots(Set.of(snapshot.getSnapshotId()));
        return build.handle((buildResult, error) -> error == null && buildResult != null
                        ? buildResult : PlotBuildBackup.CaptureResult.FAILED)
                .thenCompose(buildResult -> persistSnapshotsAsync().handle((ignored, error) ->
                        {
                            if (error == null) {
                                return new AutomaticSnapshotResult(snapshot.getSnapshotId(), true, buildResult,
                                        snapshot.getType() == SnapshotType.AUTOMATIC_SERVER_ZONE
                                                ? "Automatic server-zone snapshot saved"
                                                : "Automatic player-plot snapshot saved");
                            }
                            snapshots.remove(snapshot.getSnapshotId(), snapshot);
                            plotBuildBackup.deleteSchematic(snapshot.getSnapshotId());
                            setDirty(true);
                            return new AutomaticSnapshotResult(snapshot.getSnapshotId(), false, buildResult,
                                    "Snapshot persistence failed: " + message(error));
                        }));
    }
    
    /**
     * Take a snapshot of a plot before a risky operation.
     * @return The created snapshot
     */
    public ClaimSnapshot createSnapshot(Plot plot, SnapshotType type, String reason, UUID triggeredBy) {
        ClaimSnapshot snapshot = captureSnapshot(plot, type, reason, triggeredBy);
        snapshots.put(snapshot.getSnapshotId(), snapshot);
        setDirty(true);
        
        plugin.console().info("log_snapshots_created",
                "[Snapshots] Created snapshot {ID} for plot {PLOT} ({TYPE})",
                "ID", String.valueOf(snapshot.getSnapshotId()),
                "PLOT", String.valueOf(plot.getPlotId()),
                "TYPE", String.valueOf(type));
        
        plotBuildBackup.queueCapture(plot, snapshot);
        // Register any build capture before pruning so its data record and files stay paired.
        pruneOldSnapshots(Set.of(snapshot.getSnapshotId()));
        
        return snapshot;
    }

    /** Create immediately on the owning plot thread, but acknowledge only after YAML is durable. */
    public CompletableFuture<ClaimSnapshot> createSnapshotDurableAsync(
            Plot plot, SnapshotType type, String reason, UUID triggeredBy) {
        ClaimSnapshot snapshot = createSnapshotRecord(plot, type, reason, triggeredBy);
        return persistSnapshotsAsync().handle((ignored, error) -> {
            if (error != null) {
                snapshots.remove(snapshot.getSnapshotId(), snapshot);
                setDirty(true);
                throw new java.util.concurrent.CompletionException(error);
            }
            var dispatch = plugin.scheduler().runGlobal(() -> {
                plotBuildBackup.queueCapture(plot, snapshot);
                pruneOldSnapshots(Set.of(snapshot.getSnapshotId()));
            });
            if (!dispatch.accepted()) {
                plugin.getLogger().warning("Scheduler rejected durable snapshot build capture for "
                        + snapshot.getSnapshotId());
            }
            return snapshot;
        });
    }

    /** Create all server-zone records, then durably publish the batch as one snapshot-index write. */
    public CompletableFuture<Integer> createServerZoneSnapshotsDurableAsync(
            UUID triggeredBy, String reason, SnapshotType type) {
        if (plugin.store() == null) return CompletableFuture.completedFuture(0);
        List<Map.Entry<Plot, ClaimSnapshot>> created = new ArrayList<>();
        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null || !plot.isServerZone()) continue;
            created.add(Map.entry(plot, createSnapshotRecord(plot,
                    type == null ? SnapshotType.MANUAL : type, reason, triggeredBy)));
        }
        if (created.isEmpty()) return CompletableFuture.completedFuture(0);
        return persistSnapshotsAsync().handle((ignored, error) -> {
            if (error != null) {
                for (Map.Entry<Plot, ClaimSnapshot> entry : created) {
                    snapshots.remove(entry.getValue().getSnapshotId(), entry.getValue());
                }
                setDirty(true);
                throw new java.util.concurrent.CompletionException(error);
            }
            var dispatch = plugin.scheduler().runGlobal(() -> {
                for (Map.Entry<Plot, ClaimSnapshot> entry : created) {
                    plotBuildBackup.queueCapture(entry.getKey(), entry.getValue());
                }
                pruneOldSnapshots(created.stream().map(entry -> entry.getValue().getSnapshotId())
                        .collect(Collectors.toSet()));
            });
            if (!dispatch.accepted()) {
                plugin.getLogger().warning("Scheduler rejected durable server-zone build captures");
            }
            return created.size();
        });
    }

    public int createSnapshotsForServerZones(UUID triggeredBy, String reason, SnapshotType type) {
        if (plugin.store() == null) return 0;
        int count = 0;
        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null || !plot.isServerZone()) continue;
            createSnapshot(plot, type == null ? SnapshotType.MANUAL : type, reason, triggeredBy);
            count++;
        }
        return count;
    }

    public boolean isScheduledEnabled() {
        return plugin.getConfig().getBoolean("snapshots.enabled", true)
                && plugin.getConfig().getBoolean("snapshots.scheduled.enabled", false);
    }

    public boolean usesUnifiedAutomaticServerZoneBackups() {
        return automaticPlayerBackups.includesServerZones();
    }

    public int getScheduledIntervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("snapshots.scheduled.interval_minutes", 360));
    }

    /**
     * Cycle Off → 15 → 60 → 360 → 1440 → Off. Returns next interval minutes, or 0 when off.
     */
    public int cycleScheduledInterval() {
        boolean enabled = plugin.getConfig().getBoolean("snapshots.scheduled.enabled", false);
        int current = plugin.getConfig().getInt("snapshots.scheduled.interval_minutes", 360);
        int[] steps = {15, 60, 360, 1440};
        int nextMinutes = 0;
        boolean nextEnabled = false;
        if (!enabled) {
            nextEnabled = true;
            nextMinutes = 15;
        } else {
            int idx = -1;
            for (int i = 0; i < steps.length; i++) {
                if (steps[i] == current) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                nextEnabled = true;
                nextMinutes = 15;
            } else if (idx + 1 < steps.length) {
                nextEnabled = true;
                nextMinutes = steps[idx + 1];
            } else {
                nextEnabled = false;
                nextMinutes = 360;
            }
        }
        plugin.getConfig().set("snapshots.scheduled.enabled", nextEnabled);
        plugin.getConfig().set("snapshots.scheduled.interval_minutes", nextMinutes);
        plugin.getConfig().set("snapshots.scheduled.targets", "server_zones");
        if (plugin.cfg() != null && plugin.cfg().raw() != null && plugin.cfg().raw() != plugin.getConfig()) {
            plugin.cfg().raw().set("snapshots.scheduled.enabled", nextEnabled);
            plugin.cfg().raw().set("snapshots.scheduled.interval_minutes", nextMinutes);
            plugin.cfg().raw().set("snapshots.scheduled.targets", "server_zones");
        }
        return nextEnabled ? nextMinutes : 0;
    }

    public int runScheduledPass() {
        if (!isScheduledEnabled() || usesUnifiedAutomaticServerZoneBackups()) return 0;
        return createSnapshotsForServerZones(null, "Scheduled server-zone snapshot", SnapshotType.SCHEDULED);
    }
    
    /**
     * Rollback a plot to a previous snapshot.
     * @return true if rollback succeeded
     */
    public enum RestoreStatus {
        SNAPSHOT_NOT_FOUND,
        DUPLICATE_OPERATION,
        BACKUP_IN_PROGRESS,
        WORLD_UNAVAILABLE,
        SCHEDULER_REJECTED,
        WRONG_REGION,
        DATA_RESTORED,
        BUILD_QUEUED,
        BUILD_PARTIALLY_QUEUED,
        BUILD_UNAVAILABLE,
        COMPLETE,
        PARTIAL,
        PAUSED_REVIEW,
        FAILED
    }

    public record RestoreResult(UUID snapshotId, UUID plotId, RestoreStatus status,
                                PlotBuildBackup.RestoreQueueResult buildResult, String detail) {
        public boolean dataRestored() {
            return status == RestoreStatus.DATA_RESTORED
                    || status == RestoreStatus.BUILD_QUEUED
                    || status == RestoreStatus.BUILD_PARTIALLY_QUEUED
                    || status == RestoreStatus.COMPLETE
                    || status == RestoreStatus.PARTIAL;
        }

        public boolean complete() {
            return status == RestoreStatus.DATA_RESTORED || status == RestoreStatus.COMPLETE;
        }
    }

    /**
     * Dispatch restoration from any thread. World lookup occurs on the global/main scheduler and
     * live plot mutation occurs on the target plot's region scheduler.
     */
    public CompletableFuture<RestoreResult> restoreAsync(UUID snapshotId) {
        // Preserve the legacy API's dependable data-rollback behavior. Build mutation is opt-in
        // through the scoped overload so a missing/default-off integration cannot block callers.
        return restoreAsync(snapshotId, null, EnumSet.of(RestoreScope.FULL_DATA));
    }

    /**
     * Start a durable, duplicate-safe restore transaction. The rescue snapshot and operation state
     * are flushed before live plot data is touched; build completion is awaited before success.
     */
    public CompletableFuture<RestoreResult> restoreAsync(UUID snapshotId, UUID actorId,
                                                          Set<RestoreScope> requestedScopes) {
        ClaimSnapshot snapshot = snapshots.get(snapshotId);
        if (snapshot == null) {
            return CompletableFuture.completedFuture(result(snapshotId, null,
                    RestoreStatus.SNAPSHOT_NOT_FOUND, null, "Snapshot not found"));
        }
        EnumSet<RestoreScope> scopes = RestoreScope.normalize(requestedScopes);
        if (RestoreScope.includesData(scopes)) {
            try {
                PlotSnapshotState.validate(snapshot.getExtendedStateBlob());
                TerritoryRentalSnapshotState.validate(
                        snapshot.getTerritoryRentalStateBlob(), snapshot.getPlotId());
            } catch (IllegalArgumentException error) {
                return CompletableFuture.completedFuture(result(snapshotId, snapshot.getPlotId(),
                        RestoreStatus.FAILED, null, "Snapshot data preflight failed: " + message(error)));
            }
        }
        if (scopes.contains(RestoreScope.BUILD)) {
            return previewAsync(snapshotId, scopes).thenCompose(preview -> {
                if (preview == null) {
                    return CompletableFuture.completedFuture(result(snapshotId, snapshot.getPlotId(),
                            RestoreStatus.SNAPSHOT_NOT_FOUND, null, "Snapshot not found during preflight"));
                }
                if (!preview.ready()) {
                    return CompletableFuture.completedFuture(result(snapshotId, snapshot.getPlotId(),
                            RestoreStatus.BUILD_UNAVAILABLE, null, preview.preflightMessage()));
                }
                return restoreValidated(snapshot, actorId, scopes);
            }).exceptionally(error -> result(snapshotId, snapshot.getPlotId(),
                    RestoreStatus.FAILED, null, "Restore preflight failed: " + message(error)));
        }
        return restoreValidated(snapshot, actorId, scopes);
    }

    private CompletableFuture<RestoreResult> restoreValidated(ClaimSnapshot snapshot, UUID actorId,
                                                               EnumSet<RestoreScope> scopes) {
        UUID snapshotId = snapshot.getSnapshotId();
        if (automaticPlayerBackups.isPlotInFlight(snapshot.getPlotId())) {
            return CompletableFuture.completedFuture(result(snapshotId, snapshot.getPlotId(),
                    RestoreStatus.BACKUP_IN_PROGRESS, null,
                    "An automatic backup is already in progress for this plot"));
        }
        CompletableFuture<RestoreResult> future = new CompletableFuture<>();
        inFlightRestores.add(future);
        future.whenComplete((ignored, error) -> inFlightRestores.remove(future));

        if (plugin.scheduler() == null) {
            future.complete(result(snapshotId, snapshot.getPlotId(), RestoreStatus.SCHEDULER_REJECTED,
                    null, "Scheduler is unavailable"));
            return future;
        }

        RestoreOperation operation = RestoreOperation.start(
                snapshotId, snapshot.getPlotId(), actorId, scopes, System.currentTimeMillis());
        UUID existing = activeRestoreByPlot.putIfAbsent(snapshot.getPlotId(), operation.operationId());
        if (existing != null) {
            future.complete(result(snapshotId, snapshot.getPlotId(), RestoreStatus.DUPLICATE_OPERATION,
                    null, "Restore already active for this plot: " + existing));
            return future;
        }
        restoreOperations.put(operation.operationId(), operation);
        persistOperationsAsync().whenComplete((ignored, persistError) -> {
            if (persistError != null) {
                finishBeforeMutation(operation, future, RestoreStatus.FAILED,
                        "Could not persist restore preflight: " + message(persistError));
                return;
            }
            dispatchRescue(snapshot, operation, future);
        });
        return future;
    }

    /** Build a staff-facing preflight without mutating plot or snapshot state. */
    public CompletableFuture<RestorePreview> previewAsync(UUID snapshotId, Set<RestoreScope> requestedScopes) {
        ClaimSnapshot snapshot = snapshots.get(snapshotId);
        if (snapshot == null) return CompletableFuture.completedFuture(null);
        EnumSet<RestoreScope> scopes = RestoreScope.normalize(requestedScopes);
        CompletableFuture<RestorePreview> result = new CompletableFuture<>();
        if (plugin.scheduler() == null) {
            result.complete(preview(snapshot, null, scopes, false,
                    new PlotBuildBackup.BackupInspection(false, 0L, 0), false,
                    "Scheduler unavailable"));
            return result;
        }
        var global = plugin.scheduler().runGlobal(() -> {
            try {
                Plot current = plugin.store().getPlotById(snapshot.getPlotId());
                org.bukkit.World liveWorld = Bukkit.getWorld(current == null
                        ? snapshot.getWorldName() : current.getWorldName());
                org.bukkit.World buildWorld = scopes.contains(RestoreScope.BUILD)
                        ? Bukkit.getWorld(snapshot.getWorldName()) : liveWorld;
                plotBuildBackup.inspectAsync(snapshot).whenComplete((inspection, error) -> {
                    PlotBuildBackup.BackupInspection safe = error == null && inspection != null ? inspection
                            : new PlotBuildBackup.BackupInspection(false, 0L, 0);
                    boolean destinationSafe = buildDestinationSafe(snapshot, current, scopes);
                    String detail = current == null ? "Live plot no longer exists; rescue capture is impossible"
                            : liveWorld == null ? "Live plot world is not loaded for rescue capture"
                            : scopes.contains(RestoreScope.BUILD) && buildWorld == null
                            ? "Snapshot build world is not loaded"
                            : scopes.contains(RestoreScope.BUILD) && !destinationSafe
                            ? "Build-only restore refused because the live plot world or bounds differ; "
                            + "include identity/bounds restoration or restore data only"
                            : scopes.contains(RestoreScope.BUILD) && !safe.present()
                            ? "Selected build restore cannot run because no build backup exists"
                            : scopes.contains(RestoreScope.BUILD)
                            && safe.integrity() != PlotBuildBackup.IntegrityStatus.VALID
                            ? "Selected build restore failed integrity preflight: " + safe.detail()
                            : scopes.contains(RestoreScope.BUILD) && !safe.compatible()
                            ? "Selected build restore is incompatible: " + safe.detail()
                            : "Ready for explicit confirmation";
                    result.complete(preview(snapshot, current, scopes,
                            current != null && liveWorld != null && buildWorld != null, safe,
                            destinationSafe, detail));
                });
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        if (!global.accepted()) {
            result.completeExceptionally(new IllegalStateException("Global scheduler rejected preview: " + global));
        }
        return result;
    }

    private void dispatchRescue(ClaimSnapshot targetSnapshot, RestoreOperation operation,
                                CompletableFuture<RestoreResult> future) {
        var global = plugin.scheduler().runGlobal(() -> {
            try {
                Plot current = plugin.store().getPlotById(targetSnapshot.getPlotId());
                String worldName = current == null ? targetSnapshot.getWorldName() : current.getWorldName();
                org.bukkit.World world = worldName == null ? null : Bukkit.getWorld(worldName);
                if (world == null) {
                    finishBeforeMutation(operation, future, RestoreStatus.WORLD_UNAVAILABLE,
                            "Live plot world is not loaded");
                    return;
                }
                int x = current == null ? targetSnapshot.getX1() : current.getX1();
                int z = current == null ? targetSnapshot.getZ1() : current.getZ1();
                Location target = new Location(world, x, world.getMinHeight(), z);
                var region = plugin.scheduler().runAt(target, () -> createRescueOnOwnedRegion(
                        targetSnapshot, current, operation, future, target));
                if (!region.accepted()) finishBeforeMutation(operation, future,
                        RestoreStatus.SCHEDULER_REJECTED,
                        "Region scheduler rejected rescue capture: " + region);
            } catch (Throwable error) {
                finishBeforeMutation(operation, future, RestoreStatus.FAILED,
                        "Rescue dispatch failed: " + message(error));
            }
        });
        if (!global.accepted()) finishBeforeMutation(operation, future, RestoreStatus.SCHEDULER_REJECTED,
                "Global scheduler rejected restore: " + global);
    }

    private void createRescueOnOwnedRegion(ClaimSnapshot targetSnapshot, Plot current,
                                            RestoreOperation operation,
                                            CompletableFuture<RestoreResult> future, Location target) {
        if (!plugin.scheduler().owns(target)) {
            finishBeforeMutation(operation, future, RestoreStatus.WRONG_REGION,
                    "Region ownership check failed before rescue capture");
            return;
        }
        try {
            Plot live = current != null ? current : plugin.store().getPlotById(targetSnapshot.getPlotId());
            if (live == null) {
                finishBeforeMutation(operation, future, RestoreStatus.FAILED,
                        "Live plot no longer exists; restore was not started");
                return;
            }
            operation.transition(RestoreOperation.Status.RESCUE_CREATING,
                    "Creating automatic pre-restore rescue snapshot", System.currentTimeMillis());
            ClaimSnapshot rescue = createSnapshotRecord(live, SnapshotType.PRE_RESTORE_RESCUE,
                    "Automatic rescue before restore operation " + operation.operationId(), operation.actorId());
            operation.setRescueSnapshotId(rescue.getSnapshotId(), System.currentTimeMillis());
            // A build restore always needs the chance to create a build rescue, even when the
            // source came from separately enabled automatic build backups and manual capture is off.
            boolean buildRequested = operation.scopes().contains(RestoreScope.BUILD);
            boolean rescueIntegrationCompatible = plotBuildBackup.integrationInfo().compatible();
            if (buildRequested && !rescueIntegrationCompatible) {
                persistSnapshotsAsync().whenComplete((ignored, error) -> finishBeforeMutation(
                        operation, future, RestoreStatus.FAILED,
                        "Build integration became unavailable before rescue capture"));
                return;
            }
            boolean buildRescueEnabled = rescueIntegrationCompatible
                    && (buildRequested
                    || plotBuildBackup.isConfiguredOn());
            boolean requireBuildRescue = buildRescueEnabled && plugin.getConfig().getBoolean(
                    "snapshots.restore.require_build_rescue_when_enabled", true);
            CompletableFuture<PlotBuildBackup.CaptureResult> capture = buildRescueEnabled
                    ? plotBuildBackup.captureAsync(live, rescue, true)
                    : CompletableFuture.completedFuture(PlotBuildBackup.CaptureResult.DISABLED);
            capture.whenComplete((captureResult, captureError) -> {
                boolean buildFailed = captureError != null
                        || captureResult != PlotBuildBackup.CaptureResult.COMPLETED;
                if (requireBuildRescue && buildFailed) {
                    persistSnapshotsAsync().whenComplete((ignored, error) -> finishBeforeMutation(
                            operation, future, RestoreStatus.FAILED,
                            "Rescue build backup was not safely completed: "
                                    + (captureError == null ? captureResult : message(captureError))));
                    return;
                }
                CompletableFuture<Boolean> link = captureResult == PlotBuildBackup.CaptureResult.COMPLETED
                        ? plotBuildBackup.linkCaptureOperationAsync(
                        rescue.getSnapshotId(), operation.operationId())
                        : CompletableFuture.completedFuture(true);
                link.thenCompose(linked -> {
                    if (requireBuildRescue && !linked) {
                        return CompletableFuture.failedFuture(new IllegalStateException(
                                "Rescue build metadata could not be linked to the restore operation"));
                    }
                    return persistSnapshotsAsync();
                }).thenCompose(ignored -> {
                    operation.transition(RestoreOperation.Status.DATA_RESTORING,
                            "Rescue snapshot durable; applying selected plot data", System.currentTimeMillis());
                    return persistOperationsAsync();
                }).whenComplete((ignored, persistError) -> {
                    if (persistError != null) {
                        finishBeforeMutation(operation, future, RestoreStatus.FAILED,
                                "Could not persist rescue state: " + message(persistError));
                    } else {
                        dispatchDataRestore(targetSnapshot, operation, future);
                    }
                });
            });
        } catch (Throwable error) {
            finishBeforeMutation(operation, future, RestoreStatus.FAILED, message(error));
        }
    }

    private void dispatchDataRestore(ClaimSnapshot snapshot, RestoreOperation operation,
                                     CompletableFuture<RestoreResult> future) {
        var global = plugin.scheduler().runGlobal(() -> {
            try {
                Plot live = plugin.store().getPlotById(snapshot.getPlotId());
                String worldName = live == null ? snapshot.getWorldName() : live.getWorldName();
                org.bukkit.World world = worldName == null ? null : Bukkit.getWorld(worldName);
                if (world == null) {
                    finishBeforeMutation(operation, future, RestoreStatus.WORLD_UNAVAILABLE,
                            "World became unavailable after rescue snapshot");
                    return;
                }
                int x = live == null ? snapshot.getX1() : live.getX1();
                int z = live == null ? snapshot.getZ1() : live.getZ1();
                Location target = new Location(world, x, world.getMinHeight(), z);
                var region = plugin.scheduler().runAt(target,
                        () -> applyDataOnOwnedRegion(snapshot, live, operation, future, target));
                if (!region.accepted()) finishBeforeMutation(operation, future,
                        RestoreStatus.SCHEDULER_REJECTED,
                        "Region scheduler rejected data restore: " + region);
            } catch (Throwable error) {
                finishBeforeMutation(operation, future, RestoreStatus.FAILED,
                        "Data dispatch failed: " + message(error));
            }
        });
        if (!global.accepted()) finishBeforeMutation(operation, future, RestoreStatus.SCHEDULER_REJECTED,
                "Global scheduler rejected data restore");
    }

    private void applyDataOnOwnedRegion(ClaimSnapshot snapshot, Plot live, RestoreOperation operation,
                                        CompletableFuture<RestoreResult> future, Location target) {
        if (!plugin.scheduler().owns(target)) {
            finishBeforeMutation(operation, future, RestoreStatus.WRONG_REGION,
                    "Region ownership check failed before data restore");
            return;
        }
        try {
            Plot plot = live != null ? live : plugin.store().getPlotById(snapshot.getPlotId());
            if (plot == null) {
                plot = new Plot(snapshot.getPlotId(), snapshot.getOwner(), snapshot.getOwnerName(),
                        snapshot.getWorldName(), snapshot.getX1(), snapshot.getZ1(), snapshot.getX2(), snapshot.getZ2());
            }
            if (RestoreScope.includesData(operation.scopes())) {
                TerritoryRentalSnapshotState.validate(snapshot.getTerritoryRentalStateBlob(), snapshot.getPlotId());
                restorePlotState(plot, snapshot, operation.scopes());
                restoreTerritoryState(plot, snapshot, operation.scopes());
                plugin.store().reindexPlot(plot);
                plugin.store().setDirty(true);
            }
            Plot persistedPlot = plot;
            persistPlotAsync(persistedPlot)
                    .thenCompose(ignored -> persistRestoredServicesAsync(operation.scopes()))
                    .whenComplete((ignored, persistError) -> {
                if (persistError != null) {
                    finishAfterMutation(operation, future, null,
                            "Plot data changed but persistence failed: " + message(persistError));
                    return;
                }
                operation.markDataRestored(RestoreScope.includesData(operation.scopes())
                        ? "Selected plot data restored" : "Build-only restore; plot data unchanged",
                        System.currentTimeMillis());
                if (!operation.scopes().contains(RestoreScope.BUILD)) {
                    finishComplete(operation, future, null, "Selected plot data restored");
                    return;
                }
                prepareTrackedBuildRestore(snapshot, operation, future);
                    });
        } catch (Throwable error) {
            finishAfterMutation(operation, future, null, message(error));
        }
    }

    private void restoreTerritoryState(Plot plot, ClaimSnapshot snapshot, Set<RestoreScope> scopes) {
        if (plugin.territoryLife() == null) return;
        boolean economy = scopes.contains(RestoreScope.ECONOMY);
        boolean zones = scopes.contains(RestoreScope.ZONES_AND_STALLS);
        if (economy && !snapshot.getTerritoryRentalStateBlob().isBlank()) {
            TerritoryRentalSnapshotState.State state = TerritoryRentalSnapshotState.decode(
                    snapshot.getTerritoryRentalStateBlob());
            plugin.territoryLife().restoreRentalState(plot.getPlotId(), state.offer(), state.contract());
            plugin.territoryLife().synchronizeRestoredPlot(plot, false, zones);
            return;
        }
        // Pre-1.3.5 snapshots have no external rental payload; retain the safe reconciliation behavior.
        plugin.territoryLife().synchronizeRestoredPlot(plot, economy, zones);
    }

    private void prepareTrackedBuildRestore(ClaimSnapshot snapshot, RestoreOperation operation,
                                            CompletableFuture<RestoreResult> future) {
        plotBuildBackup.expectedBuildTilesAsync(snapshot.getSnapshotId()).thenCompose(tileIds -> {
            if (tileIds.isEmpty()) return CompletableFuture.failedFuture(
                    new IllegalStateException("No validated build files are available"));
            operation.initializeBuildTiles(tileIds, System.currentTimeMillis());
            operation.transition(RestoreOperation.Status.BUILD_QUEUED,
                    "Validated build tiles queued: " + tileIds.size(), System.currentTimeMillis());
            return plotBuildBackup.linkRestoreOperationAsync(
                    snapshot.getSnapshotId(), operation.operationId()).thenCompose(linked -> linked
                    ? persistOperationsAsync()
                    : CompletableFuture.failedFuture(new IllegalStateException(
                    "Build metadata could not be linked to the restore operation")));
        }).thenCompose(ignored -> {
            operation.transition(RestoreOperation.Status.BUILD_RUNNING,
                    "Restoring validated build tiles", System.currentTimeMillis());
            return persistOperationsAsync();
        }).whenComplete((ignored, error) -> {
            if (error != null) {
                finishAfterMutation(operation, future, null,
                        "Data restored, but build state could not be prepared: " + message(error));
                return;
            }
            runTrackedBuildRestore(snapshot, operation, future);
        });
    }

    private void runTrackedBuildRestore(ClaimSnapshot snapshot, RestoreOperation operation,
                                        CompletableFuture<RestoreResult> future) {
        restoreTrackedAsync(snapshot, operation.completedBuildTiles(),
                (tileId, success) -> {
                    operation.checkpointBuildTile(tileId, success, System.currentTimeMillis());
                    return persistOperationsAsync();
                }).whenComplete((report, error) -> {
            if (error != null || report == null) {
                operation.markBuildResult("FAILED", System.currentTimeMillis());
                finishAfterMutation(operation, future, PlotBuildBackup.RestoreQueueResult.FAILED,
                        "Data restored; tracked build restore failed: " + message(error));
                return;
            }
            operation.markBuildResult(report.result().name(), System.currentTimeMillis());
            if (report.result() == PlotBuildBackup.RestoreQueueResult.COMPLETED
                    && report.failed().isEmpty() && report.pending().isEmpty()) {
                finishComplete(operation, future, report.result(),
                        "Data and builds restored; " + report.completed().size() + " region tile(s) verified");
            } else {
                finishAfterMutation(operation, future, report.result(),
                        "Data restored; build restore requires review (completed="
                                + report.completed().size() + ", failed=" + report.failed().size()
                                + ", pending=" + report.pending().size() + ")");
            }
        });
    }

    private CompletableFuture<PlotBuildBackup.TrackedRestoreResult> restoreTrackedAsync(
            ClaimSnapshot snapshot, Set<String> alreadyCompleted,
            java.util.function.BiFunction<String, Boolean, CompletableFuture<Void>> checkpoint) {
        return plotBuildBackup.restoreTracked(snapshot, alreadyCompleted, checkpoint);
    }

    /**
     * Compatibility entry point. The restore is always routed through the durable asynchronous
     * transaction; {@code true} means the request was accepted, not that it has already finished.
     */
    @Deprecated(forRemoval = false)
    public boolean rollback(UUID snapshotId) {
        ClaimSnapshot snapshot = snapshots.get(snapshotId);
        if (snapshot == null) {
            plugin.console().warning("log_snapshots_rollback_missing",
                    "[Snapshots] Cannot rollback: snapshot {ID} not found",
                    "ID", String.valueOf(snapshotId));
            return false;
        }

        CompletableFuture<RestoreResult> future = restoreAsync(snapshotId);
        future.whenComplete((restore, error) -> {
            if (error != null || restore == null || !restore.complete()) {
                plugin.getLogger().warning("[Snapshots] Compatibility rollback did not complete: "
                        + snapshotId + " (" + (error == null ? "unknown result" : message(error)) + ")");
            }
        });
        return true;
    }

    public int inFlightRestoreCount() {
        return inFlightRestores.size();
    }

    public boolean isRestoreLocked(UUID plotId) {
        return plotId != null && activeRestoreByPlot.containsKey(plotId);
    }

    public int maintenanceLockCount() {
        return activeRestoreByPlot.size();
    }

    public RestoreOperation getRestoreOperation(UUID operationId) {
        return operationId == null ? null : restoreOperations.get(operationId);
    }

    public List<RestoreOperation> getRestoreOperations() {
        return restoreOperations.values().stream()
                .sorted(Comparator.comparingLong(RestoreOperation::startedAt).reversed()).toList();
    }

    /** Build artifacts referenced by an active or review-required operation must never be pruned. */
    public Set<UUID> protectedBuildSnapshotIds() {
        Set<UUID> protectedIds = new HashSet<>();
        for (RestoreOperation operation : restoreOperations.values()) {
            if (operation == null || operation.status() == RestoreOperation.Status.COMPLETE
                    || operation.status() == RestoreOperation.Status.FAILED
                    || operation.status() == RestoreOperation.Status.RELEASED) continue;
            if (operation.snapshotId() != null) protectedIds.add(operation.snapshotId());
            if (operation.rescueSnapshotId() != null) protectedIds.add(operation.rescueSnapshotId());
        }
        protectedIds.addAll(plotBuildBackup.activeCaptureIds());
        return Set.copyOf(protectedIds);
    }

    /** Staff acknowledgement releases a partial/paused maintenance lock without deleting history. */
    public CompletableFuture<Boolean> releaseRestoreLockAsync(UUID operationId) {
        RestoreOperation operation = restoreOperations.get(operationId);
        if (operation == null || (operation.status() != RestoreOperation.Status.PARTIAL
                && operation.status() != RestoreOperation.Status.PAUSED_REVIEW)
                || !operation.operationId().equals(activeRestoreByPlot.get(operation.plotId()))) {
            return CompletableFuture.completedFuture(false);
        }
        operation.markReleased("Maintenance lock released after staff review", System.currentTimeMillis());
        return persistOperationsAsync().handle((ignored, error) -> {
            if (error != null) {
                operation.reopenForReview("Release persistence failed; review remains required: "
                        + message(error), System.currentTimeMillis());
                return false;
            }
            return activeRestoreByPlot.remove(operation.plotId(), operation.operationId());
        });
    }

    /** Retry the same durable transaction, skipping build tiles already checkpointed complete. */
    public CompletableFuture<RestoreResult> retryRestore(UUID operationId, UUID actorId) {
        RestoreOperation operation = restoreOperations.get(operationId);
        if (operation == null || (operation.status() != RestoreOperation.Status.PARTIAL
                && operation.status() != RestoreOperation.Status.PAUSED_REVIEW)) {
            return CompletableFuture.completedFuture(result(null, null, RestoreStatus.FAILED, null,
                    "Operation is not eligible for retry"));
        }
        ClaimSnapshot snapshot = snapshots.get(operation.snapshotId());
        if (snapshot == null) return CompletableFuture.completedFuture(result(
                operation.snapshotId(), operation.plotId(), RestoreStatus.SNAPSHOT_NOT_FOUND,
                null, "Source snapshot no longer exists"));
        if (!operation.operationId().equals(activeRestoreByPlot.get(operation.plotId()))) {
            return CompletableFuture.completedFuture(result(operation.snapshotId(), operation.plotId(),
                    RestoreStatus.FAILED, null, "Maintenance lock is no longer owned by this operation"));
        }
        CompletableFuture<RestoreResult> future = new CompletableFuture<>();
        inFlightRestores.add(future);
        future.whenComplete((ignored, error) -> inFlightRestores.remove(future));
        try {
            operation.resumeForRetry("Staff retry requested by " + actorId, System.currentTimeMillis());
        } catch (RuntimeException error) {
            future.complete(result(operation.snapshotId(), operation.plotId(), RestoreStatus.FAILED,
                    null, message(error)));
            return future;
        }
        persistOperationsAsync().whenComplete((ignored, error) -> {
            if (error != null) {
                operation.pauseForReviewIfActive("Retry persistence failed: " + message(error),
                        System.currentTimeMillis());
                future.complete(result(operation.snapshotId(), operation.plotId(),
                        RestoreStatus.PAUSED_REVIEW, null, operation.detail()));
            } else if (operation.dataRestored() && operation.scopes().contains(RestoreScope.BUILD)) {
                runTrackedBuildRestore(snapshot, operation, future);
            } else {
                resumeBeforeDataRestore(snapshot, operation, future);
            }
        });
        return future;
    }

    /**
     * A restart may pause either before or after rescue capture. Never bypass that boundary: a
     * missing rescue is recreated, while an invalid persisted build rescue requires staff review.
     */
    private void resumeBeforeDataRestore(ClaimSnapshot source, RestoreOperation operation,
                                         CompletableFuture<RestoreResult> future) {
        UUID rescueId = operation.rescueSnapshotId();
        ClaimSnapshot rescue = rescueId == null ? null : snapshots.get(rescueId);
        if (rescue == null) {
            dispatchRescue(source, operation, future);
            return;
        }
        if (!operation.scopes().contains(RestoreScope.BUILD)) {
            dispatchDataRestore(source, operation, future);
            return;
        }
        plotBuildBackup.inspectAsync(rescue).whenComplete((inspection, error) -> {
            if (error == null && inspection != null && inspection.validForRestore()) {
                dispatchDataRestore(source, operation, future);
                return;
            }
            operation.pauseForReviewIfActive(
                    "Retry refused: persisted build rescue is missing, corrupt, or incompatible",
                    System.currentTimeMillis());
            persistOperationsAsync().whenComplete((ignored, persistError) -> future.complete(result(
                    operation.snapshotId(), operation.plotId(), RestoreStatus.PAUSED_REVIEW, null,
                    operation.detail() + (persistError == null ? ""
                            : "; pause persistence failed: " + message(persistError)))));
        });
    }

    public void shutdownOperations() {
        automaticPlayerBackups.shutdown();
        long now = System.currentTimeMillis();
        for (RestoreOperation operation : restoreOperations.values()) {
            if (operation.pauseForReviewIfActive(
                    "Server stopped before restore completed; staff review required", now)) {
                activeRestoreByPlot.put(operation.plotId(), operation.operationId());
            }
        }
        for (CompletableFuture<RestoreResult> future : new ArrayList<>(inFlightRestores)) {
            future.complete(result(null, null, RestoreStatus.PAUSED_REVIEW, null,
                    "Plugin shutdown paused restore for staff review"));
        }
        inFlightRestores.clear();
        try {
            operationStore.save(restoreOperations.values());
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Failed to persist restore operations during shutdown", error);
        }
    }

    private ClaimSnapshot createSnapshotRecord(Plot plot, SnapshotType type, String reason, UUID triggeredBy) {
        ClaimSnapshot snapshot = captureSnapshot(plot, type, reason, triggeredBy);
        snapshots.put(snapshot.getSnapshotId(), snapshot);
        setDirty(true);
        plugin.console().info("log_snapshots_created",
                "[Snapshots] Created snapshot {ID} for plot {PLOT} ({TYPE})",
                "ID", String.valueOf(snapshot.getSnapshotId()),
                "PLOT", String.valueOf(plot.getPlotId()), "TYPE", String.valueOf(type));
        return snapshot;
    }

    ClaimSnapshot captureSnapshot(Plot plot, SnapshotType type, String reason, UUID triggeredBy) {
        String rentalState = TerritoryRentalSnapshotState.capture(plugin.territoryLife(), plot.getPlotId());
        return new ClaimSnapshot(plot, type, reason, triggeredBy, rentalState);
    }

    private CompletableFuture<Void> persistOperationsAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        var dispatch = plugin.scheduler().runAsync(() -> {
            try {
                operationStore.save(restoreOperations.values());
                future.complete(null);
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        if (!dispatch.accepted()) future.completeExceptionally(
                new IllegalStateException("Async scheduler rejected operation persistence"));
        return future;
    }

    private CompletableFuture<Void> persistSnapshotsAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        var dispatch = plugin.scheduler().runAsync(() -> {
            try {
                save();
                future.complete(null);
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        if (!dispatch.accepted()) future.completeExceptionally(
                new IllegalStateException("Async scheduler rejected snapshot persistence"));
        return future;
    }

    private CompletableFuture<Void> persistPlotAsync(Plot plot) {
        if (plot == null || !plugin.store().isDirty()) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> future = new CompletableFuture<>();
        var dispatch = plugin.scheduler().runAsync(() -> {
            try {
                plugin.store().savePlotSync(plot);
                future.complete(null);
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        if (!dispatch.accepted()) future.completeExceptionally(
                new IllegalStateException("Async scheduler rejected plot persistence"));
        return future;
    }

    private CompletableFuture<Void> persistRestoredServicesAsync(Set<RestoreScope> scopes) {
        if (plugin.territoryLife() == null || scopes == null
                || (!scopes.contains(RestoreScope.ECONOMY)
                && !scopes.contains(RestoreScope.ZONES_AND_STALLS))) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        var dispatch = plugin.scheduler().runAsync(() -> {
            try {
                plugin.territoryLife().save();
                future.complete(null);
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });
        if (!dispatch.accepted()) future.completeExceptionally(
                new IllegalStateException("Async scheduler rejected restored service persistence"));
        return future;
    }

    private void finishComplete(RestoreOperation operation, CompletableFuture<RestoreResult> future,
                                PlotBuildBackup.RestoreQueueResult buildResult, String detail) {
        operation.transition(RestoreOperation.Status.COMPLETE, detail, System.currentTimeMillis());
        pruneOldSnapshots();
        persistOperationsAsync().whenComplete((ignored, error) -> {
            if (error == null) {
                activeRestoreByPlot.remove(operation.plotId(), operation.operationId());
                recordOperationOutcome(operation, "COMPLETE", detail);
                future.complete(result(operation.snapshotId(), operation.plotId(), RestoreStatus.COMPLETE,
                        buildResult, detail + "; operation " + operation.operationId()
                                + "; rescue " + operation.rescueSnapshotId()));
            } else {
                operation.reopenAfterCompletionPersistenceFailure(
                        "Final completion was not durable: " + message(error), System.currentTimeMillis());
                activeRestoreByPlot.put(operation.plotId(), operation.operationId());
                recordOperationOutcome(operation, "PARTIAL", operation.detail());
                future.complete(result(operation.snapshotId(), operation.plotId(), RestoreStatus.PARTIAL,
                        buildResult, detail + "; final state was not durable, so the plot remains locked: "
                                + message(error) + "; operation " + operation.operationId()));
            }
        });
    }

    private void finishAfterMutation(RestoreOperation operation, CompletableFuture<RestoreResult> future,
                                     PlotBuildBackup.RestoreQueueResult buildResult, String detail) {
        operation.transition(RestoreOperation.Status.PARTIAL, detail, System.currentTimeMillis());
        activeRestoreByPlot.put(operation.plotId(), operation.operationId());
        persistOperationsAsync().whenComplete((ignored, error) -> future.complete(result(
                operation.snapshotId(), operation.plotId(), RestoreStatus.PARTIAL, buildResult,
                detail + "; plot remains maintenance-locked; operation " + operation.operationId()
                        + "; rescue " + operation.rescueSnapshotId())));
        recordOperationOutcome(operation, "PARTIAL", detail);
    }

    private void finishBeforeMutation(RestoreOperation operation, CompletableFuture<RestoreResult> future,
                                      RestoreStatus status, String detail) {
        try {
            operation.transition(RestoreOperation.Status.FAILED, detail, System.currentTimeMillis());
        } catch (IllegalStateException ignored) { }
        activeRestoreByPlot.remove(operation.plotId(), operation.operationId());
        persistOperationsAsync().whenComplete((ignored, error) -> future.complete(result(
                operation.snapshotId(), operation.plotId(), status, null,
                detail + "; operation " + operation.operationId())));
        recordOperationOutcome(operation, "FAILED", detail);
    }

    private void recordOperationOutcome(RestoreOperation operation, String outcome, String detail) {
        if (operation == null || plugin.scheduler() == null) return;
        ClaimSnapshot source = snapshots.get(operation.snapshotId());
        ClaimSnapshot rescue = snapshots.get(operation.rescueSnapshotId());
        String previousOwner = rescue == null ? "unknown" : String.valueOf(rescue.getOwner());
        String restoredOwner = source == null ? "unknown" : String.valueOf(source.getOwner());
        long duration = Math.max(0L, System.currentTimeMillis() - operation.startedAt());
        String summary = "outcome=" + outcome + ", operation=" + operation.operationId()
                + ", snapshot=" + operation.snapshotId() + ", scopes=" + operation.scopes()
                + ", previousOwner=" + previousOwner + ", restoredOwner=" + restoredOwner
                + ", rescue=" + operation.rescueSnapshotId() + ", data=" + operation.dataResult()
                + ", build=" + operation.buildResult() + ", tilesCompleted="
                + operation.completedBuildTiles().size() + ", tilesFailed="
                + operation.failedBuildTiles().size() + ", tilesPending="
                + operation.pendingBuildTiles().size() + ", durationMs=" + duration
                + ", detail=" + (detail == null ? "" : detail);
        var dispatch = plugin.scheduler().runGlobal(() -> {
            if (plugin.audit() != null) {
                plugin.audit().record(com.aegisguard.audit.AuditCategory.SNAPSHOT_RESTORE,
                        operation.actorId(), operation.actorId() == null ? "System" : operation.actorId().toString(),
                        String.valueOf(operation.plotId()), summary);
            }
            if (!"COMPLETE".equals(outcome) && plugin.getDiscord() != null) {
                plugin.getDiscord().sendEventKey("restore_failure",
                        "discord_event_restore_failure_title", "AegisGuard restore requires attention",
                        "discord_event_restore_failure_description",
                        "Restore {OPERATION} for plot {PLOT} ended as {OUTCOME}: {DETAIL}",
                        Map.of("OPERATION", String.valueOf(operation.operationId()),
                                "PLOT", String.valueOf(operation.plotId()), "OUTCOME", outcome,
                                "DETAIL", detail == null ? "No detail" : detail), 0xD35400);
            }
        });
        if (!dispatch.accepted()) {
            plugin.getLogger().warning("[Snapshots] Could not schedule restore audit for "
                    + operation.operationId());
        }
    }

    private static RestorePreview preview(ClaimSnapshot snapshot, Plot current,
                                          EnumSet<RestoreScope> scopes, boolean worldLoaded,
                                          PlotBuildBackup.BackupInspection inspection,
                                          boolean buildDestinationSafe, String detail) {
        int minX = Math.min(snapshot.getX1(), snapshot.getX2());
        int maxX = Math.max(snapshot.getX1(), snapshot.getX2());
        int minZ = Math.min(snapshot.getZ1(), snapshot.getZ2());
        int maxZ = Math.max(snapshot.getZ1(), snapshot.getZ2());
        int chunks = (Math.floorDiv(maxX, 16) - Math.floorDiv(minX, 16) + 1)
                * (Math.floorDiv(maxZ, 16) - Math.floorDiv(minZ, 16) + 1);
        return new RestorePreview(snapshot.getSnapshotId(), snapshot.getPlotId(), snapshot.getTimestamp(),
                snapshot.getType(), snapshot.getReason(), snapshot.getTriggeredBy(),
                current == null ? null : current.getOwner(), current == null ? null : current.getOwnerName(),
                snapshot.getOwner(), snapshot.getOwnerName(), snapshot.getWorldName(),
                snapshot.getX1(), snapshot.getZ1(), snapshot.getX2(), snapshot.getZ2(),
                EnumSet.copyOf(scopes), worldLoaded, scopes.contains(RestoreScope.BUILD),
                inspection.present(), inspection.bytes(), inspection.files(), inspection.integrity(),
                inspection.compatible(), buildDestinationSafe, inspection.aggregateChecksum(), inspection.format(),
                inspection.integrationName(), inspection.integrationVersion(), chunks, detail);
    }

    private static boolean buildDestinationSafe(ClaimSnapshot snapshot, Plot current,
                                                Set<RestoreScope> scopes) {
        if (scopes == null || !scopes.contains(RestoreScope.BUILD)) return true;
        if (scopes.contains(RestoreScope.IDENTITY_AND_BOUNDS)) return true;
        return current != null
                && java.util.Objects.equals(current.getWorldName(), snapshot.getWorldName())
                && current.getX1() == snapshot.getX1() && current.getZ1() == snapshot.getZ1()
                && current.getX2() == snapshot.getX2() && current.getZ2() == snapshot.getZ2();
    }

    private static String message(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private static RestoreResult result(UUID snapshotId, UUID plotId, RestoreStatus status,
                                        PlotBuildBackup.RestoreQueueResult buildResult, String detail) {
        return new RestoreResult(snapshotId, plotId, status, buildResult, detail == null ? "" : detail);
    }
    
    /**
     * Get all snapshots for a specific plot.
     */
    public List<ClaimSnapshot> getSnapshotsForPlot(UUID plotId) {
        return snapshots.values().stream()
                .filter(s -> s.getPlotId().equals(plotId))
                .sorted(Comparator.comparingLong(ClaimSnapshot::getTimestamp).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Get all snapshots (for admin GUI).
     */
    public List<ClaimSnapshot> getAllSnapshots() {
        return new ArrayList<>(snapshots.values()).stream()
                .sorted(Comparator.comparingLong(ClaimSnapshot::getTimestamp).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Get a specific snapshot by ID.
     */
    public ClaimSnapshot getSnapshot(UUID snapshotId) {
        return snapshots.get(snapshotId);
    }
    
    /**
     * Delete a snapshot.
     */
    public boolean deleteSnapshot(UUID snapshotId) {
        boolean inUse = restoreOperations.values().stream().anyMatch(operation ->
                snapshotId != null && (snapshotId.equals(operation.snapshotId())
                        || snapshotId.equals(operation.rescueSnapshotId()))
                        && activeRestoreByPlot.containsKey(operation.plotId()));
        if (inUse) return false;
        ClaimSnapshot removed = snapshots.remove(snapshotId);
        if (removed != null) {
            plotBuildBackup.deleteSchematic(snapshotId);
            setDirty(true);
            plugin.console().info("log_snapshots_deleted",
                "[Snapshots] Deleted snapshot {ID}",
                "ID", String.valueOf(snapshotId));
            return true;
        }
        return false;
    }

    /** Remove a snapshot only when the updated index is durable; delete build files afterwards. */
    public CompletableFuture<Boolean> deleteSnapshotDurableAsync(UUID snapshotId) {
        if (snapshotId == null || protectedBuildSnapshotIds().contains(snapshotId)) {
            return CompletableFuture.completedFuture(false);
        }
        ClaimSnapshot removed = snapshots.remove(snapshotId);
        if (removed == null) return CompletableFuture.completedFuture(false);
        setDirty(true);
        return persistSnapshotsAsync().handle((ignored, error) -> {
            if (error != null) {
                snapshots.putIfAbsent(snapshotId, removed);
                setDirty(true);
                return false;
            }
            plotBuildBackup.deleteSchematic(snapshotId);
            plugin.console().info("log_snapshots_deleted",
                    "[Snapshots] Deleted snapshot {ID}", "ID", String.valueOf(snapshotId));
            return true;
        });
    }
    
    /**
     * Prune old snapshots based on config limits.
     */
    private void pruneOldSnapshots() {
        pruneOldSnapshots(Set.of());
    }

    private void pruneOldSnapshots(Set<UUID> additionallyProtected) {
        int maxSnapshots = plugin.getConfig().getInt("snapshots.max_snapshots", 100);
        long keepMinutes = plugin.getConfig().getLong("snapshots.keep_minutes", 10080); // 7 days default
        
        if (snapshots.size() <= maxSnapshots && keepMinutes <= 0) return;
        
        List<ClaimSnapshot> toRemove = selectSnapshotsToPrune(
                snapshots.values(), maxSnapshots, keepMinutes, System.currentTimeMillis());
        Set<UUID> protectedIds = restoreOperations.values().stream()
                .filter(operation -> activeRestoreByPlot.containsKey(operation.plotId()))
                .flatMap(operation -> java.util.stream.Stream.of(
                        operation.snapshotId(), operation.rescueSnapshotId()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        protectedIds.addAll(plotBuildBackup.activeCaptureIds());
        if (additionallyProtected != null) protectedIds.addAll(additionallyProtected);
        toRemove.removeIf(snapshot -> protectedIds.contains(snapshot.getSnapshotId()));
        
        // Remove
        for (ClaimSnapshot snapshot : toRemove) {
            plotBuildBackup.deleteSchematic(snapshot.getSnapshotId());
            snapshots.remove(snapshot.getSnapshotId());
        }
        
        if (!toRemove.isEmpty()) {
            setDirty(true);
            plugin.console().info("log_snapshots_pruned",
                "[Snapshots] Pruned {COUNT} old snapshots",
                "COUNT", String.valueOf(toRemove.size()));
        }
    }

    private void pruneAutomaticSnapshots(UUID plotId, SnapshotType automaticType, int retentionPerPlot,
                                         long retentionDays, long nowMillis, UUID protectedSnapshotId) {
        if (plotId == null) return;
        List<ClaimSnapshot> automatic = snapshots.values().stream()
                .filter(snapshot -> snapshot != null && plotId.equals(snapshot.getPlotId())
                        && !snapshot.getSnapshotId().equals(protectedSnapshotId)
                        && snapshot.getType() == automaticType)
                .toList();
        for (ClaimSnapshot snapshot : selectAutomaticSnapshotsToPrune(
                automatic, Math.max(0, retentionPerPlot - 1), retentionDays, nowMillis)) {
            deleteSnapshot(snapshot.getSnapshotId());
        }
    }

    static List<ClaimSnapshot> selectAutomaticSnapshotsToPrune(
            Collection<ClaimSnapshot> candidates, int retentionPerPlot,
            long retentionDays, long nowMillis) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        int cap = Math.max(0, retentionPerPlot);
        long cutoff = retentionDays <= 0 ? Long.MIN_VALUE
                : nowMillis - retentionDays * 86_400_000L;
        LinkedHashSet<ClaimSnapshot> selected = new LinkedHashSet<>();
        for (ClaimSnapshot snapshot : candidates) {
            if (snapshot != null && snapshot.getTimestamp() < cutoff) selected.add(snapshot);
        }
        List<ClaimSnapshot> survivorsNewestFirst = candidates.stream()
                .filter(Objects::nonNull).filter(snapshot -> !selected.contains(snapshot))
                .sorted(Comparator.comparingLong(ClaimSnapshot::getTimestamp).reversed()).toList();
        for (int index = cap; index < survivorsNewestFirst.size(); index++) {
            selected.add(survivorsNewestFirst.get(index));
        }
        return List.copyOf(selected);
    }

    /**
     * Compute pruning without mutating manager state. Age-expired entries are removed first, then
     * the count cap is applied only to the survivors. Keeping this calculation pure makes the
     * combined age/count behavior directly regression-testable.
     */
    static List<ClaimSnapshot> selectSnapshotsToPrune(Collection<ClaimSnapshot> candidates,
                                                       int maxSnapshots,
                                                       long keepMinutes,
                                                       long nowMillis) {
        if (candidates == null || candidates.isEmpty()) return new ArrayList<>();

        int safeMax = Math.max(0, maxSnapshots);
        long cutoff = nowMillis - (Math.max(0L, keepMinutes) * 60_000L);
        LinkedHashSet<ClaimSnapshot> selected = new LinkedHashSet<>();

        if (keepMinutes > 0) {
            for (ClaimSnapshot snapshot : candidates) {
                if (snapshot != null && snapshot.getTimestamp() < cutoff) selected.add(snapshot);
            }
        }

        int survivors = candidates.size() - selected.size();
        int excess = Math.max(0, survivors - safeMax);
        if (excess > 0) {
            List<ClaimSnapshot> oldestFirst = candidates.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingLong(ClaimSnapshot::getTimestamp))
                    .collect(Collectors.toList());
            for (ClaimSnapshot snapshot : oldestFirst) {
                if (excess <= 0) break;
                if (selected.add(snapshot)) excess--;
            }
        }

        return new ArrayList<>(selected);
    }
    
    // ============================================================
    // Persistence
    // ============================================================
    
    public synchronized void load() {
        try {
            if (!file.exists()) {
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                file.createNewFile();
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create claim-snapshots.yml", e);
        }
        
        data = YamlConfiguration.loadConfiguration(file);
        snapshots.clear();
        
        if (data.isConfigurationSection("snapshots")) {
            for (String key : data.getConfigurationSection("snapshots").getKeys(false)) {
                try {
                    String path = "snapshots." + key;
                    
                    UUID snapshotId = UUID.fromString(key);
                    UUID plotId = UUID.fromString(data.getString(path + ".plotId"));
                    UUID owner = UUID.fromString(data.getString(path + ".owner"));
                    String worldName = data.getString(path + ".world", "world");
                    
                    int x1 = data.getInt(path + ".x1");
                    int z1 = data.getInt(path + ".z1");
                    int x2 = data.getInt(path + ".x2");
                    int z2 = data.getInt(path + ".z2");
                    
                    long timestamp = data.getLong(path + ".timestamp");
                    SnapshotType type = SnapshotType.valueOf(data.getString(path + ".type", "MANUAL"));
                    String reason = data.getString(path + ".reason", "");
                    
                    UUID triggeredBy = null;
                    String actorStr = data.getString(path + ".triggeredBy", "");
                    if (!actorStr.isEmpty()) {
                        try { triggeredBy = UUID.fromString(actorStr); } catch (Throwable ignored) {}
                    }
                    
                    Map<String, Boolean> flags = new HashMap<>();
                    if (data.isConfigurationSection(path + ".flags")) {
                        ConfigurationSection flagsSec = data.getConfigurationSection(path + ".flags");
                        if (flagsSec != null) {
                            for (String flagKey : flagsSec.getKeys(false)) {
                                flags.put(flagKey, flagsSec.getBoolean(flagKey));
                            }
                        }
                    }

                    Map<UUID, String> members = new HashMap<>();
                    if (data.isConfigurationSection(path + ".members")) {
                        ConfigurationSection membersSec = data.getConfigurationSection(path + ".members");
                        if (membersSec != null) {
                            for (String memberKey : membersSec.getKeys(false)) {
                                try {
                                    members.put(UUID.fromString(memberKey), membersSec.getString(memberKey, "visitor"));
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }

                    List<UUID> bannedPlayers = new ArrayList<>();
                    for (String bannedKey : data.getStringList(path + ".banned")) {
                        try {
                            bannedPlayers.add(UUID.fromString(bannedKey));
                        } catch (IllegalArgumentException ignored) {}
                    }
                    
                    ClaimSnapshot snapshot = new ClaimSnapshot(
                            snapshotId, plotId, owner, worldName,
                            x1, z1, x2, z2, timestamp,
                            type, reason, triggeredBy,
                            data.getString(path + ".ownerName"),
                            data.getString(path + ".plotName"),
                            data.getString(path + ".description"),
                            data.getString(path + ".welcomeMessage"),
                            data.getString(path + ".farewellMessage"),
                            data.getString(path + ".entryTitle"),
                            data.getString(path + ".entrySubtitle"),
                            data.getString(path + ".customBiome"),
                            data.getString(path + ".plotStatus"),
                            data.getBoolean(path + ".serverWarp", false),
                            data.getBoolean(path + ".groupPlot", false),
                            data.getDouble(path + ".treasuryBalance", 0.0),
                            parseUuid(data.getString(path + ".groupId")),
                            data.getString(path + ".groupName"),
                            flags, members, bannedPlayers,
                            data.getString(path + ".guestPasses", ""),
                            data.getString(path + ".noticeboard", ""),
                            data.getString(path + ".allianceAccess", ""),
                            data.getString(path + ".roleNicknames", ""),
                            data.getString(path + ".roleFlags", ""),
                            data.getBoolean(path + ".lockdownActive", false),
                            data.getLong(path + ".lockdownActivatedAt", 0L),
                            data.getLong(path + ".lockdownExpiresAt", 0L),
                            data.getString(path + ".lockdownMode", "FULL"),
                            parseUuid(data.getString(path + ".lockdownActivatedBy")),
                            data.getString(path + ".lockdownActivatedByName", "Unknown"),
                            data.getString(path + ".extendedState", ""),
                            data.getString(path + ".territoryRentalState", "")
                    );
                    
                    snapshots.put(snapshotId, snapshot);
                    
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to load snapshot: " + key, e);
                }
            }
        }
        
        plugin.console().info("log_snapshots_loaded",
                "[Snapshots] Loaded {COUNT} snapshots",
                "COUNT", String.valueOf(snapshots.size()));
        restoreOperations.clear();
        activeRestoreByPlot.clear();
        boolean recovered = false;
        for (RestoreOperation operation : operationStore.load().values()) {
            if (operation.plotId() == null) continue;
            if (!operation.status().terminal()) {
                operation.transition(RestoreOperation.Status.PAUSED_REVIEW,
                        "Recovered after restart; no mutation was repeated automatically",
                        System.currentTimeMillis());
                recovered = true;
            }
            restoreOperations.put(operation.operationId(), operation);
            if (operation.status() == RestoreOperation.Status.PARTIAL
                    || operation.status() == RestoreOperation.Status.PAUSED_REVIEW) {
                activeRestoreByPlot.putIfAbsent(operation.plotId(), operation.operationId());
            }
        }
        if (recovered) {
            try {
                operationStore.save(restoreOperations.values());
            } catch (IOException error) {
                plugin.getLogger().log(Level.SEVERE, "Failed to persist recovered restore operations", error);
            }
        }
        plugin.getLogger().info("[Snapshots] Loaded " + restoreOperations.size()
                + " restore operations; " + activeRestoreByPlot.size() + " plot(s) maintenance-locked.");
        automaticPlayerBackups.load();
        setDirty(false);
    }
    
    public synchronized void save() {
        if (data == null) throw new IllegalStateException("Snapshot datastore has not been loaded");
        
        data.set("snapshots", null);
        
        for (ClaimSnapshot snapshot : snapshots.values()) {
            String path = "snapshots." + snapshot.getSnapshotId();
            
            data.set(path + ".plotId", snapshot.getPlotId().toString());
            data.set(path + ".owner", snapshot.getOwner().toString());
            data.set(path + ".world", snapshot.getWorldName());
            
            data.set(path + ".x1", snapshot.getX1());
            data.set(path + ".z1", snapshot.getZ1());
            data.set(path + ".x2", snapshot.getX2());
            data.set(path + ".z2", snapshot.getZ2());
            
            data.set(path + ".timestamp", snapshot.getTimestamp());
            data.set(path + ".type", snapshot.getType().name());
            data.set(path + ".reason", snapshot.getReason());
            data.set(path + ".triggeredBy", snapshot.getTriggeredBy() == null ? "" : snapshot.getTriggeredBy().toString());
            data.set(path + ".ownerName", snapshot.getOwnerName());
            data.set(path + ".plotName", snapshot.getPlotName());
            data.set(path + ".description", snapshot.getDescription());
            data.set(path + ".welcomeMessage", snapshot.getWelcomeMessage());
            data.set(path + ".farewellMessage", snapshot.getFarewellMessage());
            data.set(path + ".entryTitle", snapshot.getEntryTitle());
            data.set(path + ".entrySubtitle", snapshot.getEntrySubtitle());
            data.set(path + ".customBiome", snapshot.getCustomBiome());
            data.set(path + ".plotStatus", snapshot.getPlotStatus());
            data.set(path + ".serverWarp", snapshot.isServerWarp());
            data.set(path + ".groupPlot", snapshot.isGroupPlot());
            data.set(path + ".treasuryBalance", snapshot.getTreasuryBalance());
            data.set(path + ".groupId", snapshot.getGroupId() == null ? null : snapshot.getGroupId().toString());
            data.set(path + ".groupName", snapshot.getGroupName());

            data.set(path + ".flags", null);
            for (Map.Entry<String, Boolean> flag : snapshot.getFlags().entrySet()) {
                data.set(path + ".flags." + flag.getKey(), flag.getValue());
            }

            data.set(path + ".members", null);
            for (Map.Entry<UUID, String> member : snapshot.getMembers().entrySet()) {
                if (member.getKey() == null) continue;
                data.set(path + ".members." + member.getKey(), member.getValue());
            }

            List<String> banned = snapshot.getBannedPlayers().stream()
                    .filter(Objects::nonNull)
                    .map(UUID::toString)
                    .toList();
            data.set(path + ".banned", banned);
            data.set(path + ".guestPasses", snapshot.getGuestPassesBlob());
            data.set(path + ".noticeboard", snapshot.getNoticeboardBlob());
            data.set(path + ".allianceAccess", snapshot.getAllianceAccessBlob());
            data.set(path + ".roleNicknames", snapshot.getRoleNicknamesBlob());
            data.set(path + ".roleFlags", snapshot.getRoleFlagsBlob());
            data.set(path + ".lockdownActive", snapshot.isLockdownActive());
            data.set(path + ".lockdownActivatedAt", snapshot.getLockdownActivatedAt());
            data.set(path + ".lockdownExpiresAt", snapshot.getLockdownExpiresAt());
            data.set(path + ".lockdownMode", snapshot.getLockdownMode());
            data.set(path + ".lockdownActivatedBy", snapshot.getLockdownActivatedBy() == null
                    ? null : snapshot.getLockdownActivatedBy().toString());
            data.set(path + ".lockdownActivatedByName", snapshot.getLockdownActivatedByName());
            data.set(path + ".extendedState", snapshot.getExtendedStateBlob());
            data.set(path + ".territoryRentalState", snapshot.getTerritoryRentalStateBlob());
        }
        
        try {
            File parent = file.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            File temp = new File(parent, file.getName() + ".tmp");
            data.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            isDirty = false;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save claim-snapshots.yml", e);
            throw new IllegalStateException("Failed to durably save claim-snapshots.yml", e);
        }
    }

    public ClaimSnapshot getLatestSnapshotForPlot(UUID plotId) {
        List<ClaimSnapshot> matches = getSnapshotsForPlot(plotId);
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Applies every tracked field of {@code snapshot} onto {@code plot}. Package-visible and static
     * (touches no {@code SnapshotManager} instance state) so rollback correctness can be unit tested
     * directly, without a live plugin/data-store instance.
     */
    static void restorePlotState(Plot plot, ClaimSnapshot snapshot) {
        restorePlotState(plot, snapshot, RestoreScope.fullData());
    }

    static void restorePlotState(Plot plot, ClaimSnapshot snapshot, Set<RestoreScope> requestedScopes) {
        EnumSet<RestoreScope> scopes = RestoreScope.normalize(requestedScopes);
        PlotSnapshotState.validate(snapshot.getExtendedStateBlob());
        if (scopes.contains(RestoreScope.IDENTITY_AND_BOUNDS)) {
            plot.setOwner(snapshot.getOwner());
            plot.setOwnerName(snapshot.getOwnerName());
            plot.setWorld(snapshot.getWorldName());
            plot.setBounds(snapshot.getX1(), snapshot.getZ1(), snapshot.getX2(), snapshot.getZ2());
            plot.setPlotName(snapshot.getPlotName());
            plot.setDescription(snapshot.getDescription());
            plot.setWelcomeMessage(snapshot.getWelcomeMessage());
            plot.setFarewellMessage(snapshot.getFarewellMessage());
            plot.setEntryTitle(snapshot.getEntryTitle());
            plot.setEntrySubtitle(snapshot.getEntrySubtitle());
            plot.setCustomBiome(snapshot.getCustomBiome());
            plot.setPlotStatus(snapshot.getPlotStatus());
            plot.setServerWarp(snapshot.isServerWarp());
            plot.setGroupPlot(snapshot.isGroupPlot());
            plot.setTreasuryBalance(snapshot.getTreasuryBalance());
            plot.setGroupId(snapshot.getGroupId());
            plot.setGroupName(snapshot.getGroupName());
        }
        if (scopes.contains(RestoreScope.FLAGS)) {
            plot.getFlags().clear();
            plot.getFlags().putAll(snapshot.getFlags());
        }
        if (scopes.contains(RestoreScope.MEMBERS_AND_ROLES)) {
            plot.getPlayerRoles().clear();
            plot.getRoleNicknames().clear();
            plot.getRoleFlagStates().clear();
            for (Map.Entry<UUID, String> member : snapshot.getMembers().entrySet()) {
                plot.setRole(member.getKey(), member.getValue());
            }
            plot.deserializeRoleNicknames(snapshot.getRoleNicknamesBlob());
            plot.deserializeRoleFlags(snapshot.getRoleFlagsBlob());
        }
        if (scopes.contains(RestoreScope.BANS)) {
            plot.getBannedPlayers().clear();
            for (UUID banned : snapshot.getBannedPlayers()) plot.addBan(banned);
        }
        if (scopes.contains(RestoreScope.GUEST_PASSES)) {
            plot.deserializeGuestPasses(snapshot.getGuestPassesBlob());
        }
        if (scopes.contains(RestoreScope.NOTICEBOARD)) {
            plot.deserializeNoticeboard(snapshot.getNoticeboardBlob());
        }
        if (scopes.contains(RestoreScope.ALLIANCE_ACCESS)) {
            plot.deserializeAllianceAccess(snapshot.getAllianceAccessBlob());
        }
        if (scopes.contains(RestoreScope.LOCKDOWN)) {
            plot.restoreLockdown(
                    snapshot.isLockdownActive(), snapshot.getLockdownActivatedBy(),
                    snapshot.getLockdownActivatedByName(), snapshot.getLockdownActivatedAt(),
                    snapshot.getLockdownExpiresAt(), snapshot.getLockdownMode());
        }
        PlotSnapshotState.restore(plot, snapshot.getExtendedStateBlob(), scopes);
    }
    
    public boolean isDirty() { return isDirty; }
    public void setDirty(boolean dirty) { this.isDirty = dirty; }
    public void saveSync() { save(); }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
