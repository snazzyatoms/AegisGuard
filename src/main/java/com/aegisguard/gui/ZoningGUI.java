package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import com.aegisguard.objects.Zone;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ZoningGUI
 * - Manages Sub-Claims (Rentals) inside an Estate.
 * - Fully localized for dynamic language switching.
 */
public class ZoningGUI {

    private final AegisGuard plugin;

    public ZoningGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ZoningHolder implements InventoryHolder {
        private final Estate estate;
        public ZoningHolder(Estate estate) { this.estate = estate; }
        public Estate getEstate() { return estate; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Estate estate) {
        LanguageManager lang = plugin.getLanguageManager();
        
        String title = lang.getGui("title_zoning_menu"); // "Sub-Claim Manager"
        if (title.contains("Missing")) title = "§3Sub-Claim Manager";
        
        Inventory inv = Bukkit.createInventory(new ZoningHolder(estate), 54, title);

        // --- 1. LIST ZONES ---
        List<Zone> zones = estate.getZones();
        int slot = 0;
        
        for (Zone zone : zones) {
            if (slot >= 45) break; 
            
            boolean isRented = zone.isRented();
            
            // Status Text
            String statusKey = isRented ? "zone_status_rented" : "zone_status_available";
            String status = lang.getMsg(player, statusKey);
            if (status.contains("Missing")) status = isRented ? "§cRented" : "§aAvailable";

            String renterName = "None";
            String timeRemaining = "";

            if (isRented) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(zone.getRenter());
                renterName = (op.getName() != null) ? op.getName() : "Unknown";
                timeRemaining = zone.getRemainingTimeFormatted();
            }

            List<String> lore = new ArrayList<>();
            lore.add("§7Status: " + status);
            lore.add("§7Price: §6" + plugin.getEconomy().format(zone.getRentPrice(), CurrencyType.VAULT));
            lore.add(" ");
            
            if (isRented) {
                lore.add("§7Tenant: §f" + renterName);
                lore.add("§7Expires: §f" + timeRemaining);
                lore.add(" ");
                lore.add(lang.getMsg(player, "zone_evict_action")); // "Left-Click to Evict"
            }
            
            lore.add(lang.getMsg(player, "zone_delete_action")); // "Right-Click to Delete"

            inv.setItem(slot, GUIManager.createItem(
                isRented ? Material.IRON_DOOR : Material.OAK_DOOR,
                "§b" + zone.getName(),
                lore
            ));
            
            slot++;
        }
        
        // Background Filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // --- 2. ACTIONS ---
        
        // Create Button (Slot 49)
        boolean hasSelection = plugin.getSelection().hasSelection(player);
        
        List<String> createLore = hasSelection ? 
            lang.getMsgList(player, "zone_create_ready_lore") :
            lang.getMsgList(player, "zone_create_locked_lore");
            
        inv.setItem(49, GUIManager.createItem(
            Material.EMERALD_BLOCK, 
            lang.getGui("button_zone_create"), 
            createLore
        ));

        // Back (Slot 45)
        inv.setItem(45, GUIManager.createItem(Material.ARROW, lang.getGui("button_back")));
        
        player.openInventory(inv);
        // plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, ZoningHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        Estate estate = holder.getEstate();
        LanguageManager lang = plugin.getLanguageManager();
        
        // --- NAVIGATION ---
        if (e.getSlot() == 45) {
            plugin.getGuiManager().openGuardianCodex(player);
            return;
        }
        
        // --- CREATE ZONE ---
        if (e.getSlot() == 49) {
            if (plugin.getSelection().hasSelection(player)) {
                // TODO: Add Chat Input Prompt for Name/Price
                String name = "Zone-" + (estate.getZones().size() + 1);
                player.performCommand("ag zone create " + name + " 100"); 
                player.closeInventory();
            } else {
                // plugin.effects().playError(player);
                player.sendMessage(lang.getMsg(player, "must_select"));
            }
            return;
        }
        
        // --- ZONE MANAGEMENT ---
        if (e.getSlot() < 45 && e.getCurrentItem().getType() != Material.AIR) {
            String name = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            Zone target = null;
            
            for (Zone z : estate.getZones()) {
                if (z.getName().equalsIgnoreCase(name)) { target = z; break; }
            }
            
            if (target == null) return;

            // Delete
            if (e.isRightClick()) {
                estate.removeZone(target);
                // plugin.getEstateManager().saveEstate(estate);
                
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
                // plugin.msg().send(player, "zone_deleted", Map.of("ZONE", target.getName()));
                open(player, estate); 
            }
            // Evict
            else if (e.isLeftClick()) {
                if (target.isRented()) {
                    target.evict();
                    // plugin.getEstateManager().saveEstate(estate);
                    
                    // plugin.msg().send(player, "zone_evicted", Map.of("ZONE", target.getName()));
                    open(player, estate); 
                } else {
                    player.sendMessage(lang.getMsg(player, "zone_not_rented"));
                }
            }
        }
    }
}
