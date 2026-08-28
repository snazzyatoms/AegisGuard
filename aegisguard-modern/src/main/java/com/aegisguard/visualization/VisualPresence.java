package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import com.aegisguard.config.Modules;
import com.aegisguard.data.Plot;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Optional 1.4.0 visual presence: holographic entry titles, for-sale labels,
 * and scepter border direction labels. Uses titles and action bars only so
 * Folia and 1.20+ stay compatible without spawned hologram entities.
 */
public final class VisualPresence {

    private VisualPresence() {}

    public static boolean enabled(AegisGuard plugin) {
        try {
            return plugin.modules().on(Modules.Id.VISUAL_PRESENCE);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void showEntry(AegisGuard plugin, Player player, Plot plot) {
        if (plugin == null || player == null || plot == null || !enabled(plugin)) return;
        if (!plugin.getConfig().getBoolean("visual_presence.holographic_entry", true)) return;

        String title = tr(plugin, player, "presence_enter_title", "&b{PLOT}",
                Map.of("PLOT", plotName(plot)));
        String subtitle = tr(plugin, player, "presence_enter_subtitle", "&7{OWNER}",
                Map.of("OWNER", ownerName(plot)));
        if (plugin.getConfig().getBoolean("visual_presence.for_sale_labels", true) && plot.isForSale()) {
            subtitle = tr(plugin, player, "presence_enter_sale", "&6For sale: {PRICE}",
                    Map.of("PRICE", formatPrice(plot.getSalePrice())));
        }
        int fadeIn = plugin.getConfig().getInt("titles.claim_enter_exit.fade_in", 10);
        int stay = plugin.getConfig().getInt("titles.claim_enter_exit.stay", 40);
        int fadeOut = plugin.getConfig().getInt("titles.claim_enter_exit.fade_out", 10);
        player.sendTitle(color(title), color(subtitle), fadeIn, stay, fadeOut);
    }

    public static void showBorderLabel(AegisGuard plugin, Player player, Plot plot) {
        if (plugin == null || player == null || plot == null || !enabled(plugin)) return;
        if (!plugin.getConfig().getBoolean("visual_presence.smart_borders", true)) return;
        int reach = Math.max(1, plugin.getConfig().getInt("visual_presence.border_label_distance", 3));
        String dir = nearestCardinal(player.getLocation().getBlockX(), player.getLocation().getBlockZ(), plot, reach);
        if (dir == null) return;
        String label = tr(plugin, player, "presence_border_label", "&b{DIR} &7border · &f{PLOT}",
                Map.of("DIR", dirName(plugin, player, dir), "PLOT", plotName(plot)));
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(color(label)));
        } catch (Throwable ignored) {
            player.sendMessage(color(label));
        }
    }

    public static String nearestCardinal(int x, int z, Plot plot, int reach) {
        int distN = Math.abs(z - plot.getZ1());
        int distS = Math.abs(z - plot.getZ2());
        int distW = Math.abs(x - plot.getX1());
        int distE = Math.abs(x - plot.getX2());
        int best = Integer.MAX_VALUE;
        String dir = null;
        if (distN <= reach && distN <= best) { best = distN; dir = "north"; }
        if (distS <= reach && distS <= best) { best = distS; dir = "south"; }
        if (distW <= reach && distW <= best) { best = distW; dir = "west"; }
        if (distE <= reach && distE <= best) { dir = "east"; }
        return dir;
    }

    private static String dirName(AegisGuard plugin, Player player, String dir) {
        return switch (dir) {
            case "south" -> tr(plugin, player, "presence_dir_south", "South", Map.of());
            case "west" -> tr(plugin, player, "presence_dir_west", "West", Map.of());
            case "east" -> tr(plugin, player, "presence_dir_east", "East", Map.of());
            default -> tr(plugin, player, "presence_dir_north", "North", Map.of());
        };
    }

    private static String plotName(Plot plot) {
        String name = plot.getPlotName();
        if (name == null || name.isBlank()) name = plot.getOwnerName();
        if (name == null || name.isBlank()) return "Plot";
        return ChatColor.stripColor(color(name));
    }

    private static String ownerName(Plot plot) {
        String name = plot.getOwnerName();
        return name == null || name.isBlank() ? "Unknown" : ChatColor.stripColor(color(name));
    }

    private static String formatPrice(double price) {
        if (price == Math.rint(price)) return String.valueOf((long) price);
        return String.format(java.util.Locale.ROOT, "%.2f", price);
    }

    private static String tr(AegisGuard plugin, Player player, String key, String fallback, Map<String, String> placeholders) {
        String value = fallback;
        try {
            if (plugin.codex() != null) {
                String translated = plugin.codex().tr(player, key, placeholders);
                if (translated != null && !translated.isBlank() && !translated.equalsIgnoreCase(key)) {
                    value = translated;
                }
            }
        } catch (Throwable ignored) {}
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                value = value.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return value;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
