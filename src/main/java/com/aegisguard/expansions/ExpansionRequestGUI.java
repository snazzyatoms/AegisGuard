package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ExpansionRequestGUI {

    private final AegisGuard plugin;

    public ExpansionRequestGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ExpansionHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        String title = GUIManager.safeText(
            plugin.msg().get(player, "expansion_gui_title"),
            "§d§lLand Expansion Request"
        );
        Inventory inv = Bukkit.createInventory(new ExpansionHolder(), 27, title);

        // --- REQUEST BUTTON (Slot 13) ---
        // Item is clean because GUIManager.icon strips attributes
        inv.setItem(13, GUIManager.icon(
                Material.DIAMOND_PICKAXE,
                GUIManager.safeText(plugin.msg().get(player, "button_submit_request"), "§aSubmit Expansion Request"),
                plugin.msg().getList(player, "submit_request_lore", List.of(
                    "§7Allows you to request an increase to",
                    "§7your maximum allowed plot size.",
                    " ",
                    "§cFeatures in Development."
                ))
        ));

        // --- NAVIGATION ---
        // Slot 22: BACK Button (Returns to Main Menu)
        inv.setItem(22, GUIManager.icon(
                Material.ARROW, 
                GUIManager.safeText(plugin.msg().get(player, "button_back"), "§fBack to Menu"), 
                plugin.msg().getList(player, "back_lore")
        ));
        
        // Slot 26: EXIT Button (Closes Inventory)
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
            case 13: // Submit Request (Placeholder Logic)
                plugin.msg().send(player, "expansion-not-available");
                plugin.effects().playError(player);
                break;

            case 22: // BACK Button (Returns to PlayerGUI)
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
                break;
                
            case 26: // Exit Button (Closes Menu)
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                break;
                
            default:
                break;
        }
    }
}
