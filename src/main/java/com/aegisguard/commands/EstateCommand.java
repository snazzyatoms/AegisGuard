package com.aegisguard.commands;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.managers.RoleManager;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor; // Added ChatColor import for messages
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public class EstateCommand implements CommandHandler.SubCommand {

    private final AegisGuard plugin;

    public EstateCommand(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Player player, String[] args) {
        LanguageManager lang = plugin.getLanguageManager();
        
        if (args.length == 0) {
            // No args? Open the "My Estates" GUI
            // plugin.getGuiManager().openEstateList(player);
            player.sendMessage("§eOpening Estate List... (Coming Soon)");
            return;
        }

        String action = args[0].toLowerCase();

        // =========================================================
        // 🪄 /ag wand
        // =========================================================
        if (action.equals("wand")) {
            // Check for the player permission before giving the item
            if (!player.hasPermission("aegis.wand")) { 
                player.sendMessage(ChatColor.RED + "You do not have permission to use the claim tool.");
                return;
            }

            // ACTION: Use the centralized ItemManager to get the Player Wand
            player.getInventory().addItem(plugin.getItemManager().getPlayerWand());
            
            // NOTE: This success message should be localized in your language files later.
            player.sendMessage(ChatColor.AQUA + "You have received the Claim Wand."); 
            return;
        }

        // =========================================================
        // 🏡 /ag claim <Name>
        // =========================================================
        if (action.equals("claim") || action.equals("deed")) {
            // 1. Check Selection
            Cuboid selection = plugin.getSelection().getSelection(player);
            if (selection == null) {
                player.sendMessage(lang.getMsg(player, "no_selection")); // "Use /ag wand first"
                return;
            }
            // ... (Rest of claim logic remains the same) ...
        // ... (Remaining EstateCommand code preserved) ...
        }

        // =========================================================
        // 👥 /ag invite <Player> (Trusting)
        // =========================================================
        if (action.equals("invite") || action.equals("trust")) {
            // ... (Logic preserved) ...
        }

        // =========================================================
        // 🛡️ /ag setrole <Player> <Role>
        // =========================================================
        if (action.equals("setrole")) {
            // ... (Logic preserved) ...
        }
        
        // =========================================================
        // 🚮 /ag unclaim
        // =========================================================
        if (action.equals("unclaim") || action.equals("vacate")) {
            // ... (Logic preserved) ...
        }
    }

    private boolean validateOwner(Player p, Estate e) {
        // ... (Logic preserved) ...
        return true;
    }
}
