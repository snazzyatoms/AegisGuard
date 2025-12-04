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
        if (title.contains("Missing")) title = "§8Guardian Codex";
        
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Center Slot
        ItemStack statusItem;
        if (estate != null) {
            statusItem = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = statusItem.getItemMeta();
            meta.setDisplayName(lang.getMsg(player, "enter_title").replace("%name%", estate.getName()));
            
            List<String> lore = new ArrayList<>();
            lore.add(" ");
            lore.add("&8» &7Owner: &f" + Bukkit.getOfflinePlayer(estate.getOwnerId()).getName());
            lore.add(" ");
            lore.add("&eClick to Manage");
            
            meta.setLore(colorize(lore));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "manage_current_estate");
            statusItem.setItemMeta(meta);
            
            // Perks Button (Side Window)
            inv.setItem(17, plugin.getGuiManager().createActionItem(Material.ENCHANTED_BOOK, 
                "&d🔮 Active Estate Perks", 
                "view_perks", 
                "&7View active effects & buffs.", " ", "&eClick to View ➡"));

        } else {
            statusItem = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta meta = statusItem.getItemMeta();
            meta.setDisplayName(lang.getMsg(player, "exit_title"));
            
            List<String> lore = new ArrayList<>();
            lore.add("&7You are standing in unclaimed territory.");
            lore.add(" ");
            lore.add("&eClick to Deed (Claim) this land.");
            meta.setLore(colorize(lore));
            
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "start_claim");
            statusItem.setItemMeta(meta);
        }
        
        inv.setItem(13, statusItem);

        // Buttons
        inv.setItem(11, plugin.getGuiManager().createActionItem(Material.OAK_DOOR, 
            "&6My Properties", "open_estates", 
            "&7View and manage all your", "&7Private and Guild estates.", " ", "&eClick to View"));

        inv.setItem(15, plugin.getGuiManager().createActionItem(Material.GOLDEN_HELMET, 
            "&eGuild Dashboard", "open_guild", 
            "&7Access your Alliance,", "&7Treasury, and Roster.", " ", "&eClick to Open"));

        inv.setItem(22, plugin.getGuiManager().createActionItem(Material.COMPARATOR, 
            "&7Personal Settings", "open_settings", 
            "&7Language, Sounds, and Notifications.", " ", "&eClick to Configure"));

        player.openInventory(inv);
        GUIManager.playClick(player);
    }
    
    /**
     * NEW: The "Side Window" Menu for Active Perks.
     */
    public void openPerksMenu(Player player, Estate estate) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8Active Perks");
        
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Placeholder logic for perks
        int slot = 10;
        inv.setItem(slot++, GUIManager.createItem(Material.GOLDEN_PICKAXE, "&e⚡ Haste II", 
            List.of("&7Mining speed increased.", "&7Source: &fBastion Level 5")));

        inv.setItem(slot++, GUIManager.createItem(Material.SUGAR, "&b💨 Speed I", 
            List.of("&7Movement speed increased.", "&7Source: &fBastion Level 2")));
            
        inv.setItem(slot++, GUIManager.createItem(Material.FEATHER, "&f🕊 Flight", 
            List.of("&7Creative flight enabled.", "&7Source: &fAscension Level 10")));

        // Back Button
        inv.setItem(22, plugin.getGuiManager().createActionItem(Material.ARROW, "&c⬅ Back", "back_to_codex"));

        player.openInventory(inv);
    }

    private List<String> colorize(List<String> list) {
        List<String> colored = new ArrayList<>();
        for (String s : list) colored.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', s));
        return colored;
    }
}
