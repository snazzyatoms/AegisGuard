package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AdminCommand
 * --- UPGRADE NOTES ---
 * - Added "claim" and "unclaim" subcommands for Server Zones.
 * - Added "list" subcommand.
 * - All tasks are now Folia-safe.
 * - Permission check is corrected to "aegis.admin".
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AegisGuard plugin;
    // --- MODIFIED ---
    private static final String[] ADMIN_COMMANDS = { "cleanup", "reload", "list", "claim", "unclaim" };

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // --- PERMISSION FIX ---
        if (!sender.hasPermission("aegis.admin")) {
            sendMsg(sender, plugin.msg().get("no_perm"));
            return true;
        }

        if (args.length == 0) {
            sendMsg(sender, plugin.msg().prefix() + " &7Admin commands (v" + plugin.getDescription().getVersion() +"):");
            sendMsg(sender, "&e/aegisadmin cleanup &7- Remove all plots owned by banned players");
            sendMsg(sender, "&e/aegisadmin reload &7- Reload all config files");
            sendMsg(sender, "&e/aegisadmin list &7- Open a GUI of all plots on the server");
            sendMsg(sender, "&e/aegisadmin claim <name> &7- Create a Server Zone (e.g., Spawn)");
            sendMsg(sender, "&e/aegisadmin unclaim &7- Delete the Server Zone you are in");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "cleanup" -> {
                sendMsg(sender, plugin.msg().prefix() + " &7Starting async cleanup of banned player plots...");
                
                // --- FOLIA FIX ---
                plugin.runGlobalAsync(() -> {
                    plugin.store().removeBannedPlots();
                    
                    Runnable messageTask = () -> sendMsg(sender, plugin.msg().prefix() + plugin.msg().get("admin_cleanup_done"));
                    if (sender instanceof Player p) {
                        plugin.runMain(p, messageTask);
                    } else {
                        plugin.runMainGlobal(messageTask);
                    }
                });
                return true;
            }

            case "reload" -> {
                sendMsg(sender, plugin.msg().prefix() + " &7Reloading configurations...");
                
                // --- FOLIA FIX ---
                plugin.runGlobalAsync(() -> {
                    // 1. Reload config.yml (and sync new defaults)
                    plugin.cfg().reload();
                    // 2. Reload all managers that read from config.yml
                    plugin.msg().reload();
                    plugin.effects().reload();
                    plugin.worldRules().reload();
                    
                    // 3. Reload data files
                    plugin.msg().loadPlayerPreferences();
                    plugin.store().load(); // Reload plot data
                    plugin.getExpansionRequestManager().load(); // Reload expansion data
                    
                    Runnable messageTask = () -> sendMsg(sender, plugin.msg().prefix() + plugin.msg().get("admin_reload_complete"));
                    if (sender instanceof Player p) {
                        plugin.runMain(p, messageTask);
                    } else {
                        plugin.runMainGlobal(messageTask);
                    }
                });
                return true;
            }
            
            case "list" -> {
                if (!(sender instanceof Player p)) {
                    sendMsg(sender, "&cThis command can only be run by a player.");
                    return true;
                }
                plugin.gui().plotList().open(p, 0); // Open page 0
                return true;
            }
            
            case "claim": {
                if (!(sender instanceof Player p)) {
                    sendMsg(sender, "&cThis command can only be run by a player.");
                    return true;
                }
                
                if (!plugin.selection().hasBothCorners(p.getUniqueId())) {
                     plugin.msg().send(p, "must_select");
                     plugin.effects().playError(p);
                     return true;
                }
                
                plugin.selection().claimServerZone(p);
                plugin.msg().send(p, "admin-zone-created");
                plugin.effects().playClaimSuccess(p);
                return true;
            }
            case "unclaim": {
                if (!(sender instanceof Player p)) {
                    sendMsg(sender, "&cThis command can only be run by a player.");
                    return true;
                }
                
                Plot plot = plugin.store().getPlotAt(p.getLocation());
                if (plot == null || !plot.isServerZone()) {
                    plugin.msg().send(p, "admin-zone-not-found");
                    plugin.effects().playError(p);
                    return true;
                }
                
                plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
                plugin.msg().send(p, "admin-zone-deleted");
                plugin.effects().playUnclaim(p);
                return true;
            }

            default -> {
                sendMsg(sender, plugin.msg().prefix() + "&cUnknown admin subcommand.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList(ADMIN_COMMANDS), completions);
            java.util.Collections.sort(completions);
            return completions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("claim")) {
            return Arrays.asList("Spawn", "Market", "Arena");
        }
        return null;
    }

    private void sendMsg(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
