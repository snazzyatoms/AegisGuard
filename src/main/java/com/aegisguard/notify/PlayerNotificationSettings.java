package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotEnterEvent;
import com.aegisguard.api.events.PlotLeaveEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.notify.NotificationManager;
import com.aegisguard.notify.NotificationMode;
import com.aegisguard.notify.PlayerNotificationSettings;
import com.aegisguard.util.TeleportUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
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

    /**
     * Stores the last plot UUID the player was in.
     * NOTE: ConcurrentHashMap does NOT allow null values.
     * We treat "not present in map" as "wilderness / no plot".
     */
    private final Map<UUID, UUID> lastPlotId = new ConcurrentHashMap<>();

    public PlotGreetingListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!isEnabled()) return;

        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        UUID pid = (plot == null) ? null : plot.getPlotId();

        if (pid == null) lastPlotId.remove(p.getUniqueId());
        else lastPlotId.put(p.getUniqueId(), pid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!isEnabled()) return;

        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;

        // Anti-spam / performance: plot checks only need X/Z
        if (sameXZBlock(from, to)) return;

        Player player = e.getPlayer();
        UUID playerId = player.getUniqueId();

        Plot fromPlot = plugin.store().getPlotAt(from);
        Plot toPlot = plugin.store().getPlotAt(to);

        UUID fromId = (fromPlot == null) ? null : fromPlot.getPlotId();
        UUID toId = (toPlot == null) ? null : toPlot.getPlotId();

        boolean hadLast = lastPlotId.containsKey(playerId);
        UUID last = hadLast ? lastPlotId.get(playerId) : null;

        // If nothing changed relative to what we last recorded, bail
        if (toId == null) {
            if (!hadLast) return; // wilderness -> wilderness
        } else {
            if (hadLast && Objects.equals(toId, last)) return; // same plot as last recorded
        }

        // If moving into a new plot, validate ENTER first.
        // If ENTER is denied, do NOT fire LEAVE or farewell.
        if (toPlot != null && !Objects.equals(toId, fromId)) {
            PlotEnterEvent enter = new PlotEnterEvent(toPlot, player);

            if (!canEnter(player, toPlot)) {
                enter.setCancelled(true);
            }

            plugin.getServer().getPluginManager().callEvent(enter);

            if (enter.isCancelled()) {
                TeleportUtil.safeTeleport(plugin, player, from);
                return; // Do not update lastPlotId; they did not enter.
            }
        }

        // Leave (only after enter succeeded)
        if (fromPlot != null && !Objects.equals(fromId, toId)) {
            plugin.getServer().getPluginManager().callEvent(new PlotLeaveEvent(fromPlot, player));
            sendFarewell(player, fromPlot);
        }

        // Enter (only if actually changed plot)
        if (toPlot != null && !Objects.equals(toId, fromId)) {
            sendWelcome(player, toPlot);
        }

        // Update last state
        if (toId == null) lastPlotId.remove(playerId);
        else lastPlotId.put(playerId, toId);
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

    private boolean sameXZBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        if (a.getWorld() == null || b.getWorld() == null) return false;
        if (a.getWorld() != b.getWorld()) return false;
        return a.getBlockX() == b.getBlockX() && a.getBlockZ() == b.getBlockZ();
    }

    private boolean canEnter(Player player, Plot plot) {
        if (player == null || plot == null) return true;

        // If entry is allowed, we're done.
        if (plot.getFlag("entry", true)) return true;

        // Admins and bypass can always enter.
        if (plugin.isAdmin(player) || plugin.isBypassing(player) || player.hasPermission("aegis.bypass")) return true;

        // Trusted members can enter even if entry is off.
        return plot.isTrusted(player);
    }

    // ---------------------------------------------------------------------
    // 1.2.6: NotificationManager-backed preferences
    // ---------------------------------------------------------------------

    private PlayerNotificationSettings getSettings(Player player) {
        NotificationManager nm = plugin.getNotificationManager();
        if (nm != null) {
            return nm.getSettings(player.getUniqueId());
        }

        // Fallback (dev safety): mimic defaults
        PlayerNotificationSettings s = new PlayerNotificationSettings(player.getUniqueId());

        try {
            // Optional: read old config values if you still have them during transition
            Object legacy = plugin.getConfig().get("notifications." + player.getUniqueId());
            if (legacy instanceof String str) {
                s.setMode(NotificationMode.fromString(str));
            } else if (legacy instanceof Boolean b) {
                s.setGreetingsEnabled(b);
            }
        } catch (Throwable ignored) {}

        return s;
    }

    private void deliver(Player player, NotificationMode mode, String chatText, String title, String subtitle) {
        if (mode == null) mode = NotificationMode.CHAT;

        switch (mode) {
            case ACTION_BAR -> sendActionBar(player, chatText);
            case TITLE -> {
                String t = (title == null || title.trim().isEmpty()) ? "&bEntering" : title;
                String sub = (subtitle == null || subtitle.trim().isEmpty()) ? (chatText == null ? "" : chatText) : subtitle;
                player.sendTitle(color(t), color(sub), 10, 40, 10);
            }
            case CHAT -> {
                if (chatText != null && !chatText.trim().isEmpty()) {
                    player.sendMessage(color(chatText));
                }
            }
        }
    }

    private void sendActionBar(Player player, String msg) {
        if (msg == null) msg = "";
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(color(msg)));
        } catch (Throwable t) {
            player.sendMessage(color(msg));
        }
    }

    private void sendWelcome(Player player, Plot plot) {
        PlayerNotificationSettings settings = getSettings(player);
        if (settings != null && !settings.isGreetingsEnabled()) return;

        NotificationMode mode = (settings == null) ? NotificationMode.CHAT : settings.getMode();

        String msg = plot.getWelcomeMessage();
        if (msg == null || msg.trim().isEmpty()) {
            msg = tr(player,
                    "greetings.enter",
                    "&bEntering: &f{OWNER}&b's claim",
                    Map.of("OWNER", plot.getOwnerName()));
        }

        String title = null;
        String sub = null;
        if (mode == NotificationMode.TITLE) {
            title = plot.getEntryTitle();
            sub = plot.getEntrySubtitle();
            if (title == null || title.trim().isEmpty()) title = "&bEntering";
            if (sub == null || sub.trim().isEmpty()) sub = msg;
        }

        deliver(player, mode, msg, title, sub);
    }

    private void sendFarewell(Player player, Plot plot) {
        PlayerNotificationSettings settings = getSettings(player);
        if (settings != null && !settings.isGreetingsEnabled()) return;

        NotificationMode mode = (settings == null) ? NotificationMode.CHAT : settings.getMode();

        String msg = plot.getFarewellMessage();
        if (msg == null || msg.trim().isEmpty()) {
            msg = tr(player,
                    "greetings.leave",
                    "&7Leaving: &f{OWNER}&7's claim",
                    Map.of("OWNER", plot.getOwnerName()));
        }

        String title = null;
        String sub = null;
        if (mode == NotificationMode.TITLE) {
            title = "&7Leaving";
            sub = msg;
        }

        deliver(player, mode, msg, title, sub);
    }

    private String tr(Player player, String key, String fallback, Map<String, String> placeholders) {
        try {
            if (plugin.codex() != null) {
                String v = plugin.codex().tr(player, key, placeholders);
                if (v != null && !v.trim().isEmpty()) return v;
            }
        } catch (Throwable ignored) {}

        String out = fallback == null ? "" : fallback;
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
