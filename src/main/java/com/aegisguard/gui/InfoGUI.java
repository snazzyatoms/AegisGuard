package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * InfoGUI (The Guardian Codex)
 * - In-game wiki explaining commands, mechanics, and features.
 * - Updated for v1.1.1.
 */
public class InfoGUI {

    private final AegisGuard plugin;

    public InfoGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class InfoHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new InfoHolder(), 45, "§9§lThe Guardian Codex");

        // Background
        ItemStack filler = GUIManager.getFiller();
        for(int i=0; i<45; i++) inv.setItem(i, filler);

        // --- 1. CLAIMING (Slot 10) ---
        inv.setItem(10, GUIManager.createItem(Material.GOLDEN_HOE, "§e§lI. The Art of Claiming", List.of(
            "§7How to secure your land:",
            " ",
            "§f1. §7Type §b/ag wand §7to get the Scepter.",
            "§f2. §7Left-Click Corner A.",
            "§f3. §7Right-Click Corner B.",
            "§f4. §7Type §a/ag claim §7to purchase.",
            " ",
            "§7(Costs money/items depending on server rules.)"
        )));

        // --- 2. TRAVEL (Slot 12) ---
        inv.setItem(12, GUIManager.createItem(Material.ENDER_PEARL, "§b§lII. Teleportation Arts", List.of(
            "§7Travel instantly across the realm:",
            " ",
            "§e/ag home",
            "§7Teleport to your plot's spawn.",
            " ",
            "§e/ag setspawn",
            "§7Set the landing point where you stand.",
            " ",
            "§e/ag visit",
            "§7Open the Travel Menu to visit friends",
            "§7or public Server Warps."
        )));

        // --- 3. MENU MASTERY (Slot 14) ---
        inv.setItem(14, GUIManager.createItem(Material.WRITABLE_BOOK, "§d§lIII. Menu Mastery", List.of(
            "§7The Main Menu (§b/ag menu§7) controls all:",
            " ",
            "§6🚩 Flags: §7Toggle PvP, Mobs, Explosions.",
            "§b👥 Roles: §7Trust friends to build.",
            "§d✨ Cosmetics: §7Buy particle borders.",
            "§a🌿 Biomes: §7Change the grass color."
        )));

        // --- 4. SECURITY (Slot 16) ---
        inv.setItem(16, GUIManager.createItem(Material.SHIELD, "§c§lIV. Security & Banishment", List.of(
            "§7You are the lord of your land.",
            " ",
            "§c/ag kick <player>",
            "§7Expel intruders to spawn.",
            " ",
            "§4/ag ban <player>",
            "§7Create an invisible wall against them.",
            " ",
            "§7(Toggle 'Lockdown Mode' in Flags to",
            "§7ban everyone except trusted members.)"
        )));
        
        // --- 5. ECONOMY (Slot 22) ---
        inv.setItem(22, GUIManager.createItem(Material.GOLD_INGOT, "§6§lV. The Economy", List.of(
            "§7Land is a valuable asset.",
            " ",
            "§eUpkeep (Taxes):",
            "§7Pay daily tribute or lose your land.",
            " ",
            "§aMarketplace:",
            "§7Sell: §f/ag sell <price>",
            "§7Buy: §f/ag market",
            " ",
            "§cAuctions:",
            "§7Bid on expired plots via the menu."
        )));

        // --- 6. IDENTITY (Slot 24) ---
        inv.setItem(24, GUIManager.createItem(Material.NAME_TAG, "§3§lVI. Identity & Utility", List.of(
            "§7Make your land unique:",
            " ",
            "§b/ag rename <Name>",
            "§7Set a custom Title for your plot.",
            " ",
            "§b/ag setdesc <Text>",
            "§7Set a description for the Travel Menu.",
            " ",
            "§e/ag stuck",
            "§7Trapped? Teleports you to safety."
        )));
        
        // --- 7. ADVANCED (Slot 31 - Zoning/Levels) ---
        inv.setItem(31, GUIManager.createItem(Material.EXPERIENCE_BOTTLE, "§5§lVII. Advanced Features", List.of(
            "§dLeveling:",
            "§7Upgrade your plot to increase size limits",
            "§7and unlock particle buffs.",
            " ",
            "§eSub-Zones (Rentals):",
            "§7Create rentable shops inside your land.",
            "§71. Select area with Wand",
            "§72. Open Zoning Menu in Dashboard"
        )));

        // --- Navigation ---
        inv.setItem(40, GUIManager.createItem(Material.NETHER_STAR, "§fBack to Menu", List.of("§7Return to Main Menu")));
        inv.setItem(44, GUIManager.createItem(Material.BARRIER, "§cClose Codex", null));
        
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        // Nav
        if (e.getSlot() == 40) { // Back
            plugin.gui().openMain(player);
            plugin.effects().playMenuFlip(player);
        } else if (e.getSlot() == 44) { // Exit
            player.closeInventory();
            plugin.effects().playMenuClose(player);
        } else {
            // Sound feedback for reading
            if (e.getCurrentItem().getType() != Material.GRAY_STAINED_GLASS_PANE) {
                plugin.effects().playMenuFlip(player);
            }
        }
    }
}
