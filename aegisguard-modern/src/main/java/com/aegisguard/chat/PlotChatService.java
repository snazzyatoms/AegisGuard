package com.aegisguard.chat;

import com.aegisguard.AegisGuard;
import com.aegisguard.config.Modules;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Same-server Aegis Frequency: a private chat channel bound to one plot's members.
 * Cross-server sync is still a 2.0 network feature and is not implemented here.
 */
public final class PlotChatService {

    private final AegisGuard plugin;
    private final Map<UUID, UUID> tunedPlotByPlayer = new ConcurrentHashMap<>();

    public PlotChatService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        try {
            return plugin.modules().on(Modules.Id.PLOT_CHAT);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public boolean isTuned(UUID playerId) {
        return playerId != null && tunedPlotByPlayer.containsKey(playerId);
    }

    public UUID tunedPlotId(UUID playerId) {
        return playerId == null ? null : tunedPlotByPlayer.get(playerId);
    }

    public void clearPlayer(UUID playerId) {
        if (playerId != null) tunedPlotByPlayer.remove(playerId);
    }

    public void clearAll() {
        tunedPlotByPlayer.clear();
    }

    public ToggleResult toggle(Player player) {
        if (player == null) return ToggleResult.NEED_MEMBER;
        UUID current = tunedPlotByPlayer.get(player.getUniqueId());
        Plot here = plugin.store() == null ? null : plugin.store().getPlotAt(player.getLocation());
        if (here == null || !isFrequencyMember(here, player.getUniqueId())) {
            if (current != null) {
                tunedPlotByPlayer.remove(player.getUniqueId());
                return ToggleResult.OFF;
            }
            return ToggleResult.NEED_MEMBER;
        }
        if (here.getPlotId().equals(current)) {
            tunedPlotByPlayer.remove(player.getUniqueId());
            return ToggleResult.OFF;
        }
        boolean switched = current != null;
        tunedPlotByPlayer.put(player.getUniqueId(), here.getPlotId());
        return switched ? ToggleResult.SWITCHED : ToggleResult.ON;
    }

    public void turnOff(Player player) {
        if (player != null) tunedPlotByPlayer.remove(player.getUniqueId());
    }

    public SendResult send(Player speaker, String rawMessage) {
        if (speaker == null) return SendResult.NEED_MEMBER;
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) return SendResult.EMPTY;
        int max = Math.max(16, plugin.getConfig().getInt("plot_chat.max_message_length", 256));
        if (message.length() > max) message = message.substring(0, max);

        Plot plot = resolvePlot(speaker);
        if (plot == null) return SendResult.NEED_MEMBER;
        if (!isFrequencyMember(plot, speaker.getUniqueId())) {
            tunedPlotByPlayer.remove(speaker.getUniqueId());
            return SendResult.NOT_MEMBER;
        }

        String line = format(speaker, plot, message);
        Set<UUID> members = frequencyMemberIds(plot);
        int others = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null || !members.contains(online.getUniqueId())) continue;
            Player target = online;
            plugin.runMain(target, () -> target.sendMessage(line));
            if (!target.getUniqueId().equals(speaker.getUniqueId())) others++;
        }
        if (others == 0) {
            plugin.runMain(speaker, () -> speaker.sendMessage(color(translate(speaker,
                    "plot_chat_no_listeners", "&eNobody else is online on this Frequency."))));
        }
        return others == 0 ? SendResult.NO_LISTENERS : SendResult.SENT;
    }

    public boolean interceptPublicChat(Player speaker) {
        return isEnabled() && speaker != null && tunedPlotByPlayer.containsKey(speaker.getUniqueId());
    }

    public Plot resolvePlot(Player speaker) {
        if (speaker == null || plugin.store() == null) return null;
        UUID bound = tunedPlotByPlayer.get(speaker.getUniqueId());
        if (bound != null) {
            for (Plot plot : plugin.store().getAllPlots()) {
                if (plot != null && bound.equals(plot.getPlotId())) return plot;
            }
            tunedPlotByPlayer.remove(speaker.getUniqueId());
        }
        Plot here = plugin.store().getPlotAt(speaker.getLocation());
        if (here != null && isFrequencyMember(here, speaker.getUniqueId())) return here;
        return null;
    }

    public String plotLabel(Plot plot) {
        if (plot == null) return "Plot";
        String name = plot.getPlotName();
        if (name == null || name.isBlank()) name = plot.getOwnerName();
        if (name == null || name.isBlank()) return "Plot";
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name));
    }

    public String format(Player speaker, Plot plot, String message) {
        String template = translate(speaker, "plot_chat_format",
                "&8[&b{PLOT}&8] &f{PLAYER}&7: &f{MESSAGE}");
        String filled = template
                .replace("{PLOT}", plotLabel(plot))
                .replace("{PLAYER}", speaker.getName())
                .replace("{MESSAGE}", message);
        return ChatColor.translateAlternateColorCodes('&', filled);
    }

    public static boolean isFrequencyMember(Plot plot, UUID playerId) {
        if (plot == null || playerId == null) return false;
        if (plot.isBanned(playerId)) return false;
        if (plot.isOwner(playerId)) return true;
        if (plot.isRentedBy(playerId)) return true;
        String role = plot.getRole(playerId);
        if (role == null) return false;
        String normalized = role.toLowerCase(Locale.ROOT);
        return !normalized.isBlank()
                && !"visitor".equals(normalized)
                && !"default".equals(normalized)
                && !"none".equals(normalized);
    }

    public static Set<UUID> frequencyMemberIds(Plot plot) {
        Set<UUID> ids = new HashSet<>();
        if (plot == null) return ids;
        if (plot.getOwner() != null) ids.add(plot.getOwner());
        if (plot.getPlayerRoles() != null) {
            for (Map.Entry<UUID, String> entry : plot.getPlayerRoles().entrySet()) {
                if (isFrequencyMember(plot, entry.getKey())) ids.add(entry.getKey());
            }
        }
        UUID renter = plot.getCurrentRenter();
        if (plot.isRentedBy(renter)) ids.add(renter);
        return ids;
    }

    private String translate(Player player, String key, String fallback) {
        try {
            if (plugin.codex() != null) {
                String value = plugin.codex().tr(player, key, Map.of());
                if (value != null && !value.isBlank() && !value.equalsIgnoreCase(key)) return value;
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public enum ToggleResult { ON, OFF, SWITCHED, NEED_MEMBER }

    public enum SendResult { SENT, NO_LISTENERS, NEED_MEMBER, NOT_MEMBER, EMPTY }
}
