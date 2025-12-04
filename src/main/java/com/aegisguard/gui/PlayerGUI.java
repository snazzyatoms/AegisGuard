package com.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.objects.Estate;
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

public class PlayerGUI {

    private final AegisGuard plugin;
    private final NamespacedKey actionKey;

    public PlayerGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "ag_action");
    }

    public static class PlayerMenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        
        String title = lang.getGui("title_main");
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 27, title);

        // Background Filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // =========================================================
        // 📍 CENTER SLOT: CURRENT LOCATION STATUS
        // =========================================================
        ItemStack statusItem;
        if (estate != null) {
            // Standing in Estate
            statusItem = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = statusItem.getItemMeta();
            meta.setDisplayName(lang.getMsg(player, "enter_title").replace("%name%", estate.getName()));
            
            List<String> lore = new ArrayList<>();
            lore.add(" ");
            lore.add("&8» &7Owner: &f" + Bukkit.getOfflinePlayer(estate.getOwnerId()).getName());
            lore.add(" ");
            lore.add("&eClick to Manage");
            
            // Convert & Colorize
            List<String> coloredLore = new ArrayList<>();
            for(String s : lore) coloredLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', s));
            meta.setLore(coloredLore);
            
            // Tag for listener
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "manage_current_estate");
            statusItem.setItemMeta(meta);
            
            // --- 🔮 PERKS BUTTON ---
            inv.setItem(17, plugin.getGuiManager().createActionItem(Material.ENCHANTED_BOOK, 
                "&d🔮 Active Estate Perks", 
                "view_perks", 
                "&7View active effects & buffs.", " ", "&eClick to View ➡"));

        } else {
            // Standing in Wilderness
            statusItem = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta meta = statusItem.getItemMeta();
            meta.setDisplayName(lang.getMsg(player, "exit_title")); // "Wilderness"
            
            List<String> lore = new ArrayList<>();
            lore.add("&7You are standing in unclaimed territory.");
            lore.add(" ");
            lore.add("&eClick to Deed (Claim) this land.");
            
            // Convert & Colorize
            List<String> coloredLore = new ArrayList<>();
            for(String s : lore) coloredLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', s));
            meta.setLore(coloredLore);
            
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "start_claim");
            statusItem.setItemMeta(meta);
        }
        
        inv.setItem(13, statusItem);

        // =========================================================
        // 🔘 NAVIGATION BUTTONS
        // =========================================================
        
        // [11] My Estates
        inv.setItem(11, plugin.getGuiManager().createActionItem(Material.OAK_DOOR, 
            "&6My Properties", "open_estates", 
            "&7View and manage all your", "&7Private and Guild estates.", " ", "&eClick to View"));

        // [15] Guild Dashboard
        inv.setItem(15, plugin.getGuiManager().createActionItem(Material.GOLDEN_HELMET, 
            "&eGuild Dashboard", "open_guild", 
            "&7Access your Alliance,", "&7Treasury, and Roster.", " ", "&eClick to Open"));

        // [22] Settings
        inv.setItem(22, plugin.getGuiManager().createActionItem(Material.COMPARATOR, 
            "&7Personal Settings", "open_settings", 
            "&7Language, Sounds, and Notifications.", " ", "&eClick to Configure"));

        player.openInventory(inv);
        GUIManager.playClick(player);
    }
    
    // This handles clicks SPECIFICALLY for the PlayerMenuHolder
    public void handleClick(Player player, InventoryClickEvent e) {
        // This is actually handled by GuiListener using NBT tags now!
        // You don't technically need logic here if GuiListener is doing the work.
        // But if you want to keep it modular:
        
        e.setCancelled(true);
        // Logic is delegated to NBT tags in GuiListener.java
    }
}
