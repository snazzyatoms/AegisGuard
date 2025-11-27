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
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        // 45 Slots for a spacious, book-like layout
        Inventory inv = Bukkit.createInventory(new InfoHolder(), 45, "§9§lThe Guardian Codex");

        // Background
        for(int i=0; i<45; i++) {
            inv.setItem(i, GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        // --- 1. THE ART OF CLAIMING (Slot 10) ---
        inv.setItem(10, GUIManager.icon(
            Material.GOLDEN_HOE, // Represents the tool
            "§e§lI. The Art of Claiming",
            List.of(
                "§7How to secure your own land:",
                " ",
                "§f1. §7Type §b/ag wand §7to receive the Scepter.",
                "§f2. §7Right-Click the first corner of your land.",
                "§f3. §7Left-Click the opposite diagonal corner.",
                "§f4. §7Type §a/ag claim §7to purchase the deed.",
                " ",
                "§7(The Scepter is consumed upon success.)"
            )
        ));

        // --- 2. TELEPORTATION & TRAVEL (Slot 12) ---
        inv.setItem(12, GUIManager.icon(
            Material.ENDER_PEARL,
            "§b§lII. Teleportation Arts",
            List.of(
                "§7Travel instantly across the realm:",
                " ",
                "§e/ag home",
                "§7Teleports you to your plot's spawn.",
                " ",
                "§e/ag setspawn",
                "§7Sets the landing point where you stand.",
                " ",
                "§e/ag visit",
                "§7Opens the §fTravel Menu §7to visit friends",
                "§7or public Server Warps (Spawn, Market)."
            )
        ));

        // --- 3. MENU MASTERY (Slot 14) ---
        inv.setItem(14, GUIManager.icon(
            Material.WRITABLE_BOOK,
            "§d§lIII. Menu Mastery",
            List.of(
                "§7The Main Menu (§b/ag menu§7) controls all:",
                " ",
                "§6🚩 Plot Flags:",
                "§7Toggle PvP, Mob Spawning, Explosions,",
                "§7Flight, and Privacy (Lockdown).",
                " ",
                "§b👥 Roles Manager:",
                "§7Trust friends to build or just visit.",
                " ",
                "§9⚙ Player Settings:",
                "§7Toggle your personal sound/chat preferences."
            )
        ));

        // --- 4. SECURITY & MODERATION (Slot 16) ---
        inv.setItem(16, GUIManager.icon(
            Material.SHIELD,
            "§c§lIV. Security & Banishment",
            List.of(
                "§7You are the lord of your land.",
                "§7If a player causes trouble:",
                " ",
                "§c/ag kick <player>",
                "§7Expels them to the world spawn immediately.",
                " ",
                "§4/ag ban <player>",
                "§7Erects an invisible wall against them.",
                "§7They can never re-enter until unbanned."
            )
        ));
        
        // --- 5. ECONOMY (Slot 22 - Center) ---
        inv.setItem(22, GUIManager.icon(
            Material.GOLD_INGOT,
            "§6§lV. The Economy",
            List.of(
                "§7Land is a valuable asset.",
                " ",
                "§eUpkeep (Taxes):",
                "§7Pay daily tribute or lose your land.",
                "§7Unpaid plots go to the §cAuction House§7.",
                " ",
                "§aMarketplace:",
                "§7Sell your plot: §f/ag sell <price>",
                "§7Buy plots: §f/ag market"
            )
        ));

        // --- 6. IDENTITY & UTILITY (Slot 24 - NEW) ---
        // Ensure the lore list is populated from messages.yml
        List<String> utilityLore = plugin.msg().getList(player, "utility_chapter_lore");
        if (utilityLore == null || utilityLore.isEmpty()) {
            // Fallback if messages.yml update hasn't propagated
            utilityLore = List.of(
                "§7Make your land unique:",
                " ",
                "§b/ag rename <Name>",
                "§7Set a custom Title for your plot.",
                " ",
                "§b/ag setdesc <Text>",
                "§7Set a description visible in the Travel Menu.",
                " ",
                "§e/ag stuck",
                "§7Trapped? Teleports you to safety."
            );
        }

        inv.setItem(24, GUIManager.icon(
            Material.NAME_TAG,
            GUIManager.safeText(plugin.msg().get(player, "button_utility_chapter"), "§3§lVI. Identity & Utility"),
            utilityLore
        ));

        // --- 7. ADMIN GUIDE (Slot 31 - Only for Ops) ---
        if (plugin.isAdmin(player)) {
            inv.setItem(31, GUIManager.icon(
                Material.COMMAND_BLOCK,
                "§4§lVII. Operator's Guide",
                List.of(
                    "§7Authorized Personnel Only.",
                    " ",
                    "§c/agadmin bypass",
                    "§7Break/Build in any claim ignoring rules.",
                    " ",
                    "§c/ag setwarp <name>",
                    "§7Turn the current plot into a Server Warp",
                    "§7(Visible in the Travel Menu).",
                    " ",
                    "§c/agadmin wand",
                    "§7Manually get the Sentinel's Scepter."
                )
            ));
        }
        
        // --- Navigation ---
        inv.setItem(40, GUIManager.icon(Material.NETHER_STAR, "§fBack to Menu", List.of("§7Return to Main Menu")));
        inv.setItem(44, GUIManager.icon(Material.BARRIER, "§cExit", List.of("§7Close the Codex")));
        
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        
        if (e.getSlot() == 40) { // Back
            plugin.gui().openMain(player);
            plugin.effects().playMenuFlip(player);
        } else if (e.getSlot() == 44) { // Exit
            player.closeInventory();
            plugin.effects().playMenuClose(player);
        } else {
            // Just play sound for reading pages
            plugin.effects().playMenuFlip(player);
        }
    }
}
