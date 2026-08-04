package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;

public class MapHookManager {

    private final AegisGuard plugin;
    private DynmapHook dynmap;
    private BlueMapHook blueMap;
    private Pl3xMapHook pl3xMap;

    public MapHookManager(AegisGuard plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        // 1. Check for Dynmap
        if (Bukkit.getPluginManager().isPluginEnabled("dynmap")) {
            if (plugin.cfg().raw().getBoolean("hooks.dynmap.enabled", false)) {
                // Initialize DynmapHook
                this.dynmap = new DynmapHook(plugin);
            }
        }

        // 2. Check for BlueMap (Wrapped in try-catch for NoClassDefFoundError)
        if (Bukkit.getPluginManager().isPluginEnabled("BlueMap")) {
            if (plugin.cfg().raw().getBoolean("hooks.bluemap.enabled", false)) {
                try {
                    this.blueMap = new BlueMapHook(plugin);
                    plugin.console().info("log_map_bluemap_hooked", "Hooked into BlueMap!");
                } catch (NoClassDefFoundError | Exception e) { // Catch both direct class errors and initialization errors
                    plugin.console().warning("log_map_bluemap_failed", "BlueMap detected but API failed to initialize.");
                }
            }
        }

        // 3. Check for Pl3xMap / Squaremap (Wrapped in try-catch for NoClassDefFoundError)
        if (Bukkit.getPluginManager().isPluginEnabled("Pl3xMap") || Bukkit.getPluginManager().isPluginEnabled("Squaremap")) {
            if (plugin.cfg().raw().getBoolean("hooks.pl3xmap.enabled", false)) {
                try {
                    this.pl3xMap = new Pl3xMapHook(plugin);
                    plugin.console().info("log_map_pl3xmap_hooked", "Hooked into Pl3xMap!");
                } catch (NoClassDefFoundError | Exception e) {
                    plugin.console().warning("log_map_pl3xmap_failed", "Pl3xMap detected but API failed to initialize.");
                }
            }
        }
    }

    /**
     * Public method to force all active hooks to re-render or reload settings.
     * This method is called from /agadmin reload.
     */
    public void reload() {
        // Reload all maps if active. We catch errors on the reload as well.
        if (dynmap != null) {
            try { dynmap.update(); } catch (Exception e) { plugin.console().severe("log_map_dynmap_update_failed", "Dynmap update failed!"); }
        }
        if (blueMap != null) {
             try { blueMap.update(); } catch (Exception e) { plugin.console().severe("log_map_bluemap_update_failed", "BlueMap update failed!"); }
        }
        if (pl3xMap != null) {
            try { pl3xMap.update(); } catch (Exception e) { plugin.console().severe("log_map_pl3xmap_update_failed", "Pl3xMap update failed!"); }
        }
    }
}
