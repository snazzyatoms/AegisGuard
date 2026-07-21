package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * This task runs for a single player, showing them the borders
 * of the plot they are currently standing in while they hold
 * an Aegis wand / scepter.
 */
public class PlotVisualizerTask extends BukkitRunnable {

    private final AegisGuard plugin;
    private final Player player;
    private Plot lastPlot = null;
    private Particle particle;
    private Particle.DustOptions dustOptions;

    // --- Cross-version support for REDSTONE (old) vs DUST (new) ---
    private static final Particle DUST_PARTICLE_TYPE;
    static {
        Particle p;
        try {
            // 1.20.5+ (new naming)
            p = Particle.valueOf("DUST");
        } catch (IllegalArgumentException e) {
            try {
                // 1.20.4 and older
                p = Particle.valueOf("REDSTONE");
            } catch (IllegalArgumentException ex) {
                // Very old / weird server: fallback
                p = Particle.FLAME;
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
        // Player gone? Stop this task.
        if (!player.isOnline()) {
            return;
        }

        Plot currentPlot = plugin.store().getPlotAt(player.getLocation());

        if (currentPlot != null) {
            if (!currentPlot.equals(lastPlot)) {
                lastPlot = currentPlot;
            }

            // Refresh cosmetic each tick in case player changes border style
            updateParticle(currentPlot.getBorderParticle());
            drawPlotBorders(currentPlot);
        } else {
            lastPlot = null;
        }
    }

    /**
     * Decide which particle to use:
     *  - plot border cosmetic (if set)
     *  - fallback to visualization.particle from config
     *  - fallback to FLAME if invalid
     */
    private void updateParticle(String cosmeticParticleName) {
        String particleName = cosmeticParticleName != null
                ? cosmeticParticleName
                : plugin.cfg().raw().getString("visualization.particle", "FLAME");

        try {
            this.particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.particle = Particle.FLAME;
        }

        // If using DUST/REDSTONE, prepare DustOptions
        if (this.particle == DUST_PARTICLE_TYPE) {
            // You can later make this color configurable per cosmetic
            this.dustOptions = new Particle.DustOptions(Color.AQUA, 1.0F);
        } else {
            this.dustOptions = null;
        }
    }

    private void drawPlotBorders(Plot plot) {
        if (plot == null) return;

        // Ensure we’re in the same world as the plot
        World playerWorld = player.getWorld();
        if (!playerWorld.getName().equals(plot.getWorld())) {
            return;
        }

        // Draw at roughly the player’s feet/eye level so it feels "present"
        int y = player.getLocation().getBlockY();
        int step = 2;

        // X edges
        for (int x = plot.getX1(); x <= plot.getX2(); x += step) {
            spawnParticle(new Location(playerWorld, x + 0.5, y, plot.getZ1() + 0.5));
            spawnParticle(new Location(playerWorld, x + 0.5, y, plot.getZ2() + 0.5));
        }

        // Z edges
        for (int z = plot.getZ1(); z <= plot.getZ2(); z += step) {
            spawnParticle(new Location(playerWorld, plot.getX1() + 0.5, y, z + 0.5));
            spawnParticle(new Location(playerWorld, plot.getX2() + 0.5, y, z + 0.5));
        }
    }

    private void spawnParticle(Location loc) {
        if (dustOptions != null && particle == DUST_PARTICLE_TYPE) {
            // Cross-version dust style border
            player.spawnParticle(DUST_PARTICLE_TYPE, loc, 1, dustOptions);
        } else {
            player.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
        }
    }
}
