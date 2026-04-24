package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * DynmapHook
 * - Integrates with Dynmap to display plots visually.
 */
public class DynmapHook {

    private final AegisGuard plugin;
    private DynmapAPI dynmap;
    private MarkerAPI markerAPI;
    private MarkerSet markerSet;
    
    // Config Cache
    private int strokeColor, fillColor;
    private double strokeOpacity, fillOpacity;
    private int strokeWeight;

    public DynmapHook(AegisGuard plugin) {
        this.plugin = plugin;
        
        Plugin dynmapPlugin = Bukkit.getPluginManager().getPlugin("dynmap");
        if (dynmapPlugin instanceof DynmapAPI) {
            this.dynmap = (DynmapAPI) dynmapPlugin;
            this.markerAPI = dynmap.getMarkerAPI();
            
            if (this.markerAPI != null) {
                loadConfig();
                setupMarkerSet();
                update(); // Initial update
                plugin.getLogger().info("Successfully hooked into Dynmap.");
            }
        }
    }
    
    private void loadConfig() {
        strokeColor = Integer.parseInt(plugin.cfg().raw().getString("hooks.dynmap.style.stroke_color", "00FF00"), 16);
        strokeOpacity = plugin.cfg().raw().getDouble("hooks.dynmap.style.stroke_opacity", 0.8);
        strokeWeight = plugin.cfg().raw().getInt("hooks.dynmap.style.stroke_weight", 3);
        fillColor = Integer.parseInt(plugin.cfg().raw().getString("hooks.dynmap.style.fill_color", "00FF00"), 16);
        fillOpacity = plugin.cfg().raw().getDouble("hooks.dynmap.style.fill_opacity", 0.35);
    }

    private void setupMarkerSet() {
        String layerName = plugin.cfg().raw().getString("hooks.dynmap.layer_name", "AegisGuard Plots");
        this.markerSet = markerAPI.getMarkerSet("aegisguard.plots");
        
        if (this.markerSet == null) {
            this.markerSet = markerAPI.createMarkerSet("aegisguard.plots", layerName, null, false);
        } else {
            this.markerSet.setMarkerSetLabel(layerName);
        }
    }

    /**
     * Called by MapHookManager to refresh markers
     */
    public void update() {
        if (markerSet == null) return;
        
        // Run Async
        plugin.runGlobalAsync(this::render);
    }

    private void render() {
        Collection<Plot> plots = plugin.store().getAllPlots();
        Map<String, AreaMarker> existingMarkers = new HashMap<>();
        
        for(AreaMarker marker : markerSet.getAreaMarkers()) {
            existingMarkers.put(marker.getMarkerID(), marker);
        }
        
        for (Plot plot : plots) {
            String markerId = "ag_plot_" + plot.getPlotId().toString();
            AreaMarker marker = existingMarkers.remove(markerId); 
            
            String worldName = plot.getWorld();
            if (Bukkit.getWorld(worldName) == null) continue;
            
            double[] x = { plot.getX1(), plot.getX2() + 1.0 };
            double[] z = { plot.getZ1(), plot.getZ2() + 1.0 };
            
            if (marker == null) {
                marker = markerSet.createAreaMarker(markerId, plot.getOwnerName(), false, worldName, x, z, false);
            } else {
                marker.setCornerLocations(x, z);
                marker.setLabel(plot.getOwnerName());
                if (!marker.getWorld().equals(worldName)) {
                    marker.deleteMarker();
                    marker = markerSet.createAreaMarker(markerId, plot.getOwnerName(), false, worldName, x, z, false);
                }
            }
            
            if (marker != null) {
                marker.setDescription(buildPopupHTML(plot));
                
                // Color Logic
                int sColor = strokeColor;
                int fColor = fillColor;
                
                if (plot.isServerZone()) {
                    sColor = 0xFF0000; // Red
                    fColor = 0xFF0000;
                } else if (plot.isForSale()) {
                    sColor = 0xFFFF00; // Yellow
                    fColor = 0xFFFF00;
                }
                
                marker.setFillStyle(fillOpacity, fColor);
                marker.setLineStyle(strokeWeight, strokeOpacity, sColor);
            }
        }
        
        // Remove deleted
        for (AreaMarker oldMarker : existingMarkers.values()) {
            oldMarker.deleteMarker();
        }
    }
    
    private String buildPopupHTML(Plot plot) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial;text-align:center;\">");
        
        // Header
        sb.append("<div style=\"font-weight:bold;font-size:120%;margin-bottom:5px;\">");
        if (plot.isServerZone()) {
            sb.append("<span style=\"color:#FF0000;\">Server Zone</span>");
        } else {
            sb.append(plot.getOwnerName()).append("'s Plot");
        }
        sb.append("</div>");
        
        // Details
        sb.append("<hr>");
        if (plot.isForSale()) {
            sb.append("<div>Status: <span style=\"color:green;font-weight:bold;\">FOR SALE</span></div>");
            sb.append("<div>Price: ").append(plot.getSalePrice()).append("</div>");
        }
        
        sb.append("<div>Level: ").append(plot.getLevel()).append("</div>");
        
        // Roles
        Map<UUID, String> roles = plot.getPlayerRoles();
        if (!roles.isEmpty()) {
            sb.append("<div style=\"margin-top:5px;font-weight:bold;\">Members:</div>");
            String members = roles.entrySet().stream()
                .filter(e -> !e.getValue().equals("owner"))
                .map(e -> {
                    OfflinePlayer p = Bukkit.getOfflinePlayer(e.getKey());
                    return (p.getName() != null ? p.getName() : "Unknown");
                })
                .collect(Collectors.joining(", "));
            
            if (members.isEmpty()) members = "None";
            sb.append("<div>").append(members).append("</div>");
        }
        
        sb.append("</div>");
        return sb.toString();
    }
}
