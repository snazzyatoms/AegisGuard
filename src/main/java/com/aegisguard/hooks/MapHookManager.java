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
            if (plugin.cfg().raw().getBoolean("hooks.dynmap.enabled", true)) {
                this.dynmap = new DynmapHook(plugin);
            }
        }

        // 2. Check for BlueMap
        if (Bukkit.getPluginManager().isPluginEnabled("BlueMap")) {
            // Safe config check (defaults to true if missing)
            if (plugin.cfg().raw().getBoolean("hooks.bluemap.enabled", true)) {
                try {
                    this.blueMap = new BlueMapHook(plugin);
                    plugin.getLogger().info("Hooked into BlueMap!");
                } catch (NoClassDefFoundError e) {
                    plugin.getLogger().warning("BlueMap detected but API missing.");
                }
            }
        }

        // 3. Check for Pl3xMap / Squaremap
        if (Bukkit.getPluginManager().isPluginEnabled("Pl3xMap") || Bukkit.getPluginManager().isPluginEnabled("Squaremap")) {
            if (plugin.cfg().raw().getBoolean("hooks.pl3xmap.enabled", true)) {
                try {
                    this.pl3xMap = new Pl3xMapHook(plugin);
                    plugin.getLogger().info("Hooked into Pl3xMap!");
                } catch (NoClassDefFoundError e) {
                    plugin.getLogger().warning("Pl3xMap detected but API missing.");
                }
            }
        }
    }

    public void reload() {
        // Trigger updates for all active hooks
        if (dynmap != null) dynmap.update(); // You need to add update() to DynmapHook
        if (blueMap != null) blueMap.update();
        if (pl3xMap != null) pl3xMap.update();
    }
}
