package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Handles all /aegis sound ... subcommands.
 * This class is delegated to by AegisCommand.
 *
 * --- UPGRADE NOTES ---
 * - PERMISSION FIX: Now uses correct "aegis.admin" permission.
 * - CONFIG FIX: Now writes to correct "sounds.global_enabled" path.
 * - LAG FIX: Now saves config asynchronously using Folia-safe scheduler.
 */
public class SoundCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;

    private static final String[] MODES = { "global", "player" };
    private static final String[] TOGGLES = { "on", "off" };

    public SoundCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * This onCommand is called by AegisCommand.
     * args[0] will be "global" or "player", NOT "sound".
     */
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // --- PERMISSION FIX ---
        if (!sender.hasPermission("aegis.admin")) {
            plugin.msg().send(sender, "no_perm");
            return true;
        }

        if (args.length == 0) {
            sendMsg(sender, "&eUsage:");
            sendMsg(sender, "&7/aegis sound global <on|off>");
            sendMsg(sender, "&7/aegis sound player <name> <on|off>");
            return true;
        }

        String mode = args[0].toLowerCase();
        switch (mode) {
            case "global" -> {
                if (args.length < 2) {
                    sendMsg(sender, "&cUsage: /aegis sound global <on|off>");
                    return true;
                }
                boolean enable = args[1].equalsIgnoreCase("on");
                
                // --- CONFIG FIX ---
                plugin.getConfig().set("sounds.global_enabled", enable);
                
                // --- LAG FIX ---
                plugin.runGlobalAsync(plugin::saveConfig);
                
                sendMsg(sender, "&a✔ Global sounds " + (enable ? "enabled" : "disabled"));
            }
            case "player" -> {
                if (args.length < 3) {
                    sendMsg(sender, "&cUsage: /aegis sound player <name> <on|off>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sendMsg(sender, "&cPlayer not found: " + args[1]);
                    return true;
                }
                boolean enable = args[2].equalsIgnoreCase("on");

                // --- CONFIG FIX ---
                plugin.getConfig().set("sounds.players." + target.getUniqueId(), enable);
                
                // --- LAG FIX ---
                plugin.runGlobalAsync(plugin::saveConfig);
                
                sendMsg(sender, "&a✔ Sounds for " + target.getName() + " " + (enable ? "enabled" : "disabled"));
            }
            default -> {
                sendMsg(sender, "&cInvalid mode. Use &7global &cor &7player");
            }
        }
        return true;
    }

    /**
     * Handles tab completion for /aegis sound ...
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Suggesting "global" or "player"
            StringUtil.copyPartialMatches(args[0], Arrays.asList(MODES), completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("global")) {
                // Suggesting "on" or "off" for global
                StringUtil.copyPartialMatches(args[1], Arrays.asList(TOGGLES), completions);
            }
            if (args[0].equalsIgnoreCase("player")) {
                // Suggesting player names
                return null; // Let Bukkit handle player name completion
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("player")) {
                // Suggesting "on" or "off" for player
                StringUtil.copyPartialMatches(args[2], Arrays.asList(TOGGLES), completions);
            }
        }
        Collections.sort(completions);
        return completions;
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
