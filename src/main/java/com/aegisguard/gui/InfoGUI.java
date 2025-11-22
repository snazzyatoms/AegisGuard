package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public class InfoGUI {

    private final AegisGuard plugin;

    public InfoGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class InfoHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new InfoHolder(), 27, "§9§lGuardian Codex");

        // --- CHAPTER 1: THE WAND --- (Slot 10)
        inv.setItem(10, GUIManager.icon(
            Material.LIGHTNING_ROD,
            "§bI. The Sacred Scepter",
            List.of(
                "§7The Scepter is your primary tool.",
                "§7Right-Click a corner to mark Point 1.",
                "§7Left-Click a corner to mark Point 2.",
                "§7Then, use /ag claim or the Main Menu."
            )
        ));

        // --- CHAPTER 2: PLOT MANAGEMENT --- (Slot 12)
        inv.setItem(12, GUIManager.icon(
            Material.OAK_SIGN,
            "§bII. Land Invocations",
            List.of(
                "§7/ag unclaim & /ag resize.",
                "§7/ag setspawn & /ag home.",
                "§7Manage your land's welcome/farewell messages."
            )
        ));

        // --- CHAPTER 3: ECONOMY & TAX --- (Slot 14)
        inv.setItem(14, GUIManager.icon(
            Material.GOLD_BLOCK,
            "§bIII. Upkeep & Market",
            List.of(
                "§7Plots require a daily upkeep tax.",
                "§7Failure to pay sends the plot to Auction.",
                "§7Use /ag sell to list your plot on the Marketplace."
            )
        ));

        // --- CHAPTER 4: PERMISSIONS --- (Slot 16)
        inv.setItem(16, GUIManager.icon(
            Material.PLAYER_HEAD,
            "§bIV. Roles & Trust",
            List.of(
                "§7Manage trusted members via the Roles Menu.",
                "§7Flags can be toggled in the Plot Flags GUI.",
                "§7(PvP, Mob Spawning, Chest Access, etc.)"
            )
        ));
        
        // --- NAVIGATION ---
        inv.setItem(22, GUIManager.icon(Material.ARROW, "§fBack", List.of("§7Return to Main Menu")));
        
        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true); // Locks ALL items 
        
        if (e.getSlot() == 22) { // Back button (Slot 22)
            try {
                plugin.gui().openMain(player); // Attempt to open the main menu
                plugin.effects().playMenuFlip(player);
            } catch (Exception ex) {
                plugin.getLogger().severe("AegisGuard Menu Crash: Failed to open main menu from Codex. See stacktrace below.");
                player.closeInventory(); // Close the broken menu safely
                ex.printStackTrace();
            }
        } else if (e.getSlot() >= 10 && e.getSlot() <= 16) {
            plugin.effects().playMenuFlip(player);
        }
    }
}
