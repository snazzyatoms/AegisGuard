package com.aegisguard; // Make sure this matches your actual package structure (e.g. com.aegisguard.listeners)

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * Handles logic related to banned players, specifically auto-removing plots.
 * This cleans up the main plugin class.
 */
public class BannedPlayerListener implements Listener {

    private final AegisGuard plugin;

    public BannedPlayerListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Listens for a banned player trying to log in.
     * When detected, this will fire an ASYNC task to remove their plots.
     */
    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_BANNED) {

            // --- FIX: Use plugin's internal helper ---
            // This replaces the incompatible "getMainThreadExecutor" call.
            // It automatically handles Folia vs Bukkit scheduling.
            plugin.runGlobalAsync(() -> {
                
                // Remove plots from data store
                plugin.store().removePlots(e.getUniqueId()); // Ensure method name matches IDataStore (removePlots vs removeAllPlots)

                plugin.getLogger().info("[AegisGuard] Auto-removed plots for banned player (on login): " + e.getUniqueId());
            });
        }
    }
}
