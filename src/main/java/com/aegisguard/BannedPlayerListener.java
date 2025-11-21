package com.aegisguard;

import com.aegisguard.data.Plot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.ArrayList;
import java.util.List;

public class BannedPlayerListener implements Listener {

    private final AegisGuard plugin;

    public BannedPlayerListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_BANNED) {

            plugin.runGlobalAsync(() -> {
                // FIX: Use standard iteration instead of missing 'removePlots' method
                List<Plot> plots = plugin.store().getPlots(e.getUniqueId());
                
                if (plots != null && !plots.isEmpty()) {
                    // Create a copy to avoid concurrent modification exceptions
                    List<Plot> toRemove = new ArrayList<>(plots);
                    
                    for (Plot plot : toRemove) {
                        plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
                    }
                    
                    plugin.getLogger().info("[AegisGuard] Auto-removed " + toRemove.size() + " plots for banned player: " + e.getUniqueId());
                }
            });
        }
    }
}
