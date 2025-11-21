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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * DynmapHook
 * - Integrates with Dynmap to display all AegisGuard plots.
 * - Fixed to compile on all Spigot/Paper versions using Reflection.
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
                    runFullRender(); 
                    startSyncTask();
                    isEnabled = true;
                    plugin.getLogger().info("Successfully hooked into Dynmap.");
                }
            } else {
                plugin.getLogger().warning("Dynmap plugin not found or invalid.");
            }
        }
    }

    private void setupMarkerSet() {
        if (markerAPI == null) return;

        String layerName = plugin.cfg().raw().getString("hooks.dynmap.layer_name", "AegisGuard Plots");
        this.markerSet = markerAPI.getMarkerSet("aegisguard.plots");
        
        if (this.markerSet == null) {
            this.markerSet = markerAPI.createMarkerSet("aegisguard.plots", layerName, null, false);
        } else {
            this.markerSet.setMarkerSetLabel(layerName);
        }

        // Settings
        int strokeColor = Integer.parseInt(plugin.cfg().raw().getString("hooks.dynmap.style.stroke_color", "00FF00"), 16);
        double strokeOpacity = plugin.cfg().raw().getDouble("hooks.dynmap.style.stroke_opacity", 0.8);
        int strokeWeight = plugin.cfg().raw().getInt("hooks.dynmap.style.stroke_weight", 3);
        int fillColor = Integer.parseInt(plugin.cfg().raw().getString("hooks.dynmap.style.fill_color", "00FF00"), 16);
        double fillOpacity = plugin.cfg().raw().getDouble("hooks.dynmap.style.fill_opacity", 0.35);

        if (markerSet != null) {
            // Use Reflection to call setDefaultAreaStyle to avoid compilation errors on older/newer APIs
            try {
                Method setStyle = markerSet.getClass().getMethod("setDefaultAreaStyle", int.class, double.class, int.class, double.class, int.class);
                setStyle.invoke(markerSet, strokeWeight, strokeOpacity, strokeColor, fillOpacity, fillColor);
            } catch (Exception ignored) {
                // Method not found in this Dynmap version, skipping default style
            }
        }
    }

    private void startSyncTask() {
        long intervalTicks = 20L * 60 * plugin.cfg().raw().getLong("hooks.dynmap.sync_interval_minutes", 5);
        long initialDelay = 20L * 30; 

        if (plugin.isFolia()) {
            // Reflection for Folia Scheduler to avoid "cannot find symbol" error
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                Method runMethod = scheduler.getClass().getMethod("runAtFixedRate", JavaPlugin.class, Consumer.class, long.class, long.class);
                runMethod.invoke(scheduler, plugin, (Consumer<Object>) t -> runFullRender(), initialDelay, intervalTicks);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to schedule Folia task via reflection: " + e.getMessage());
            }
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runFullRender, initialDelay, intervalTicks);
        }
    }

    private void runFullRender() {
        if (!isEnabled || markerSet == null) return;
        
        // Ensure Async
        if (Bukkit.isPrimaryThread()) {
            plugin.runGlobalAsync(this::runFullRender);
            return;
        }

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
                marker = markerSet.createAreaMarker(markerId, "", false, worldName, x, z, false);
            } else {
                marker.setCornerLocations(x, z);
                
                // FIX: Manual World Check without using setWorld() to avoid API conflict
                if (!marker.getWorld().equals(worldName)) {
                    marker.deleteMarker();
                    marker = markerSet.createAreaMarker(markerId, "", false, worldName, x, z, false);
                }
            }
            
            if (marker != null) {
                marker.setDescription(buildPopupHTML(plot));
                
                if (plot.isServerZone()) {
                    marker.setFillStyle(0.35, 0xFF0000);
                    marker.setLineStyle(3, 0.8, 0xFF0000);
                }
            }
        }
        
        for (AreaMarker oldMarker : existingMarkers.values()) {
            oldMarker.deleteMarker();
        }
    }
    
    private String buildPopupHTML(Plot plot) {
        String ownerName = plot.getOwnerName();
        String roleList = plot.getPlayerRoles().entrySet().stream()
            .filter(e -> !e.getValue().equals("owner"))
            .map(e -> {
                OfflinePlayer p = Bukkit.getOfflinePlayer(e.getKey());
                return (p.getName() != null ? p.getName() : "Unknown") + " (" + e.getValue() + ")";
            })
            .collect(Collectors.joining("<br>"));

        if (roleList.isEmpty()) roleList = "None";

        return "<div style=\"font-weight:bold;font-size:120%;\">" +
               ownerName + "'s Plot</div>" +
               "<div style=\"font-weight:bold;\">Roles:</div>" +
               roleList;
    }
}
