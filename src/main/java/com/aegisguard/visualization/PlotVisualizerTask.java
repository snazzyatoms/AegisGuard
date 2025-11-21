package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * This task runs for a single player, showing them the borders
 * of the plot they are currently standing in.
 */
public class PlotVisualizerTask extends BukkitRunnable {

    private final AegisGuard plugin;
    private final Player player;
    private Plot lastPlot = null;
    private Particle particle;
    private Particle.DustOptions dustOptions;

    // --- FIX: Cross-version support for REDSTONE (old) vs DUST (new) ---
    private static final Particle DUST_PARTICLE_TYPE;
    static {
        Particle p;
        try {
            p = Particle.valueOf("DUST"); // 1.20.5+
        } catch (IllegalArgumentException e) {
            try {
                p = Particle.valueOf("REDSTONE"); // 1.20.4 and older
            } catch (IllegalArgumentException ex) {
                p = Particle.FLAME; // Fallback if neither exists (unlikely)
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

        Plot currentPlot = plugin.store().getPlotAt(player.getLocation());

        if (currentPlot != null) {
            if (!currentPlot.equals(lastPlot)) {
                lastPlot = currentPlot;
            }
            // Check for custom cosmetic particle
            updateParticle(currentPlot.getBorderParticle());
            drawPlotBorders(currentPlot);
        } else {
            lastPlot = null;
        }
    }

    private void updateParticle(String cosmeticParticleName) {
        String particleName = cosmeticParticleName != null ? cosmeticParticleName 
                : plugin.cfg().raw().getString("visualization.particle", "FLAME");
        
        try {
            this.particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.particle = Particle.FLAME;
        }

        // FIX: Compare against our dynamic constant instead of hardcoded REDSTONE
        if (this.particle == DUST_PARTICLE_TYPE) {
            this.dustOptions = new Particle.DustOptions(Color.AQUA, 1.0F);
        } else {
            this.dustOptions = null;
        }
    }

    private void drawPlotBorders(Plot plot) {
        Location worldLoc = player.getWorld().getSpawnLocation(); 
        if (!worldLoc.getWorld().getName().equals(plot.getWorld())) return;

        int y = player.getLocation().getBlockY();
        int step = 2;

        for (int x = plot.getX1(); x <= plot.getX2(); x += step) {
            spawnParticle(new Location(worldLoc.getWorld(), x, y, plot.getZ1()));
            spawnParticle(new Location(worldLoc.getWorld(), x, y, plot.getZ2()));
        }
        for (int z = plot.getZ1(); z <= plot.getZ2(); z += step) {
            spawnParticle(new Location(worldLoc.getWorld(), plot.getX1(), y, z));
            spawnParticle(new Location(worldLoc.getWorld(), plot.getX2(), y, z));
        }
    }

    private void spawnParticle(Location loc) {
        if (dustOptions != null) {
            // FIX: Use the dynamic DUST_PARTICLE_TYPE
            player.spawnParticle(DUST_PARTICLE_TYPE, loc, 1, dustOptions);
        } else {
            player.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
        }
    }
}
