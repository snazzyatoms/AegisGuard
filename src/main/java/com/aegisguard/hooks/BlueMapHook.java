package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.ExtrudeMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.Collection;
import java.util.Map;
import java.util.Vector;

public class BlueMapHook {

    private final AegisGuard plugin;
    private MarkerSet markerSet;
    private final String MARKER_SET_ID = "aegisguard_plots";

    public BlueMapHook(AegisGuard plugin) {
        this.plugin = plugin;
        
        BlueMapAPI.onEnable(api -> {
            // Create or Retrieve Marker Set
            this.markerSet = api.getMarkerAPI().createMarkerSet(MARKER_SET_ID);
            this.markerSet.setLabel(plugin.cfg().raw().getString("hooks.bluemap.label", "Claims"));
            update();
        });
    }

    public void update() {
        if (markerSet == null) return;

        plugin.runGlobalAsync(() -> {
            // Clear old markers (optional, or rely on overwriting IDs)
            // markerSet.getMarkers().clear(); 

            Collection<Plot> plots = plugin.store().getAllPlots();
            
            for (Plot plot : plots) {
                String id = "plot_" + plot.getPlotId().toString();
                
                // Coordinates
                double x1 = plot.getX1();
                double x2 = plot.getX2() + 1; // +1 to cover full block
                double z1 = plot.getZ1();
                double z2 = plot.getZ2() + 1;
                
                // Extrude Height (Visualizes safe zone walls)
                float minY = 60f;
                float maxY = 120f; // Height of the 3D box

                Shape shape = Shape.createRect(x1, z1, x2, z2);
                
                // Create 3D Marker
                ExtrudeMarker marker = markerSet.createExtrudeMarker(id, plugin.getMapWorld(plot.getWorld()), shape, minY, maxY);
                
                // Info
                marker.setLabel(plot.getOwnerName() + "'s Plot");
                marker.setDetail(getHtml(plot)); // Reuse your HTML builder logic
                
                // Colors
                Color color = plot.isServerZone() ? new Color(255, 0, 0, 100) : new Color(0, 255, 0, 50); // Red/Green
                Color lineColor = plot.isServerZone() ? new Color(255, 0, 0, 255) : new Color(0, 255, 0, 255);
                
                marker.setFillColor(color);
                marker.setLineColor(lineColor);
            }
        });
    }
    
    // Quick helper to map Bukkit world to BlueMap world
    private de.bluecolored.bluemap.api.BlueMapMap plugin.getMapWorld(String worldName) {
        // Logic to find map via API... simplified for brevity
        return null; 
    }
    
    private String getHtml(Plot plot) {
        return "<div style='text-align:center;'><b>" + plot.getOwnerName() + "</b></div>";
    }
}
