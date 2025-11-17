package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotStore;
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
    private PlotStore.Plot lastPlot = null;
    private final Particle particle;
    private final Particle.DustOptions dustOptions;

    public PlotVisualizerTask(AegisGuard plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        // Load particle from config
        String particleName = plugin.cfg().raw().getString("visualization.particle", "FLAME");
        Particle p;
        try {
            p = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle in config: " + particleName + ". Defaulting to FLAME.");
            p = Particle.FLAME;
        }
        this.particle = p;

        // Special handling for REDSTONE particles (which use color)
        if (this.particle == Particle.REDSTONE) {
            String colorStr = plugin.cfg().raw().getString("visualization.color", "0,255,255"); // Cyan default
            String[] parts = colorStr.split(",");
            int r = Integer.parseInt(parts[0].trim());
            int g = Integer.parseInt(parts[1].trim());
            int b = Integer.parseInt(parts[2].trim());
            this.dustOptions = new Particle.DustOptions(Color.fromRGB(r, g, b), 1.0F);
        } else {
            this.dustOptions = null;
        }
    }

    @Override
    public void run() {
        // Stop if player logs off
        if (!player.isOnline()) {
            this.cancel();
            return;
        }

        PlotStore.Plot currentPlot = plugin.store().getPlotAt(player.getLocation());

        // If the player is in a plot, draw its borders
        if (currentPlot != null) {
            // Optimization: only redraw if the plot is new
            if (!currentPlot.equals(lastPlot)) {
                lastPlot = currentPlot;
                // You could send a title/action bar message here
                // player.sendTitle(" ", "§bNow viewing " + currentPlot.getOwnerName() + "'s plot", 10, 40, 10);
            }
            drawPlotBorders(currentPlot);
        } else {
            lastPlot = null;
        }
    }

    private void drawPlotBorders(PlotStore.Plot plot) {
        Location worldLoc = player.getWorld().getSpawnLocation(); // Just to get a valid world
        if (!worldLoc.getWorld().getName().equals(plot.getWorld())) {
            return; // Player is in a different world
        }

        int y = player.getLocation().getBlockY(); // Draw at player's eye level
        int step = 2; // Draw a particle every 2 blocks

        // Draw X-axis lines (North and South borders)
        for (int x = plot.getX1(); x <= plot.getX2(); x += step) {
            spawnParticle(new Location(worldLoc.getWorld(), x, y, plot.getZ1()));
            spawnParticle(new Location(worldLoc.getWorld(), x, y, plot.getZ2()));
        }

        // Draw Z-axis lines (East and West borders)
        for (int z = plot.getZ1(); z <= plot.getZ2(); z += step) {
            spawnParticle(new Location(worldLoc.getWorld(), plot.getX1(), y, z));
            spawnParticle(new Location(worldLoc.getWorld(), plot.getX2(), y, z));
        }
    }

    private void spawnParticle(Location loc) {
        if (dustOptions != null) {
            player.spawnParticle(Particle.REDSTONE, loc, 1, dustOptions);
        } else {
            player.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
        }
    }
}
