package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotEnterEvent;
import com.aegisguard.api.events.PlotLeaveEvent;
import com.aegisguard.data.Plot;
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

    /**
     * Prime the cache so players don't get a "Leaving/Entering" burst
     * the moment they wiggle after joining.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Plot plot = plugin.store().getPlotAt(p.getLocation());
        UUID pid = (plot == null) ? null : plot.getPlotId();

        if (pid == null) lastPlotId.remove(p.getUniqueId());
        else lastPlotId.put(p.getUniqueId(), pid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
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
        // Presentation only. ProtectionManager owns authoritative enter/leave events
        // and entry denial, while this listener handles titles/chat/action bar delivery
        // after movement has already been accepted.
        if (fromPlot != null && !Objects.equals(fromId, toId)) {
            sendFarewell(player, fromPlot);
        }

        if (toPlot != null && !Objects.equals(toId, fromId)) {
            sendWelcome(player, toPlot);
            com.aegisguard.visualization.VisualPresence.showEntry(plugin, player, toPlot);
        }

        // Update last state: remove for wilderness, store for plots
        if (toId == null) lastPlotId.remove(playerId);
        else lastPlotId.put(playerId, toId);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastPlotId.remove(e.getPlayer().getUniqueId());
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

        // Trusted members (roles) can enter even if entry is off.
        return plot.isTrusted(player);
    }

    // ---------------------------------------------------------------------
    // 1.2.6 QoL: Per-player notification controls (greetings toggle + mode)
    // ---------------------------------------------------------------------

    private PlayerNotificationSettings getNotificationSettings(Player player) {
        UUID uuid = player.getUniqueId();

        // ✅ Preferred source: NotificationManager (single truth)
        try {
            if (plugin.getNotificationManager() != null) {
                PlayerNotificationSettings s = plugin.getNotificationManager().getSettings(uuid);
                if (s != null) return s;
            }
        } catch (Throwable ignored) {}

        // Fallback: read config directly (keeps compatibility)
        String base = "player_notifications.players." + uuid;

        boolean greetings = plugin.getConfig().getBoolean(base + ".greetings", true);
        boolean adminUpdates = plugin.getConfig().getBoolean(base + ".admin_updates", true);

        // ✅ Align default with SettingsGUI + PlayerNotificationSettings
        String modeRaw = plugin.getConfig().getString(base + ".mode", "ACTION_BAR");

        // Legacy compatibility (old: notifications.<uuid> could be boolean OR string)
        Object legacy = plugin.getConfig().get("notifications." + uuid);
        if (legacy instanceof Boolean) {
            greetings = (Boolean) legacy;
        } else if (legacy instanceof String) {
            modeRaw = (String) legacy;
        }

        NotificationMode mode = NotificationMode.fromString(modeRaw);
        return new PlayerNotificationSettings(mode, greetings, adminUpdates);
    }

    private void deliver(Player player, NotificationMode mode, String chatText, String title, String subtitle) {
        if (mode == null) mode = NotificationMode.ACTION_BAR;

        switch (mode) {
            case ACTION_BAR -> sendActionBar(player, chatText);

            case TITLE -> {
                String t = (title == null || title.trim().isEmpty()) ? "&bEntering" : title;
                String sub = (subtitle == null || subtitle.trim().isEmpty())
                        ? (chatText == null ? "" : chatText)
                        : subtitle;
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
            // Fallback to chat if actionbar isn't available for some reason
            player.sendMessage(color(msg));
        }
    }

    private void sendWelcome(Player player, Plot plot) {
        String msg = plot.getWelcomeMessage();
        if (!isConfiguredGreeting(msg, "greetings.enter")) return;

        PlayerNotificationSettings settings = getNotificationSettings(player);
        if (!shouldSendGreeting(player, settings, true)) return;

        NotificationMode mode = resolveMode(settings);

        String title = null;
        String sub = null;

        // If TITLE mode, prefer plot-configured title/subtitle.
        if (shouldSendTitles() && mode == NotificationMode.TITLE) {
            title = plot.getEntryTitle();
            sub = plot.getEntrySubtitle();

            if (title == null || title.trim().isEmpty()) title = "&bEntering";
            if (sub == null || sub.trim().isEmpty()) sub = msg;
        }

        deliverGreeting(player, mode, msg, title, sub);
    }

    private void sendFarewell(Player player, Plot plot) {
        String msg = plot.getFarewellMessage();
        if (!isConfiguredGreeting(msg, "greetings.leave")) return;

        PlayerNotificationSettings settings = getNotificationSettings(player);
        if (!shouldSendGreeting(player, settings, false)) return;

        NotificationMode mode = resolveMode(settings);

        String title = null;
        String sub = null;

        // For TITLE mode, show a simple leave title
        if (shouldSendTitles() && mode == NotificationMode.TITLE) {
            title = "&7Leaving";
            sub = msg;
        }

        deliverGreeting(player, mode, msg, title, sub);
    }

    private void deliverGreeting(Player player, NotificationMode mode, String message, String title, String subtitle) {
        if (mode == NotificationMode.CHAT && !shouldSendChat()) return;
        if (mode == NotificationMode.TITLE && !shouldSendTitles()) return;
        deliver(player, mode, message, title, subtitle);
    }

    private boolean shouldSendGreeting(Player player, PlayerNotificationSettings settings, boolean entering) {
        String base = "titles.claim_enter_exit";

        if (!plugin.getConfig().getBoolean(base + ".enabled", true)) {
            return hasBypassPermission(player);
        }

        if (entering && !plugin.getConfig().getBoolean(base + ".show_enter", true)) return false;
        if (!entering && !plugin.getConfig().getBoolean(base + ".show_exit", true)) return false;

        String requiredPermission = plugin.getConfig().getString(base + ".required_permission", "");
        if (requiredPermission != null && !requiredPermission.isBlank() && !player.hasPermission(requiredPermission)) {
            return false;
        }

        String mode = plugin.getConfig().getString(base + ".mode", "PER_PLAYER").toUpperCase();
        if ("FORCE_ON".equals(mode)) return true;
        if ("FORCE_OFF".equals(mode)) return hasBypassPermission(player);

        if (!plugin.getConfig().getBoolean(base + ".allow_player_toggle", true)) {
            return plugin.getConfig().getBoolean(base + ".default_player_setting", true);
        }

        return settings == null || settings.greetingsEnabled();
    }

    private NotificationMode resolveMode(PlayerNotificationSettings settings) {
        if (settings != null && settings.getMode() != null) {
            return settings.getMode();
        }

        String configured = plugin.getConfig().getString("titles.claim_enter_exit.notification_location",
                plugin.getConfig().getString("titles.notification_location", "ACTION_BAR"));
        return NotificationMode.fromString(configured);
    }

    private boolean shouldSendChat() {
        return plugin.getConfig().getBoolean("titles.claim_enter_exit.show_chat", true);
    }

    private boolean shouldSendTitles() {
        return plugin.getConfig().getBoolean("titles.claim_enter_exit.show_titles", true);
    }

    private boolean hasBypassPermission(Player player) {
        String bypassPermission = plugin.getConfig().getString("titles.claim_enter_exit.bypass_permission", "aegis.notify.bypass");
        return bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission);
    }

    private boolean isConfiguredGreeting(String message, String legacyKey) {
        return message != null
                && !message.isBlank()
                && !message.trim().equalsIgnoreCase(legacyKey);
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
