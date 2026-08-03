package com.aegisguard.guidance;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotClaimEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Milestone 5 (Clearer Player Guidance) - opens the optional first-claim walkthrough the first
 * time a player successfully claims a plot of their own.
 *
 * Listens on {@link PlotClaimEvent} rather than hard-coding a hook inside
 * {@code SelectionService}, so admin-created server zones (which fire the same event with a
 * different owner semantic) and any future claim paths are covered automatically. The event is
 * observed only - never cancelled - and the actual "has this player claimed before" check and
 * "have they seen it" gate both live in {@link FirstClaimWalkthroughGUI}, so this listener stays
 * a thin trigger.
 */
public class FirstClaimGuidanceListener implements Listener {

    private final AegisGuard plugin;

    public FirstClaimGuidanceListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlotClaim(PlotClaimEvent event) {
        Player player = event.getPlayer();
        if (player == null || !player.isOnline()) return;

        // The plot is persisted synchronously right after this event fires, so a short delay
        // guarantees the claim (and any confirmation messages) land before the walkthrough opens.
        plugin.runEntityLater(player, () -> {
            if (player.isOnline()) {
                plugin.gui().walkthrough().openIfFirstClaim(player);
            }
        }, 20L);
    }
}
