package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotEnterEvent;
import com.aegisguard.api.events.PlotLeaveEvent;
import com.aegisguard.data.Plot;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlotGreetingListener implements Listener {

    private final AegisGuard plugin;

    // Tracks the last plot the player was in (by plotId). Prevents spam.
    private final Map<UUID, UUID> lastPlotId = new ConcurrentHashMap<>();

    public PlotGreetingListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        // Skip micro-moves (most important anti-spam)
        if (from.getWorld() == to.getWorld()
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = e.getPlayer();

        Plot fromPlot = plugin.data().getPlotAt(from);
        Plot toPlot = plugin.data().getPlotAt(to);

        UUID fromId = (fromPlot == null) ? null : fromPlot.getPlotId();
        UUID toId = (toPlot == null) ? null : toPlot.getPlotId();

        // If nothing changed, bail (extra anti-spam safety)
        UUID last = lastPlotId.get(player.getUniqueId());
        if ((toId == null && last == null) || (toId != null && toId.equals(last))) {
            return;
        }

        // Leaving a plot
        if (fromPlot != null && (toPlot == null || !fromId.equals(toId))) {
            plugin.getServer().getPluginManager().callEvent(new PlotLeaveEvent(fromPlot, player));
            sendFarewell(player, fromPlot);
        }

        // Entering a plot
        if (toPlot != null && (fromPlot == null || !toId.equals(fromId))) {
            PlotEnterEvent enter = new PlotEnterEvent(toPlot, player);
            plugin.getServer().getPluginManager().callEvent(enter);

            // If someone cancels entry, bounce them back
            if (enter.isCancelled()) {
                player.teleportAsync(from); // Paper/Folia safe
                return;
            }

            sendWelcome(player, toPlot);
        }

        // Update tracker
        lastPlotId.put(player.getUniqueId(), toId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastPlotId.remove(e.getPlayer().getUniqueId());
    }

    private void sendWelcome(Player player, Plot plot) {
        String msg = plot.getWelcomeMessage();

        // Default fallback if owner never set one
        if (msg == null || msg.isBlank()) {
            msg = "&bEntering: &f" + plot.getOwnerName() + "&b's claim";
        }

        player.sendMessage(color(msg));

        // Optional: if you want Titles too, use entryTitle/entrySubtitle when present
        String title = plot.getEntryTitle();
        String sub = plot.getEntrySubtitle();
        if (title != null && !title.isBlank()) {
            player.sendTitle(color(title), color(sub == null ? "" : sub), 10, 40, 10);
        }
    }

    private void sendFarewell(Player player, Plot plot) {
        String msg = plot.getFarewellMessage();

        if (msg == null || msg.isBlank()) {
            msg = "&7Leaving: &f" + plot.getOwnerName() + "&7's claim";
        }

        player.sendMessage(color(msg));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
