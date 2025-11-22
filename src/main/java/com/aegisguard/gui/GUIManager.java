package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * ExpansionRequestGUI
 * - Menu for players to submit a request for a manual plot expansion.
 */
public class ExpansionRequestGUI {

    private final AegisGuard plugin;

    public ExpansionRequestGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Tag holder so click handler only reacts to this GUI.
     * Must be PUBLIC for GUIListener.
     */
    public static class ExpansionHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        String title = GUIManager.safeText(
                plugin.msg().get(player, "expansion_gui_title"),
                "§d§lLand Expansion Request"
        );
        Inventory inv = Bukkit.createInventory(new ExpansionHolder(), 27, title);

        // --- REQUEST BUTTON (Slot 12) ---
        // FIX: Using the icon method that strips attributes for a clean GUI look.
        inv.setItem(12, GUIManager.icon(
                Material.DIAMOND_PICKAXE,
                GUIManager.safeText(plugin.msg().get(player, "button_submit_request"), "§aSubmit Expansion Request"),
                plugin.msg().getList(player, "submit_request_lore", List.of(
                    "§7Allows you to request an increase to",
                    "§7your maximum allowed plot size.",
                    " ",
                    "§cFeatures in Development." // Temporary text
                ))
        ));
        
        // --- ADMIN VIEW BUTTON (Slot 14) ---
        if (player.hasPermission("aegisguard.admin")) {
            inv.setItem(14, GUIManager.icon(
                Material.COMPASS,
                GUIManager.safeText(plugin.msg().get(player, "button_view_requests_admin"), "§cView Pending Requests (Admin)"),
                plugin.msg().getList(player, "view_requests_admin_lore")
            ));
        }

        // --- NAVIGATION ---
        // NEW: Back Button (Slot 22)
        inv.setItem(22, GUIManager.icon(Material.NETHER_STAR, "§fBack to Menu", List.of("§7Return to the main AegisGuard menu.")));
        
        // Exit Button (Slot 26)
        inv.setItem(26, GUIManager.icon(
                Material.BARRIER,
                GUIManager.safeText(plugin.msg().get(player, "button_exit"), "§cExit"),
                plugin.msg().getList(player, "exit_lore")
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        switch (e.getSlot()) {
            case 12: // Submit Expansion Request
                plugin.msg().send(player, "expansion-not-available");
                plugin.effects().playError(player);
                break;
                
            case 14: // Admin View Requests
                if (player.hasPermission("aegisguard.admin")) {
                    plugin.gui().expansionRequestAdmin().open(player, 0);
                    plugin.effects().playMenuFlip(player);
                }
                break;
                
            case 22: // NEW: Back Button
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
                break;

            case 26: // Exit
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                break;

            default:
                break;
        }
    }
}
