package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import com.aegisguard.objects.Guild;
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

    // Pl3xMap v1.21.x uses String keys
    private static final String LAYER_KEY = "aegisguard_estates";

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
                layer = new SimpleLayer(LAYER_KEY, () -> "AegisGuard Estates");
                layer.setUpdateInterval(300); // client-side update interval (seconds)
                layer.setPriority(99);        // show on top
                mapWorld.getLayerRegistry().register(LAYER_KEY, layer);
            }

            // Clear old markers to prevent ghosts
            layer.clearMarkers();

            // Add new markers
            Collection<Estate> estates = plugin.getEstateManager().getAllEstates();
            
            for (Estate estate : estates) {
                // Ensure estate belongs to this world
                if (estate.getWorld() == null || !estate.getWorld().getName().equals(bukkitWorld.getName())) {
                    continue;
                }

                String keyId = "estate_" + estate.getId();

                // Rectangle marker
                // Use Cuboid helpers
                int x1 = estate.getRegion().getLowerNE().getBlockX();
                int z1 = estate.getRegion().getLowerNE().getBlockZ();
                int x2 = estate.getRegion().getUpperSW().getBlockX() + 1; // +1 to cover full block
                int z2 = estate.getRegion().getUpperSW().getBlockZ() + 1;

                Marker<?> rect = Marker.rectangle(
                        keyId,
                        Point.of(x1, z1),
                        Point.of(x2, z2)
                );

                // Styling (Colors in ARGB format)
                int strokeColor;
                int fillColor;

                if (estate.isForSale()) {
                    strokeColor = 0xFFFFFF00; // Yellow
                    fillColor   = 0x55FFFF00;
                } else if (estate.isGuild()) {
                    strokeColor = 0xFF0000FF; // Blue/Purple for Guilds
                    fillColor   = 0x550000FF;
                } else {
                    strokeColor = 0xFF00FF00; // Green for Private
                    fillColor   = 0x5500FF00;
                }

                // Build Popup
                String ownerName;
                if (estate.isGuild()) {
                    Guild guild = plugin.getAllianceManager().getGuild(estate.getOwnerId());
                    ownerName = (guild != null) ? guild.getName() : "Unknown Guild";
                } else {
                    ownerName = Bukkit.getOfflinePlayer(estate.getOwnerId()).getName();
                }

                Options options = Options.builder()
                        .stroke(new Stroke(strokeColor, 2))
                        .fill(new Fill(fillColor))
                        .popupContent(buildPopup(estate, ownerName))
                        .build();

                rect.setOptions(options);
                layer.addMarker(rect);
            }
        }
    }

    private String buildPopup(Estate estate, String ownerName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='text-align:center;'>");
        
        // Title
        sb.append("<b>").append(estate.getName()).append("</b><br/>");
        sb.append("Owner: ").append(ownerName).append("<br/>");

        if (estate.isForSale()) {
            sb.append("<span style='color:yellow;'>FOR SALE: $")
              .append(estate.getSalePrice())
              .append("</span><br/>");
        }

        if (estate.isGuild()) {
             sb.append("<span style='color:cyan;'>[GUILD TERRITORY]</span><br/>");
        }

        sb.append("Level: ").append(estate.getLevel());
        sb.append("</div>");

        return sb.toString();
    }
}
