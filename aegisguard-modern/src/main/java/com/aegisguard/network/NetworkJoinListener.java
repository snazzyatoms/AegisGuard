package com.aegisguard.network;

import com.aegisguard.AegisGuard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Join/quit hooks for the network layer: pull shared player data, verify the
 * proxy's server list once, and land pending cross-server arrivals.
 */
public final class NetworkJoinListener implements Listener {

    private final AegisGuard plugin;

    public NetworkJoinListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.network() == null || !plugin.network().isNetworked()) return;
        // Player data pull can happen immediately (async inside).
        if (plugin.networkPlayerData() != null) {
            plugin.networkPlayerData().pullOnJoin(event.getPlayer());
        }
        // One-shot self-check: does the proxy actually know us by this name?
        plugin.network().verifyServerName();
        // Arrival landing is delayed a touch so the join finishes first.
        if (plugin.networkTravel() != null) {
            plugin.scheduler().runEntityLater(event.getPlayer(),
                    () -> plugin.networkTravel().handleJoin(event.getPlayer()), null, 20L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.networkPlayerData() == null) return;
        // Flush the row before they can land on another backend.
        plugin.networkPlayerData().pushNow(event.getPlayer().getUniqueId());
        plugin.networkPlayerData().evictOnQuit(event.getPlayer().getUniqueId());
    }
}
