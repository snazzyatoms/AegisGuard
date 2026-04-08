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
        if (dynmap == null && isPluginEnabled("Dynmap", "dynmap")) {
            if (plugin.cfg().raw().getBoolean("hooks.dynmap.enabled", true)) {
                // Initialize DynmapHook
                this.dynmap = new DynmapHook(plugin);
            }
        }

        // 2. Check for BlueMap (Wrapped in try-catch for NoClassDefFoundError)
        if (blueMap == null && isPluginEnabled("BlueMap")) {
            if (plugin.cfg().raw().getBoolean("hooks.bluemap.enabled", true)) {
                try {
                    this.blueMap = new BlueMapHook(plugin);
                    plugin.getLogger().info("Hooked into BlueMap!");
                } catch (NoClassDefFoundError | Exception e) { // Catch both direct class errors and initialization errors
                    plugin.getLogger().warning("BlueMap detected but API failed to initialize.");
                }
            }
        }

        // 3. Check for Pl3xMap / Squaremap (Wrapped in try-catch for NoClassDefFoundError)
        if (pl3xMap == null && isPluginEnabled("Pl3xMap", "Squaremap", "squaremap")) {
            if (plugin.cfg().raw().getBoolean("hooks.pl3xmap.enabled", true)) {
                try {
                    this.pl3xMap = new Pl3xMapHook(plugin);
                    plugin.getLogger().info("Hooked into Pl3xMap!");
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
        reloadDynmapHook();
        reloadBlueMapHook();
        reloadPl3xMapHook();

        if (dynmap != null) {
            try { dynmap.update(); } catch (Exception e) { plugin.getLogger().severe("Dynmap update failed!"); }
        }
        if (blueMap != null) {
             try { blueMap.update(); } catch (Exception e) { plugin.getLogger().severe("BlueMap update failed!"); }
        }
        if (pl3xMap != null) {
            try { pl3xMap.update(); } catch (Exception e) { plugin.getLogger().severe("Pl3xMap update failed!"); }
        }
    }

    private void reloadDynmapHook() {
        boolean enabled = isPluginEnabled("Dynmap", "dynmap")
                && plugin.cfg().raw().getBoolean("hooks.dynmap.enabled", true);
        if (!enabled) {
            if (dynmap != null) {
                dynmap.shutdown();
                dynmap = null;
            }
            return;
        }

        if (dynmap != null) {
            dynmap.shutdown();
            dynmap = null;
        }
        dynmap = new DynmapHook(plugin);
    }

    private void reloadBlueMapHook() {
        boolean enabled = isPluginEnabled("BlueMap")
                && plugin.cfg().raw().getBoolean("hooks.bluemap.enabled", true);
        if (!enabled) {
            if (blueMap != null) {
                blueMap.shutdown();
                blueMap = null;
            }
            return;
        }

        if (blueMap != null) {
            blueMap.shutdown();
            blueMap = null;
        }
        try {
            blueMap = new BlueMapHook(plugin);
            plugin.getLogger().info("Hooked into BlueMap!");
        } catch (NoClassDefFoundError | Exception e) {
            blueMap = null;
            plugin.getLogger().warning("BlueMap detected but API failed to initialize.");
        }
    }

    private void reloadPl3xMapHook() {
        boolean enabled = isPluginEnabled("Pl3xMap", "Squaremap", "squaremap")
                && plugin.cfg().raw().getBoolean("hooks.pl3xmap.enabled", true);
        if (!enabled) {
            if (pl3xMap != null) {
                pl3xMap.shutdown();
                pl3xMap = null;
            }
            return;
        }

        if (pl3xMap != null) {
            pl3xMap.shutdown();
            pl3xMap = null;
        }
        try {
            pl3xMap = new Pl3xMapHook(plugin);
            plugin.getLogger().info("Hooked into Pl3xMap!");
        } catch (NoClassDefFoundError | Exception e) {
            pl3xMap = null;
            plugin.getLogger().warning("Pl3xMap detected but API failed to initialize.");
        }
    }

    private boolean isPluginEnabled(String... names) {
        if (names == null) return false;
        for (String name : names) {
            if (name != null && !name.isBlank() && Bukkit.getPluginManager().isPluginEnabled(name)) {
                return true;
            }
        }
        return false;
    }
}
