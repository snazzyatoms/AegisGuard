package com.aegisguard.snapshots;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.Arrays;
import com.aegisguard.scheduler.AegisScheduler;

/**
 * Optional WorldEdit/FAWE schematic copy of a plot AABB, stored beside claim-data snapshots.
 * Disabled by default. Staff restore pastes the schematic after claim-data rollback.
 */
public final class PlotBuildBackup implements BuildBackupService {

    public enum IntegrityStatus { NONE, VALID, MISSING, CORRUPT, INCOMPATIBLE, UNVERIFIED }

    public record IntegrationInfo(String name, String version, boolean available,
                                  boolean compatible, String detail) { }

    public record BackupInspection(boolean present, long bytes, int files,
                                   IntegrityStatus integrity, boolean compatible,
                                   String aggregateChecksum, String format,
                                   String integrationName, String integrationVersion,
                                   String detail) {
        public BackupInspection(boolean present, long bytes, int files) {
            this(present, bytes, files, present ? IntegrityStatus.UNVERIFIED : IntegrityStatus.NONE,
                    false, "", "", "", "", present ? "Manifest not validated" : "No build backup");
        }

        public boolean validForRestore() {
            return present && integrity == IntegrityStatus.VALID && compatible;
        }
    }

    public record StorageReport(long totalBytes, long configuredLimitBytes, int manifests,
                                int orphanFiles, int corruptBackups, int missingBackups,
                                int incompatibleBackups, int protectedBackups,
                                int prunedBackups, int quarantinedFiles,
                                boolean dryRun, List<String> details) {
        public StorageReport {
            details = details == null ? List.of() : List.copyOf(details);
        }
    }

    public record TrackedRestoreResult(RestoreQueueResult result, Set<String> completed,
                                       Set<String> failed, Set<String> pending) {
        public TrackedRestoreResult {
            completed = completed == null ? Set.of() : Set.copyOf(completed);
            failed = failed == null ? Set.of() : Set.copyOf(failed);
            pending = pending == null ? Set.of() : Set.copyOf(pending);
        }
    }

    private record TileBounds(int minX, int maxX, int minZ, int maxZ, File destination) { }

    /** Folia region edge length in blocks (32 chunks). */
    static final int FOLIA_REGION_BLOCKS = 512;

