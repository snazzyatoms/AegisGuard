package com.yourname.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;

/**
 * PetitionGUI (Formerly ExpansionRequestGUI)
 * - Allows PRIVATE estate owners to ask Admins for land.
 * - Updated for v1.3.0 Estate System.
 */
public class PetitionGUI {

    private final AegisGuard plugin;
    private final NamespacedKey actionKey;

    public PetitionGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "ag_action");
    }

    public static class PetitionHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        
        // Title: "Petition for Land" or "Expansion Request"
        String title = lang.getGui("title_petition"); 
        Inventory inv = Bukkit.createInventory(new PetitionHolder(), 36, title);

        // Background Filler
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        // Get Current Estate
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        boolean valid = (estate != null && estate.getOwnerId().equals(player.getUniqueId()));
        
        // Lore Key based on validity
        String statusLoreKey = valid ? "petition_click_submit" : "petition_locked_lore";

        // --- TIER 1 (+5) ---
        inv.setItem(11, createActionItem(Material.WOODEN_PICKAXE, 
            lang.getMsg(player, "petition_tier1_name"), // "Tier 1 Petition"
            lang.getMsgList(player, "petition_tier1_lore"),
            "submit_5"
        ));

        // --- TIER 2 (+10) ---
        inv.setItem(12, createActionItem(Material.STONE_PICKAXE, 
            lang.getMsg(player, "petition_tier2_name"), 
            lang.getMsgList(player, "petition_tier2_lore"),
            "submit_10"
        ));

        // --- TIER 3 (+20) ---
        inv.setItem(13, createActionItem(Material.IRON_PICKAXE, 
            lang.getMsg(player, "petition_tier3_name"), 
            lang.getMsgList(player, "petition_tier3_lore"),
            "submit_20"
        ));

        // --- TIER 4 (+35) ---
        inv.setItem(14, createActionItem(Material.GOLDEN_PICKAXE, 
            lang.getMsg(player, "petition_tier4_name"), 
            lang.getMsgList(player, "petition_tier4_lore"),
            "submit_35"
        ));

        // --- TIER 5 (+50) ---
        inv.setItem(15, createActionItem(Material.DIAMOND_PICKAXE, 
            lang.getMsg(player, "petition_tier5_name"), 
            lang.getMsgList(player, "petition_tier5_lore"),
            "submit_50"
        ));

        // --- ADMIN VIEW (Slot 31) ---
        if (plugin.isAdmin(player)) {
            inv.setItem(31, createActionItem(Material.COMPASS, 
                lang.getMsg(player, "button_view_petitions_admin"), 
                lang.getMsgList(player, "view_petitions_admin_lore"),
                "admin_view"
            ));
        }

        // --- NAVIGATION ---
        inv.setItem(27, createActionItem(Material.ARROW, 
            lang.getGui("button_back"), 
            null, 
            "back"
        ));
        
        inv.setItem(35, createActionItem(Material.BARRIER, 
            lang.getGui("button_close"), 
            null, 
            "close"
        ));

        player.openInventory(inv);
        // plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        ItemStack item = e.getCurrentItem();
        if (item == null || item.getItemMeta() == null) return;
        
        if (!item.getItemMeta().getPersistentDataContainer().has(actionKey, PersistentDataType.STRING)) return;
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);

        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        int currentRadius = (estate != null) ? estate.getRegion().getWidth() / 2 : 0;

        switch (action) {
            case "submit_5":
                if (validateEstate(player, estate)) submit(player, estate, currentRadius + 5);
                break;
            case "submit_10":
                if (validateEstate(player, estate)) submit(player, estate, currentRadius + 10);
                break;
            case "submit_20":
                if (validateEstate(player, estate)) submit(player, estate, currentRadius + 20);
                break;
            case "submit_35":
                if (validateEstate(player, estate)) submit(player, estate, currentRadius + 35);
                break;
            case "submit_50":
                if (validateEstate(player, estate)) submit(player, estate, currentRadius + 50);
                break;

            case "admin_view":
                if (plugin.isAdmin(player)) {
                    // Open Admin List GUI (You need to update ExpansionRequestAdminGUI too)
                    // plugin.getGuiManager().openPetitionAdminGUI(player);
                }
                break;

            case "back":
                // plugin.getGuiManager().openMainMenu(player);
                break;
                
            case "close":
                player.closeInventory();
                break;
        }
    }
    
    private void submit(Player player, Estate estate, int newRadius) {
        // Renamed Method: createRequest -> createPetition
        plugin.getPetitionManager().createRequest(player, estate, newRadius);
        player.closeInventory();
        // plugin.effects().playConfirm(player);
    }

    private boolean validateEstate(Player player, Estate estate) {
        LanguageManager lang = plugin.getLanguageManager();
        
        if (estate == null || !estate.getOwnerId().equals(player.getUniqueId())) {
            player.sendMessage(lang.getMsg(player, "no_permission"));
            return false;
        }
        
        if (plugin.getPetitionManager().hasPendingRequest(player.getUniqueId())) {
            player.sendMessage(lang.getMsg(player, "petition_exists")); 
            return false;
        }
        return true;
    }

    // --- HELPER ---
    private ItemStack createItem(Material mat, String name) {
        return createActionItem(mat, name, null, null);
    }

    private ItemStack createActionItem(Material mat, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
        
        if (lore != null) {
            List<String> colorLore = new ArrayList<>();
            for (String l : lore) colorLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', l));
            meta.setLore(colorLore);
        }
        
        if (action != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        }
        
        item.setItemMeta(meta);
        return item;
    }
}
