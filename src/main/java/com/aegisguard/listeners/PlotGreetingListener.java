package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotEnterEvent;
import com.aegisguard.api.events.PlotLeaveEvent;
import com.aegisguard.data.Plot;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;

public class PlotGreetingListener implements Listener {

    private final AegisGuard plugin;

    public PlotGreetingListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnter(PlotEnterEvent e) {
        Plot plot = e.getPlot();
        var p = e.getPlayer();

        // Title/Sub (from plot custom OR codex default)
        String title = (plot.getEntryTitle() != null && !plot.getEntryTitle().isBlank())
                ? plot.getEntryTitle()
                : plugin.codex().tr(p, "title_entering", Map.of("PLOT_NAME", safe(plot), "OWNER", plot.getOwnerName()));

        String sub = (plot.getEntrySubtitle() != null && !plot.getEntrySubtitle().isBlank())
                ? plot.getEntrySubtitle()
                : plugin.codex().tr(p, "subtitle_entering", Map.of("PLOT_NAME", safe(plot), "OWNER", plot.getOwnerName()));

        p.sendTitle(cc(title), cc(sub), 10, 40, 10);

        // Welcome message (plot custom, optional)
        String welcome = plot.getWelcomeMessage();
        if (welcome != null && !welcome.isBlank()) {
            p.sendMessage(cc(welcome
                    .replace("{PLOT}", safe(plot))
                    .replace("{OWNER}", plot.getOwnerName() == null ? "" : plot.getOwnerName())
            ));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(PlotLeaveEvent e) {
        Plot plot = e.getPlot();
        var p = e.getPlayer();

        String farewell = plot.getFarewellMessage();
        if (farewell != null && !farewell.isBlank()) {
            p.sendMessage(cc(farewell
                    .replace("{PLOT}", safe(plot))
                    .replace("{OWNER}", plot.getOwnerName() == null ? "" : plot.getOwnerName())
            ));
        }
    }

    private String safe(Plot plot) {
        // if you later add plot names, swap this
        return (plot.getOwnerName() != null ? plot.getOwnerName() : "Unknown") + "'s Claim";
    }

    private String cc(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
