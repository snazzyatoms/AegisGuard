package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotEnterEvent;
import com.aegisguard.api.events.PlotLeaveEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.util.TeleportUtil;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlotGreetingListener implements Listener {

    private final AegisGuard plugin;
    private final Map<UUID, UUID> lastPlotId = new ConcurrentHashMap<>();

    public PlotGreetingListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Prime the cache so players don't get a "Leaving/Entering" burst
     * the moment they wiggle their mouse after joining.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!isEnabled()) return;

        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        lastPlotId.put(p.getUniqueId(), plot == null ? null : plot.getPlotId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!isEnabled()) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        // Anti-spam: ignore same-block moves
        if (sameBlock(from, to)) return;

        Player player = e.getPlayer();

        Plot fromPlot = plugin.store().getPlotAt(from);
        Plot toPlot = plugin.store().getPlotAt(to);

        UUID fromId = (fromPlot == null) ? null : fromPlot.getPlotId();
        UUID toId = (toPlot == null) ? null : toPlot.getPlotId();

        UUID last = lastPlotId.get(player.getUniqueId());

        // Nothing changed relative to what we last recorded
        if (Objects.equals(toId, last)) return;

        // --- Leave ---
        if (fromPlot != null && !Objects.equals(fromId, toId)) {
            plugin.getServer().getPluginManager().callEvent(new PlotLeaveEvent(fromPlot, player));
            sendFarewell(player, fromPlot);
        }

        // --- Enter ---
        if (toPlot != null && !Objects.equals(toId, fromId)) {
            PlotEnterEvent enter = new PlotEnterEvent(toPlot, player);

            // Optional hard rule: respect plot entry flag for non-trusted players
            if (!canEnter(player, toPlot)) {
                enter.setCancelled(true);
            }

            plugin.getServer().getPluginManager().callEvent(enter);

            if (enter.isCancelled()) {
                // ✅ 1.16+ compatible + Folia-safe via your util
                TeleportUtil.safeTeleport(plugin, player, from);
                // Do NOT update lastPlotId; they didn't actually enter.
                return;
            }

            sendWelcome(player, toPlot);
        }

        lastPlotId.put(player.getUniqueId(), toId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastPlotId.remove(e.getPlayer().getUniqueId());
    }

    private boolean isEnabled() {
        try {
            return plugin.cfg() == null || plugin.cfg().raw().getBoolean("greetings.enabled", true);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean sameBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        if (a.getWorld() != b.getWorld()) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private boolean canEnter(Player player, Plot plot) {
        if (player == null || plot == null) return true;

        // If entry is allowed, we're done.
        if (plot.getFlag("entry", true)) return true;

        // Admins can always enter.
        if (plugin.isAdmin(player) || player.hasPermission("aegis.bypass")) return true;

        // Trusted members (roles) can enter even if entry is off.
        return plot.isTrusted(player);
    }

    private void sendWelcome(Player player, Plot plot) {
        // Don’t message the owner unless you want that:
        // if (plot.getOwner().equals(player.getUniqueId())) return;

        String msg = plot.getWelcomeMessage();
        if (msg == null || msg.trim().isEmpty()) {
            // Prefer Codex if available, otherwise fallback
            msg = tr(player,
                    "greetings.enter",
                    "&bEntering: &f{OWNER}&b's claim",
                    Map.of("OWNER", plot.getOwnerName()));
        }

        player.sendMessage(color(msg));

        // Optional title/subtitle if you want (uses plot fields)
        String title = plot.getEntryTitle();
        String sub = plot.getEntrySubtitle();
        if (title != null && !title.trim().isEmpty()) {
            player.sendTitle(color(title), color(sub == null ? "" : sub), 10, 40, 10);
        }
    }

    private void sendFarewell(Player player, Plot plot) {
        String msg = plot.getFarewellMessage();
        if (msg == null || msg.trim().isEmpty()) {
            msg = tr(player,
                    "greetings.leave",
                    "&7Leaving: &f{OWNER}&7's claim",
                    Map.of("OWNER", plot.getOwnerName()));
        }
        player.sendMessage(color(msg));
    }

    private String tr(Player player, String key, String fallback, Map<String, String> placeholders) {
        try {
            if (plugin.codex() != null) {
                String v = plugin.codex().tr(player, key, placeholders);
                if (v != null && !v.trim().isEmpty()) return v;
            }
        } catch (Throwable ignored) {}
        // apply placeholders to fallback too
        String out = fallback;
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
