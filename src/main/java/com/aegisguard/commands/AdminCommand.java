package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate; // FIXED: Changed from Plot to Estate
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
                if (plugin.getLanguageManager() != null) plugin.getLanguageManager().load();
                player.sendMessage(ChatColor.GREEN + "AegisGuard configuration reloaded.");
                break;

            case "wand":
                // Using the new getSelectionManager() alias we added
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

        // 1. Get Selection
        // Note: getSelectionLocations returns Location[] { loc1, loc2 }
        Location[] sel = plugin.getSelectionManager().getSelectionLocations(player.getUniqueId());
        
        if (sel == null || sel[0] == null || sel[1] == null) {
            player.sendMessage(ChatColor.RED + "You must select an area with the Sentinel's Scepter first.");
            return;
        }

        // 2. Check Overlap
        // We pass 'null' as the estate to ignore, meaning check everything
        Estate existing = plugin.getDataStore().getEstateAt(sel[0]); 
        if (existing != null) {
             // Basic overlap check (you might want a more complex range check here later)
             // For now, if the start point is inside another estate, block it.
             player.sendMessage(ChatColor.RED + "This selection overlaps with estate: " + existing.getDisplayName());
             return;
        }

        // 3. Create SERVER Estate
        // Using fixed UUID 0-0-0-0-0 for Server Ownership
        UUID serverUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
        
        // Constructor: Estate(UUID plotId, UUID ownerId, String ownerName, String world, int x1, int z1, int x2, int z2)
        Estate serverEstate = new Estate(
                UUID.randomUUID(), 
                serverUUID, 
                "SERVER", 
                sel[0].getWorld().getName(), 
                sel[0].getBlockX(), sel[0].getBlockZ(), 
                sel[1].getBlockX(), sel[1].getBlockZ()
        );

        // 4. Automatically Enable SafeZone Flags
        serverEstate.setFlag("pvp", false);
        serverEstate.setFlag("mobs", false);     // This triggers the MobBarrierTask
        serverEstate.setFlag("mob-spawning", false);
        serverEstate.setFlag("explosion", false);
        serverEstate.setFlag("fire-spread", false);
        serverEstate.setFlag("entry", true);     // Public entry
        serverEstate.setFlag("safe_zone", true); // Activates "God Mode"
        
        serverEstate.setDisplayName(ChatColor.translateAlternateColorCodes('&', plotName));
        
        // 5. Save using the new method name
        plugin.getDataStore().saveEstate(serverEstate);

        // 6. Cache it in memory (EstateManager) so it appears immediately without restart
        plugin.getEstateManager().addEstate(serverEstate);

        player.sendMessage(ChatColor.GREEN + "Server Estate '" + plotName + "' created successfully.");
        player.sendMessage(ChatColor.GRAY + "SafeZone flags (No PvP/Mobs) have been auto-applied.");
    }

    private void handleForceDelete(Player player) {
        // Updated to use Estate object
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        
        if (estate == null) {
            player.sendMessage(ChatColor.RED + "You are not standing in a plot.");
            return;
        }
        
        // Remove from DB
        plugin.getDataStore().removeEstate(estate.getPlotId());
        
        // Remove from Memory
        plugin.getEstateManager().removeEstate(estate.getPlotId());
        
        player.sendMessage(ChatColor.GREEN + "Estate '" + estate.getDisplayName() + "' force deleted.");
    }

    private void sendAdminHelp(Player player) {
        player.sendMessage(ChatColor.GOLD + "--- AegisGuard Admin ---");
        player.sendMessage(ChatColor.YELLOW + "/ag admin wand " + ChatColor.WHITE + "- Get Selection Wand");
        player.sendMessage(ChatColor.YELLOW + "/ag admin claim <Name> " + ChatColor.WHITE + "- Create Server Zone");
        player.sendMessage(ChatColor.YELLOW + "/ag admin delete " + ChatColor.WHITE + "- Force delete plot");
        player.sendMessage(ChatColor.YELLOW + "/ag admin reload " + ChatColor.WHITE + "- Reload Config");
    }
}
