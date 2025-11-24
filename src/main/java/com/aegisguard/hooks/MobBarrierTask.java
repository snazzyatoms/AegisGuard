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

public class MobBarrierTask implements Runnable {

    private final AegisGuard plugin;

    public MobBarrierTask(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // --- FIX: Check if feature is enabled in config first! ---
        if (!plugin.cfg().raw().getBoolean("mob_barrier.enabled", false)) {
            return;
        }

        // Iterate over all known plots
        for (Plot plot : plugin.store().getAllPlots()) {
            boolean isServer = plot.isServerZone();
            boolean noMobs = !plot.getFlag("mobs", true); // true = mobs allowed, false = denied
            
            // We only care about Server Zones, Safe Zones, or plots where mobs are explicitly denied
            if (!isServer && !plugin.protection().isSafeZoneEnabled(plot) && !noMobs) {
                continue; 
            }

            World world = Bukkit.getWorld(plot.getWorld());
            if (world == null) continue;

            // Scan the entities in the plot's general area
            int minChunkX = plot.getX1() >> 4;
            int minChunkZ = plot.getZ1() >> 4;
            int maxChunkX = plot.getX2() >> 4;
            int maxChunkZ = plot.getZ2() >> 4;

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    if (!world.isChunkLoaded(cx, cz)) continue;
                    
                    for (Entity entity : world.getChunkAt(cx, cz).getEntities()) {
                        if (entity instanceof Monster || entity instanceof Slime || entity instanceof Phantom) {
                            if (plot.isInside(entity.getLocation())) {
                                entity.remove();
                                
                                // Optional: Check if particles are enabled in config
                                if (plugin.cfg().raw().getBoolean("mob_barrier.remove_particles", true)) {
                                    world.spawnParticle(Particle.SMOKE_NORMAL, entity.getLocation().add(0, 1, 0), 5, 0.1, 0.1, 0.1, 0.05);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
