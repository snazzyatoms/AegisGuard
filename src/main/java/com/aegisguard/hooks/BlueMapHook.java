package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;

import java.util.Collection;
import java.util.Optional;

public class BlueMapHook {

    private final AegisGuard plugin;
    private MarkerSet markerSet;
    private final String MARKER_SET_ID = "aegisguard_plots";
    private BlueMapAPI api;

    public BlueMapHook(AegisGuard plugin) {
        this.plugin = plugin;
        
        // Wait for BlueMap to enable
        BlueMapAPI.onEnable(api -> {
            this.api = api;
            // Create or Retrieve Marker Set via MarkerAPI
            this.markerSet = api.getMarkerAPI().createMarkerSet(MARKER_SET_ID);
            this.markerSet.setLabel(plugin.cfg().raw().getString("hooks.bluemap.label", "Claims"));
            update();
        });
    }

    public void update() {
        if (markerSet == null || api == null) return;

        plugin.runGlobalAsync(() -> {
            Collection<Plot> plots = plugin.store().getAllPlots();
            
            // Clear existing markers to prevent duplicates on update
            markerSet.getMarkers().clear(); 
            
            for (Plot plot : plots) {
                String id = "plot_" + plot.getPlotId().toString();
                
                // Get the map instance for this world
                BlueMapMap map = getMapForWorld(plot.getWorld());
                if (map == null) continue;

                // Coordinates
                double x1 = plot.getX1();
                double x2 = plot.getX2() + 1; 
                double z1 = plot.getZ1();
                double z2 = plot.getZ2() + 1;
                
                // Shape
                Shape shape = Shape.createRect(x1, z1, x2, z2);
                
                // Height (Default visual range for a claim wall)
                float minY = 64f; 
                float maxY = 100f;

                // FIX: BlueMap markers MUST be created specific to a Map instance
                ExtrudeMarker marker = markerSet.createExtrudeMarker(id, map, shape, minY, maxY);
                
                // Info
                marker.setLabel(plot.getOwnerName() + "'s Plot");
                marker.setDetail(getHtml(plot)); 
                
                // Colors (ARGB)
                // Default: Green, Server: Red, Sale: Yellow (Check in final logic)
                Color color = plot.isServerZone() ? new Color(255, 0, 0, 100) : new Color(0, 255, 0, 50); 
                Color lineColor = plot.isServerZone() ? new Color(255, 0, 0, 255) : new Color(0, 255, 0, 255);
                
                if (plot.isForSale()) {
                    color = new Color(255, 255, 0, 50);
                    lineColor = new Color(255, 255, 0, 255);
                }

                marker.setFillColor(color);
                marker.setLineColor(lineColor);
            }
        });
    }
    
    // --- Helper Methods ---

    /**
     * Finds the first available map for the given world name.
     */
    private BlueMapMap getMapForWorld(String worldName) {
        if (api == null) return null;
        
        Optional<BlueMapWorld> worldOpt = api.getWorld(worldName);
        if (worldOpt.isPresent()) {
            // Return the first map found for this world (usually the surface map)
            Collection<BlueMapMap> maps = worldOpt.get().getMaps();
            if (!maps.isEmpty()) {
                return maps.iterator().next();
            }
        }
        return null; 
    }
    
    /**
     * Builds the HTML popup for the map marker.
     */
    private String getHtml(Plot plot) {
        return "<div style='text-align:center;'>" +
               "<div style='font-weight:bold;'>" + plot.getOwnerName() + "</div>" +
               "<div>Level: " + plot.getLevel() + "</div>" +
               (plot.isForSale() ? "<div style='color:yellow;'>FOR SALE</div>" : "") +
               "</div>";
    }
}
