package com.aegisguard;

import com.aegisguard.data.Plot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import java.util.List;
import java.util.ArrayList;

public class BannedPlayerListener implements Listener {

    private final AegisGuard plugin;

    public BannedPlayerListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (e.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_BANNED) {
            plugin.runGlobalAsync(() -> {
                // Get all plots for this banned player
                List<Plot> plots = plugin.store().getPlots(e.getUniqueId());
                
                if (plots != null && !plots.isEmpty()) {
                    // Create copy to avoid concurrent modification
                    List<Plot> toRemove = new ArrayList<>(plots);
                    
                    for (Plot plot : toRemove) {
                        // Remove each plot individually using the correct method
                        plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
                    }
                    plugin.getLogger().info("[AegisGuard] Auto-removed " + toRemove.size() + " plots for banned player: " + e.getUniqueId());
                }
            });
        }
    }
}
