package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExhaustionEvent;

/**
 * Cancels exhaustion so saturation and hunger do not drain inside a server-plot
 * sanctuary. Loaded reflectively so AegisGuard still enables on 1.20.0–1.20.2,
 * where {@link EntityExhaustionEvent} does not exist. Folia-safe: the event
 * runs on the player's region.
 */
public final class SanctuaryExhaustionListener implements Listener {
    private final AegisGuard plugin;

    public SanctuaryExhaustionListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSanctuaryExhaustion(EntityExhaustionEvent e) {
        Player player = e.getEntity();
        if (player == null) return;
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (!plugin.protection().keepsHunger(plot)) return;
        e.setCancelled(true);
    }
}
