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
        if (!plugin.cfg().raw().getBoolean("mob_barrier.enabled", false)) return;

        for (Plot plot : plugin.store().getAllPlots()) {
            boolean isServer = plot.isServerZone();
            boolean isSafeZone = plugin.protection().isSafeZoneEnabled(plot); 
            boolean noMobs = !plot.getFlag("mobs", true);

            if (!isServer && !isSafeZone && !noMobs) {
                continue; 
            }

            World world = Bukkit.getWorld(plot.getWorld());
            if (world == null) continue;

            int minChunkX = plot.getX1() >> 4;
            int minChunkZ = plot.getZ1() >> 4;
            int maxChunkX = plot.getX2() >> 4;
            int maxChunkZ = plot.getZ2() >> 4;

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    if (!world.isChunkLoaded(cx, cz)) continue;
                    
                    Chunk chunk = world.getChunkAt(cx, cz);
                    for (Entity entity : chunk.getEntities()) {
                        if (entity instanceof Monster || entity instanceof Slime || entity instanceof Phantom) {
                            if (plot.isInside(entity.getLocation())) {
                                
                                // --- FIX: Schedule removal on Main Thread ---
                                plugin.runMain(null, () -> {
                                    if (entity.isValid()) { // Check if still alive
                                        entity.remove();
                                        if (plugin.cfg().raw().getBoolean("mob_barrier.remove_particles", true)) {
                                            world.spawnParticle(Particle.SMOKE_NORMAL, entity.getLocation().add(0, 1, 0), 5, 0.1, 0.1, 0.1, 0.05);
                                        }
                                    }
                                });
                                
                            }
                        }
                    }
                }
            }
        }
    }
}
