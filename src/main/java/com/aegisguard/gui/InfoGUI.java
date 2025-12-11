package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

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
        String title = GUIManager.safeText(
                plugin.codex().tr(player, "codex_gui_title"),
                "§9§lThe Guardian Codex"
        );
        Inventory inv = Bukkit.createInventory(new InfoHolder(), 45, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        // --- 1. CLAIMING ---
        inv.setItem(10, GUIManager.createItem(
                Material.GOLDEN_HOE,
                plugin.codex().tr(player, "codex_claim_title"),
                plugin.codex().list(player, "codex_claim_lore")
        ));

        // --- 2. TRAVEL ---
        inv.setItem(12, GUIManager.createItem(
                Material.ENDER_PEARL,
                plugin.codex().tr(player, "codex_travel_title"),
                plugin.codex().list(player, "codex_travel_lore")
        ));

        // --- 3. MENUS ---
        inv.setItem(14, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                plugin.codex().tr(player, "codex_menus_title"),
                plugin.codex().list(player, "codex_menus_lore")
        ));

        // --- 4. SECURITY ---
        inv.setItem(16, GUIManager.createItem(
                Material.SHIELD,
                plugin.codex().tr(player, "codex_security_title"),
                plugin.codex().list(player, "codex_security_lore")
        ));

        // --- 5. ECONOMY ---
        inv.setItem(22, GUIManager.createItem(
                Material.GOLD_INGOT,
                plugin.codex().tr(player, "codex_economy_title"),
                plugin.codex().list(player, "codex_economy_lore")
        ));

        // --- 6. IDENTITY ---
        inv.setItem(24, GUIManager.createItem(
                Material.NAME_TAG,
                plugin.codex().tr(player, "codex_identity_title"),
                plugin.codex().list(player, "codex_identity_lore")
        ));

        // --- 7. ADVANCED ---
        inv.setItem(31, GUIManager.createItem(
                Material.EXPERIENCE_BOTTLE,
                plugin.codex().tr(player, "codex_advanced_title"),
                plugin.codex().list(player, "codex_advanced_lore")
        ));

        // --- Navigation ---
        inv.setItem(40, GUIManager.createItem(
                Material.NETHER_STAR,
                plugin.codex().tr(player, "button_back_menu"),
                plugin.codex().list(player, "back_menu_lore")
        ));

        inv.setItem(44, GUIManager.createItem(
                Material.BARRIER,
                plugin.codex().tr(player, "button_exit"),
                plugin.codex().list(player, "exit_lore")
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (e.getSlot() == 40) {
            plugin.gui().openMain(player);
            plugin.effects().playMenuFlip(player);
        } else if (e.getSlot() == 44) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
        } else {
            if (e.getCurrentItem().getType() != Material.GRAY_STAINED_GLASS_PANE) {
                plugin.effects().playMenuFlip(player);
            }
        }
    }
}
