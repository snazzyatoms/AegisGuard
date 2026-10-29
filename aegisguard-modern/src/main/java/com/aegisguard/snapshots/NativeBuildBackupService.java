package com.aegisguard.snapshots;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.scheduler.AegisScheduler;
import com.aegisguard.snapshots.PlotBuildBackup.BackupInspection;
import com.aegisguard.snapshots.PlotBuildBackup.CaptureResult;
import com.aegisguard.snapshots.PlotBuildBackup.IntegrationInfo;
import com.aegisguard.snapshots.PlotBuildBackup.IntegrityStatus;
import com.aegisguard.snapshots.PlotBuildBackup.RestoreQueueResult;
import com.aegisguard.snapshots.PlotBuildBackup.StorageReport;
import com.aegisguard.snapshots.PlotBuildBackup.TrackedRestoreResult;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Zero-dependency, Folia-native plot build backup engine. Captures block data plus common
 * block-entity payloads (container contents, sign text) into compressed {@code .agtile}
 * files and restores them through region-safe dispatches. Selected automatically when no
 * WorldEdit/FAWE integration is available so full block restores never require an add-on.
 */
public final class NativeBuildBackupService implements BuildBackupService {

    static final int TILE_MAGIC = 0x41475431; // "AGT1"
    static final int TILE_VERSION = 1;
    static final String TILE_FORMAT = "AGTILE_V1";
    private static final String TILE_EXT = ".agtile";

    private record TileBounds(int minX, int maxX, int minZ, int maxZ, File destination) { }

    private record StoredState(int relX, int relY, int relZ, String type, byte[] payload) { }

    private record NativeTile(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                              List<String> palette, int[] indices, List<StoredState> states) { }

    private final AegisGuard plugin;
    private final File folder;
    private final BuildBackupMetadataStore metadataStore;
    private final Set<UUID> activeCaptureIds = ConcurrentHashMap.newKeySet();

