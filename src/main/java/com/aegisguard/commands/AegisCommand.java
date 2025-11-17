package com.aegisguard;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Handles all player-facing /aegis commands.
 * Replaces the onCommand logic from the main plugin class.
 */
public class AegisCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;

    // Subcommands for tab completion
    private static final String[] SUB_COMMANDS = { "wand", "menu", "claim", "unclaim" };
    private static final String[] ADMIN_SUB_COMMANDS = { "wand", "menu", "claim", "unclaim", "sound" };

    public AegisCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.msg().send(sender, "players_only");
            return true;
        }

        if (args.length == 0) {
            plugin.gui().openMain(p);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "wand" -> {
                p.getInventory().addItem(createScepter());
                plugin.msg().send(p, "wand_given");
            }
            case "menu" -> plugin.gui().openMain(p);
            case "claim" -> plugin.selection().confirmClaim(p);
            case "unclaim" -> plugin.selection().unclaimHere(p);

            case "sound" -> {
                // Admin: global toggle only
                if (!p.hasPermission("aegisguard.admin")) {
                    plugin.msg().send(p, "no_perm");
                    return true;
                }
                if (args.length < 2 || !args[1].equalsIgnoreCase("global")) {
                    sendMsg(p, "&eUsage:");
                    sendMsg(p, "&7/aegis sound global <on|off>");
                    return true;
                }
                if (args.length < 3) {
                    sendMsg(p, "&cUsage: /aegis sound global <on|off>");
                    return true;
                }
                boolean enable = args[2].equalsIgnoreCase("on");
                plugin.getConfig().set("sounds.global_enabled", enable);
                plugin.saveConfig();
                plugin.msg().send(p, enable ? "sound_enabled" : "sound_disabled");
            }

            default -> plugin.msg().send(p, "usage_main");
        }
        return true;
    }

    /**
     * Handles tab completion for /aegis
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            final List<String> completions = new ArrayList<>();
            // Use different command lists based on permission
            List<String> commands = sender.hasPermission("aegisguard.admin") ?
                    Arrays.asList(ADMIN_SUB_COMMANDS) :
                    Arrays.asList(SUB_COMMANDS);

            StringUtil.copyPartialMatches(args[0], commands, completions);
            Collections.sort(completions);
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("sound") && sender.hasPermission("aegisguard.admin")) {
            return Arrays.asList("global");
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("sound") && args[1].equalsIgnoreCase("global") && sender.hasPermission("aegisguard.admin")) {
            return Arrays.asList("on", "off");
        }

        return null; // No other completions
    }

    /**
     * Utility: Create Aegis Scepter
     * (Moved from main class)
     */
    private ItemStack createScepter() {
        ItemStack rod = new ItemStack(Material.LIGHTNING_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            // We translate color codes here for consistency
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&bAegis Scepter"));
            meta.setLore(Arrays.asList(
                    ChatColor.translateAlternateColorCodes('&', "&7Right-click: Open Aegis Menu"),
                    ChatColor.translateAlternateColorCodes('&', "&7Left/Right-click: Select corners"),
                    ChatColor.translateAlternateColorCodes('&', "&7Sneak + Left: Expand/Resize")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            rod.setItemMeta(meta);
        }
        return rod;
    }

    /**
     * Helper method to send a color-formatted message.
     */
    private void sendMsg(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
