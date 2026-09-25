package com.aegisguard.gatherings;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotEnterEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Grants optional visitor passes when someone walks into a live Open House. */
public final class GatheringListener implements Listener {

    private final AegisGuard plugin;

    public GatheringListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlotEnter(PlotEnterEvent event) {
        GatheringService service = plugin.gatherings();
        if (service == null || !service.isEnabled()) return;
        service.welcomeVisitor(event.getPlayer(), event.getPlot());
    }
}
