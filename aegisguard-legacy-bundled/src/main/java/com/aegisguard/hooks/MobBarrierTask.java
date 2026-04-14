package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * MobBarrierTask
 *
 * Periodically enforces mob barrier rules for plots.
 *
 * Semantics:
 *  - Uses ProtectionManager + Plot flags as the single source of truth.
 *  - Server Zones: always mob-protected (barrier always active).
 *  - Normal Plots:
 *      - mobs flag ON  (green) => hostiles inside the plot are removed.
 *      - mobs flag OFF (red)   => vanilla behavior; barrier does nothing,
 *                                mobs may walk in / stay in the plot.
 *
 * NOTE:
 *  - Safe Zone alone does NOT force mob barrier here anymore.
 *    That means you can have Safe Zone ON (for structural / environment safety)
 *    but Mob Protection OFF, and mobs will be allowed to exist in the plot.
 *  - If you want safe zones to also always mob-clean, that can be made a
 *    config toggle later (e.g. mob_barrier.safe_zone_forces_mobs: true).
 */
public class MobBarrierTask implements Runnable {

    private final AegisGuard plugin;

    public MobBarrierTask(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.cfg().raw().getBoolean("mob_barrier.enabled", false)) {
            return;
        }

        List<Plot> plotsToCheck = new ArrayList<>();

        // Collect all plots that should be "mob-cleaned"
        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null) continue;

            boolean isServer = plot.isServerZone();

            // Use the same semantics as ProtectionManager:
            // mobs flag: true = mob protection ON (green)
            boolean mobsProtected = plugin.protection().isFlagEnabled(plot, "mobs");

            // If this is not a server plot and mob protection is OFF,
            // then we skip it entirely (vanilla behavior).
            if (!isServer && !mobsProtected) {
                continue;
            }

            plotsToCheck.add(plot);
        }

        if (plotsToCheck.isEmpty()) {
            return;
        }

        // Process each plot
        for (Plot plot : plotsToCheck) {
            processPlot(plot);
        }
    }

    private void processPlot(Plot plot) {
        World world = Bukkit.getWorld(plot.getWorld());
        if (world == null) return;

        int minChunkX = plot.getX1() >> 4;
        int minChunkZ = plot.getZ1() >> 4;
        int maxChunkX = plot.getX2() >> 4;
        int maxChunkZ = plot.getZ2() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                final int finalCx = cx;
                final int finalCz = cz;

                // Folia-safe scheduling per chunk region
                if (plugin.isFolia()) {
                    if (!world.isChunkLoaded(cx, cz)) continue;

                    if (!runRegionTask(world, finalCx, finalCz, () -> checkChunkForMobs(world, plot, finalCx, finalCz))) {
                        plugin.runMainGlobal(() -> checkChunkForMobs(world, plot, finalCx, finalCz));
                    }
                } else {
                    // Non-Folia: schedule chunk work on main thread
                    plugin.runMainGlobal(() -> checkChunkForMobs(world, plot, finalCx, finalCz));
                }
            }
        }
    }

    private void checkChunkForMobs(World world, Plot plot, int cx, int cz) {
        if (!world.isChunkLoaded(cx, cz)) return;

        // Re-evaluate current protection state at execution time.
        // This way, if the player toggled mob protection OFF since
        // the run() scan, we immediately stop despawning mobs.
        boolean isServer = plot.isServerZone();
        boolean mobsProtected = plugin.protection().isFlagEnabled(plot, "mobs");
        if (!isServer && !mobsProtected) {
            // Mob protection currently OFF for this plot -> vanilla
            return;
        }

        try {
            Chunk chunk = world.getChunkAt(cx, cz);
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Monster || entity instanceof Slime || entity instanceof Phantom) {
                    if (plot.isInside(entity.getLocation())) {
                        removeMob(entity);
                    }
                }
            }
        } catch (Exception e) {
            // Log error but don't crash the task
            plugin.getLogger().warning(
                    "Error checking chunk at " + cx + ", " + cz +
                    " in world " + world.getName() + ": " + e.getMessage()
            );
        }
    }

    private void removeMob(Entity entity) {
        if (plugin.isFolia()) {
            // On Folia, use entity scheduler (region-thread safe)
            if (runEntityTask(entity, () -> {
                if (entity.isValid()) {
                    entity.remove();
                    spawnRemovalParticle(entity);
                }
            })) {
                return;
            }
        } else {
            // Non-Folia: always go to main thread
            plugin.runMain(null, () -> {
                if (entity.isValid()) {
                    entity.remove();
                    spawnRemovalParticle(entity);
                }
            });
        }
    }

    private void spawnRemovalParticle(Entity entity) {
        if (plugin.cfg().raw().getBoolean("mob_barrier.remove_particles", true)) {
            entity.getWorld().spawnParticle(
                    Particle.SMOKE_NORMAL,
                    entity.getLocation().add(0, 1, 0),
                    5,
                    0.1, 0.1, 0.1,
                    0.05
            );
        }
    }

    private boolean runRegionTask(World world, int chunkX, int chunkZ, Runnable task) {
        try {
            Method getRegionScheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler");
            Object scheduler = getRegionScheduler.invoke(Bukkit.getServer());

            Method run = scheduler.getClass().getMethod(
                    "run",
                    Plugin.class,
                    World.class,
                    int.class,
                    int.class,
                    Consumer.class
            );
            run.invoke(scheduler, plugin, world, chunkX, chunkZ, (Consumer<Object>) scheduledTask -> task.run());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean runEntityTask(Entity entity, Runnable task) {
        try {
            Method getScheduler = entity.getClass().getMethod("getScheduler");
            Object scheduler = getScheduler.invoke(entity);

            Method run = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            run.invoke(scheduler, plugin, (Consumer<Object>) scheduledTask -> task.run(), null);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
