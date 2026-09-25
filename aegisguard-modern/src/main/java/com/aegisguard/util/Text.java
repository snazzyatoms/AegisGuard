package com.aegisguard.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * Central text helpers for legacy {@code &} color codes and action-bar/title
 * delivery.
 *
 * <p>The GUI layer still speaks legacy color strings. Keeping every
 * {@code ChatColor.translateAlternateColorCodes} / {@code spigot().sendMessage}
 * call in one place means a future Adventure (Component) migration only has to
 * change this file instead of hundreds of call sites.
 */
public final class Text {

    private Text() {}

    /** Translate {@code &} color codes. Safe for Spigot, Paper, and Folia. */
    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    /** Strip color/format codes from a legacy string. */
    public static String strip(String text) {
        return ChatColor.stripColor(color(text));
    }

    /** Send a legacy-formatted chat message. */
    public static void send(Player player, String text) {
        if (player == null) return;
        player.sendMessage(color(text));
    }

    /**
     * Send a legacy-formatted action-bar message using the Bungee chat API,
     * which is available on Spigot, Paper, and Folia. Falls back to normal
     * chat if the action bar channel is unavailable.
     */
    public static void actionBar(Player player, String text) {
        if (player == null) return;
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(color(text)));
        } catch (Throwable t) {
            player.sendMessage(color(text));
        }
    }

    /**
     * Show a title + subtitle with legacy colors. Centralizes the deprecated
     * {@code sendTitle} call behind one helper.
     */
    public static void title(Player player, String title, String subtitle,
                             int fadeIn, int stay, int fadeOut) {
        if (player == null) return;
        player.sendTitle(color(title), color(subtitle), fadeIn, stay, fadeOut);
    }
}