    public NativeBuildBackupService(AegisGuard plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "plot-backups-native");
        this.metadataStore = new BuildBackupMetadataStore(folder);
    }

    @Override
    public boolean isConfiguredOn() {
        return plugin.getConfig().getBoolean("snapshots.build_backup.enabled", false);
    }

    private boolean nativeEnabled() {
        return plugin.getConfig().getBoolean("snapshots.build_backup.native_engine", true);
    }

    @Override
    public boolean isReady() {
        return isConfiguredOn() && integrationInfo().compatible();
    }

    @Override
    public IntegrationInfo integrationInfo() {
        if (!nativeEnabled()) {
            return new IntegrationInfo("AegisGuard Native", plugin.getDescription().getVersion(),
                    true, false, "Built-in engine disabled by snapshots.build_backup.native_engine");
        }
        return new IntegrationInfo("AegisGuard Native", plugin.getDescription().getVersion(),
                true, true, plugin.isFolia()
                ? "Built-in region-safe engine (Folia, no add-ons required)"
                : "Built-in engine (no add-ons required)");
    }

    public File tileFileFor(UUID snapshotId) {
        return new File(folder, snapshotId + TILE_EXT);
    }

    private File tileFile(UUID snapshotId, int minX, int minZ) {
        return new File(folder, snapshotId + "__" + minX + "_" + minZ + TILE_EXT);
    }

    private File[] tileFiles(UUID snapshotId) {
        if (snapshotId == null || !folder.isDirectory()) return null;
        String prefix = snapshotId + "__";
        return folder.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(TILE_EXT));
    }

    private List<File> backupFiles(UUID snapshotId) {
        List<File> files = new ArrayList<>();
        File single = tileFileFor(snapshotId);
        if (single.isFile()) files.add(single);
        File[] tiles = tileFiles(snapshotId);
        if (tiles != null) files.addAll(List.of(tiles));
        return files;
    }

    private boolean hasBackup(UUID snapshotId) {
        return !backupFiles(snapshotId).isEmpty();
    }

    // ---------------------------------------------------------------- inspection

    @Override
    public CompletableFuture<BackupInspection> inspectAsync(UUID snapshotId) {
        ClaimSnapshot snapshot = plugin.getSnapshotManager() == null
                ? null : plugin.getSnapshotManager().getSnapshot(snapshotId);
        return inspectInternal(snapshotId, snapshot);
    }

    @Override
    public CompletableFuture<BackupInspection> inspectAsync(ClaimSnapshot snapshot) {
        return inspectInternal(snapshot == null ? null : snapshot.getSnapshotId(), snapshot);
    }

    private CompletableFuture<BackupInspection> inspectInternal(UUID snapshotId, ClaimSnapshot snapshot) {
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
                if (metadata == null && manifestExists) {
                    result.complete(new BackupInspection(hasBackup(snapshotId), 0L, 0,
                            IntegrityStatus.CORRUPT, integration.compatible(), "", "",
                            integration.name(), integration.version(), "Build manifest is malformed"));
                    return;
                }
                result.complete(inspectSync(snapshotId, snapshot, metadata, integration));
            } catch (Throwable error) {
                result.complete(new BackupInspection(hasBackup(snapshotId), 0L, 0,
                        IntegrityStatus.CORRUPT, integration.compatible(), "", "",
                        integration.name(), integration.version(),
                        "Inspection failed: " + safeMessage(error)));
            }
        });
        if (!dispatch.accepted()) result.complete(new BackupInspection(false, 0L, 0));
        return result;
    }

    @Override
    public CompletableFuture<Map<UUID, BackupInspection>> inspectBatchAsync(
            java.util.Collection<ClaimSnapshot> snapshots) {
        CompletableFuture<Map<UUID, BackupInspection>> result = new CompletableFuture<>();
        if (plugin.scheduler() == null) {
            result.complete(Map.of());
            return result;
        }
        IntegrationInfo integration = integrationInfo();
        List<ClaimSnapshot> safe = snapshots == null ? List.of()
                : snapshots.stream().filter(java.util.Objects::nonNull).toList();
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> {
            Map<UUID, BackupInspection> inspections = new java.util.LinkedHashMap<>();
            for (ClaimSnapshot snapshot : safe) {
                UUID id = snapshot.getSnapshotId();
                try {
                    BuildBackupMetadata metadata = metadataStore.load(id);
                    boolean manifestExists = metadataStore.file(id).isFile();
                    if (metadata == null && manifestExists) {
                        inspections.put(id, new BackupInspection(hasBackup(id), 0L, 0,
                                IntegrityStatus.CORRUPT, integration.compatible(), "", "",
                                integration.name(), integration.version(), "Build manifest is malformed"));
                        continue;
                    }
                    inspections.put(id, inspectSync(id, snapshot, metadata, integration));
                } catch (Throwable error) {
                    inspections.put(id, new BackupInspection(hasBackup(id), 0L, 0,
                            IntegrityStatus.CORRUPT, integration.compatible(), "", "",
                            integration.name(), integration.version(), safeMessage(error)));
                }
            }
            result.complete(Map.copyOf(inspections));
        });
        if (!dispatch.accepted()) result.complete(Map.of());
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
                    ? "No build backup" : "Manifest exists but tile files are missing");
        }
        if (metadata == null) {
            return new BackupInspection(true, bytes, actual.size(), IntegrityStatus.UNVERIFIED,
                    integration.compatible(), "", "Unknown", integration.name(),
                    integration.version(), "Build files exist without a metadata manifest");
        }
        if (!TILE_FORMAT.equalsIgnoreCase(metadata.format())) {
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
                        "Missing tile file " + entry.fileName());
            }
            if (file.length() != entry.bytes()
                    || !BuildBackupMetadataStore.sha256(file).equalsIgnoreCase(entry.sha256())) {
                return new BackupInspection(true, bytes, actual.size(), IntegrityStatus.CORRUPT,
                        integration.compatible(), metadata.aggregateChecksum(), metadata.format(),
                        metadata.integrationName(), metadata.integrationVersion(),
                        "Checksum or size mismatch for " + entry.fileName());
            }
        }
        return new BackupInspection(true, bytes, actual.size(), IntegrityStatus.VALID,
                integration.compatible(), metadata.aggregateChecksum(), metadata.format(),
                metadata.integrationName(), metadata.integrationVersion(), "Native build backup verified");
    }

    private String metadataMismatch(UUID snapshotId, ClaimSnapshot snapshot,
                                    BuildBackupMetadata metadata, List<File> actual) {
        if (!snapshotId.equals(metadata.snapshotId())) return "Manifest snapshot id mismatch";
        if (metadata.files().isEmpty()) return "Manifest lists no tile files";
        if (snapshot != null) {
            if (snapshot.getPlotId() != null && !snapshot.getPlotId().equals(metadata.plotId())) {
                return "Manifest plot id mismatch";
            }
            if (snapshot.getWorldName() != null && metadata.worldName() != null
                    && !metadata.worldName().isEmpty()
                    && !snapshot.getWorldName().equalsIgnoreCase(metadata.worldName())) {
                return "Manifest world mismatch";
            }
        }
        Set<String> names = new HashSet<>();
        for (File file : actual) names.add(file.getName());
        for (BuildBackupMetadata.BuildFile entry : metadata.files()) {
            if (!names.contains(entry.fileName())) return "Expected tile missing: " + entry.fileName();
        }
        return null;
    }

    // ---------------------------------------------------------------- capture

    @Override
    public CaptureResult preview(ClaimSnapshot snapshot) {
        return preview(snapshot, isConfiguredOn());
    }

    private CaptureResult preview(ClaimSnapshot snapshot, boolean featureEnabled) {
        if (snapshot == null || !featureEnabled) return CaptureResult.DISABLED;
        IntegrationInfo integration = integrationInfo();
        if (!integration.available()) return CaptureResult.DISABLED;
        if (!integration.compatible()) return CaptureResult.INCOMPATIBLE;
        World world = Bukkit.getWorld(snapshot.getWorldName());
        if (world == null) return CaptureResult.SKIPPED_WORLD;
        long blocks = volume(snapshot.getX1(), snapshot.getZ1(),
                snapshot.getX2(), snapshot.getZ2(), world.getMinHeight(), world.getMaxHeight() - 1);
        long cap = Math.max(1L, plugin.getConfig().getLong(
                "snapshots.build_backup.max_volume", 2_000_000L));
        if (blocks > cap) return CaptureResult.SKIPPED_VOLUME;
        return CaptureResult.QUEUED;
    }

    @Override
    public CaptureResult queueCapture(Plot plot, ClaimSnapshot snapshot) {
        CaptureResult preview = preview(snapshot);
        if (preview != CaptureResult.QUEUED) {
            if (preview == CaptureResult.SKIPPED_VOLUME) {
                plugin.getLogger().warning("[PlotBackup] Snapshot " + snapshot.getSnapshotId()
                        + " exceeds max_volume; skipped native build copy.");
            } else if (preview == CaptureResult.SKIPPED_WORLD) {
                plugin.getLogger().warning("[PlotBackup] World not loaded; skipped native build copy for "
                        + snapshot.getSnapshotId());
            }
            return preview;
        }
        captureAsync(plot, snapshot);
        return CaptureResult.QUEUED;
    }

    @Override
    public CompletableFuture<CaptureResult> captureAsync(Plot plot, ClaimSnapshot snapshot) {
        return captureAsync(plot, snapshot, isConfiguredOn());
    }

    @Override
    public CompletableFuture<CaptureResult> captureAsync(Plot plot, ClaimSnapshot snapshot,
                                                          boolean featureEnabled) {
        CaptureResult preflight = preview(snapshot, featureEnabled);
        if (preflight != CaptureResult.QUEUED) return CompletableFuture.completedFuture(preflight);
        activeCaptureIds.add(snapshot.getSnapshotId());
        World world = Bukkit.getWorld(snapshot.getWorldName());
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
                return persistMetadataAsync(snapshot, null)
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

    @Override
    public Set<UUID> activeCaptureIds() {
        return Set.copyOf(activeCaptureIds);
    }

    private CompletableFuture<Void> discardIncompleteCaptureAsync(UUID snapshotId) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> {
            deleteBackupFiles(snapshotId);
            result.complete(null);
        });
        if (!dispatch.accepted()) {
            plugin.getLogger().severe("[PlotBackup] Could not clean incomplete native capture "
                    + snapshotId + "; its files will fail manifest preflight and be quarantined later.");
            result.complete(null);
        }
        return result;
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
                    ? tileFileFor(snapshot.getSnapshotId())
                    : tileFile(snapshot.getSnapshotId(), bounds[0], bounds[2]);
            tiles.add(new TileBounds(bounds[0], bounds[1], bounds[2], bounds[3], destination));
        }
        return List.copyOf(tiles);
    }

    /**
     * Reads the tile's blocks on its owning region thread (one chunk per tile on Folia), then
     * writes the compressed tile on the async executor so serialization never stalls a region.
     */
    private CompletableFuture<Boolean> captureTileAsync(ClaimSnapshot snapshot, World world,
                                                        int minY, int maxY, int minX, int maxX,
                                                        int minZ, int maxZ, File dest) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Location target = new Location(world, minX, minY, minZ);
        AegisScheduler.DispatchResult regionDispatch = plugin.scheduler().runAt(target, () -> {
            try {
                int sizeX = maxX - minX + 1;
                int sizeY = maxY - minY + 1;
                int sizeZ = maxZ - minZ + 1;
                Map<String, Integer> paletteIds = new HashMap<>();
                List<String> palette = new ArrayList<>();
                int[] indices = new int[sizeX * sizeY * sizeZ];
                List<StoredState> states = new ArrayList<>();
                int index = 0;
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            Block block = world.getBlockAt(x, y, z);
                            String data = block.getBlockData().getAsString();
                            Integer paletteIndex = paletteIds.get(data);
                            if (paletteIndex == null) {
                                paletteIndex = palette.size();
                                paletteIds.put(data, paletteIndex);
                                palette.add(data);
                            }
                            indices[index++] = paletteIndex;
                            captureBlockState(block, x - minX, y - minY, z - minZ, states);
                        }
                    }
                }
                NativeTile tile = new NativeTile(minX, minY, minZ, sizeX, sizeY, sizeZ,
                        palette, indices, states);
                AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(
                        () -> result.complete(writeTile(tile, dest)));
                if (!dispatch.accepted()) {
                    plugin.getLogger().warning("[PlotBackup] Async scheduler rejected native tile write for "
                            + snapshot.getSnapshotId() + ": " + dispatch);
                    result.complete(false);
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "[PlotBackup] Failed to capture native tile for snapshot "
                        + snapshot.getSnapshotId(), t);
                result.complete(false);
            }
        });
        if (!regionDispatch.accepted()) result.complete(false);
        return result;
    }

    /**
     * Captures common block-entity payloads. Containers keep their contents and signs keep
     * their front-side text; other block entities are structure-only in the native format.
     */
    private void captureBlockState(Block block, int relX, int relY, int relZ,
                                   List<StoredState> states) {
        try {
            org.bukkit.block.BlockState state = block.getState();
            if (state instanceof Container container) {
                // getState() already returns a detached snapshot copy, so getInventory()
                // here reads the snapshot contents — portable across Spigot/Paper/Folia.
                Map<String, Object> payload = new HashMap<>();
                payload.put("items", container.getInventory().getContents());
                states.add(new StoredState(relX, relY, relZ, "container", serializePayload(payload)));
            } else if (state instanceof Sign sign) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("front", frontLines(sign));
                states.add(new StoredState(relX, relY, relZ, "sign", serializePayload(payload)));
            }
        } catch (Throwable error) {
            plugin.getLogger().fine("[PlotBackup] Skipped block-entity data at "
                    + block.getX() + "," + block.getY() + "," + block.getZ()
                    + ": " + safeMessage(error));
        }
    }

    @SuppressWarnings("deprecation")
    private List<String> frontLines(Sign sign) {
        List<String> lines = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) lines.add(sign.getLine(i));
        return lines;
    }

    private byte[] serializePayload(Map<String, Object> payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(payload);
        }
        return bytes.toByteArray();
    }

    private boolean writeTile(NativeTile tile, File dest) {
        File temp = new File(folder, dest.getName() + ".tmp");
        try {
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("[PlotBackup] Could not create plot-backups-native folder.");
                return false;
            }
            try (DataOutputStream out = new DataOutputStream(
                    new GZIPOutputStream(new FileOutputStream(temp)))) {
                out.writeInt(TILE_MAGIC);
                out.writeInt(TILE_VERSION);
                out.writeInt(tile.minX());
                out.writeInt(tile.minY());
                out.writeInt(tile.minZ());
                out.writeInt(tile.sizeX());
                out.writeInt(tile.sizeY());
                out.writeInt(tile.sizeZ());
                out.writeInt(tile.palette().size());
                for (String entry : tile.palette()) out.writeUTF(entry);
                out.writeInt(tile.states().size());
                for (StoredState state : tile.states()) {
                    out.writeInt(state.relX());
                    out.writeInt(state.relY());
                    out.writeInt(state.relZ());
                    out.writeUTF(state.type());
                    out.writeInt(state.payload().length);
                    out.write(state.payload());
                }
                out.writeInt(tile.indices().length);
                for (int value : tile.indices()) out.writeInt(value);
            }
            moveAtomically(temp, dest);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[PlotBackup] Failed to write native tile "
                    + dest.getName(), t);
            return false;
        } finally {
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException cleanupError) {
                plugin.getLogger().warning("[PlotBackup] Could not clean temporary tile " + temp.getName());
            }
        }
    }

    // ---------------------------------------------------------------- restore

    /**
     * Sequential, durable cross-region restore mirroring the WorldEdit path: each tile is
     * pasted only on its owning region and the supplied checkpoint must persist before the
     * next tile begins.
     */
    @Override
    public CompletableFuture<TrackedRestoreResult> restoreTracked(
            ClaimSnapshot snapshot, Set<String> alreadyCompleted,
            BiFunction<String, Boolean, CompletableFuture<Void>> checkpoint) {
        Set<String> completed = ConcurrentHashMap.newKeySet();
        if (alreadyCompleted != null) completed.addAll(alreadyCompleted);
        Set<String> failed = ConcurrentHashMap.newKeySet();
        if (snapshot == null) {
            return CompletableFuture.completedFuture(new TrackedRestoreResult(
                    RestoreQueueResult.NO_BACKUP, completed, failed, Set.of()));
        }
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
                    World world = Bukkit.getWorld(snapshot.getWorldName());
                    if (world == null) {
                        result.complete(new TrackedRestoreResult(RestoreQueueResult.WORLD_UNAVAILABLE,
                                completed, failed, Set.of()));
                        return;
                    }
                    AegisScheduler.DispatchResult async = plugin.scheduler().runAsync(() -> {
                        try {
                            BuildBackupMetadata metadata = metadataStore.load(snapshot.getSnapshotId());
                            if (metadata == null || metadata.files().isEmpty()) {
                                result.complete(new TrackedRestoreResult(
                                        RestoreQueueResult.INTEGRITY_FAILED, completed, failed, Set.of()));
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
                                    return readAndPasteAsync(snapshot, world, file)
                                            .thenCompose(success -> {
                                                if (success) completed.add(entry.fileName());
                                                else failed.add(entry.fileName());
                                                CompletableFuture<Void> durable = checkpoint == null
                                                        ? CompletableFuture.completedFuture(null)
                                                        : checkpoint.apply(entry.fileName(), success);
                                                return durable == null ? CompletableFuture.failedFuture(
                                                        new IllegalStateException("Tile checkpoint returned null"))
                                                        : durable;
                                            });
                                });
                            }
                            chain.whenComplete((ignored, chainError) -> {
                                Set<String> pending = new HashSet<>();
                                for (BuildBackupMetadata.BuildFile entry : metadata.files()) {
                                    if (!completed.contains(entry.fileName())
                                            && !failed.contains(entry.fileName())) {
                                        pending.add(entry.fileName());
                                    }
                                }
                                if (chainError != null || !failed.isEmpty()) {
                                    result.complete(new TrackedRestoreResult(
                                            RestoreQueueResult.PARTIALLY_COMPLETED,
                                            completed, failed, pending));
                                } else {
                                    result.complete(new TrackedRestoreResult(
                                            RestoreQueueResult.COMPLETED, completed, failed, pending));
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

    /** Reads the tile on the async executor, then applies it in rate-limited region slices. */
    private CompletableFuture<Boolean> readAndPasteAsync(ClaimSnapshot snapshot, World world, File file) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        AegisScheduler.DispatchResult async = plugin.scheduler().runAsync(() -> {
            try {
                NativeTile tile = readTile(file);
                pasteTile(snapshot, world, tile, result);
            } catch (Throwable error) {
                plugin.getLogger().log(Level.WARNING,
                        "[PlotBackup] Failed to read native tile " + file.getName(), error);
                result.complete(false);
            }
        });
        if (!async.accepted()) result.complete(false);
        return result;
    }

    /**
     * Applies a bounded number of blocks per region tick (snapshots.build_backup.native
     * _blocks_per_tick) so a large tile never stalls its region. Physics are suppressed
     * during the bulk pass, matching fast-paste behaviour.
     */
    private void pasteTile(ClaimSnapshot snapshot, World world, NativeTile tile,
                           CompletableFuture<Boolean> result) {
        int budget = Math.max(256, plugin.getConfig().getInt(
                "snapshots.build_backup.native_blocks_per_tick", 8192));
        int total = tile.indices().length;
        int[] cursor = {0};
        boolean[] hadUnknown = {false};
        java.util.concurrent.atomic.AtomicBoolean dispatched = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable[] slice = new Runnable[1];
        slice[0] = () -> {
            try {
                int end = Math.min(total, cursor[0] + budget);
                for (int i = cursor[0]; i < end; i++) {
                    int relX = i % tile.sizeX();
                    int relZ = (i / tile.sizeX()) % tile.sizeZ();
                    int relY = i / (tile.sizeX() * tile.sizeZ());
                    String data = tile.palette().get(tile.indices()[i]);
                    try {
                        BlockData blockData = Bukkit.createBlockData(data);
                        world.getBlockAt(tile.minX() + relX, tile.minY() + relY, tile.minZ() + relZ)
                                .setBlockData(blockData, false);
                    } catch (IllegalArgumentException unknownBlock) {
                        hadUnknown[0] = true;
                    }
                }
                cursor[0] = end;
                if (cursor[0] < total) {
                    dispatched.set(false);
                    scheduleSlice(world, tile, slice[0], result, dispatched);
                } else {
                    restoreBlockStates(world, tile);
                    if (hadUnknown[0]) {
                        plugin.getLogger().warning("[PlotBackup] Native tile for snapshot "
                                + snapshot.getSnapshotId() + " contained block types this server "
                                + "version does not recognise; those positions were left as-is.");
                    }
                    plugin.getLogger().info("[PlotBackup] Restored native tile for plot "
                            + snapshot.getPlotId() + " from snapshot " + snapshot.getSnapshotId());
                    result.complete(true);
                }
            } catch (Throwable error) {
                plugin.getLogger().log(Level.WARNING, "[PlotBackup] Failed to apply native tile for snapshot "
                        + snapshot.getSnapshotId(), error);
                result.complete(false);
            }
        };
        dispatched.set(false);
        scheduleSlice(world, tile, slice[0], result, dispatched);
    }

    private void scheduleSlice(World world, NativeTile tile, Runnable slice,
                               CompletableFuture<Boolean> result,
                               java.util.concurrent.atomic.AtomicBoolean dispatched) {
        if (!dispatched.compareAndSet(false, true)) return;
        Location target = new Location(world, tile.minX(), tile.minY(), tile.minZ());
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAt(target, () -> {
            dispatched.set(false);
            slice.run();
        });
        if (!dispatch.accepted()) result.complete(false);
    }

    private void restoreBlockStates(World world, NativeTile tile) {
        for (StoredState state : tile.states()) {
            try {
                Block block = world.getBlockAt(tile.minX() + state.relX(),
                        tile.minY() + state.relY(), tile.minZ() + state.relZ());
                Object payload = deserializePayload(state.payload());
                if (!(payload instanceof Map<?, ?> map)) continue;
                if ("container".equals(state.type())
                        && block.getState() instanceof Container container
                        && map.get("items") instanceof ItemStack[] items) {
                    container.getInventory().setContents(items);
                    container.update(true, false);
                } else if ("sign".equals(state.type())
                        && block.getState() instanceof Sign sign
                        && map.get("front") instanceof List<?> lines) {
                    applySignLines(sign, lines);
                    sign.update(true, false);
                }
            } catch (Throwable error) {
                plugin.getLogger().fine("[PlotBackup] Skipped restoring block entity at native tile offset "
                        + state.relX() + "," + state.relY() + "," + state.relZ()
                        + ": " + safeMessage(error));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void applySignLines(Sign sign, List<?> lines) {
        for (int i = 0; i < 4 && i < lines.size(); i++) {
            Object line = lines.get(i);
            sign.setLine(i, line == null ? "" : String.valueOf(line));
        }
    }

    private Object deserializePayload(byte[] payload) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                new java.io.ByteArrayInputStream(payload))) {
            return in.readObject();
        }
    }

    private NativeTile readTile(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new GZIPInputStream(new FileInputStream(file)))) {
            if (in.readInt() != TILE_MAGIC) {
                throw new IOException("Not an AegisGuard tile: " + file.getName());
            }
            int version = in.readInt();
            if (version != TILE_VERSION) {
                throw new IOException("Unsupported native tile version " + version);
            }
            int minX = in.readInt();
            int minY = in.readInt();
            int minZ = in.readInt();
            int sizeX = in.readInt();
            int sizeY = in.readInt();
            int sizeZ = in.readInt();
            int paletteSize = in.readInt();
            if (paletteSize < 0 || paletteSize > 1_000_000) throw new IOException("Bad palette size");
            List<String> palette = new ArrayList<>(paletteSize);
            for (int i = 0; i < paletteSize; i++) palette.add(in.readUTF());
            int stateCount = in.readInt();
            if (stateCount < 0 || stateCount > 500_000) throw new IOException("Bad block-entity count");
            List<StoredState> states = new ArrayList<>(stateCount);
            for (int i = 0; i < stateCount; i++) {
                int relX = in.readInt();
                int relY = in.readInt();
                int relZ = in.readInt();
                String type = in.readUTF();
                int length = in.readInt();
                if (length < 0 || length > 16 * 1024 * 1024) throw new IOException("Bad payload size");
                byte[] payload = new byte[length];
                in.readFully(payload);
                states.add(new StoredState(relX, relY, relZ, type, payload));
            }
            int indexCount = in.readInt();
            if (indexCount != sizeX * sizeY * sizeZ) {
                throw new IOException("Tile index count mismatch");
            }
            int[] indices = new int[indexCount];
            for (int i = 0; i < indexCount; i++) {
                indices[i] = in.readInt();
                if (indices[i] < 0 || indices[i] >= paletteSize) throw new EOFException("Bad palette index");
            }
            return new NativeTile(minX, minY, minZ, sizeX, sizeY, sizeZ, palette, indices, states);
        }
    }

    // ---------------------------------------------------------------- metadata + storage

    private CompletableFuture<Boolean> persistMetadataAsync(ClaimSnapshot snapshot,
                                                             UUID captureOperationId) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> {
            try {
                BuildBackupMetadata metadata = createMetadata(snapshot, captureOperationId);
                if (metadata == null || metadata.files().isEmpty()) {
                    result.complete(false);
                    return;
                }
                metadataStore.save(metadata);
                result.complete(true);
            } catch (Throwable error) {
                plugin.getLogger().log(Level.WARNING,
                        "[PlotBackup] Could not persist native build metadata for "
                                + snapshot.getSnapshotId(), error);
                result.complete(false);
            }
        });
        if (!dispatch.accepted()) result.complete(false);
        return result;
    }

    private BuildBackupMetadata createMetadata(ClaimSnapshot snapshot,
                                               UUID captureOperationId) throws IOException {
        Map<String, int[]> boundsByName = new HashMap<>();
        for (TileBounds tile : captureTiles(snapshot)) {
            boundsByName.put(tile.destination().getName(),
                    new int[]{tile.minX(), tile.minZ(), tile.maxX(), tile.maxZ()});
        }
        List<BuildBackupMetadata.BuildFile> files = new ArrayList<>();
        for (File file : backupFiles(snapshot.getSnapshotId())) {
            int[] coords = boundsByName.get(file.getName());
            if (coords == null) continue;
            files.add(new BuildBackupMetadata.BuildFile(file.getName(), coords[0], coords[1],
                    coords[2], coords[3], file.length(), BuildBackupMetadataStore.sha256(file)));
        }
        if (files.isEmpty()) return null;
        World world = Bukkit.getWorld(snapshot.getWorldName());
        IntegrationInfo integration = integrationInfo();
        return new BuildBackupMetadata(BuildBackupMetadata.CURRENT_SCHEMA, snapshot.getSnapshotId(),
                snapshot.getPlotId(), snapshot.getWorldName(),
                Math.min(snapshot.getX1(), snapshot.getX2()), Math.min(snapshot.getZ1(), snapshot.getZ2()),
                Math.max(snapshot.getX1(), snapshot.getX2()), Math.max(snapshot.getZ1(), snapshot.getZ2()),
                world == null ? 0 : world.getMinHeight(), world == null ? 0 : world.getMaxHeight() - 1,
                System.currentTimeMillis(), snapshot.getType() == null ? "UNKNOWN" : snapshot.getType().name(),
                TILE_FORMAT, integration.name(), integration.version(), List.copyOf(files),
                captureOperationId, null);
    }

    @Override
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

    @Override
    public void deleteSchematic(UUID snapshotId) {
        if (snapshotId == null) return;
        if (plugin.getSnapshotManager() != null
                && plugin.getSnapshotManager().protectedBuildSnapshotIds().contains(snapshotId)) {
            plugin.getLogger().warning("[PlotBackup] Refused to delete restore-protected backup "
                    + snapshotId);
            return;
        }
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(
                () -> deleteBackupFiles(snapshotId));
        if (!dispatch.accepted()) {
            plugin.getLogger().warning("[PlotBackup] Scheduler rejected native backup cleanup for "
                    + snapshotId + ": " + dispatch);
        }
    }

    private void deleteBackupFiles(UUID snapshotId) {
        for (File file : backupFiles(snapshotId)) {
            if (file.exists() && !file.delete()) {
                plugin.getLogger().warning("[PlotBackup] Could not delete " + file.getName());
            }
        }
        try {
            metadataStore.delete(snapshotId);
        } catch (IOException error) {
            plugin.getLogger().warning("[PlotBackup] Could not delete native metadata for " + snapshotId);
        }
    }

    /**
     * Lean maintenance for the native store: orphans are quarantined, retention is enforced
     * per plot and globally through the same pruning rules as the WorldEdit engine.
     */
    @Override
    public CompletableFuture<StorageReport> maintainStorageAsync(boolean dryRun) {
        CompletableFuture<StorageReport> result = new CompletableFuture<>();
        if (plugin.scheduler() == null) {
            result.complete(new StorageReport(0L, 0L, 0, 0, 0, 0, 0, 0, 0, 0, dryRun,
                    List.of("Scheduler unavailable")));
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
        AegisScheduler.DispatchResult dispatch = plugin.scheduler().runAsync(() -> {
            try {
                result.complete(maintainStorageSync(snapshots, protectedIds, enforce,
                        perPlot, limitBytes, dryRun));
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
                                              long limitBytes, boolean dryRun) throws IOException {
        Map<UUID, ClaimSnapshot> byId = new HashMap<>();
        for (ClaimSnapshot snapshot : snapshots) {
            if (snapshot != null && snapshot.getSnapshotId() != null) {
                byId.put(snapshot.getSnapshotId(), snapshot);
            }
        }
        List<String> details = new ArrayList<>();
        int orphanFiles = 0;
        int quarantined = 0;
        File[] stored = folder.listFiles((dir, name) -> name.endsWith(TILE_EXT)
                || name.endsWith(".meta.yml") || name.endsWith(".tmp"));
        if (stored != null) {
            for (File file : stored) {
                UUID id = snapshotIdFromArtifact(file.getName());
                if (id != null && byId.containsKey(id)) continue;
                orphanFiles++;
                details.add("Orphan: " + file.getName());
                if (!dryRun && quarantine(file)) quarantined++;
            }
        }
        List<BuildBackupMetadata> manifests = new ArrayList<>();
        int missing = 0;
        int corrupt = 0;
        int incompatible = 0;
        for (UUID id : metadataStore.listSnapshotIds()) {
            BuildBackupMetadata metadata = metadataStore.load(id);
            ClaimSnapshot snapshot = byId.get(id);
            if (metadata == null) {
                corrupt++;
                details.add("Malformed native build manifest: " + id);
                continue;
            }
            if (snapshot == null) continue;
            manifests.add(metadata);
            BackupInspection inspection = inspectSync(id, snapshot, metadata, integrationInfo());
            if (inspection.integrity() == IntegrityStatus.MISSING) missing++;
            else if (inspection.integrity() == IntegrityStatus.CORRUPT) corrupt++;
            else if (inspection.integrity() == IntegrityStatus.INCOMPATIBLE
                    || !inspection.compatible()) incompatible++;
        }
        Set<UUID> prune = enforce
                ? selectBuildBackupsToPrune(manifests, perPlot, limitBytes, protectedIds)
                : Set.of();
        for (UUID id : prune) {
            details.add("Prune native build backup: " + id);
            if (!dryRun) deleteBackupFiles(id);
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

    private long currentStorageBytes() {
        if (!folder.isDirectory()) return 0L;
        File[] files = folder.listFiles(File::isFile);
        if (files == null) return 0L;
        return java.util.Arrays.stream(files).mapToLong(File::length).sum();
    }

    private static UUID snapshotIdFromArtifact(String name) {
        if (name == null) return null;
        int marker = name.indexOf("__");
        int suffix = name.indexOf(TILE_EXT);
        String raw;
        if (marker > 0) raw = name.substring(0, marker);
        else if (suffix > 0) raw = name.substring(0, suffix);
        else if (name.endsWith(".meta.yml")) raw = name.substring(0, name.length() - 9);
        else return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    @Override
    public CompletableFuture<Boolean> linkCaptureOperationAsync(UUID snapshotId, UUID operationId) {
        return updateOperationLinkAsync(snapshotId, operationId, true);
    }

    @Override
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

    // ---------------------------------------------------------------- helpers

    private File safeBackupFile(String fileName) throws IOException {
        File candidate = new File(folder, fileName);
        String folderPath = folder.getCanonicalPath() + File.separator;
        if (!candidate.getCanonicalPath().startsWith(folderPath)) {
            throw new IOException("Unsafe backup path: " + fileName);
        }
        return candidate;
    }

    private void moveAtomically(File source, File destination) throws IOException {
        try {
            Files.move(source.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicFailure) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    // These helpers mirror the WorldEdit engine's pure functions but live here so this
    // service never triggers class-loading of the WorldEdit-backed implementation on
    // servers where WorldEdit/FAWE is absent.
    static long volume(int x1, int z1, int x2, int z2, int minY, int maxY) {
        long width = Math.abs((long) x2 - x1) + 1L;
        long length = Math.abs((long) z2 - z1) + 1L;
        long height = Math.abs((long) maxY - minY) + 1L;
        return width * length * height;
    }

    static List<int[]> computeTileBounds(int minX, int maxX, int minZ, int maxZ,
                                         int configuredMaxChunks, boolean folia) {
        int normalizedMinX = Math.min(minX, maxX);
        int normalizedMaxX = Math.max(minX, maxX);
        int normalizedMinZ = Math.min(minZ, maxZ);
        int normalizedMaxZ = Math.max(minZ, maxZ);
        configuredMaxChunks = Math.max(1, Math.min(256, configuredMaxChunks));
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
        Map<UUID, List<BuildBackupMetadata>> byPlot = new HashMap<>();
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
}
