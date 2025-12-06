package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public class AdminCommand implements CommandHandler.SubCommand {

    private final AegisGuard plugin;

    public AdminCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Player player, String[] args) {
        if (!player.hasPermission("aegisguard.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to administer the Aegis.");
            return;
        }

        if (args.length == 0) {
            sendAdminHelp(player);
            return;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload":
                plugin.reloadConfig();
                // plugin.getLanguageManager().reload(); // If you have this
                player.sendMessage(ChatColor.GREEN + "AegisGuard configuration reloaded.");
                break;

            case "wand":
                player.getInventory().addItem(plugin.getSelectionManager().getWand());
                player.sendMessage(ChatColor.GOLD + "You have received the Sentinel's Scepter.");
                break;

            case "claim":
            case "server": // Usage: /ag admin claim <Name>
                handleServerClaim(player, args);
                break;

            case "delete":
                handleForceDelete(player);
                break;

            default:
                sendAdminHelp(player);
                break;
        }
    }

    private void handleServerClaim(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /ag admin claim <Name>");
            return;
        }

        String plotName = args[1];

        // 1. Get Selection (Requires SelectionService)
        Location[] sel = plugin.getSelectionManager().getSelection(player.getUniqueId());
        if (sel == null || sel[0] == null || sel[1] == null) {
            player.sendMessage(ChatColor.RED + "You must select an area with the Sentinel's Scepter first.");
            return;
        }

        // 2. Check Overlap using SQLDataStore
        if (plugin.getDataStore().isAreaOverlapping(null, sel[0].getWorld().getName(), 
                sel[0].getBlockX(), sel[0].getBlockZ(), 
                sel[1].getBlockX(), sel[1].getBlockZ())) {
            player.sendMessage(ChatColor.RED + "This area overlaps with an existing plot!");
            return;
        }

        // 3. Create SERVER Plot (Owner UUID is null or specific Server UUID)
        UUID serverUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
        Plot serverPlot = new Plot(UUID.randomUUID(), serverUUID, "SERVER", 
                sel[0].getWorld().getName(), 
                sel[0].getBlockX(), sel[0].getBlockZ(), 
                sel[1].getBlockX(), sel[1].getBlockZ());

        // 4. FIX: Automatically Enable SafeZone Flags
        serverPlot.setFlag("pvp", false);
        serverPlot.setFlag("mob-spawning", false);
        serverPlot.setFlag("explosion", false);
        serverPlot.setFlag("fire-spread", false);
        serverPlot.setFlag("entry", true); // Public entry usually true for spawns
        serverPlot.setDisplayName(ChatColor.translateAlternateColorCodes('&', plotName));
        
        // 5. Save
        plugin.getDataStore().addPlot(serverPlot);
        plugin.getDataStore().savePlot(serverPlot);

        player.sendMessage(ChatColor.GREEN + "Server Estate '" + plotName + "' created successfully.");
        player.sendMessage(ChatColor.GRAY + "SafeZone flags (No PvP/Mobs) have been auto-applied.");
    }

    private void handleForceDelete(Player player) {
        Plot plot = plugin.getDataStore().getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(ChatColor.RED + "You are not standing in a plot.");
            return;
        }
        
        plugin.getDataStore().removePlot(plot.getOwner(), plot.getPlotId());
        player.sendMessage(ChatColor.GREEN + "Plot forcefully deleted.");
    }

    private void sendAdminHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- AegisGuard Admin ---");
        player.sendMessage(ChatColor.YELLOW + "/ag admin wand " + ChatColor.WHITE + "- Get Selection Wand");
        player.sendMessage(ChatColor.YELLOW + "/ag admin claim <Name> " + ChatColor.WHITE + "- Create Server Zone");
        player.sendMessage(ChatColor.YELLOW + "/ag admin delete " + ChatColor.WHITE + "- Force delete plot");
        player.sendMessage(ChatColor.YELLOW + "/ag admin reload " + ChatColor.WHITE + "- Reload Config");
    }
}
