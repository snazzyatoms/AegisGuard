package com.yourname.aegisguard.hooks;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.objects.Estate;
import com.yourname.aegisguard.objects.Guild;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * DynmapHook
 * - Integrates with Dynmap to display estates visually.
 * - Updated for v1.3.0 Estate System.
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

    // Marker-set id used on each map
    private static final String MARKER_SET_ID = "aegisguard_estates";

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
        strokeColor = Integer.parseInt(plugin.getConfig().getString("maps.dynmap.style.stroke_color", "00FF00"), 16);
        strokeOpacity = plugin.getConfig().getDouble("maps.dynmap.style.stroke_opacity", 0.8);
        strokeWeight = plugin.getConfig().getInt("maps.dynmap.style.stroke_weight", 3);
        fillColor = Integer.parseInt(plugin.getConfig().getString("maps.dynmap.style.fill_color", "00FF00"), 16);
        fillOpacity = plugin.getConfig().getDouble("maps.dynmap.style.fill_opacity", 0.35);
    }

    private void setupMarkerSet() {
        String layerName = plugin.getConfig().getString("maps.dynmap.layer_name", "AegisGuard Estates");
        this.markerSet = markerAPI.getMarkerSet(MARKER_SET_ID);
        
        if (this.markerSet == null) {
            this.markerSet = markerAPI.createMarkerSet(MARKER_SET_ID, layerName, null, false);
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
        Collection<Estate> estates = plugin.getEstateManager().getAllEstates();
        Map<String, AreaMarker> existingMarkers = new HashMap<>();
        
        for(AreaMarker marker : markerSet.getAreaMarkers()) {
            existingMarkers.put(marker.getMarkerID(), marker);
        }
        
        for (Estate estate : estates) {
            if (estate.getWorld() == null) continue;

            String markerId = "ag_estate_" + estate.getId().toString();
            AreaMarker marker = existingMarkers.remove(markerId); 
            
            String worldName = estate.getWorld().getName();
            
            // Coords from Cuboid
            double[] x = { estate.getRegion().getLowerNE().getBlockX(), estate.getRegion().getUpperSW().getBlockX() + 1.0 };
            double[] z = { estate.getRegion().getLowerNE().getBlockZ(), estate.getRegion().getUpperSW().getBlockZ() + 1.0 };
            
            // Determine Label
            String label;
            if (estate.isGuild()) {
                Guild guild = plugin.getAllianceManager().getGuild(estate.getOwnerId());
                String guildName = (guild != null) ? guild.getName() : "Unknown Guild";
                label = guildName + " (Guild)";
            } else {
                String ownerName = Bukkit.getOfflinePlayer(estate.getOwnerId()).getName();
                label = estate.getName() + " (" + ownerName + ")";
            }

            if (marker == null) {
                marker = markerSet.createAreaMarker(markerId, label, false, worldName, x, z, false);
            } else {
                marker.setCornerLocations(x, z);
                marker.setLabel(label);
                if (!marker.getWorld().equals(worldName)) {
                    marker.deleteMarker();
                    marker = markerSet.createAreaMarker(markerId, label, false, worldName, x, z, false);
                }
            }
            
            if (marker != null) {
                marker.setDescription(buildPopupHTML(estate, label));
                
                // Color Logic
                int sColor = strokeColor;
                int fColor = fillColor;
                
                if (estate.isForSale()) {
                    sColor = 0xFFFF00; // Yellow
                    fColor = 0xFFFF00;
                } else if (estate.isGuild()) {
                    sColor = 0x0000FF; // Blue
                    fColor = 0x0000FF;
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
    
    private String buildPopupHTML(Estate estate, String label) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial;text-align:center;\">");
        
        // Header
        sb.append("<div style=\"font-weight:bold;font-size:120%;margin-bottom:5px;\">");
        sb.append(label);
        sb.append("</div>");
        
        // Details
        sb.append("<hr>");
        if (estate.isForSale()) {
            sb.append("<div>Status: <span style=\"color:green;font-weight:bold;\">FOR SALE</span></div>");
            sb.append("<div>Price: $").append(estate.getSalePrice()).append("</div>");
        }
        
        sb.append("<div>Level: ").append(estate.getLevel()).append("</div>");
        
        if (estate.isGuild()) {
             sb.append("<div style=\"color:blue;font-weight:bold;\">[GUILD TERRITORY]</div>");
        }
        
        sb.append("</div>");
        return sb.toString();
    }
}
