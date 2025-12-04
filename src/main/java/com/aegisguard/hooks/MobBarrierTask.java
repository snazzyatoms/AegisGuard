package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Wither;

public class MobBarrierTask implements Runnable {

    private final AegisGuard plugin;

    public MobBarrierTask(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("mob_barrier.enabled", false)) return;

        for (Estate estate : plugin.getEstateManager().getAllEstates()) {
            // Flags check
            // If mobs are allowed, skip this estate (save performance)
            if (estate.getFlag("mobs")) continue;

            // Check Safe Zone override
            if (estate.getFlag("safe_zone")) {
                // Safe zones always repel mobs
            } else {
                // Standard estate logic (already checked 'mobs' flag above)
            }

            World world = estate.getWorld();
            if (world == null) continue;

            int minChunkX = estate.getRegion().getLowerNE().getBlockX() >> 4;
            int minChunkZ = estate.getRegion().getLowerNE().getBlockZ() >> 4;
            int maxChunkX = estate.getRegion().getUpperSW().getBlockX() >> 4;
            int maxChunkZ = estate.getRegion().getUpperSW().getBlockZ() >> 4;

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    if (!world.isChunkLoaded(cx, cz)) continue;
                    
                    Chunk chunk = world.getChunkAt(cx, cz);
                    for (Entity entity : chunk.getEntities()) {
                        if (isHostile(entity)) {
                            // Check if inside the Cuboid
                            if (estate.getRegion().contains(entity.getLocation())) {
                                
                                // --- REMOVAL LOGIC ---
                                plugin.runMain(null, () -> {
                                    if (entity.isValid()) { // Check if still alive
                                        entity.remove();
                                        if (plugin.getConfig().getBoolean("mob_barrier.remove_particles", true)) {
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

    // Reusing the robust check from ProtectionManager
    private boolean isHostile(Entity e) {
        if (e == null) return false;

        if (e instanceof Monster) return true;
        if (e instanceof Slime) return true;
        if (e instanceof Phantom) return true;
        if (e instanceof Shulker) return true;
        if (e instanceof Ghast) return true;
        if (e instanceof Hoglin) return true;
        if (e instanceof EnderDragon || e instanceof Wither) return true;

        // String-Based Checks for Future Mobs (1.21+)
        String name = e.getType().name();
        if (name.contains("WARDEN")) return true;
        if (name.contains("BREEZE")) return true;
        if (name.contains("BOGGED")) return true;
        if (name.contains("CREAKING")) return true;

        return false;
    }
}
