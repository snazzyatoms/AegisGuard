package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class LandGrantGUI {

    private final AegisGuard plugin;
    private final NamespacedKey dirKey;
    private final NamespacedKey amtKey;

    public LandGrantGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.dirKey = new NamespacedKey(plugin, "grant_dir");
        this.amtKey = new NamespacedKey(plugin, "grant_amt");
    }

    public static class LandGrantHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void openInvoice(Player player, Estate estate) {
        LanguageManager lang = plugin.getLanguageManager();
        String title = lang.getGui("title_land_grant"); 
        if (title.contains("Missing")) title = "§8Land Grant Office";
        
        Inventory inv = Bukkit.createInventory(new LandGrantHolder(), 36, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        // Center Info
        inv.setItem(13, GUIManager.createItem(Material.MAP, 
            "&eCurrent Territory", 
            List.of("&7Size: &f" + estate.getRegion().getWidth() + "x" + estate.getRegion().getLength(), "&7Area: &f" + estate.getRegion().getArea() + " blocks")
        ));

        // Directions
        inv.setItem(4, createExpansionButton("North", estate));
        inv.setItem(22, createExpansionButton("South", estate));
        inv.setItem(14, createExpansionButton("East", estate));
        inv.setItem(12, createExpansionButton("West", estate));

        // Close
        inv.setItem(31, GUIManager.createItem(Material.BARRIER, lang.getGui("button_close")));

        player.openInventory(inv);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        ItemMeta meta = e.getCurrentItem().getItemMeta();
        if (meta == null) return;

        if (meta.getPersistentDataContainer().has(dirKey, PersistentDataType.STRING)) {
            String dir = meta.getPersistentDataContainer().get(dirKey, PersistentDataType.STRING);
            int amt = meta.getPersistentDataContainer().get(amtKey, PersistentDataType.INTEGER);
            
            Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
            
            if (estate != null) {
                plugin.getLandGrantManager().processExpansion(player, estate, amt, dir);
                player.closeInventory();
            } else {
                player.sendMessage("§cYou must be standing in the estate to expand it.");
                player.closeInventory();
            }
        }
        
        if (e.getSlot() == 31) {
            player.closeInventory();
        }
    }

    private ItemStack createExpansionButton(String direction, Estate estate) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        
        int amount = 5; 
        double cost = plugin.getLandGrantManager().calculateGrantCost(estate, amount);

        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&eExpand " + direction));
        
        List<String> lore = new ArrayList<>();
        lore.add(" ");
        lore.add("§7Purchase §f" + amount + " blocks");
        lore.add("§7in the §f" + direction + " §7direction.");
        lore.add(" ");
        lore.add("§cCost: §f$" + String.format("%.2f", cost));
        lore.add("§7(Taken from Guild Treasury)");
        lore.add(" ");
        lore.add("§eClick to Sign Grant");
        
        List<String> finalLore = new ArrayList<>();
        for(String s : lore) finalLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', s));
        meta.setLore(finalLore);
        
        meta.getPersistentDataContainer().set(dirKey, PersistentDataType.STRING, direction);
        meta.getPersistentDataContainer().set(amtKey, PersistentDataType.INTEGER, amount);
        
        item.setItemMeta(meta);
        return item;
    }
}
