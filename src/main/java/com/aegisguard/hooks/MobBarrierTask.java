package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import org.bukkit.Chunk;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.*;

public class MobBarrierTask implements Runnable {

    private final AegisGuard plugin;

    public MobBarrierTask(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (!plugin.getConfig().getBoolean("mob_barrier.enabled", false)) return;

        // CRITICAL FIX: Entity access MUST happen on the Main Server Thread.
        // We schedule this logic to run synchronously to prevent server crashes.
        plugin.runMain(null, () -> {
            
            for (Estate estate : plugin.getEstateManager().getAllEstates()) {
                World world = estate.getWorld();
                if (world == null) continue;

                // 1. Check Permissions/Flags
                // If this is a Safe Zone, OR if "mobs" flag is disabled -> CLEANUP TIME
                boolean isSafeZone = estate.getFlag("safe_zone");
                boolean mobsAllowed = estate.getFlag("mobs");

                // If mobs are allowed AND it's not a safe zone, we skip this estate
                if (mobsAllowed && !isSafeZone) continue;

                // 2. Calculate Chunk Boundaries
                // We convert block coordinates to chunk coordinates (divide by 16)
                int minChunkX = estate.getRegion().getLowerNE().getBlockX() >> 4;
                int minChunkZ = estate.getRegion().getLowerNE().getBlockZ() >> 4;
                int maxChunkX = estate.getRegion().getUpperSW().getBlockX() >> 4;
                int maxChunkZ = estate.getRegion().getUpperSW().getBlockZ() >> 4;

                // 3. Iterate Chunks
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                        
                        // PERFORMANCE: Never load a chunk just to check for mobs. 
                        // Only check currently active chunks.
                        if (!world.isChunkLoaded(cx, cz)) continue;
                        
                        Chunk chunk = world.getChunkAt(cx, cz);
                        
                        // 4. Scan Entities
                        // This array covers everything: Ground (Zombies), Sky (Phantoms), Bosses (Wither)
                        for (Entity entity : chunk.getEntities()) {
                            
                            // Optimization: Skip players and non-living immediately
                            if (!(entity instanceof LivingEntity)) continue;
                            if (entity instanceof Player) continue;

                            if (isHostile(entity)) {
                                // 5. Precision Check: Is the mob actually INSIDE the boundary?
                                // If your estate goes from Bedrock to Sky (Y: -64 to 320), this catches Phantoms too.
                                if (estate.getRegion().contains(entity.getLocation())) {
                                    
                                    // 6. TERMINATE
                                    removeMob(entity);
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    private void removeMob(Entity entity) {
        if (!entity.isValid()) return; // Already dead

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

    /**
     * Determines if an entity is hostile.
     * Covers Ground (Zombies, Creepers), Sky (Phantoms, Ghasts), and Bosses.
     */
    private boolean isHostile(Entity e) {
        if (e == null) return false;

        // Standard Categorization
        if (e instanceof Monster) return true; // Zombies, Skeles, Creepers, Spiders
        if (e instanceof Slime) return true;   // Slimes, Magma Cubes
        if (e instanceof Phantom) return true; // Sky Attack
        if (e instanceof Shulker) return true;
        if (e instanceof Ghast) return true;   // Sky Attack
        if (e instanceof Hoglin) return true;
        if (e instanceof EnderDragon || e instanceof Wither) return true;

        // String-Based Checks for Future Mobs (1.21+ support without crashing on old versions)
        String name = e.getType().name();
        if (name.contains("WARDEN")) return true;
        if (name.contains("BREEZE")) return true;
        if (name.contains("BOGGED")) return true;
        if (name.contains("CREAKING")) return true;

        return false;
    }
}
