package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * AdminCommand
 * - Handles all /aegis admin commands
 * - Cleanup banned player plots
 * - Future: show claims, transfer, force-unclaim, etc.
 */
// We implement TabCompleter to provide subcommand suggestions
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;

    // List of admin subcommands for the tab completer
    private static final String[] ADMIN_COMMANDS = { "cleanup" };

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("aegisguard.admin")) {
            sendMsg(sender, plugin.msg().get("no_perm")); // Use our new helper
            return true;
        }

        if (args.length == 0) {
            // Send a formatted help message
            sendMsg(sender, "&6&lAegisGuard Admin&r &7(v" + plugin.getDescription().getVersion() + ")");
            sendMsg(sender, "&e/aegis admin cleanup &7- Remove all plots owned by banned players.");
            // Later we can add: /aegis admin showclaims, /aegis admin transfer, etc.
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "cleanup" -> {
                sendMsg(sender, plugin.msg().prefix() + " &7Starting cleanup of banned players' plots... (This may take a moment)");

                // --- IMPROVEMENT ---
                // Run this on a separate thread to avoid lagging the server.
                // We use CompletableFuture.runAsync supplied by the plugin's scheduler.
                CompletableFuture.runAsync(() -> {
                    // This code is now running ASYNC
                    plugin.store().removeBannedPlots();

                    // Send the "done" message back on the main server thread
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sendMsg(sender, plugin.msg().prefix() + plugin.msg().get("admin_cleanup_done"));
                    });

                }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin)); // Spigot's async executor

                return true;
            }

            default -> {
                sendMsg(sender, plugin.msg().prefix() + "&cUnknown admin subcommand.");
                return true;
            }
        }
    }

    /**
     * --- NEW ---
     * Handles tab completion for the /aegis admin command.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            // If they are typing the first argument, suggest our subcommands
            StringUtil.copyPartialMatches(args[0], Arrays.asList(ADMIN_COMMANDS), completions);
            Collections.sort(completions);
            return completions;
        }
        // No suggestions for other arguments
        return null;
    }

    /**
     * --- NEW ---
     * Helper method to send a color-formatted message.
     * This avoids repeating ChatColor.translateAlternateColorCodes
     */
    private void sendMsg(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
