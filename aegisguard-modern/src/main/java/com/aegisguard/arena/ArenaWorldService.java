package com.aegisguard.arena;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.generator.ChunkGenerator;

import java.util.Locale;
import java.util.UUID;

/**
 * Owns the optional dedicated arena world ({@code arena.world.*}).
 *
 * <p>When enabled, the plugin creates/loads a dedicated world at startup —
 * VOID by default so staff can lay out arena floors without terrain. Arena
 * floors are server-zone plots, which inherit the full protection flag set, so
 * mobs and players cannot grief the floor mid-run; between runs we only need
 * to sweep stray entities/items, not restore blocks.</p>
 *
 * <p>World creation happens during onEnable (single startup region), which is
 * safe on both Paper and Folia.</p>
 */
public final class ArenaWorldService {

    private final AegisGuard plugin;

    public ArenaWorldService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("arena.world.enabled", false);
    }

    public String worldName() {
        String name = plugin.getConfig().getString("arena.world.name", "aegis_arena");
        return name == null || name.isBlank() ? "aegis_arena" : name;
    }

    public boolean isArenaWorld(World world) {
        return world != null && world.getName().equalsIgnoreCase(worldName());
    }

    /**
     * Create or load the configured arena world. Called once from onEnable.
     * Missing worlds are created; a world already on disk is just loaded.
     */
    public World ensureWorld() {
        if (!isEnabled()) return null;
        String name = worldName();
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            tune(existing);
            return existing;
        }
        String gen = plugin.getConfig().getString("arena.world.generator", "VOID")
                .toUpperCase(Locale.ROOT);
        try {
            WorldCreator creator = new WorldCreator(name);
            switch (gen) {
                case "FLAT" -> creator.type(WorldType.FLAT).generateStructures(false);
                case "NORMAL" -> { /* vanilla terrain */ }
                default -> creator.generator(new VoidGenerator());
            }
            creator.environment(World.Environment.NORMAL);
            World world = creator.createWorld();
            if (world != null) {
                tune(world);
                plugin.getLogger().info("[Arena] Dedicated arena world ready: " + name
                        + " (" + gen + ")");
            }
            return world;
        } catch (Throwable t) {
            plugin.getLogger().warning("[Arena] Could not create arena world '" + name
                    + "': " + t.getMessage());
            return null;
        }
    }

    private void tune(World world) {
        try {
            world.setKeepSpawnInMemory(
                    plugin.getConfig().getBoolean("arena.world.keep_spawn_loaded", false));
        } catch (Throwable ignored) { }
    }

    /**
     * Auto-provision a server-zone plot around the player when they run
     * {@code arena create} standing in the arena world. Returns the zone plot,
     * or null when provisioning doesn't apply (not the arena world, already a
     * plot underfoot, or the claim flow declined).
     */
    public Plot autoZone(Player player) {
        if (!isEnabled() || player == null) return null;
        Location loc = player.getLocation();
        if (loc == null || !isArenaWorld(loc.getWorld())) return null;
        if (plugin.store().getPlotAt(loc) != null) return null;
        int radius = Math.max(4, plugin.getConfig().getInt("arena.world.auto_zone_radius", 24));
        try {
            plugin.selection().setSelectionAround(player, radius);
            plugin.selection().confirmClaim(player, true); // server claim — no charge, zone flags
            return plugin.store().getPlotAt(player.getLocation());
        } catch (Throwable t) {
            plugin.getLogger().warning("[Arena] Auto-zone failed: " + t.getMessage());
            return null;
        }
    }

    /**
     * End-of-run sweep: remove stray non-arena entities inside the run's arena
     * plot — dropped items, XP orbs, projectiles, and anything not tagged to a
     * run (tagged mobs despawn via the normal path). Bounded by
     * {@code arena.world.cleanup_max_entities}; runs on the plot's region on
     * Folia via {@link ArenaScheduler#runAtLocation}.
     */
    public void cleanupFloor(ArenaRun run, ArenaDefinition def) {
        if (!isEnabled() || run == null || def == null) return;
        if (!plugin.getConfig().getBoolean("arena.world.cleanup_on_run_end", true)) return;

        // Instanced run: the combat space is the pasted cuboid, not a plot.
        Location io = run.getInstanceOrigin();
        ArenaFloor instancedFloor = run.getFloor();
        if (io != null && io.getWorld() != null
                && instancedFloor != null && instancedFloor.getCaptureSizeX() > 0) {
            World iw = io.getWorld();
            sweepRegion(run, iw, io.getBlockX(), io.getBlockZ(),
                    io.getBlockX() + instancedFloor.getCaptureSizeX() - 1,
                    io.getBlockZ() + instancedFloor.getCaptureSizeZ() - 1);
            return;
        }

        UUID floorPlotId = run.getFloor() != null && run.getFloor().getFloorPlotId() != null
                ? run.getFloor().getFloorPlotId() : def.getArenaPlotId();
        Plot floor = floorPlotId == null ? null : plugin.store().getPlotById(floorPlotId);
        if (floor == null) return;
        World world = Bukkit.getWorld(floor.getWorld());
        if (world == null || !isArenaWorld(world)) return;

        sweepRegion(run, world,
                Math.min(floor.getX1(), floor.getX2()), Math.min(floor.getZ1(), floor.getZ2()),
                Math.max(floor.getX1(), floor.getX2()), Math.max(floor.getZ1(), floor.getZ2()));
    }

    /**
     * Chunk-iterated entity sweep over a world-space XZ rectangle — items, XP
     * orbs, projectiles, and untagged strays (tagged mobs despawn via the run
     * path). Region-dispatched; never loads chunks on Folia.
     */
    private void sweepRegion(ArenaRun run, World world, int minX, int minZ, int maxX, int maxZ) {
        int max = Math.max(16, plugin.getConfig().getInt("arena.world.cleanup_max_entities", 500));
        Location anchor = new Location(world,
                (minX + maxX) / 2.0, world.getMinHeight() + 1, (minZ + maxZ) / 2.0);
        plugin.arena().scheduler().runAtLocation(anchor, () -> {
            int removed = 0;
            // Per-chunk iteration: on Folia a region thread may only touch its
            // own chunks, and isChunkLoaded keeps us from loading anything.
            for (int cx = minX >> 4; cx <= maxX >> 4 && removed < max; cx++) {
                for (int cz = minZ >> 4; cz <= maxZ >> 4 && removed < max; cz++) {
                    org.bukkit.Chunk chunk;
                    try {
                        if (!world.isChunkLoaded(cx, cz)) continue;
                        chunk = world.getChunkAt(cx, cz);
                    } catch (Throwable t) {
                        continue; // not owned by this region — skip
                    }
                    for (Entity e : chunk.getEntities()) {
                        if (removed >= max) break;
                        if (e == null || e instanceof Player) continue;
                        Location el = e.getLocation();
                        if (el.getBlockX() < minX || el.getBlockX() > maxX
                                || el.getBlockZ() < minZ || el.getBlockZ() > maxZ) continue;
                        // Arena-tagged mobs are despawned by the run path; leave them.
                        if (e instanceof LivingEntity
                                && plugin.arena().keys().readRunId(e.getPersistentDataContainer()) != null) {
                            continue;
                        }
                        if (e instanceof Item || e instanceof ExperienceOrb || e instanceof Projectile
                                || e instanceof LivingEntity) {
                            e.remove();
                            removed++;
                        }
                    }
                }
            }
            if (removed > 0) {
                run.journal("floor_cleanup removed=" + removed);
            }
        });
    }

    /** Empty world generator — every chunk is pure air. */
    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, java.util.Random random,
                                           int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }
    }
}
