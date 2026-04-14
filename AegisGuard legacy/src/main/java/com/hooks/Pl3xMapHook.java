package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.option.Fill;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Stroke;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;

import java.util.Collection;

public class Pl3xMapHook {

    // Pl3xMap v1.21.x uses String keys, not net.pl3x.map.core.Key
    private static final String LAYER_KEY = "aegisguard_plots";

    private final AegisGuard plugin;

    public Pl3xMapHook(AegisGuard plugin) {
        this.plugin = plugin;

        // Register periodic update task (async)
        // 5 minute refresh (6000 ticks), 100 tick initial delay
        Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::update,
                100L,
                6000L
        );
    }

    public void update() {
        // Loop through Bukkit worlds to find matching Pl3xMap worlds
        for (org.bukkit.World bukkitWorld : Bukkit.getWorlds()) {
            World mapWorld = Pl3xMap.api().getWorldRegistry().get(bukkitWorld.getName());
            if (mapWorld == null) {
                continue; // Pl3xMap not tracking this world
            }

            // Get or create the layer
            SimpleLayer layer;
            if (mapWorld.getLayerRegistry().has(LAYER_KEY)) {
                layer = (SimpleLayer) mapWorld.getLayerRegistry().get(LAYER_KEY);
            } else {
                layer = new SimpleLayer(LAYER_KEY, () -> "AegisGuard Claims");
                layer.setUpdateInterval(300); // client-side update interval (seconds)
                layer.setPriority(99);        // show on top
                mapWorld.getLayerRegistry().register(LAYER_KEY, layer);
            }

            // Clear old markers to prevent ghosts
            layer.clearMarkers();

            // Add new markers
            Collection<Plot> plots = plugin.store().getAllPlots();
            for (Plot plot : plots) {
                // Ensure plot belongs to this world
                if (!plot.getWorld().equals(bukkitWorld.getName())) {
                    continue;
                }

                String keyId = "plot_" + plot.getPlotId();

                // Rectangle marker (note: keys are Strings now)
                Marker<?> rect = Marker.rectangle(
                        keyId,
                        Point.of(plot.getX1(), plot.getZ1()),
                        Point.of(plot.getX2() + 1, plot.getZ2() + 1) // +1 to cover full block visually
                );

                // Styling (Colors in ARGB format)
                // Server: Red (0xFFFF0000), Player: Green (0xFF00FF00)
                int strokeColor = plot.isServerZone() ? 0xFFFF0000 : 0xFF00FF00;
                int fillColor   = plot.isServerZone() ? 0x55FF0000 : 0x5500FF00;

                if (plot.isForSale()) {
                    strokeColor = 0xFFFFFF00; // Yellow
                    fillColor   = 0x55FFFF00;
                }

                Options options = Options.builder()
                        .stroke(new Stroke(strokeColor, 2))
                        .fill(new Fill(fillColor))
                        .popupContent(buildPopup(plot))
                        .build();

                rect.setOptions(options);
                layer.addMarker(rect);
            }
        }
    }

    private String buildPopup(Plot plot) {
        String owner = plot.isServerZone()
                ? "<span style='color:red;font-weight:bold;'>Server Zone</span>"
                : plot.getOwnerName();

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='text-align:center;'>");
        sb.append("<b>").append(owner).append("</b><br/>");

        if (plot.isForSale()) {
            sb.append("<span style='color:yellow;'>FOR SALE: $")
              .append(plot.getSalePrice())
              .append("</span><br/>");
        }

        sb.append("Level: ").append(plot.getLevel());
        sb.append("</div>");

        return sb.toString();
    }
}
