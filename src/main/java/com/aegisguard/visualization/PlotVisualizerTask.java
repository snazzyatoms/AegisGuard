package com.yourname.aegisguard.visualization;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * This task runs for a single player, showing them the borders
 * of the estate they are currently standing in.
 * - Updated for v1.3.0 Estate System.
 */
public class PlotVisualizerTask extends BukkitRunnable {

    private final AegisGuard plugin;
    private final Player player;
    private Estate lastEstate = null;
    private Particle particle;
    private Particle.DustOptions dustOptions;

    // --- CROSS-VERSION COMPATIBILITY (1.20.4 vs 1.20.5+) ---
    private static final Particle DUST_PARTICLE_TYPE;
    static {
        Particle p;
        try {
            p = Particle.valueOf("DUST"); // 1.20.5+
        } catch (IllegalArgumentException e) {
            try {
                p = Particle.valueOf("REDSTONE"); // 1.20.4 and older
            } catch (IllegalArgumentException ex) {
                p = Particle.FLAME; // Fallback
            }
        }
        DUST_PARTICLE_TYPE = p;
    }

    public PlotVisualizerTask(AegisGuard plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        updateParticle(null); // Initialize default
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            this.cancel();
            return;
        }

        // v1.3.0: Use EstateManager
        Estate currentEstate = plugin.getEstateManager().getEstateAt(player.getLocation());

        if (currentEstate != null) {
            if (!currentEstate.equals(lastEstate)) {
                lastEstate = currentEstate;
            }
            // Check for custom cosmetic particle (stored in Estate object)
            // Make sure you added getBorderParticle() to Estate.java!
            updateParticle(currentEstate.getDescription()); // Using Description field as placeholder if particle field missing
            // Ideally: updateParticle(currentEstate.getBorderParticle());
            
            drawEstateBorders(currentEstate);
        } else {
            lastEstate = null;
        }
    }

    private void updateParticle(String cosmeticParticleName) {
        String particleName = cosmeticParticleName != null ? cosmeticParticleName 
                : plugin.getConfig().getString("visuals.particles.default_particle", "FLAME");
        
        try {
            this.particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.particle = Particle.FLAME;
        }

        if (this.particle == DUST_PARTICLE_TYPE) {
            this.dustOptions = new Particle.DustOptions(Color.AQUA, 1.0F);
        } else {
            this.dustOptions = null;
        }
    }

    private void drawEstateBorders(Estate estate) {
        if (estate.getWorld() == null || !player.getWorld().equals(estate.getWorld())) return;

        int y = player.getLocation().getBlockY();
        int step = 2; // Density

        // Use Cuboid Helper
        int x1 = estate.getRegion().getLowerNE().getBlockX();
        int z1 = estate.getRegion().getLowerNE().getBlockZ();
        int x2 = estate.getRegion().getUpperSW().getBlockX();
        int z2 = estate.getRegion().getUpperSW().getBlockZ();

        // Draw Rectangle
        for (int x = x1; x <= x2; x += step) {
            spawnParticle(new Location(estate.getWorld(), x, y, z1));
            spawnParticle(new Location(estate.getWorld(), x, y, z2));
        }
        for (int z = z1; z <= z2; z += step) {
            spawnParticle(new Location(estate.getWorld(), x1, y, z));
            spawnParticle(new Location(estate.getWorld(), x2, y, z));
        }
    }

    private void spawnParticle(Location loc) {
        if (dustOptions != null) {
            player.spawnParticle(DUST_PARTICLE_TYPE, loc, 1, dustOptions);
        } else {
            player.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
        }
    }
}
