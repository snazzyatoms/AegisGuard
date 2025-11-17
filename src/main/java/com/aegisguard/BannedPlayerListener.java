package com.aegisguard;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.concurrent.CompletableFuture;

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

            // --- IMPROVEMENT ---
            // The event is already async, but we want to run our *own* async task
            // to avoid holding up the login thread, and to be consistent
            // with the /aegis admin cleanup command.
            CompletableFuture.runAsync(() -> {
                // This is now running on a separate thread
                plugin.store().removeAllPlots(e.getUniqueId());

                // Logging is thread-safe
                plugin.getLogger().info("[AegisGuard] Auto-removed plots for banned player (on login): " + e.getUniqueId());

            }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)); // Spigot's async executor
        }
    }
}
