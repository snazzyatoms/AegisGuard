package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import org.bukkit.Chunk;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.entity.Slime;
import org.bukkit.event.entity.EntityType;

public class MobBarrierTask implements Runnable {

    private final AegisGuard plugin;

    public MobBarrierTask(AegisGuard plugin) {
        this.plugin = plugin;
    }
    
    private boolean isEstateSafe(Estate estate) {
        if (estate == null) return false;
        if (estate.isServerEstate()) return true;
        return estate.getFlag("safe_zone") || !estate.getFlag("mobs");
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("mob_barrier.enabled", false)) return;

        // Execute the main logic on the main thread or global region scheduler (handled by plugin.runMainGlobal)
        plugin.runMainGlobal(() -> {
            
            for (Estate estate : plugin.getEstateManager().getAllEstates()) {
                World world = estate.getWorld();
                if (world == null) continue;

                // 1. Check Permissions/Flags
                // If the estate is not safe (mobs allowed), skip the cleanup task.
                if (!isEstateSafe(estate)) continue;

                // 2. Calculate Chunk Boundaries
                int minChunkX = estate.getRegion().getLowerNE().getBlockX() >> 4;
                int minChunkZ = estate.getRegion().getLowerNE().getBlockZ() >> 4;
                int maxChunkX = estate.getRegion().getUpperSW().getBlockX() >> 4;
                int maxChunkZ = estate.getRegion().getUpperSW().getBlockZ() >> 4;

                // 3. Iterate Chunks
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                        
                        if (!world.isChunkLoaded(cx, cz)) continue;
                        
                        Chunk chunk = world.getChunkAt(cx, cz);
                        
                        // 4. Scan Entities
                        for (Entity entity : chunk.getEntities()) {
                            
                            // Optimization: Skip players, passive mobs, and utility entities
                            if (!(entity instanceof LivingEntity) || entity instanceof Player) continue;
                            if (entity instanceof ArmorStand || entity instanceof ItemFrame) continue;

                            // Handle slimes to prevent perpetual spawning/removal loop
                            if (entity.getType() == EntityType.SLIME || entity.getType() == EntityType.MAGMA_CUBE) {
                                if (((Slime) entity).getSize() <= 1) continue;
                            }

                            if (isHostile(entity)) {
                                // 5. Precision Check: Is the mob actually INSIDE the boundary?
                                if (estate.getRegion().contains(entity.getLocation())) {
                                    removeMob(entity); // 6. TERMINATE
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private void removeMob(Entity entity) {
        if (!entity.isValid()) return;

        // Effects
        if (plugin.getConfig().getBoolean("mob_barrier.remove_particles", true)) {
            entity.getWorld().spawnParticle(
                Particle.SMOKE_NORMAL, 
                entity.getLocation().add(0, 1, 0), 
                5, 0.1, 0.1, 0.1, 0.05
            );
        }
        
        // Removal
        entity.remove();
    }
    
    // Hostile check is identical to ProtectionManager's check for consistency
    private boolean isHostile(Entity e) {
        if (e == null) return false;
        if (e instanceof Monster) return true;
        if (e instanceof Slime) return true;
        if (e instanceof Phantom) return true;
        if (e instanceof Shulker) return true;
        if (e instanceof Ghast) return true;
        if (e instanceof Hoglin) return true;
        if (e instanceof EnderDragon || e instanceof Wither) return true;

        String name = e.getType().name();
        return name.contains("WARDEN") || 
               name.contains("BREEZE") || 
               name.contains("BOGGED") || 
               name.contains("CREAKING");
    }
}
