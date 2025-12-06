package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
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
            player.sendMessage(ChatColor.RED + "You do not have permission.");
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
                // FIXED: loadAllLocales()
                if (plugin.getLanguageManager() != null) plugin.getLanguageManager().loadAllLocales();
                player.sendMessage(ChatColor.GREEN + "AegisGuard configuration reloaded.");
                break;

            case "wand":
                player.getInventory().addItem(plugin.getSelectionManager().getWand());
                player.sendMessage(ChatColor.GOLD + "You have received the Sentinel's Scepter.");
                break;

            case "claim":
            case "server": 
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
        Location[] sel = plugin.getSelectionManager().getSelectionLocations(player.getUniqueId());
        
        if (sel == null || sel[0] == null || sel[1] == null) {
            player.sendMessage(ChatColor.RED + "Select area with Wand first.");
            return;
        }

        Estate existing = plugin.getDataStore().getEstateAt(sel[0]); 
        if (existing != null) {
             // FIXED: getName()
             player.sendMessage(ChatColor.RED + "Overlaps with: " + existing.getName());
             return;
        }

        UUID serverUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
        
        // FIXED: Create Cuboid and use correct Constructor
        // Ensure vertical expansion
        Location min = sel[0]; min.setY(sel[0].getWorld().getMinHeight());
        Location max = sel[1]; max.setY(sel[0].getWorld().getMaxHeight());
        Cuboid region = new Cuboid(min, max);

        Estate serverEstate = new Estate(
                UUID.randomUUID(), 
                plotName, 
                serverUUID, 
                false, // isGuild
                sel[0].getWorld(), 
                region
        );

        serverEstate.setFlag("pvp", false);
        serverEstate.setFlag("mobs", false);
        serverEstate.setFlag("safe_zone", true);
        
        // FIXED: Use DataStore
        plugin.getDataStore().saveEstate(serverEstate);
        
        // FIXED: Add to manager (method name check)
        plugin.getEstateManager().registerEstateFromLoad(serverEstate);

        player.sendMessage(ChatColor.GREEN + "Server Estate '" + plotName + "' created.");
    }

    private void handleForceDelete(Player player) {
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        
        if (estate == null) {
            player.sendMessage(ChatColor.RED + "Not in a plot.");
            return;
        }
        
        // FIXED: getId()
        plugin.getDataStore().removeEstate(estate.getId());
        plugin.getEstateManager().removeEstate(estate.getId());
        
        player.sendMessage(ChatColor.GREEN + "Estate '" + estate.getName() + "' deleted.");
    }

    private void sendAdminHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- AegisGuard Admin ---");
        player.sendMessage(ChatColor.YELLOW + "/ag admin wand");
        player.sendMessage(ChatColor.YELLOW + "/ag admin claim <Name>");
        player.sendMessage(ChatColor.YELLOW + "/ag admin delete");
        player.sendMessage(ChatColor.YELLOW + "/ag admin reload");
    }
}
