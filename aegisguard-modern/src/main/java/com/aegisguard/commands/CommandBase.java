package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;  // ✅ NEW: Currency Type enum
import com.aegisguard.groups.PlotGroup;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import java.util.*;

/**
 * Shared plumbing for {@code /aegis} subcommand handlers: locale-aware message
 * helpers, placeholder substitution, and cross-handler utilities.
 */
public abstract class CommandBase {

    protected final AegisGuard plugin;

    protected CommandBase(AegisGuard plugin) {
        this.plugin = plugin;
    }

    protected String tr(CommandSender sender, String key, String fallback) {
        return tr(sender, key, fallback, Collections.emptyMap());
    }

    protected String tr(CommandSender sender, String key, String fallback, Map<String, String> placeholders) {
        // If Codex is missing, use fallback.
        if (plugin.codex() == null || key == null || key.isBlank()) {
            return applyPlaceholders(fallback, placeholders);
        }

        try {
            String value = plugin.codex().tr(sender, key, placeholders);

            // IMPORTANT: CodexEngine returns the key if missing.
            if (value == null || value.isBlank() || value.equalsIgnoreCase(key)) {
                return applyPlaceholders(fallback, placeholders);
            }

            return value;
        } catch (Throwable ignored) {
            return applyPlaceholders(fallback, placeholders);
        }
    }

    protected void sendKey(CommandSender sender, String key, String fallback) {
        sendKey(sender, key, fallback, Collections.emptyMap());
    }

    protected void sendKey(CommandSender sender, String key, String fallback, Map<String, String> placeholders) {
        String msg = tr(sender, key, fallback, placeholders);
        sendMsg(sender, msg);
    }

    protected void sendMsg(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    protected String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty() || placeholders == null || placeholders.isEmpty()) return input;

        String out = input;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String k = e.getKey();
            if (k == null || k.isEmpty()) continue;
            String v = (e.getValue() == null) ? "" : e.getValue();
            out = out.replace("{" + k + "}", v);
        }
        return out;
    }

    protected void notifyGroup(PlotGroup group, UUID excludePlayer,
                             String titleKey, String titleFallback,
                             String messageKey, String messageFallback,
                             Map<String, String> placeholders) {
        if (plugin.notifications() == null || group == null) return;
        if (!plugin.getConfig().getBoolean("group_plots.notifications.enabled", true)) return;

        plugin.notifications().notifyGroupMembers(
                group,
                excludePlayer,
                titleKey,
                titleFallback,
                messageKey,
                messageFallback,
                placeholders
        );
    }

    protected void notifyPlot(Plot plot, UUID excludePlayer,
                            String titleKey, String titleFallback,
                            String messageKey, String messageFallback,
                            Map<String, String> placeholders) {
        if (plugin.notifications() == null || plot == null) return;
        plugin.notifications().notifyPlotMembers(
                plot,
                excludePlayer,
                titleKey,
                titleFallback,
                messageKey,
                messageFallback,
                placeholders
        );
    }

    protected void notifyLowTreasuryIfNeeded(PlotGroup group, Plot linkedPlot, UUID excludePlayer) {
        if (plugin.notifications() == null || group == null) return;
        if (!plugin.getConfig().getBoolean("group_plots.notifications.enabled", true)) return;

        double threshold = Math.max(0.0D, plugin.getConfig().getDouble("group_plots.notifications.low_treasury_threshold", 250.0D));
        if (threshold <= 0.0D) return;

        double balance = linkedPlot != null ? linkedPlot.getTreasuryBalance() : group.getTreasuryBalance();
        if (balance > threshold) return;

        String formattedBalance = plugin.eco() != null && plugin.eco().isVaultReady()
                ? plugin.eco().format(balance, CurrencyType.VAULT)
                : String.format(Locale.US, "%.2f", balance);

        notifyGroup(
                group,
                excludePlayer,
                "notify_group_title",
                "&6Group Update",
                "notify_group_treasury_low",
                "&e{GROUP}'s treasury is running low. Remaining balance: &6{BALANCE}&e.",
                Map.of(
                        "GROUP", group.getName(),
                        "BALANCE", formattedBalance
                )
        );
    }

    protected String getReadablePlotName(Plot plot) {
        if (plot == null) return "Claim";
        String entryTitle = plot.getEntryTitle();
        if (entryTitle != null && !entryTitle.isBlank()) return entryTitle;
        String plotName = plot.getPlotName();
        if (plotName != null && !plotName.isBlank()) return plotName;
        String ownerName = plot.getOwnerName();
        if (ownerName != null && !ownerName.isBlank()) return ownerName + "'s Claim";
        return "Claim";
    }

    protected long parsePositiveLong(String s) {
        try {
            long v = Long.parseLong(s);
            return v > 0 ? v : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    protected Boolean parseBooleanArg(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable", "enabled", "yes" -> Boolean.TRUE;
            case "off", "false", "disable", "disabled", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

}