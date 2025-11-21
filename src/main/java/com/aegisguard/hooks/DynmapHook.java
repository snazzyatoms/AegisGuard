package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DynmapHook
 * - Integrates with Dynmap to display all AegisGuard plots on the web map.
 * - Features Folia-safe scheduling and async rendering.
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
            Plugin dynmapPlugin = Bukkit.getPluginManager().getPlugin("dynmap");
            
            if (dynmapPlugin instanceof DynmapAPI) {
                this.dynmap = (DynmapAPI) dynmapPlugin;
                this.markerAPI = dynmap.getMarkerAPI();
                
                if (this.markerAPI != null) {
                    setupMarkerSet();
                    runFullRender(); // Run first render immediately
                    startSyncTask();
                    isEnabled = true;
                    plugin.getLogger().info("Successfully hooked into Dynmap.");
                }
            } else {
                plugin.getLogger().warning("Dynmap hook enabled in config, but Dynmap plugin not found or invalid.");
            }
        }
    }

    /**
     * Creates the "AegisGuard" layer on Dynmap.
     */
    private void setupMarkerSet() {
        if (markerAPI == null) return;

        String layerName = plugin.cfg().raw().getString("hooks.dynmap.layer_name", "AegisGuard Plots");
        this.markerSet = markerAPI.getMarkerSet("aegisguard.plots");
        
        if (this.markerSet == null) {
            this.markerSet = markerAPI.createMarkerSet("aegisguard.plots", layerName, null, false);
        } else {
            this.markerSet.setMarkerSetLabel(layerName);
        }

        // Configure appearance defaults
        int strokeColor = Integer.parseInt(plugin.cfg().raw().getString("hooks.dynmap.style.stroke_color", "00FF00"), 16);
        double strokeOpacity = plugin.cfg().raw().getDouble("hooks.dynmap.style.stroke_opacity", 0.8);
        int strokeWeight = plugin.cfg().raw().getInt("hooks.dynmap.style.stroke_weight", 3);
        int fillColor = Integer.parseInt(plugin.cfg().raw().getString("hooks.dynmap.style.fill_color", "00FF00"), 16);
        double fillOpacity = plugin.cfg().raw().getDouble("hooks.dynmap.style.fill_opacity", 0.35);

        if (markerSet != null) {
            markerSet.setDefaultAreaStyle(strokeWeight, strokeOpacity, strokeColor, fillOpacity, fillColor);
        }
    }

    /**
     * Starts a repeating task to sync all plots with the map.
     * Handles both Folia and standard Bukkit schedulers.
     */
    private void startSyncTask() {
        long intervalTicks = 20L * 60 * plugin.cfg().raw().getLong("hooks.dynmap.sync_interval_minutes", 5);
        long initialDelay = 20L * 30; // 30 seconds
        
        if (plugin.isFolia()) {
             Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> runFullRender(), initialDelay, intervalTicks);
        } else {
             Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runFullRender, initialDelay, intervalTicks);
        }
    }

    /**
     * Wipes and re-draws all plots.
     * This runs on the thread it is called from (Async usually).
     */
    private void runFullRender() {
        if (!isEnabled || markerSet == null) return;
        
        // Ensure we are async if not already (prevent main thread lag)
        if (Bukkit.isPrimaryThread()) {
            plugin.runGlobalAsync(this::runFullRender);
            return;
        }

        plugin.getLogger().info("Running Dynmap full render...");
        Collection<Plot> plots = plugin.store().getAllPlots();
        Map<String, AreaMarker> existingMarkers = new HashMap<>();
        
        // Cache existing markers to detect deletions
        for(AreaMarker marker : markerSet.getAreaMarkers()) {
            existingMarkers.put(marker.getMarkerID(), marker);
        }
        
        for (Plot plot : plots) {
            String markerId = "ag_plot_" + plot.getPlotId().toString();
            AreaMarker marker = existingMarkers.remove(markerId); // Get and remove from map to track leftovers
            
            String worldName = plot.getWorld();
            if (Bukkit.getWorld(worldName) == null) continue; // Don't draw for unloaded worlds
            
            // Dynmap expects x and z arrays for polygon corners (adding 1.0 to coordinate 2 for block coverage)
            double[] x = { plot.getX1(), plot.getX2() + 1.0 };
            double[] z = { plot.getZ1(), plot.getZ2() + 1.0 };
            
            if (marker == null) {
                // Create new marker
                marker = markerSet.createAreaMarker(markerId, "", false, worldName, x, z, false);
            } else {
                // Update existing
                marker.setCornerLocations(x, z);
                marker.setWorld(worldName);
            }
            
            if (marker != null) {
                // Set popup info
                marker.setDescription(buildPopupHTML(plot));
                
                // Optional: Custom style per plot (e.g. red for server zones)
                if (plot.isServerZone()) {
                    marker.setFillStyle(0.35, 0xFF0000);
                    marker.setLineStyle(3, 0.8, 0xFF0000);
                }
            }
        }
        
        // Clean up old markers (plots that were deleted since last render)
        for (AreaMarker oldMarker : existingMarkers.values()) {
            oldMarker.deleteMarker();
        }
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
