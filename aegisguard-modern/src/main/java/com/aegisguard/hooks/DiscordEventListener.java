package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotClaimEvent;
import com.aegisguard.api.events.PlotDeleteEvent;
import com.aegisguard.data.Plot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;

/**
 * Posts claim lifecycle events to the configured Discord webhook. Registration
 * is harmless when hooks.discord is off — {@link DiscordWebhook#sendEvent}
 * fails closed per-event and per-hook.
 */
public class DiscordEventListener implements Listener {

    private static final int GREEN = 0x2ECC71;
    private static final int RED = 0xE74C3C;

    private final AegisGuard plugin;

    public DiscordEventListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlotClaim(PlotClaimEvent event) {
        if (plugin.getDiscord() == null) return;
        Plot plot = event.getPlot();
        String actor = event.getPlayer() == null ? "System" : event.getPlayer().getName();
        plugin.getDiscord().sendEventKey("claim_create",
                "discord_event_claim_create_title", "Plot claimed",
                "discord_event_claim_create_description", "{PLAYER} claimed {PLOT} in {WORLD}.",
                Map.of(
                        "PLAYER", actor,
                        "PLOT", plotName(plot),
                        "WORLD", plot == null || plot.getWorldName() == null ? "?" : plot.getWorldName()),
                GREEN);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlotDelete(PlotDeleteEvent event) {
        if (plugin.getDiscord() == null) return;
        Plot plot = event.getPlot();
        plugin.getDiscord().sendEventKey("claim_delete",
                "discord_event_claim_delete_title", "Plot deleted",
                "discord_event_claim_delete_description", "{PLOT} owned by {OWNER} was deleted.",
                Map.of(
                        "PLOT", plotName(plot),
                        "OWNER", plot == null || plot.getOwnerName() == null ? "?" : plot.getOwnerName()),
                RED);
    }

    private static String plotName(Plot plot) {
        if (plot == null) return "?";
        String name = plot.getPlotName();
        return name == null || name.isBlank() ? plot.getPlotId().toString() : name;
    }
}
