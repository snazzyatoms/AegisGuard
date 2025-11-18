package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * DynmapHook
 * - Integrates with Dynmap to display all AegisGuard plots on the web map.
 */
public class DynmapHook {

    private final AegisGuard plugin;
    private DynmapAPI dynmap;
    private MarkerAPI markerAPI;
    private MarkerSet markerSet;

    private boolean isEnabled = false;

    public DynmapHook(AegisGuard plugin) {
        this.plugin = plugin;
        
        if (plugin.cfg().raw().getBoolean("hooks.dynmap.enabled", false)) {
            if (Bukkit.getPluginManager().isPluginEnabled("dynmap")) {
                this.dynmap = (DynmapAPI) Bukkit.getPluginManager().getPlugin("dynmap");
                this.markerAPI = dynmap.getMarkerAPI();
                setupMarkerSet();
                runFullRender(); // Run first render
                startSyncTask();
                isEnabled = true;
                plugin.getLogger().info("Successfully hooked into Dynmap.");
            } else {
                plugin.getLogger().warning("Dynmap hook enabled in config, but Dynmap plugin not found.");
            }
        }
    }

    /**
     * Creates the "AegisGuard" layer on Dynmap.
     */
    private void setupMarkerSet() {
        if (markerAPI == null) return;

        this.markerSet = markerAPI.getMarkerSet("aegisguard.plots");
        if (this.markerSet == null) {
            this.markerSet = markerAPI.createMarkerSet("aegisguard.plots", 
                    plugin.cfg().raw().getString("hooks.dynmap.layer_name", "AegisGuard Plots"), 
                    null, false);
        } else {
            this.markerSet.setMarkerSetLabel(plugin.cfg().raw().getString("hooks.dynmap.layer_name", "AegisGuard Plots"));
        }

        // Configure appearance
        int strokeColor = Integer.parseInt(plugin.cfg().raw().getString("hooks.dynmap.style.stroke_color", "00FF00"), 16);
        double strokeOpacity = plugin.cfg().raw().getDouble("hooks.dynmap.style.stroke_opacity", 0.8);
        int strokeWeight = plugin.cfg().raw().getInt("hooks.dynmap.style.stroke_weight", 3);
        int fillColor = Integer.parseInt(plugin.cfg().raw().getString("hooks.dynmap.style.fill_color", "00FF00"), 16);
        double fillOpacity = plugin.cfg().raw().getDouble("hooks.dynmap.style.fill_opacity", 0.35);

        markerSet.setDefaultAreaStyle(strokeWeight, strokeOpacity, strokeColor, fillOpacity, fillColor);
    }

    /**
     * Starts a repeating task to sync all plots with the map.
     */
    private void startSyncTask() {
        long interval = 20L * 60 * plugin.cfg().raw().getLong("hooks.dynmap.sync_interval_minutes", 5);
        
        plugin.runGlobalAsync(() -> {
            plugin.getServer().getScheduler().runTaskTimer(plugin, 
                    this::runFullRender, 
                    20L * 30, // Initial 30-second delay
                    interval // Repeat every X minutes
            );
        });
    }

    /**
     * Wipes and re-draws all plots.
     * This is run asynchronously.
     */
    private void runFullRender() {
        if (!isEnabled) return;
        
        plugin.runGlobalAsync(() -> {
            plugin.getLogger().info("Running Dynmap full render...");
            Collection<Plot> plots = plugin.store().getAllPlots();
            Map<String, AreaMarker> existingMarkers = new HashMap<>();
            
            for(AreaMarker marker : markerSet.getAreaMarkers()) {
                existingMarkers.put(marker.getMarkerID(), marker);
            }
            
            for (Plot plot : plots) {
                String markerId = "ag_plot_" + plot.getPlotId().toString();
                AreaMarker marker = existingMarkers.remove(markerId); // Get and remove from map
                
                String worldName = plot.getWorld();
                if (Bukkit.getWorld(worldName) == null) continue; // Don't draw for unloaded worlds
                
                double[] x = { plot.getX1(), plot.getX2() + 1 };
                double[] z = { plot.getZ1(), plot.getZ2() + 1 };
                
                if (marker == null) {
                    // Create new marker
                    marker = markerSet.createAreaMarker(markerId, "", false, worldName, x, z, false);
                } else {
                    // Update existing
                    marker.setCornerLocations(x, z);
                    marker.setWorld(worldName);
                }
                
                if (marker == null) continue;
                
                // Set popup info
                marker.setDescription(buildPopupHTML(plot));
            }
            
            // Clean up old markers (for deleted plots)
            for (AreaMarker oldMarker : existingMarkers.values()) {
                oldMarker.deleteMarker();
            }
        });
    }
    
    /**
     * Creates the HTML for the Dynmap popup.
     */
    private String buildPopupHTML(Plot plot) {
        String ownerName = plot.getOwnerName();
        String roleList = plot.getPlayerRoles().entrySet().stream()
            .filter(e -> !e.getValue().equals("owner"))
            .map(e -> {
                OfflinePlayer p = Bukkit.getOfflinePlayer(e.getKey());
                return (p.getName() != null ? p.getName() : "Unknown") + " (" + e.getValue() + ")";
            })
            .collect(Collectors.joining("<br>"));

        if (roleList.isEmpty()) {
            roleList = "None";
        }

        return "<div style=\"font-weight:bold;font-size:120%;\">" +
               ownerName + "'s Plot</div>" +
               "<div style=\"font-weight:bold;\">Roles:</div>" +
               roleList;
    }
}
