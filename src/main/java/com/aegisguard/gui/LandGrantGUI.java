package com.yourname.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.LandGrantManager;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.Arrays;

public class LandGrantGUI {

    private final AegisGuard plugin;

    public LandGrantGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public void openInvoice(Player player, Estate estate) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8Land Grant Office");

        // The 4 Direction Buttons
        inv.setItem(10, createExpansionButton("North", estate));
        inv.setItem(12, createExpansionButton("South", estate));
        inv.setItem(14, createExpansionButton("East", estate));
        inv.setItem(16, createExpansionButton("West", estate));

        player.openInventory(inv);
    }

    private ItemStack createExpansionButton(String direction, Estate estate) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        
        int amount = 5; // Default chunk size to buy
        double cost = plugin.getLandGrantManager().calculateGrantCost(estate, amount);

        meta.setDisplayName("§eExpand " + direction);
        meta.setLore(Arrays.asList(
            " ",
            "§7Purchase " + amount + " blocks",
            "§7in the " + direction + " direction.",
            " ",
            "§cCost: §f$" + String.format("%.2f", cost),
            "§eClick to Sign Grant"
        ));
        
        // Store data for the Listener
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "grant_dir"), PersistentDataType.STRING, direction);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "grant_amt"), PersistentDataType.INTEGER, amount);
        
        item.setItemMeta(meta);
        return item;
    }
}
