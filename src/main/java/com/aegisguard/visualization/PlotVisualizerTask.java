package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class PlotVisualizerTask extends BukkitRunnable {

    private final AegisGuard plugin;
    private final Player player;
    private Estate lastEstate = null;
    
    // Visual Settings
    private Particle particle;
    private Particle.DustOptions dustOptions;

    // Cross-Version Support
    private static final Particle DUST_PARTICLE_TYPE;
    static {
        Particle p;
        try { p = Particle.valueOf("DUST"); } 
        catch (IllegalArgumentException e) {
            try { p = Particle.valueOf("REDSTONE"); } 
            catch (IllegalArgumentException ex) { p = Particle.FLAME; }
        }
        DUST_PARTICLE_TYPE = p;
    }

    public PlotVisualizerTask(AegisGuard plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        updateParticle(null, false);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            this.cancel();
            return;
        }

        Estate currentEstate = plugin.getEstateManager().getEstateAt(player.getLocation());

        if (currentEstate != null) {
            if (!currentEstate.equals(lastEstate)) {
                lastEstate = currentEstate;
            }
            
            // Update particles based on type
            boolean isServer = currentEstate.isServerZone();
            updateParticle(currentEstate.getBorderParticle(), isServer);
            
            drawEstateBorders(currentEstate, isServer);
        } else {
            lastEstate = null;
        }
    }

    private void updateParticle(String cosmeticName, boolean isServer) {
        // SERVER ZONES = ALWAYS RED DUST
        if (isServer) {
            this.particle = DUST_PARTICLE_TYPE;
            this.dustOptions = new Particle.DustOptions(Color.RED, 1.5F);
            return;
        }

        // Player Estates = Custom or Default (Flame/Blue)
        String name = cosmeticName != null ? cosmeticName : 
                      plugin.getConfig().getString("visuals.particles.default_particle", "FLAME");
        
        try {
            this.particle = Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.particle = Particle.FLAME;
        }

        if (this.particle == DUST_PARTICLE_TYPE) {
            this.dustOptions = new Particle.DustOptions(Color.AQUA, 1.0F);
        } else {
            this.dustOptions = null;
        }
    }

    private void drawEstateBorders(Estate estate, boolean isServer) {
        if (estate.getWorld() == null || !player.getWorld().equals(estate.getWorld())) return;

        int y = player.getLocation().getBlockY();
        // Server zones are denser
        int step = isServer ? 1 : 2; 

        int x1 = estate.getRegion().getLowerNE().getBlockX();
        int z1 = estate.getRegion().getLowerNE().getBlockZ();
        int x2 = estate.getRegion().getUpperSW().getBlockX();
        int z2 = estate.getRegion().getUpperSW().getBlockZ();

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
