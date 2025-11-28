package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ZoningGUI
 * - Manages Sub-Claims (Rentals) inside a plot.
 * - Allows creating, deleting, and evicting tenants.
 */
public class ZoningGUI {

    private final AegisGuard plugin;

    public ZoningGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ZoningHolder implements InventoryHolder {
        private final Plot plot;
        public ZoningHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        String title = "§3Sub-Claim Manager";
        Inventory inv = Bukkit.createInventory(new ZoningHolder(plot), 54, title);

        // --- 1. LIST ZONES ---
        List<Zone> zones = plot.getZones();
        int slot = 0;
        
        for (Zone zone : zones) {
            if (slot >= 45) break; // Pagination limit (can add pages later if needed)
            
            boolean isRented = zone.isRented();
            String status = isRented ? "§cRented" : "§aAvailable";
            String renterName = "None";
            String timeRemaining = "";

            if (isRented) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(zone.getRenter());
                renterName = (op.getName() != null) ? op.getName() : "Unknown";
                timeRemaining = zone.getRemainingTimeFormatted();
            }

            List<String> lore = new ArrayList<>();
            lore.add("§7Status: " + status);
            lore.add("§7Price: §6" + plugin.eco().format(zone.getRentPrice(), com.aegisguard.economy.CurrencyType.VAULT));
            lore.add(" ");
            if (isRented) {
                lore.add("§7Tenant: §f" + renterName);
                lore.add("§7Expires: §f" + timeRemaining);
                lore.add(" ");
                lore.add("§cLeft-Click to Evict Tenant");
            }
            lore.add("§cRight-Click to Delete Zone");

            inv.setItem(slot, GUIManager.createItem(
                isRented ? Material.IRON_DOOR : Material.OAK_DOOR,
                "§b" + zone.getName(),
                lore
            ));
            
            slot++;
        }
        
        // Fill empty slots with glass to separate the footer
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // --- 2. ACTIONS ---
        
        // Create Button (Slot 49)
        boolean hasSelection = plugin.selection().hasSelection(player);
        inv.setItem(49, GUIManager.createItem(
            Material.EMERALD_BLOCK, 
            "§aCreate New Zone", 
            hasSelection ? 
                List.of("§7Create a zone from your", "§7current wand selection.", " ", "§eClick to Create") :
                List.of("§cYou must select corners", "§cwith the Wand first!", " ", "§7(Zone will default to $100)")
        ));

        // Back (Slot 45)
        inv.setItem(45, GUIManager.createItem(Material.ARROW, "§fBack", List.of("§7Return to dashboard.")));
        
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, ZoningHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        Plot plot = holder.getPlot();
        
        // --- NAVIGATION ---
        if (e.getSlot() == 45) {
            plugin.gui().openMain(player);
            return;
        }
        
        // --- CREATE ZONE ---
        if (e.getSlot() == 49) {
            if (plugin.selection().hasSelection(player)) {
                // Auto-generate name based on count (Zone-1, Zone-2)
                String name = "Zone-" + (plot.getZones().size() + 1);
                
                // You would typically open an Anvil GUI here for the name, 
                // but for simplicity, we create a default one and let them rename later.
                player.performCommand("ag zone create " + name + " 100"); 
                player.closeInventory();
            } else {
                plugin.effects().playError(player);
                player.sendMessage("§cYou must select a region with the Wand first.");
            }
            return;
        }
        
        // --- ZONE MANAGEMENT ---
        if (e.getSlot() < 45 && e.getCurrentItem().getType() != Material.AIR) {
            String name = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            Zone target = null;
            
            for (Zone z : plot.getZones()) {
                if (z.getName().equalsIgnoreCase(name)) { target = z; break; }
            }
            
            if (target == null) return;

            // Delete
            if (e.isRightClick()) {
                plot.removeZone(target);
                plugin.store().setDirty(true);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
                open(player, plot); // Refresh
            }
            // Evict
            else if (e.isLeftClick()) {
                if (target.isRented()) {
                    target.evict();
                    plugin.store().setDirty(true);
                    player.sendMessage("§eTenant evicted from " + target.getName());
                    open(player, plot); // Refresh
                } else {
                    player.sendMessage("§cThis zone is not rented.");
                }
            }
        }
    }
}
