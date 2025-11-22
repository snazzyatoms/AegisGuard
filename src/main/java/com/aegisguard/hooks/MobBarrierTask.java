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

import java.util.Collection;

/**
 * MobBarrierTask
 * - Scans "Safe Zone" and "Server Zone" plots.
 * - Vaporizes any hostile mobs that wander inside.
 * - More efficient than listening to EntityMoveEvent (which lags servers).
 */
public class MobBarrierTask implements Runnable {

    private final AegisGuard plugin;

    public MobBarrierTask(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Iterate over all known plots
        for (Plot plot : plugin.store().getAllPlots()) {
            // We only care about Server Zones (Spawn/Market) or Plots with "Safe Zone" enabled
            // OR plots where the user explicitly denied "mobs" flag
            boolean isServer = plot.isServerZone();
            boolean noMobs = !plot.getFlag("mobs", true); // true = mobs allowed, false = denied

            if (!isServer && !plugin.protection().isSafeZoneEnabled(plot) && !noMobs) {
                continue; // Skip normal plots that allow mobs
            }

            World world = Bukkit.getWorld(plot.getWorld());
            if (world == null) continue;

            // Scan the entities in the plot's general area
            // Optimization: We scan the chunks the plot touches
            int minChunkX = plot.getX1() >> 4;
            int minChunkZ = plot.getZ1() >> 4;
            int maxChunkX = plot.getX2() >> 4;
            int maxChunkZ = plot.getZ2() >> 4;

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    if (!world.isChunkLoaded(cx, cz)) continue;
                    
                    Chunk chunk = world.getChunkAt(cx, cz);
                    for (Entity entity : chunk.getEntities()) {
                        // Target Hostile Mobs (Monsters, Slimes, Phantoms)
                        if (entity instanceof Monster || entity instanceof Slime || entity instanceof Phantom) {
                            // Precise check: Is the mob ACTUALLY inside the plot boundary?
                            if (plot.isInside(entity.getLocation())) {
                                // VAPORIZE IT
                                entity.remove();
                                world.spawnParticle(Particle.SMOKE_NORMAL, entity.getLocation().add(0, 1, 0), 5, 0.1, 0.1, 0.1, 0.05);
                            }
                        }
                    }
                }
            }
        }
    }
}
