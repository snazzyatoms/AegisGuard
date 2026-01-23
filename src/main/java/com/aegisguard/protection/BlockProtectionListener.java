package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.plot.Plot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockProtectionListener implements Listener {

    private final AegisGuard plugin;

    public BlockProtectionListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Plot plot = plugin.getPlotManager().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        if (plugin.isBypassing(e.getPlayer())) return;

        if (!plot.canBuild(e.getPlayer(), "BLOCK_BREAK")) {
            e.setCancelled(true);
            plugin.getMessageManager().send(e.getPlayer(), "cannot_break");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Plot plot = plugin.getPlotManager().getPlotAt(e.getBlock().getLocation());
        if (plot == null) return;

        if (plugin.isBypassing(e.getPlayer())) return;

        if (!plot.canBuild(e.getPlayer(), "BLOCK_PLACE")) {
            e.setCancelled(true);
            plugin.getMessageManager().send(e.getPlayer(), "cannot_place");
        }
    }
}