    private final AegisGuard plugin;
    private final File folder;
    private final BuildBackupMetadataStore metadataStore;
    private final AtomicLong lastStorageWarningAt = new AtomicLong();
    private final Set<UUID> activeCaptureIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public PlotBuildBackup(AegisGuard plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "plot-backups");
        this.metadataStore = new BuildBackupMetadataStore(folder);
    }

    public boolean isConfiguredOn() {
        return plugin.getConfig().getBoolean("snapshots.build_backup.enabled", false);
    }

    public boolean isWorldEditPresent() {
        return integrationInfo().compatible();
    }

    public IntegrationInfo integrationInfo() {
        try {
            Plugin fawe = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit");
            Plugin worldEdit = Bukkit.getPluginManager().getPlugin("WorldEdit");
            Plugin selected = fawe != null && fawe.isEnabled() ? fawe
                    : worldEdit != null && worldEdit.isEnabled() ? worldEdit : null;
            if (selected == null) {
                return new IntegrationInfo("None", "", false, false,
                        "WorldEdit or FastAsyncWorldEdit is not enabled");
            }
            Class.forName("com.sk89q.worldedit.WorldEdit");
            String version = selected.getDescription().getVersion();
            if (plugin.isFolia()
                    && plugin.getConfig().getBoolean("snapshots.build_backup.folia.require_fawe", true)
                    && (fawe == null || !fawe.isEnabled())) {
                return new IntegrationInfo(selected.getName(), version, true, false,
                        "Folia build backups require FastAsyncWorldEdit under the current safety policy");
            }
            return new IntegrationInfo(selected.getName(), version, true, true,
                    plugin.isFolia() ? "Folia region-tiled integration ready" : "Platform integration ready");
        } catch (Throwable error) {
            return new IntegrationInfo("Unknown", "", false, false,
                    "WorldEdit API unavailable: " + safeMessage(error));
        }
    }

    public boolean isReady() {
        return isConfiguredOn() && isWorldEditPresent();
    }

    public File schematicFile(UUID snapshotId) {
        return new File(folder, snapshotId + ".schem");
    }

    public boolean hasSchematic(UUID snapshotId) {
        if (snapshotId == null) return false;
        File file = schematicFile(snapshotId);
        if (file.isFile() && file.length() > 0L) return true;
        File[] tiles = tileFiles(snapshotId);
        return tiles != null && tiles.length > 0;
    }

    /** Inspect backup files away from the main/global or region thread. */
    public CompletableFuture<BackupInspection> inspectAsync(UUID snapshotId) {
        ClaimSnapshot snapshot = plugin.getSnapshotManager() == null
                ? null : plugin.getSnapshotManager().getSnapshot(snapshotId);
        return inspectAsync(snapshotId, snapshot, true);
    }

    public CompletableFuture<BackupInspection> inspectAsync(ClaimSnapshot snapshot) {
        return inspectAsync(snapshot == null ? null : snapshot.getSnapshotId(), snapshot, true);
    }

    private CompletableFuture<BackupInspection> inspectAsync(UUID snapshotId, ClaimSnapshot snapshot,
                                                              boolean adoptLegacy) {
        CompletableFuture<BackupInspection> result = new CompletableFuture<>();
        if (snapshotId == null || plugin.scheduler() == null) {
            result.complete(new BackupInspection(false, 0L, 0));
            return result;
        }
        IntegrationInfo integration = integrationInfo();
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> {
            try {
                BuildBackupMetadata metadata = metadataStore.load(snapshotId);
                boolean manifestExists = metadataStore.file(snapshotId).isFile();
                if (metadata == null && !manifestExists && adoptLegacy && snapshot != null
                        && hasSchematic(snapshotId) && integration.compatible()) {
                    metadata = createMetadata(snapshot, integration, null, null);
                    if (metadata != null) metadataStore.save(metadata);
                }
                if (metadata == null && manifestExists) {
                    result.complete(new BackupInspection(hasSchematic(snapshotId), 0L, 0,
                            IntegrityStatus.CORRUPT, integration.compatible(), "", "",
                            integration.name(), integration.version(), "Build manifest is malformed"));
                    return;
                }
                result.complete(inspectSync(snapshotId, snapshot, metadata, integration));
            } catch (Throwable error) {
                result.complete(new BackupInspection(hasSchematic(snapshotId), 0L, 0,
                        IntegrityStatus.CORRUPT, integration.compatible(), "", "", integration.name(),
                        integration.version(), "Inspection failed: " + safeMessage(error)));
            }
        });
        if (!dispatch.accepted()) result.complete(new BackupInspection(false, 0L, 0));
        return result;
    }

    public CompletableFuture<java.util.Map<UUID, BackupInspection>> inspectBatchAsync(
            java.util.Collection<ClaimSnapshot> snapshots) {
        CompletableFuture<java.util.Map<UUID, BackupInspection>> result = new CompletableFuture<>();
        if (plugin.scheduler() == null) {
            result.complete(java.util.Map.of());
            return result;
        }
        IntegrationInfo integration = integrationInfo();
        List<ClaimSnapshot> safe = snapshots == null ? List.of()
                : snapshots.stream().filter(java.util.Objects::nonNull).toList();
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> {
            java.util.Map<UUID, BackupInspection> inspections = new java.util.LinkedHashMap<>();
            for (ClaimSnapshot snapshot : safe) {
                UUID id = snapshot.getSnapshotId();
                try {
                    BuildBackupMetadata metadata = metadataStore.load(id);
                    boolean manifestExists = metadataStore.file(id).isFile();
                    if (metadata == null && !manifestExists && hasSchematic(id)
                            && integration.compatible()) {
                        metadata = createMetadata(snapshot, integration, null, null);
                        if (metadata != null) metadataStore.save(metadata);
                    }
                    if (metadata == null && manifestExists) {
                        inspections.put(id, new BackupInspection(hasSchematic(id), 0L, 0,
                                IntegrityStatus.CORRUPT, integration.compatible(), "", "",
                                integration.name(), integration.version(), "Build manifest is malformed"));
                        continue;
                    }
                    inspections.put(id, inspectSync(id, snapshot, metadata, integration));
                } catch (Throwable error) {
                    inspections.put(id, new BackupInspection(hasSchematic(id), 0L, 0,
                            IntegrityStatus.CORRUPT, integration.compatible(), "", "",
                            integration.name(), integration.version(), safeMessage(error)));
                }
            }
            result.complete(java.util.Map.copyOf(inspections));
        });
        if (!dispatch.accepted()) result.complete(java.util.Map.of());
        return result;
    }

    public static long volume(int x1, int z1, int x2, int z2, int minY, int maxY) {
        long width = Math.abs((long) x2 - x1) + 1L;
        long length = Math.abs((long) z2 - z1) + 1L;
        long height = Math.abs((long) maxY - minY) + 1L;
        return width * length * height;
    }

    public enum CaptureResult {
        DISABLED,
        QUEUED,
        COMPLETED,
        PARTIALLY_QUEUED,
        SCHEDULER_REJECTED,
        SKIPPED_WORLD,
        SKIPPED_VOLUME,
        INCOMPATIBLE,
        FAILED
    }

    public enum RestoreQueueResult {
        NO_BACKUP,
        QUEUED,
        COMPLETED,
        PARTIALLY_QUEUED,
        PARTIALLY_COMPLETED,
        WORLD_EDIT_UNAVAILABLE,
        INCOMPATIBLE_BACKUP,
        INTEGRITY_FAILED,
        WORLD_UNAVAILABLE,
        SCHEDULER_REJECTED,
        FAILED
    }

    public CaptureResult preview(ClaimSnapshot snapshot) {
        return preview(snapshot, isConfiguredOn());
    }

    CaptureResult preview(ClaimSnapshot snapshot, boolean featureEnabled) {
        if (snapshot == null || !featureEnabled) return CaptureResult.DISABLED;
        IntegrationInfo integration = integrationInfo();
        if (!integration.available()) return CaptureResult.DISABLED;
        if (!integration.compatible()) return CaptureResult.INCOMPATIBLE;
        org.bukkit.World world = Bukkit.getWorld(snapshot.getWorldName());
        if (world == null) return CaptureResult.SKIPPED_WORLD;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        long blocks = volume(snapshot.getX1(), snapshot.getZ1(), snapshot.getX2(), snapshot.getZ2(), minY, maxY);
        long cap = Math.max(1L, plugin.getConfig().getLong("snapshots.build_backup.max_volume", 2_000_000L));
        if (blocks > cap) return CaptureResult.SKIPPED_VOLUME;
        return CaptureResult.QUEUED;
    }

    public CaptureResult queueCapture(Plot plot, ClaimSnapshot snapshot) {
        CaptureResult preview = preview(snapshot);
        if (preview != CaptureResult.QUEUED) {
            if (preview == CaptureResult.SKIPPED_VOLUME) {
                plugin.getLogger().warning("[PlotBackup] Snapshot " + snapshot.getSnapshotId()
                        + " exceeds max_volume; skipped build copy.");
            } else if (preview == CaptureResult.SKIPPED_WORLD) {
                plugin.getLogger().warning("[PlotBackup] World not loaded; skipped build copy for "
                        + snapshot.getSnapshotId());
            }
            return preview;
        }
        captureAsync(plot, snapshot);
        return CaptureResult.QUEUED;
    }

    /** Capture all required region tiles and complete only after every schematic file is durable. */
    public CompletableFuture<CaptureResult> captureAsync(Plot plot, ClaimSnapshot snapshot) {
        return captureAsync(plot, snapshot, isConfiguredOn());
    }

    public CompletableFuture<CaptureResult> captureAsync(Plot plot, ClaimSnapshot snapshot, boolean featureEnabled) {
        CaptureResult preflight = preview(snapshot, featureEnabled);
        if (preflight != CaptureResult.QUEUED) return CompletableFuture.completedFuture(preflight);
        IntegrationInfo integration = integrationInfo();
        if (!integration.compatible()) return CompletableFuture.completedFuture(CaptureResult.DISABLED);
        activeCaptureIds.add(snapshot.getSnapshotId());
        org.bukkit.World world = Bukkit.getWorld(snapshot.getWorldName());
        if (world == null) {
            activeCaptureIds.remove(snapshot.getSnapshotId());
            return CompletableFuture.completedFuture(CaptureResult.SKIPPED_WORLD);
        }
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        List<TileBounds> tiles = captureTiles(snapshot);
        CompletableFuture<Integer> chain = CompletableFuture.completedFuture(0);
        for (TileBounds tile : tiles) {
            chain = chain.thenCompose(succeeded -> captureTileAsync(snapshot, world, minY, maxY,
                    tile.minX(), tile.maxX(), tile.minZ(), tile.maxZ(), tile.destination())
                    .thenApply(ok -> succeeded + (Boolean.TRUE.equals(ok) ? 1 : 0)));
        }
        return chain.thenCompose(succeeded -> {
            if (succeeded == tiles.size()) {
                return persistMetadataAsync(snapshot, integration, null)
                        .thenCompose(saved -> {
                            if (!saved) return CompletableFuture.completedFuture(CaptureResult.FAILED);
                            return maintainStorageAsync(false).handle((report, error) -> CaptureResult.COMPLETED);
                        });
            }
            CaptureResult incomplete = succeeded > 0
                    ? CaptureResult.PARTIALLY_QUEUED : CaptureResult.FAILED;
            return discardIncompleteCaptureAsync(snapshot.getSnapshotId()).thenApply(ignored -> incomplete);
        }).whenComplete((ignored, error) -> activeCaptureIds.remove(snapshot.getSnapshotId()));
    }

    public Set<UUID> activeCaptureIds() {
        return Set.copyOf(activeCaptureIds);
    }

    /** A partial file set can never be retained and later mistaken for a complete legacy backup. */
    private CompletableFuture<Void> discardIncompleteCaptureAsync(UUID snapshotId) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> {
            deleteSchematicFiles(snapshotId);
            result.complete(null);
        });
        if (!dispatch.accepted()) {
            plugin.getLogger().severe("[PlotBackup] Could not clean incomplete build capture "
                    + snapshotId + "; its files will fail manifest preflight and be quarantined later.");
            result.complete(null);
        }
        return result;
    }

    /**
     * Sequential, durable cross-region restore. Each tile is pasted only on its owning region and
     * the supplied checkpoint must persist before the next tile begins.
     */
    public CompletableFuture<TrackedRestoreResult> restoreTracked(
            ClaimSnapshot snapshot, Set<String> alreadyCompleted,
            BiFunction<String, Boolean, CompletableFuture<Void>> checkpoint) {
        if (snapshot == null) return CompletableFuture.completedFuture(new TrackedRestoreResult(
                RestoreQueueResult.NO_BACKUP, Set.of(), Set.of(), Set.of()));
        Set<String> completed = java.util.concurrent.ConcurrentHashMap.newKeySet();
        if (alreadyCompleted != null) completed.addAll(alreadyCompleted);
        Set<String> failed = java.util.concurrent.ConcurrentHashMap.newKeySet();
        CompletableFuture<TrackedRestoreResult> result = new CompletableFuture<>();
        inspectAsync(snapshot).whenComplete((inspection, inspectionError) -> {
            if (inspectionError != null || inspection == null || !inspection.present()) {
                result.complete(new TrackedRestoreResult(RestoreQueueResult.NO_BACKUP,
                        completed, failed, Set.of()));
                return;
            }
            if (inspection.integrity() != IntegrityStatus.VALID) {
                result.complete(new TrackedRestoreResult(RestoreQueueResult.INTEGRITY_FAILED,
                        completed, failed, Set.of()));
                return;
            }
            if (!inspection.compatible()) {
                result.complete(new TrackedRestoreResult(RestoreQueueResult.INCOMPATIBLE_BACKUP,
                        completed, failed, Set.of()));
                return;
            }
            AegisScheduler.DispatchResult global = plugin.scheduler().runGlobal(() -> {
                try {
                    org.bukkit.World world = Bukkit.getWorld(snapshot.getWorldName());
                    if (world == null) {
                        result.complete(new TrackedRestoreResult(RestoreQueueResult.WORLD_UNAVAILABLE,
                                completed, failed, Set.of()));
                        return;
                    }
                    AegisScheduler.DispatchResult async = plugin.scheduler().runAsync(() -> {
                        try {
                        BuildBackupMetadata metadata = metadataStore.load(snapshot.getSnapshotId());
                        if (metadata == null || metadata.files().isEmpty()) {
                            result.complete(new TrackedRestoreResult(RestoreQueueResult.INTEGRITY_FAILED,
                                    completed, failed, Set.of()));
                            return;
                        }
                        List<BuildBackupMetadata.BuildFile> remaining = metadata.files().stream()
                                .filter(file -> !completed.contains(file.fileName())).toList();
                        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                        for (BuildBackupMetadata.BuildFile entry : remaining) {
                            chain = chain.thenCompose(ignored -> {
                                File file;
                                try {
                                    file = safeBackupFile(entry.fileName());
                                } catch (IOException error) {
                                    return CompletableFuture.failedFuture(error);
                                }
                                Location target = new Location(world, entry.minX(), metadata.minY(), entry.minZ());
                                return readAndPasteAsync(snapshot, world, target, file,
                                        entry.minX(), metadata.minY(), entry.minZ())
                                        .thenCompose(success -> {
                                            if (success) completed.add(entry.fileName());
                                            else failed.add(entry.fileName());
                                            CompletableFuture<Void> durable = checkpoint == null
                                                    ? CompletableFuture.completedFuture(null)
                                                    : checkpoint.apply(entry.fileName(), success);
                                            return durable == null ? CompletableFuture.failedFuture(
                                                    new IllegalStateException("Tile checkpoint returned null")) : durable;
                                        });
                            });
                        }
                        chain.whenComplete((ignored, chainError) -> {
                            Set<String> pending = new HashSet<>();
                            for (BuildBackupMetadata.BuildFile entry : metadata.files()) {
                                if (!completed.contains(entry.fileName()) && !failed.contains(entry.fileName())) {
                                    pending.add(entry.fileName());
                                }
                            }
                            if (chainError != null) {
                                result.complete(new TrackedRestoreResult(RestoreQueueResult.PARTIALLY_COMPLETED,
                                        completed, failed, pending));
                            } else if (!failed.isEmpty()) {
                                result.complete(new TrackedRestoreResult(RestoreQueueResult.PARTIALLY_COMPLETED,
                                        completed, failed, pending));
                            } else {
                                result.complete(new TrackedRestoreResult(RestoreQueueResult.COMPLETED,
                                        completed, failed, pending));
                            }
                        });
                        } catch (Throwable error) {
                            result.complete(new TrackedRestoreResult(RestoreQueueResult.FAILED,
                                    completed, failed, Set.of()));
                        }
                    });
                    if (!async.accepted()) result.complete(new TrackedRestoreResult(
                            RestoreQueueResult.SCHEDULER_REJECTED, completed, failed, Set.of()));
                } catch (Throwable error) {
                    result.complete(new TrackedRestoreResult(RestoreQueueResult.FAILED,
                            completed, failed, Set.of()));
                }
            });
            if (!global.accepted()) result.complete(new TrackedRestoreResult(
                    RestoreQueueResult.SCHEDULER_REJECTED, completed, failed, Set.of()));
        });
        return result;
    }

    public CompletableFuture<List<String>> expectedBuildTilesAsync(UUID snapshotId) {
        CompletableFuture<List<String>> result = new CompletableFuture<>();
        if (snapshotId == null || plugin.scheduler() == null) {
            result.complete(List.of());
            return result;
        }
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> {
            try {
                BuildBackupMetadata metadata = metadataStore.load(snapshotId);
                result.complete(metadata == null ? List.of() : metadata.fileNames());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        if (!dispatch.accepted()) result.complete(List.of());
        return result;
    }

    public void deleteSchematic(UUID snapshotId) {
        if (snapshotId == null) return;
        if (plugin.getSnapshotManager() != null
                && plugin.getSnapshotManager().protectedBuildSnapshotIds().contains(snapshotId)) {
            plugin.getLogger().warning("[PlotBackup] Refused to delete restore-protected backup "
                    + snapshotId);
            return;
        }
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> deleteSchematicFiles(snapshotId));
        if (!dispatch.accepted()) {
            plugin.getLogger().warning("[PlotBackup] Scheduler rejected backup cleanup for " + snapshotId
                    + ": " + dispatch);
        }
    }

    private void deleteSchematicFiles(UUID snapshotId) {
        File file = schematicFile(snapshotId);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("[PlotBackup] Could not delete " + file.getName());
        }
        File[] tiles = tileFiles(snapshotId);
        if (tiles != null) {
            for (File tile : tiles) {
                if (tile.exists() && !tile.delete()) {
                    plugin.getLogger().warning("[PlotBackup] Could not delete " + tile.getName());
                }
            }
        }
        try {
            metadataStore.delete(snapshotId);
        } catch (IOException error) {
            plugin.getLogger().warning("[PlotBackup] Could not delete metadata for " + snapshotId);
        }
    }

    private List<TileBounds> captureTiles(ClaimSnapshot snapshot) {
        int minX = Math.min(snapshot.getX1(), snapshot.getX2());
        int maxX = Math.max(snapshot.getX1(), snapshot.getX2());
        int minZ = Math.min(snapshot.getZ1(), snapshot.getZ2());
        int maxZ = Math.max(snapshot.getZ1(), snapshot.getZ2());
        int configuredMaxChunks = Math.max(1, Math.min(256, plugin.getConfig().getInt(
                "snapshots.build_backup.max_chunks_per_region_job", 4)));
        List<TileBounds> tiles = new ArrayList<>();
        for (int[] bounds : computeTileBounds(minX, maxX, minZ, maxZ,
                configuredMaxChunks, plugin.isFolia())) {
            File destination = bounds[0] == minX && bounds[1] == maxX
                    && bounds[2] == minZ && bounds[3] == maxZ
                    ? schematicFile(snapshot.getSnapshotId())
                    : tileFile(snapshot.getSnapshotId(), bounds[0], bounds[2]);
            tiles.add(new TileBounds(bounds[0], bounds[1], bounds[2], bounds[3], destination));
        }
        return List.copyOf(tiles);
    }

    static List<int[]> computeTileBounds(int minX, int maxX, int minZ, int maxZ,
                                         int configuredMaxChunks, boolean folia) {
        int normalizedMinX = Math.min(minX, maxX);
        int normalizedMaxX = Math.max(minX, maxX);
        int normalizedMinZ = Math.min(minZ, maxZ);
        int normalizedMaxZ = Math.max(minZ, maxZ);
        configuredMaxChunks = Math.max(1, Math.min(256, configuredMaxChunks));
        // Folia owns chunks dynamically. A fixed 32x32-chunk region grid is not an ownership
        // guarantee, so each Folia task is deliberately constrained to one chunk.
        int maxChunks = folia ? 1 : configuredMaxChunks;
        int chunksX = Math.max(1, (int) Math.floor(Math.sqrt(maxChunks)));
        int chunksZ = Math.max(1, maxChunks / chunksX);
        long chunkCount = (long) (Math.floorDiv(normalizedMaxX, 16) - Math.floorDiv(normalizedMinX, 16) + 1)
                * (Math.floorDiv(normalizedMaxZ, 16) - Math.floorDiv(normalizedMinZ, 16) + 1);
        if (chunkCount <= maxChunks) {
            return List.of(new int[]{normalizedMinX, normalizedMaxX, normalizedMinZ, normalizedMaxZ});
        }
        List<int[]> tiles = new ArrayList<>();
        for (int tileMinX = normalizedMinX; tileMinX <= normalizedMaxX; ) {
            int chunkMaxX = (Math.floorDiv(tileMinX, 16) + chunksX) * 16 - 1;
            int tileMaxX = Math.min(normalizedMaxX, chunkMaxX);
            for (int tileMinZ = normalizedMinZ; tileMinZ <= normalizedMaxZ; ) {
                int chunkMaxZ = (Math.floorDiv(tileMinZ, 16) + chunksZ) * 16 - 1;
                int tileMaxZ = Math.min(normalizedMaxZ, chunkMaxZ);
                tiles.add(new int[]{tileMinX, tileMaxX, tileMinZ, tileMaxZ});
                tileMinZ = tileMaxZ + 1;
            }
            tileMinX = tileMaxX + 1;
        }
        return List.copyOf(tiles);
    }

    private CompletableFuture<Boolean> captureTileAsync(ClaimSnapshot snapshot, org.bukkit.World bukkitWorld,
                                                         int minY, int maxY, int minX, int maxX,
                                                         int minZ, int maxZ, File dest) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Location target = new Location(bukkitWorld, minX, minY, minZ);
        AegisScheduler.DispatchResult regionDispatch = plugin.scheduler().runAt(target, () -> {
            try {
            boolean copyEntities = plugin.getConfig().getBoolean("snapshots.build_backup.copy_entities", false);

            World weWorld = BukkitAdapter.adapt(bukkitWorld);
            CuboidRegion region = new CuboidRegion(
                    weWorld,
                    BlockVector3.at(minX, minY, minZ),
                    BlockVector3.at(maxX, maxY, maxZ)
            );
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
            clipboard.setOrigin(BlockVector3.at(minX, minY, minZ));
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                ForwardExtentCopy copy = new ForwardExtentCopy(
                        editSession, region, clipboard, region.getMinimumPoint());
                copy.setCopyingEntities(copyEntities);
                Operations.complete(copy);
            }
            AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(
                    () -> result.complete(writeClipboard(snapshot, clipboard, dest)));
            if (!dispatch.accepted()) {
                plugin.getLogger().warning("[PlotBackup] Async scheduler rejected schematic write for "
                        + snapshot.getSnapshotId() + ": " + dispatch);
                result.complete(false);
            }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "[PlotBackup] Failed to capture builds for snapshot "
                        + snapshot.getSnapshotId(), t);
                result.complete(false);
            }
        });
        if (!regionDispatch.accepted()) result.complete(false);
        return result;
    }

    private boolean writeClipboard(ClaimSnapshot snapshot, Clipboard clipboard, File dest) {
        File temp = new File(folder, dest.getName() + ".tmp");
        try {
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("[PlotBackup] Could not create plot-backups folder.");
                return false;
            }
            try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(new FileOutputStream(temp))) {
                writer.write(clipboard);
            }
            moveAtomically(temp, dest);
            plugin.getLogger().info("[PlotBackup] Saved schematic " + dest.getName()
                    + " for plot " + snapshot.getPlotId());
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[PlotBackup] Failed to write builds for snapshot "
                    + snapshot.getSnapshotId(), t);
            return false;
        } finally {
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException cleanupError) {
                plugin.getLogger().warning("[PlotBackup] Could not clean temporary schematic "
                        + temp.getName());
            }
        }
    }

    private CompletableFuture<Boolean> readAndPasteAsync(ClaimSnapshot snapshot,
                                                          org.bukkit.World world,
                                                          Location target,
                                                          File file,
                                                          int destMinX,
                                                          int destMinY,
                                                          int destMinZ) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        AegisScheduler.DispatchResult async = plugin.scheduler().runAsync(() -> {
            try {
                Clipboard clipboard = readClipboard(file);
                AegisScheduler.DispatchResult region = plugin.scheduler().runAt(target,
                        () -> result.complete(restoreClipboard(snapshot, world, clipboard,
                                destMinX, destMinY, destMinZ)));
                if (!region.accepted()) result.complete(false);
            } catch (Throwable error) {
                plugin.getLogger().log(Level.WARNING,
                        "[PlotBackup] Failed to prepare " + file.getName(), error);
                result.complete(false);
            }
        });
        if (!async.accepted()) result.complete(false);
        return result;
    }

    private Clipboard readClipboard(File file) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) format = BuiltInClipboardFormat.SPONGE_SCHEMATIC;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            return reader.read();
        }
    }

    private boolean restoreClipboard(ClaimSnapshot snapshot, org.bukkit.World bukkitWorld, Clipboard clipboard,
                                  int destMinX, int destMinY, int destMinZ) {
        try {
            BlockVector3 to = alignedPasteTo(
                    BlockVector3.at(destMinX, destMinY, destMinZ),
                    clipboard.getMinimumPoint(),
                    clipboard.getOrigin()
            );
            boolean copyEntities = plugin.getConfig().getBoolean("snapshots.build_backup.copy_entities", false);
            World weWorld = BukkitAdapter.adapt(bukkitWorld);
            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                Operation paste = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(to)
                        .copyEntities(copyEntities)
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(paste);
            }
            plugin.getLogger().info("[PlotBackup] Restored schematic for plot " + snapshot.getPlotId()
                    + " from snapshot " + snapshot.getSnapshotId());
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[PlotBackup] Failed to restore builds for snapshot "
                    + snapshot.getSnapshotId(), t);
            return false;
        }
    }

    /**
     * WorldEdit {@code ClipboardHolder.createPaste().to()} places the clipboard origin at {@code to}.
     * Align so the clipboard minimum corner lands on {@code destMin} even if origin was not persisted.
     */
    static BlockVector3 alignedPasteTo(BlockVector3 destMin, BlockVector3 clipMin, BlockVector3 origin) {
        return destMin.add(origin.subtract(clipMin));
    }

    static boolean spansMultipleFoliaRegions(ClaimSnapshot snapshot) {
        int minX = Math.min(snapshot.getX1(), snapshot.getX2());
        int maxX = Math.max(snapshot.getX1(), snapshot.getX2());
        int minZ = Math.min(snapshot.getZ1(), snapshot.getZ2());
        int maxZ = Math.max(snapshot.getZ1(), snapshot.getZ2());
        return Math.floorDiv(minX, FOLIA_REGION_BLOCKS) != Math.floorDiv(maxX, FOLIA_REGION_BLOCKS)
                || Math.floorDiv(minZ, FOLIA_REGION_BLOCKS) != Math.floorDiv(maxZ, FOLIA_REGION_BLOCKS);
    }

    private File tileFile(UUID snapshotId, int minX, int minZ) {
        return new File(folder, snapshotId + "__" + minX + "_" + minZ + ".schem");
    }

    private File[] tileFiles(UUID snapshotId) {
        if (snapshotId == null || !folder.isDirectory()) return null;
        String prefix = snapshotId + "__";
        return folder.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".schem"));
    }

    static int[] parseTileCoords(String fileName, UUID snapshotId) {
        if (fileName == null || snapshotId == null) return null;
        String prefix = snapshotId + "__";
        if (!fileName.startsWith(prefix) || !fileName.endsWith(".schem")) return null;
        String body = fileName.substring(prefix.length(), fileName.length() - ".schem".length());
        int split = body.lastIndexOf('_');
        if (split <= 0) return null;
        try {
            return new int[] { Integer.parseInt(body.substring(0, split)), Integer.parseInt(body.substring(split + 1)) };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private CompletableFuture<Boolean> persistMetadataAsync(ClaimSnapshot snapshot,
                                                             IntegrationInfo integration,
                                                             UUID captureOperationId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> {
            try {
                BuildBackupMetadata metadata = createMetadata(snapshot, integration,
                        captureOperationId, null);
                if (metadata == null || metadata.files().isEmpty()) {
                    result.complete(false);
                    return;
                }
                metadataStore.save(metadata);
                result.complete(true);
            } catch (Throwable error) {
                plugin.getLogger().log(Level.WARNING, "[PlotBackup] Could not persist build metadata for "
                        + snapshot.getSnapshotId(), error);
                result.complete(false);
            }
        });
        if (!dispatch.accepted()) result.complete(false);
        return result;
    }

    /**
     * Inspect, retain, and quarantine build artifacts without touching Bukkit state.
     * Orphans are moved to a recoverable quarantine; referenced/active restore files are protected.
     */
    public CompletableFuture<StorageReport> maintainStorageAsync(boolean dryRun) {
        CompletableFuture<StorageReport> result = new CompletableFuture<>();
        if (plugin.scheduler() == null) {
            result.complete(new StorageReport(0L, 0L, 0, 0, 0, 0,
                    0, 0, 0, 0, dryRun, List.of("Scheduler unavailable")));
            return result;
        }
        List<ClaimSnapshot> snapshots = plugin.getSnapshotManager() == null
                ? List.of() : plugin.getSnapshotManager().getAllSnapshots();
        Set<UUID> protectedIds = plugin.getSnapshotManager() == null
                ? Set.of() : plugin.getSnapshotManager().protectedBuildSnapshotIds();
        boolean enforce = plugin.getConfig().getBoolean(
                "snapshots.build_backup.storage.enforce_limits", true);
        int perPlot = Math.max(1, plugin.getConfig().getInt(
                "snapshots.build_backup.storage.retention_per_plot", 10));
        long limitMb = Math.max(1L, Math.min(1_000_000L, plugin.getConfig().getLong(
                "snapshots.build_backup.storage.global_max_megabytes", 4096L)));
        long limitBytes = Math.multiplyExact(limitMb, 1024L * 1024L);
        String orphanPolicy = plugin.getConfig().getString(
                "snapshots.build_backup.storage.orphan_policy", "quarantine");
        IntegrationInfo integration = integrationInfo();
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> {
            try {
                StorageReport report = maintainStorageSync(snapshots, protectedIds, enforce,
                        perPlot, limitBytes, dryRun, integration, orphanPolicy);
                if (!dryRun) maybeAlertStorage(report);
                result.complete(report);
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        if (!dispatch.accepted()) result.completeExceptionally(
                new IllegalStateException("Storage maintenance scheduling rejected"));
        return result;
    }

    private StorageReport maintainStorageSync(List<ClaimSnapshot> snapshots,
                                               Set<UUID> protectedIds,
                                               boolean enforce, int perPlot,
                                               long limitBytes, boolean dryRun,
                                               IntegrationInfo integration,
                                               String orphanPolicy) throws IOException {
        java.util.Map<UUID, ClaimSnapshot> byId = new java.util.HashMap<>();
        for (ClaimSnapshot snapshot : snapshots) {
            if (snapshot != null && snapshot.getSnapshotId() != null) byId.put(snapshot.getSnapshotId(), snapshot);
        }
        List<String> details = new ArrayList<>();
        int orphanFiles = 0;
        int quarantined = 0;
        File[] stored = folder.listFiles((dir, name) -> name.endsWith(".schem")
                || name.endsWith(".meta.yml") || name.endsWith(".tmp"));
        if (stored != null) {
            for (File file : stored) {
                UUID id = snapshotIdFromArtifact(file.getName());
                if (id != null && byId.containsKey(id)) continue;
                orphanFiles++;
                details.add("Orphan: " + file.getName());
                if (!dryRun && "quarantine".equalsIgnoreCase(orphanPolicy)
                        && quarantine(file)) quarantined++;
            }
        }

        List<BuildBackupMetadata> manifests = new ArrayList<>();
        int missing = 0;
        int corrupt = 0;
        int incompatible = 0;
        if (integration.compatible()) {
            for (ClaimSnapshot snapshot : snapshots) {
                UUID id = snapshot.getSnapshotId();
                if (!hasSchematic(id) || metadataStore.file(id).isFile()) continue;
                try {
                    BuildBackupMetadata adopted = createMetadata(snapshot, integration, null, null);
                    if (adopted != null) {
                        if (!dryRun) metadataStore.save(adopted);
                        manifests.add(adopted);
                    }
                } catch (Exception invalidLegacy) {
                    corrupt++;
                    details.add("Invalid legacy build backup: " + id + " ("
                            + safeMessage(invalidLegacy) + ")");
                }
            }
        }
        for (UUID id : metadataStore.listSnapshotIds()) {
            BuildBackupMetadata metadata = metadataStore.load(id);
            ClaimSnapshot snapshot = byId.get(id);
            if (metadata == null) {
                corrupt++;
                details.add("Malformed build manifest: " + id);
                continue;
            }
            if (snapshot == null) continue;
            if (manifests.stream().anyMatch(existing -> id.equals(existing.snapshotId()))) continue;
            manifests.add(metadata);
            BackupInspection inspection = inspectSync(id, snapshot, metadata, integration);
            if (inspection.integrity() == IntegrityStatus.MISSING) missing++;
            else if (inspection.integrity() == IntegrityStatus.CORRUPT) corrupt++;
            else if (inspection.integrity() == IntegrityStatus.INCOMPATIBLE
                    || !inspection.compatible()) incompatible++;
        }

        Set<UUID> prune = enforce
                ? selectBuildBackupsToPrune(manifests, perPlot, limitBytes, protectedIds)
                : Set.of();
        for (UUID id : prune) {
            details.add("Prune build backup: " + id);
            if (!dryRun) deleteSchematicFiles(id);
        }
        long remaining = currentStorageBytes();
        return new StorageReport(remaining, limitBytes, manifests.size(), orphanFiles,
                corrupt, missing, incompatible, protectedIds.size(), prune.size(), quarantined,
                dryRun, details);
    }

    private boolean quarantine(File file) {
        try {
            File destinationFolder = new File(folder, "quarantine");
            Files.createDirectories(destinationFolder.toPath());
            File destination = new File(destinationFolder,
                    System.currentTimeMillis() + "-" + file.getName());
            moveAtomically(file, destination);
            return true;
        } catch (IOException error) {
            plugin.getLogger().warning("[PlotBackup] Could not quarantine " + file.getName());
            return false;
        }
    }

    private void maybeAlertStorage(StorageReport report) {
        if (report == null || plugin.getDiscord() == null) return;
        boolean serious = report.corruptBackups() > 0 || report.missingBackups() > 0
                || report.incompatibleBackups() > 0
                || report.totalBytes() > report.configuredLimitBytes();
        if (!serious) return;
        long now = System.currentTimeMillis();
        long cooldown = Math.max(1L, plugin.getConfig().getLong(
                "snapshots.build_backup.storage.warning_cooldown_minutes", 60L)) * 60_000L;
        long previous = lastStorageWarningAt.get();
        if (now - previous < cooldown || !lastStorageWarningAt.compareAndSet(previous, now)) return;
        plugin.getDiscord().sendEventKey("backup_warning",
                "discord_event_backup_warning_title", "AegisGuard build-backup warning",
                "discord_event_backup_warning_description",
                "Storage used {USED}/{LIMIT} bytes; corrupt={CORRUPT}, missing={MISSING}, incompatible={INCOMPATIBLE}, orphans={ORPHANS}.",
                java.util.Map.of("USED", String.valueOf(report.totalBytes()),
                        "LIMIT", String.valueOf(report.configuredLimitBytes()),
                        "CORRUPT", String.valueOf(report.corruptBackups()),
                        "MISSING", String.valueOf(report.missingBackups()),
                        "INCOMPATIBLE", String.valueOf(report.incompatibleBackups()),
                        "ORPHANS", String.valueOf(report.orphanFiles())), 0xC0392B);
    }

    private long currentStorageBytes() {
        if (!folder.isDirectory()) return 0L;
        File[] files = folder.listFiles(File::isFile);
        if (files == null) return 0L;
        return Arrays.stream(files).mapToLong(File::length).sum();
    }

    private static UUID snapshotIdFromArtifact(String name) {
        if (name == null) return null;
        int marker = name.indexOf("__");
        int suffix = name.indexOf(".schem");
        String raw;
        if (marker > 0) raw = name.substring(0, marker);
        else if (suffix > 0) raw = name.substring(0, suffix);
        else if (name.endsWith(".meta.yml")) raw = name.substring(0, name.length() - 9);
        else return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    static Set<UUID> selectBuildBackupsToPrune(java.util.Collection<BuildBackupMetadata> candidates,
                                                int retentionPerPlot, long globalLimitBytes,
                                                Set<UUID> protectedIds) {
        if (candidates == null || candidates.isEmpty()) return Set.of();
        int cap = Math.max(1, retentionPerPlot);
        long limit = Math.max(1L, globalLimitBytes);
        Set<UUID> protectedSafe = protectedIds == null ? Set.of() : Set.copyOf(protectedIds);
        List<BuildBackupMetadata> manifests = candidates.stream()
                .filter(java.util.Objects::nonNull).toList();
        Set<UUID> prune = new java.util.LinkedHashSet<>();
        java.util.Map<UUID, List<BuildBackupMetadata>> byPlot = new java.util.HashMap<>();
        for (BuildBackupMetadata metadata : manifests) {
            byPlot.computeIfAbsent(metadata.plotId(), ignored -> new ArrayList<>()).add(metadata);
        }
        for (List<BuildBackupMetadata> values : byPlot.values()) {
            values.sort(Comparator.comparingLong(BuildBackupMetadata::createdAt).reversed());
            for (int index = cap; index < values.size(); index++) {
                UUID id = values.get(index).snapshotId();
                if (!protectedSafe.contains(id)) prune.add(id);
            }
        }
        long retainedBytes = manifests.stream().filter(meta -> !prune.contains(meta.snapshotId()))
                .mapToLong(BuildBackupMetadata::totalBytes).sum();
        if (retainedBytes > limit) {
            List<BuildBackupMetadata> oldest = manifests.stream()
                    .filter(meta -> !prune.contains(meta.snapshotId()))
                    .filter(meta -> !protectedSafe.contains(meta.snapshotId()))
                    .sorted(Comparator.comparingLong(BuildBackupMetadata::createdAt)).toList();
            for (BuildBackupMetadata metadata : oldest) {
                if (retainedBytes <= limit) break;
                if (prune.add(metadata.snapshotId())) retainedBytes -= metadata.totalBytes();
            }
        }
        return Set.copyOf(prune);
    }

    public CompletableFuture<Boolean> linkCaptureOperationAsync(UUID snapshotId, UUID operationId) {
        return updateOperationLinkAsync(snapshotId, operationId, true);
    }

    public CompletableFuture<Boolean> linkRestoreOperationAsync(UUID snapshotId, UUID operationId) {
        return updateOperationLinkAsync(snapshotId, operationId, false);
    }

    private CompletableFuture<Boolean> updateOperationLinkAsync(UUID snapshotId, UUID operationId,
                                                                 boolean capture) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (snapshotId == null || operationId == null || plugin.scheduler() == null) {
            result.complete(false);
            return result;
        }
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> {
            try {
                BuildBackupMetadata metadata = metadataStore.load(snapshotId);
                if (metadata == null) {
                    result.complete(false);
                    return;
                }
                metadataStore.save(capture ? metadata.withCaptureOperation(operationId)
                        : metadata.withLastRestoreOperation(operationId));
                result.complete(true);
            } catch (Throwable error) {
                result.complete(false);
            }
        });
        if (!dispatch.accepted()) result.complete(false);
        return result;
    }

    private BackupInspection inspectSync(UUID snapshotId, ClaimSnapshot snapshot,
                                          BuildBackupMetadata metadata,
                                          IntegrationInfo integration) throws IOException {
        List<File> actual = backupFiles(snapshotId);
        long bytes = actual.stream().mapToLong(File::length).sum();
        if (actual.isEmpty()) {
            return new BackupInspection(false, 0L, 0,
                    metadata == null ? IntegrityStatus.NONE : IntegrityStatus.MISSING,
                    integration.compatible(), "", metadata == null ? "" : metadata.format(),
                    integration.name(), integration.version(), metadata == null
                    ? "No build backup" : "Manifest exists but schematic files are missing");
        }
        if (metadata == null) {
            return new BackupInspection(true, bytes, actual.size(), IntegrityStatus.UNVERIFIED,
                    integration.compatible(), "", "Unknown", integration.name(), integration.version(),
                    "Build files exist without a metadata manifest");
        }
        if (!"SPONGE_SCHEMATIC".equalsIgnoreCase(metadata.format())) {
            return new BackupInspection(true, bytes, actual.size(), IntegrityStatus.INCOMPATIBLE,
                    false, metadata.aggregateChecksum(), metadata.format(),
                    metadata.integrationName(), metadata.integrationVersion(),
                    "Unsupported build-backup format " + metadata.format());
        }
        String mismatch = metadataMismatch(snapshotId, snapshot, metadata, actual);
        if (mismatch != null) {
            return new BackupInspection(true, bytes, actual.size(), IntegrityStatus.CORRUPT,
                    integration.compatible(), metadata.aggregateChecksum(), metadata.format(),
                    metadata.integrationName(), metadata.integrationVersion(), mismatch);
        }
        for (BuildBackupMetadata.BuildFile entry : metadata.files()) {
            File file = safeBackupFile(entry.fileName());
            if (!file.isFile()) {
                return new BackupInspection(true, bytes, actual.size(), IntegrityStatus.MISSING,
                        integration.compatible(), metadata.aggregateChecksum(), metadata.format(),
                        metadata.integrationName(), metadata.integrationVersion(),
                        "Missing schematic file " + entry.fileName());
            }
            if (file.length() != entry.bytes() || !sha256(file).equalsIgnoreCase(entry.sha256())) {
                return new BackupInspection(true, bytes, actual.size(), IntegrityStatus.CORRUPT,
                        integration.compatible(), metadata.aggregateChecksum(), metadata.format(),
                        metadata.integrationName(), metadata.integrationVersion(),
                        "Checksum or size mismatch for " + entry.fileName());
            }
            if (integration.compatible()) try {
                Clipboard parsed = readClipboard(file);
                if (parsed.getMinimumPoint().getX() != entry.minX()
                        || parsed.getMinimumPoint().getZ() != entry.minZ()
                        || parsed.getMaximumPoint().getX() != entry.maxX()
                        || parsed.getMaximumPoint().getZ() != entry.maxZ()
                        || parsed.getMinimumPoint().getY() != metadata.minY()
                        || parsed.getMaximumPoint().getY() != metadata.maxY()) {
                    return new BackupInspection(true, bytes, actual.size(), IntegrityStatus.CORRUPT,
                            integration.compatible(), metadata.aggregateChecksum(), metadata.format(),
                            metadata.integrationName(), metadata.integrationVersion(),
                            "Schematic bounds do not match manifest for " + entry.fileName());
                }
            } catch (Throwable parseError) {
                return new BackupInspection(true, bytes, actual.size(), IntegrityStatus.INCOMPATIBLE,
                        false, metadata.aggregateChecksum(), metadata.format(),
                        metadata.integrationName(), metadata.integrationVersion(),
                        "Current integration cannot parse " + entry.fileName() + ": "
                                + safeMessage(parseError));
            }
        }
        return new BackupInspection(true, bytes, actual.size(), IntegrityStatus.VALID,
                integration.compatible(), metadata.aggregateChecksum(), metadata.format(),
                metadata.integrationName(), metadata.integrationVersion(), integration.compatible()
                ? "Build backup integrity verified" : integration.detail());
    }

    private String metadataMismatch(UUID snapshotId, ClaimSnapshot snapshot,
                                    BuildBackupMetadata metadata, List<File> actual) {
        if (metadata.schemaVersion() != BuildBackupMetadata.CURRENT_SCHEMA) return "Unsupported metadata schema";
        if (!snapshotId.equals(metadata.snapshotId())) return "Snapshot identifier mismatch";
        if (snapshot != null) {
            if (!java.util.Objects.equals(snapshot.getPlotId(), metadata.plotId())) return "Plot identifier mismatch";
            if (!java.util.Objects.equals(snapshot.getWorldName(), metadata.worldName())) return "World mismatch";
            if (snapshot.getX1() != metadata.x1() || snapshot.getZ1() != metadata.z1()
                    || snapshot.getX2() != metadata.x2() || snapshot.getZ2() != metadata.z2()) {
                return "Plot bounds mismatch";
            }
        }
        Set<String> expected = new HashSet<>(metadata.fileNames());
        Set<String> found = new HashSet<>();
        for (File file : actual) found.add(file.getName());
        if (!expected.equals(found)) return "Manifest file set does not match schematic files";
        return coverageMismatch(metadata);
    }

    /** Validate that manifest rectangles cover the whole plot exactly once, with no holes/overlap. */
    static String coverageMismatch(BuildBackupMetadata metadata) {
        if (metadata == null || metadata.files().isEmpty()) return "Manifest has no build files";
        int plotMinX = Math.min(metadata.x1(), metadata.x2());
        int plotMaxX = Math.max(metadata.x1(), metadata.x2());
        int plotMinZ = Math.min(metadata.z1(), metadata.z2());
        int plotMaxZ = Math.max(metadata.z1(), metadata.z2());
        if (metadata.minY() > metadata.maxY()) return "Manifest has invalid vertical bounds";
        long covered = 0L;
        List<BuildBackupMetadata.BuildFile> files = metadata.files();
        for (int index = 0; index < files.size(); index++) {
            BuildBackupMetadata.BuildFile file = files.get(index);
            if (file.minX() > file.maxX() || file.minZ() > file.maxZ()
                    || file.minX() < plotMinX || file.maxX() > plotMaxX
                    || file.minZ() < plotMinZ || file.maxZ() > plotMaxZ) {
                return "Build tile bounds escape the plot or are inverted";
            }
            covered += ((long) file.maxX() - file.minX() + 1L)
                    * ((long) file.maxZ() - file.minZ() + 1L);
            for (int otherIndex = 0; otherIndex < index; otherIndex++) {
                BuildBackupMetadata.BuildFile other = files.get(otherIndex);
                boolean overlaps = file.minX() <= other.maxX() && file.maxX() >= other.minX()
                        && file.minZ() <= other.maxZ() && file.maxZ() >= other.minZ();
                if (overlaps) return "Build tile bounds overlap";
            }
        }
        long expected = ((long) plotMaxX - plotMinX + 1L) * ((long) plotMaxZ - plotMinZ + 1L);
        return covered == expected ? null : "Build tile coverage has holes";
    }

    private BuildBackupMetadata createMetadata(ClaimSnapshot snapshot, IntegrationInfo integration,
                                                UUID captureOperationId,
                                                UUID lastRestoreOperationId) throws Exception {
        if (snapshot == null) return null;
        List<File> files = backupFiles(snapshot.getSnapshotId());
        if (files.isEmpty()) return null;
        int minX = Math.min(snapshot.getX1(), snapshot.getX2());
        int maxX = Math.max(snapshot.getX1(), snapshot.getX2());
        int minZ = Math.min(snapshot.getZ1(), snapshot.getZ2());
        int maxZ = Math.max(snapshot.getZ1(), snapshot.getZ2());
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        List<BuildBackupMetadata.BuildFile> entries = new ArrayList<>();
        for (File file : files) {
            if (file.length() <= 0L) throw new IOException("Empty schematic file " + file.getName());
            // Parse every legacy/current file before trusting it as a restoration baseline.
            Clipboard parsed = integration.compatible() ? readClipboard(file) : null;
            if (parsed != null) {
                minY = Math.min(minY, parsed.getMinimumPoint().getY());
                maxY = Math.max(maxY, parsed.getMaximumPoint().getY());
            }
            int[] coords = parseTileCoords(file.getName(), snapshot.getSnapshotId());
            int fileMinX = parsed == null ? (coords == null ? minX : coords[0])
                    : parsed.getMinimumPoint().getX();
            int fileMinZ = parsed == null ? (coords == null ? minZ : coords[1])
                    : parsed.getMinimumPoint().getZ();
            int fileMaxX = parsed == null ? (coords == null ? maxX : Math.min(maxX,
                    (Math.floorDiv(fileMinX, FOLIA_REGION_BLOCKS) + 1) * FOLIA_REGION_BLOCKS - 1))
                    : parsed.getMaximumPoint().getX();
            int fileMaxZ = parsed == null ? (coords == null ? maxZ : Math.min(maxZ,
                    (Math.floorDiv(fileMinZ, FOLIA_REGION_BLOCKS) + 1) * FOLIA_REGION_BLOCKS - 1))
                    : parsed.getMaximumPoint().getZ();
            entries.add(new BuildBackupMetadata.BuildFile(file.getName(), fileMinX, fileMinZ,
                    fileMaxX, fileMaxZ, file.length(), sha256(file)));
        }
        if (minY == Integer.MAX_VALUE) minY = 0;
        if (maxY == Integer.MIN_VALUE) maxY = 0;
        return new BuildBackupMetadata(BuildBackupMetadata.CURRENT_SCHEMA,
                snapshot.getSnapshotId(), snapshot.getPlotId(), snapshot.getWorldName(),
                snapshot.getX1(), snapshot.getZ1(), snapshot.getX2(), snapshot.getZ2(),
                minY, maxY, snapshot.getTimestamp(), snapshot.getType().name(),
                "SPONGE_SCHEMATIC", integration.name(), integration.version(), entries,
                captureOperationId, lastRestoreOperationId);
    }

    private List<File> backupFiles(UUID snapshotId) {
        if (snapshotId == null) return List.of();
        List<File> files = new ArrayList<>();
        File single = schematicFile(snapshotId);
        if (single.isFile() && single.length() > 0L) files.add(single);
        File[] tiles = tileFiles(snapshotId);
        if (tiles != null) {
            Arrays.stream(tiles).filter(File::isFile).filter(file -> file.length() > 0L)
                    .forEach(files::add);
        }
        files.sort(Comparator.comparing(File::getName));
        return List.copyOf(files);
    }

    private File safeBackupFile(String name) throws IOException {
        if (name == null || name.isBlank() || !new File(name).getName().equals(name)) {
            throw new IOException("Unsafe backup filename");
        }
        File file = new File(folder, name);
        String root = folder.getCanonicalPath() + File.separator;
        if (!file.getCanonicalPath().startsWith(root)) throw new IOException("Backup path escapes storage folder");
        return file;
    }

    static String sha256(File file) throws IOException {
        try (InputStream input = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value == null ? new byte[0] : value));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static void moveAtomically(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String safeMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) current = current.getCause();
        return current == null || current.getMessage() == null
                ? (error == null ? "unknown error" : error.getClass().getSimpleName())
                : current.getMessage();
    }
}
