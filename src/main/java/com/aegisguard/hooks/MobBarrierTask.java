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

import java.util.ArrayList;
import java.util.List;

public class MobBarrierTask implements Runnable {

    private final AegisGuard plugin;

    public MobBarrierTask(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.cfg().raw().getBoolean("mob_barrier.enabled", false)) return;

        List<Plot> plotsToCheck = new ArrayList<>();
        
        // First, collect all plots that need checking
        for (Plot plot : plugin.store().getAllPlots()) {
            boolean isServer = plot.isServerZone();
            boolean isSafeZone = plugin.protection().isSafeZoneEnabled(plot); 
            boolean noMobs = !plot.getFlag("mobs", true);

            if (!isServer && !isSafeZone && !noMobs) {
                continue; 
            }
            plotsToCheck.add(plot);
        }
        
        if (plotsToCheck.isEmpty()) return;
        
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
                
                if (plugin.isFolia()) {
                    // On Folia, check if chunk is loaded before scheduling
                    if (!world.isChunkLoaded(cx, cz)) continue;
                    
                    // Schedule chunk check on its region thread
                    Bukkit.getRegionScheduler().run(plugin, world, finalCx, finalCz, scheduledTask -> {
                        checkChunkForMobs(world, plot, finalCx, finalCz);
                    });
                } else {
                    // Non-Folia: check directly
                    checkChunkForMobs(world, plot, cx, cz);
                }
            }
        }
    }
    
    private void checkChunkForMobs(World world, Plot plot, int cx, int cz) {
        if (!world.isChunkLoaded(cx, cz)) return;
        
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
            plugin.getLogger().warning("Error checking chunk at " + cx + ", " + cz + " in world " + world.getName() + ": " + e.getMessage());
        }
    }
    
    private void removeMob(Entity entity) {
        if (plugin.isFolia()) {
            // On Folia, use entity scheduler
            entity.getScheduler().run(plugin, scheduledTask -> {
                if (entity.isValid()) {
                    entity.remove();
                    spawnRemovalParticle(entity);
                }
            }, null);
        } else {
            // Non-Folia: use main thread
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
            entity.getWorld().spawnParticle(Particle.SMOKE_NORMAL, entity.getLocation().add(0, 1, 0), 5, 0.1, 0.1, 0.1, 0.05);
        }
    }
}
