package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;

public class MapHookManager {

    private final AegisGuard plugin;
    private DynmapHook dynmap;
    private BlueMapHook blueMap;
    // private Pl3xMapHook pl3xMap; // TODO: Ensure Pl3xMapHook class exists/updated

    public MapHookManager(AegisGuard plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        // 1. Check for Dynmap
        if (Bukkit.getPluginManager().isPluginEnabled("dynmap")) {
            // v1.3.0: Updated config path to 'maps.dynmap.enabled'
            if (plugin.getConfig().getBoolean("maps.dynmap.enabled", true)) {
                this.dynmap = new DynmapHook(plugin);
                plugin.getLogger().info("Hooked into Dynmap.");
            }
        }

        // 2. Check for BlueMap (Wrapped in try-catch for NoClassDefFoundError)
        if (Bukkit.getPluginManager().isPluginEnabled("BlueMap")) {
            if (plugin.cfg().isBlueMapEnabled()) { // Uses new AGConfig getter
                try {
                    this.blueMap = new BlueMapHook(plugin);
                    plugin.getLogger().info("Hooked into BlueMap!");
                } catch (NoClassDefFoundError | Exception e) { 
                    plugin.getLogger().warning("BlueMap detected but API failed to initialize.");
                }
            }
        }

        // 3. Check for Pl3xMap / Squaremap
        if (Bukkit.getPluginManager().isPluginEnabled("Pl3xMap") || Bukkit.getPluginManager().isPluginEnabled("Squaremap")) {
            if (plugin.cfg().isPl3xMapEnabled()) { // Uses new AGConfig getter
                try {
                    // this.pl3xMap = new Pl3xMapHook(plugin);
                    // plugin.getLogger().info("Hooked into Pl3xMap!");
                } catch (NoClassDefFoundError | Exception e) {
                    plugin.getLogger().warning("Pl3xMap detected but API failed to initialize.");
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
            try { dynmap.update(); } catch (Exception e) { plugin.getLogger().severe("Dynmap update failed!"); }
        }
        if (blueMap != null) {
             try { blueMap.update(); } catch (Exception e) { plugin.getLogger().severe("BlueMap update failed!"); }
        }
        // if (pl3xMap != null) {
        //    try { pl3xMap.update(); } catch (Exception e) { plugin.getLogger().severe("Pl3xMap update failed!"); }
        // }
    }
}
