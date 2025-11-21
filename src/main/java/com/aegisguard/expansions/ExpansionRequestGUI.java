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

    // This tells the plugin "This inventory belongs to the Expansion GUI"
    // Must be PUBLIC STATIC so GUIListener can access it
    public static class ExpansionHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new ExpansionHolder(), 27, "§bExpand Plot");

        // The Icon
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§aSubmit Expansion Request");
            meta.setLore(List.of("§7Click to request a plot expansion.", "§7(Feature in development)"));
            item.setItemMeta(meta);
        }
        inv.setItem(13, item);

        // Close Button
        inv.setItem(22, GUIManager.icon(Material.BARRIER, "§cClose", null));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true); // Stop them from taking items
        
        if (e.getSlot() == 22) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
        }
        
        // Logic to actually submit the request would go here when you implement it
    }
}
