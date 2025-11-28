package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Rectangle;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Fill;
import net.pl3x.map.core.markers.option.Stroke;
import net.pl3x.map.core.world.World;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.Key;
import net.pl3x.map.core.markers.Point;

import java.util.Collection;

public class Pl3xMapHook {

    private final AegisGuard plugin;
    private final Key layerKey = Key.of("aegisguard_plots");

    public Pl3xMapHook(AegisGuard plugin) {
        this.plugin = plugin;
        
        // Register Layer for each world
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::update, 100L, 20L * 60 * 5); // 5 min sync
    }

    public void update() {
        for (org.bukkit.World bukkitWorld : Bukkit.getWorlds()) {
            World mapWorld = Pl3xMap.api().getWorldRegistry().get(bukkitWorld.getName());
            if (mapWorld == null) continue;

            SimpleLayer layer = (SimpleLayer) mapWorld.getLayerRegistry().get(layerKey);
            if (layer == null) {
                layer = new SimpleLayer(layerKey, () -> "AegisGuard Claims");
                mapWorld.getLayerRegistry().register(layerKey, layer);
            }

            // Clear old
            layer.clearMarkers();

            // Add new
            for (Plot plot : plugin.store().getAllPlots()) {
                if (!plot.getWorld().equals(bukkitWorld.getName())) continue;

                String keyId = "plot_" + plot.getPlotId();
                
                Rectangle rect = Marker.rectangle(
                    Key.of(keyId), 
                    Point.of(plot.getX1(), plot.getZ1()), 
                    Point.of(plot.getX2() + 1, plot.getZ2() + 1)
                );

                // Styling
                int color = plot.isServerZone() ? 0xFFFF0000 : 0xFF00FF00; // ARGB
                
                Options options = Options.builder()
                    .stroke(new Stroke(color, 2))
                    .fill(new Fill(color & 0x55FFFFFF)) // Lower alpha for fill
                    .popupContent("<b>" + plot.getOwnerName() + "</b>")
                    .build();

                rect.setOptions(options);
                layer.addMarker(rect);
            }
        }
    }
}
