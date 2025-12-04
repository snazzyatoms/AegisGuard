package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import com.aegisguard.objects.Guild;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class BlueMapHook {

    private final AegisGuard plugin;
    private BlueMapAPI api;

    // Marker-set id used on each map
    private static final String MARKER_SET_ID = "aegisguard_estates";

    public BlueMapHook(AegisGuard plugin) {
        this.plugin = plugin;

        // Wait for BlueMap to be enabled
        BlueMapAPI.onEnable(api -> {
            this.api = api;
            update(); // initial update when BlueMap is ready
        });
    }

    /**
     * Rebuild all markers on all maps based on current estates.
     */
    public void update() {
        if (api == null) return;

        plugin.runGlobalAsync(() -> {
            Collection<Estate> estates = plugin.getEstateManager().getAllEstates();

            // 1) Clear our marker-set on every map
            for (BlueMapMap map : api.getMaps()) {
                MarkerSet set = map.getMarkerSets().get(MARKER_SET_ID);
                if (set != null) {
                    set.getMarkers().clear();
                }
            }

            // 2) Rebuild markers per estate
            for (Estate estate : estates) {
                if (estate.getWorld() == null) continue;
                
                BlueMapMap map = getMapForWorld(estate.getWorld().getName());
                if (map == null) continue;

                // Get/create marker set for this map
                MarkerSet markerSet = getOrCreateMarkerSet(map);

                String id = "estate_" + estate.getId();

                // Coords (expand by +1 to cover whole blocks visually)
                // Use Cuboid region helper
                double x1 = estate.getRegion().getLowerNE().getBlockX();
                double x2 = estate.getRegion().getUpperSW().getBlockX() + 1;
                double z1 = estate.getRegion().getLowerNE().getBlockZ();
                double z2 = estate.getRegion().getUpperSW().getBlockZ() + 1;

                Shape shape = Shape.createRect(x1, z1, x2, z2);

                float minY = 64f;   // can be tweaked or made configurable
                float maxY = 100f;

                // Build marker
                String ownerName;
                if (estate.isGuild()) {
                    Guild guild = plugin.getAllianceManager().getGuild(estate.getOwnerId());
                    ownerName = (guild != null) ? guild.getName() : "Unknown Guild";
                } else {
                    ownerName = Bukkit.getOfflinePlayer(estate.getOwnerId()).getName();
                }
                
                String label = estate.getName() + " (" + ownerName + ")";
                ExtrudeMarker marker = new ExtrudeMarker(label, shape, minY, maxY);

                // Popup detail
                marker.setDetail(getHtml(estate, ownerName));

                // Colors
                Color fillColor;
                Color lineColor;

                if (estate.isForSale()) {
                    // Yellow for sale
                    fillColor = new Color(255, 255, 0, 60);
                    lineColor = new Color(255, 255, 0, 255);
                } else if (estate.isGuild()) {
                    // Blue/Purple for Guilds
                    fillColor = new Color(100, 0, 255, 60);
                    lineColor = new Color(100, 0, 255, 255);
                } else {
                    // Green for Private
                    fillColor = new Color(0, 255, 0, 60);
                    lineColor = new Color(0, 255, 0, 255);
                }

                marker.setFillColor(fillColor);
                marker.setLineColor(lineColor);
                marker.setLineWidth(2);

                // Put into marker set
                markerSet.put(id, marker);
            }
        });
    }

    /**
     * Get or create the marker-set for a given map.
     */
    private MarkerSet getOrCreateMarkerSet(BlueMapMap map) {
        Map<String, MarkerSet> sets = map.getMarkerSets();

        MarkerSet existing = sets.get(MARKER_SET_ID);
        if (existing != null) {
            return existing;
        }

        String label = plugin.getConfig().getString("maps.bluemap.label", "Estates");
        MarkerSet set = new MarkerSet(label);
        set.setToggleable(true);
        set.setDefaultHidden(false);
        set.setSorting(0);

        sets.put(MARKER_SET_ID, set);
        return set;
    }

    /**
     * Finds the first map associated with a given world name.
     */
    private BlueMapMap getMapForWorld(String worldName) {
        if (api == null) return null;

        Optional<BlueMapWorld> worldOpt = api.getWorld(worldName);
        if (worldOpt.isEmpty()) return null;

        Collection<BlueMapMap> maps = worldOpt.get().getMaps();
        if (maps.isEmpty()) return null;

        // Usually the first one is the main surface map
        return maps.iterator().next();
    }

    /**
     * Builds the HTML popup for the marker.
     */
    private String getHtml(Estate estate, String ownerName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='text-align:center;'>")
          .append("<div style='font-weight:bold;'>").append(estate.getName()).append("</div>")
          .append("<div>Owner: ").append(ownerName).append("</div>")
          .append("<div>Level: ").append(estate.getLevel()).append("</div>");

        if (estate.isForSale()) {
            sb.append("<div style='color:yellow;font-weight:bold;'>FOR SALE: $")
              .append(estate.getSalePrice())
              .append("</div>");
        }
        
        if (estate.isGuild()) {
            sb.append("<div style='color:cyan;'>[GUILD TERRITORY]</div>");
        }

        sb.append("</div>");
        return sb.toString();
    }
}
