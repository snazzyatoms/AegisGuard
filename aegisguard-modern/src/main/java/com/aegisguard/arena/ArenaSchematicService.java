package com.aegisguard.arena;

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
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * WorldEdit/FAWE-backed schematic maps for instanced arena floors.
 *
 * <p>A floor carries a {@code .schem} file plus the world-space corner it was
 * captured at ({@link ArenaFloor#setCaptureBounds}). When a run picks an
 * instanced floor this service:</p>
 * <ol>
 *   <li>allocates a free instance slot in the schematic world,</li>
 *   <li>pastes a fresh copy there while the party waits through countdown/</li>
 *   <li>lobby (paste usually finishes long before combat starts),</li>
 *   <li>re-bases every spawn as {@code instanceOrigin + (absolute - captureMin)},</li>
 *   <li>wipes the pasted cuboid back to air when the run ends.</li>
 * </ol>
 *
 * <p>Slots sit on a line along +X in {@code arena.schematics.world} (the
 * dedicated arena world by default) spaced {@code arena.schematics.instance_spacing}
 * apart — far enough that ordinary capture volumes never collide.</p>
 *
 * <p>WorldEdit and FastAsyncWorldEdit are soft-depends. When neither is
 * present the service reports unavailable and instanced floors refuse to
 * start — nothing else in the arena system depends on it.</p>
 */
public final class ArenaSchematicService {

    private final AegisGuard plugin;
    private final File folder;
    /** runId → paste future, only while a paste is in flight. */
    private final Map<UUID, CompletableFuture<Boolean>> pendingPastes = new ConcurrentHashMap<>();
    /** runId → allocated slot index for release at run end. */
    private final Map<UUID, Integer> runSlots = new ConcurrentHashMap<>();
    private final Set<Integer> usedSlots = ConcurrentHashMap.newKeySet();

    public ArenaSchematicService(AegisGuard plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "arena-schematics");
    }

    public File folder() { return folder; }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("arena.schematics.enabled", true);
    }

    /** WE or FAWE present — same detection the build-backup service uses. */
    public boolean worldEditAvailable() {
        try {
            return plugin.getSnapshotManager() != null
                    && plugin.getSnapshotManager().buildBackup() != null
                    && plugin.getSnapshotManager().buildBackup().integrationInfo().compatible();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Sanitize + resolve a schematic name inside the arena-schematics folder. */
    public File schematicFile(String name) {
        if (name == null || name.isBlank()) return null;
        String clean = name.trim();
        if (!clean.toLowerCase(java.util.Locale.ROOT).endsWith(".schem")) clean += ".schem";
        if (clean.contains("..") || clean.contains("/") || clean.contains("\\")) return null;
        return new File(folder, clean);
    }

    /**
     * Capture {@code world[min..max]} into {@code fileName} and stamp the
     * floor's capture geometry so instance spawns can be re-based later.
     * The region work runs on the owning region thread; the file write is async.
     */
    public CompletableFuture<Boolean> captureFloor(ArenaFloor floor, String fileName,
                                                   org.bukkit.World world,
                                                   int minX, int minY, int minZ,
                                                   int maxX, int maxY, int maxZ) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        if (floor == null || world == null || !isEnabled() || !worldEditAvailable()) {
            result.complete(false);
            return result;
        }
        File dest = schematicFile(fileName);
        if (dest == null) {
            result.complete(false);
            return result;
        }
        int nMinX = Math.min(minX, maxX), nMaxX = Math.max(minX, maxX);
        int nMinY = Math.min(minY, maxY), nMaxY = Math.max(minY, maxY);
        int nMinZ = Math.min(minZ, maxZ), nMaxZ = Math.max(minZ, maxZ);
        long volume = (long) (nMaxX - nMinX + 1) * (nMaxY - nMinY + 1) * (nMaxZ - nMinZ + 1);
        long maxBlocks = plugin.getConfig().getLong("arena.schematics.max_blocks", 1_500_000L);
        if (volume <= 0 || volume > maxBlocks) {
            result.complete(false);
            return result;
        }
        Location anchor = new Location(world, nMinX, nMinY, nMinZ);
        var dispatch = plugin.scheduler().runAt(anchor, () -> {
            try {
                World weWorld = BukkitAdapter.adapt(world);
                CuboidRegion region = new CuboidRegion(
                        weWorld,
                        BlockVector3.at(nMinX, nMinY, nMinZ),
                        BlockVector3.at(nMaxX, nMaxY, nMaxZ));
                BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
                clipboard.setOrigin(BlockVector3.at(nMinX, nMinY, nMinZ));
                try (EditSession session = WorldEdit.getInstance().newEditSession(weWorld)) {
                    ForwardExtentCopy copy = new ForwardExtentCopy(
                            session, region, clipboard, region.getMinimumPoint());
                    copy.setCopyingEntities(false);
                    Operations.complete(copy);
                }
                plugin.scheduler().runAsync(() -> {
                    boolean written = writeClipboard(clipboard, dest);
                    if (written) {
                        floor.setSchematic(dest.getName());
                        floor.setCaptureBounds(world.getName(),
                                nMinX, nMinY, nMinZ,
                                nMaxX - nMinX + 1, nMaxY - nMinY + 1, nMaxZ - nMinZ + 1);
                    }
                    result.complete(written);
                });
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING,
                        "[Arena] Schematic capture failed: " + dest.getName(), t);
                result.complete(false);
            }
        });
        if (!dispatch.accepted()) result.complete(false);
        return result;
    }

    /**
     * Allocate an instance slot for {@code run} and start the paste. The run's
     * instance origin is set synchronously so spawn re-basing works
     * immediately; the returned future completes when the copy is durable.
     * Returns null when instancing can't run (no WE, bad file, no slots).
     */
    public CompletableFuture<Boolean> prepareInstance(ArenaRun run, ArenaFloor floor) {
        if (run == null || floor == null || !floor.isInstanced()
                || !isEnabled() || !worldEditAvailable()) return null;
        File file = schematicFile(floor.getSchematic());
        if (file == null || !file.isFile()) return null;
        org.bukkit.World world = instanceWorld(floor);
        if (world == null) return null;
        int slot = allocateSlot();
        if (slot < 0) return null;

        Location origin = slotOrigin(world, slot);
        run.setInstanceOrigin(origin);
        runSlots.put(run.getRunId(), slot);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingPastes.put(run.getRunId(), future);

        plugin.scheduler().runAsync(() -> {
            Clipboard clipboard;
            try {
                clipboard = readClipboard(file);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING,
                        "[Arena] Could not read schematic " + file.getName(), t);
                releaseSlot(run.getRunId());
                future.complete(false);
                return;
            }
            var region = plugin.scheduler().runAt(origin, () -> {
                boolean ok = pasteClipboard(world, clipboard, origin);
                if (!ok) releaseSlot(run.getRunId());
                future.complete(ok);
            });
            if (!region.accepted()) {
                releaseSlot(run.getRunId());
                future.complete(false);
            }
        });
        future.whenComplete((ok, err) -> pendingPastes.remove(run.getRunId()));
        return future;
    }

    /**
     * Re-base an absolute map location into this run's pasted instance:
     * {@code instanceOrigin + (absolute - captureMin)}, in the instance world.
     * Non-instanced runs (or unschematized floors) get the absolute location.
     */
    public Location instanced(ArenaRun run, Location absolute) {
        if (run == null || absolute == null) return absolute;
        Location origin = run.getInstanceOrigin();
        ArenaFloor floor = run.getFloor();
        if (origin == null || origin.getWorld() == null
                || floor == null || floor.getCaptureWorld() == null) return absolute;
        Location rebased = absolute.clone();
        rebased.setWorld(origin.getWorld());
        rebased.setX(origin.getX() + (absolute.getX() - floor.getCaptureMinX()));
        rebased.setY(origin.getY() + (absolute.getY() - floor.getCaptureMinY()));
        rebased.setZ(origin.getZ() + (absolute.getZ() - floor.getCaptureMinZ()));
        return rebased;
    }

    /**
     * Run finished — wipe the pasted cuboid to air and free the slot.
     * Fire-and-forget on the owning region; safe to call twice.
     */
    public void releaseInstance(ArenaRun run) {
        if (run == null) return;
        Integer slot = runSlots.remove(run.getRunId());
        pendingPastes.remove(run.getRunId());
        Location origin = run.getInstanceOrigin();
        ArenaFloor floor = run.getFloor();
        run.setInstanceOrigin(null);
        if (slot == null || origin == null || origin.getWorld() == null || !isEnabled()) {
            if (slot != null) usedSlots.remove(slot);
            return;
        }
        int sizeX = floor != null ? floor.getCaptureSizeX() : 0;
        int sizeY = floor != null ? floor.getCaptureSizeY() : 0;
        int sizeZ = floor != null ? floor.getCaptureSizeZ() : 0;
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || !worldEditAvailable()) {
            usedSlots.remove(slot);
            return;
        }
        org.bukkit.World world = origin.getWorld();
        CuboidRegion wipe = new CuboidRegion(
                BukkitAdapter.adapt(world),
                BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ()),
                BlockVector3.at(origin.getBlockX() + sizeX - 1,
                        origin.getBlockY() + sizeY - 1, origin.getBlockZ() + sizeZ - 1));
        plugin.scheduler().runAt(origin, () -> {
            try {
                if (worldEditAvailable()) {
                    try (EditSession session =
                                 WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                        session.setBlocks(wipe, BlockTypes.AIR.getDefaultState());
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING,
                        "[Arena] Instance wipe failed at " + origin, t);
            } finally {
                usedSlots.remove(slot);
            }
        });
    }

    /**
     * Region size of a schematic file ({x,y,z extents}), read async for
     * {@code map setorigin} — hand-built schems carry their own dimensions.
     */
    public CompletableFuture<int[]> schematicSizeAsync(String name) {
        CompletableFuture<int[]> result = new CompletableFuture<>();
        File file = schematicFile(name);
        if (file == null || !file.isFile()) {
            result.complete(null);
            return result;
        }
        var dispatch = plugin.scheduler().runAsync(() -> {
            try {
                Clipboard clip = readClipboard(file);
                BlockVector3 dim = clip.getRegion().getMaximumPoint()
                        .subtract(clip.getRegion().getMinimumPoint()).add(1, 1, 1);
                result.complete(new int[] { dim.getX(), dim.getY(), dim.getZ() });
            } catch (Throwable t) {
                result.complete(null);
            }
        });
        if (!dispatch.accepted()) result.complete(null);
        return result;
    }

    /** Sweep on shutdown: free slots without wiping (world save handles state). */
    public void shutdown() {
        pendingPastes.clear();
        runSlots.clear();
        usedSlots.clear();
    }

    public int usedSlots() { return usedSlots.size(); }

    public int maxInstances() {
        return Math.max(1, plugin.getConfig().getInt("arena.schematics.max_instances", 8));
    }

    // ------------------------------------------------------------------ slots

    private int allocateSlot() {
        for (int i = 0; i < maxInstances(); i++) {
            if (usedSlots.add(i)) return i;
        }
        return -1;
    }

    private void releaseSlot(UUID runId) {
        Integer slot = runSlots.remove(runId);
        if (slot != null) usedSlots.remove(slot);
    }

    private Location slotOrigin(org.bukkit.World world, int slot) {
        int spacing = Math.max(64,
                plugin.getConfig().getInt("arena.schematics.instance_spacing", 512));
        int y = plugin.getConfig().getInt("arena.schematics.paste_y", 80);
        return new Location(world, (long) spacing * (slot + 1), y, 0);
    }

    /**
     * The world instances paste into: {@code arena.schematics.world} when set,
     * else the dedicated arena world when enabled, else the capture world.
     */
    private org.bukkit.World instanceWorld(ArenaFloor floor) {
        String configured = plugin.getConfig().getString("arena.schematics.world", "");
        if (configured != null && !configured.isBlank()) {
            org.bukkit.World w = Bukkit.getWorld(configured.trim());
            if (w != null) return w;
        }
        if (plugin.arenaWorld() != null && plugin.arenaWorld().isEnabled()) {
            org.bukkit.World w = plugin.arenaWorld().ensureWorld();
            if (w != null) return w;
        }
        return floor.getCaptureWorld() == null ? null : Bukkit.getWorld(floor.getCaptureWorld());
    }

    // ------------------------------------------------------------------ WE I/O

    private boolean writeClipboard(Clipboard clipboard, File dest) {
        File temp = new File(folder, dest.getName() + ".tmp");
        try {
            if (!folder.exists() && !folder.mkdirs()) return false;
            try (ClipboardWriter writer =
                         BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(new FileOutputStream(temp))) {
                writer.write(clipboard);
            }
            java.nio.file.Files.move(temp.toPath(), dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "[Arena] Could not write schematic " + dest.getName(), t);
            try { java.nio.file.Files.deleteIfExists(temp.toPath()); } catch (Exception ignored) {}
            return false;
        }
    }

    private Clipboard readClipboard(File file) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) format = BuiltInClipboardFormat.SPONGE_SCHEMATIC;
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            return reader.read();
        }
    }

    private boolean pasteClipboard(org.bukkit.World world, Clipboard clipboard, Location minCorner) {
        try {
            // createPaste().to() places the clipboard ORIGIN — shift it so the
            // clipboard minimum corner lands on our slot origin exactly.
            BlockVector3 to = BlockVector3
                    .at(minCorner.getBlockX(), minCorner.getBlockY(), minCorner.getBlockZ())
                    .add(clipboard.getOrigin().subtract(clipboard.getMinimumPoint()));
            World weWorld = BukkitAdapter.adapt(world);
            try (EditSession session = WorldEdit.getInstance().newEditSession(weWorld)) {
                Operation paste = new ClipboardHolder(clipboard)
                        .createPaste(session)
                        .to(to)
                        .copyEntities(false)
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(paste);
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[Arena] Instance paste failed", t);
            return false;
        }
    }

    /**
     * Plot XZ bounds for a {@code map save} capture. Plots are full-height
     * columns, so the command also takes an optional y-range — the world
     * height is the default.
     */
    public static int[] plotBounds(Plot plot, org.bukkit.World world, int minY, int maxY) {
        if (plot == null || world == null) return null;
        int lo = minY == Integer.MIN_VALUE ? world.getMinHeight() : Math.max(minY, world.getMinHeight());
        int hi = maxY == Integer.MAX_VALUE ? world.getMaxHeight() : Math.min(maxY, world.getMaxHeight());
        return new int[] {
                Math.min(plot.getX1(), plot.getX2()), lo, Math.min(plot.getZ1(), plot.getZ2()),
                Math.max(plot.getX1(), plot.getX2()), hi, Math.max(plot.getZ1(), plot.getZ2())
        };
    }
}
