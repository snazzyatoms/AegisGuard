package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

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
        Inventory inv = Bukkit.createInventory(new ZoningHolder(plot), 54, "§3Sub-Claim Manager");

        // List Zones
        int slot = 0;
        for (Zone zone : plot.getZones()) {
            if (slot >= 45) break;
            
            String renter = zone.isRented() ? Bukkit.getOfflinePlayer(zone.getRenter()).getName() : "None";
            String status = zone.isRented() ? "§cRented" : "§aAvailable";
            
            inv.setItem(slot++, GUIManager.icon(
                Material.IRON_DOOR,
                "§b" + zone.getName(),
                List.of(
                    "§7Status: " + status,
                    "§7Tenant: §f" + renter,
                    "§7Price: §6" + zone.getRentPrice(),
                    " ",
                    "§eRight-Click to Delete Zone"
                )
            ));
        }
        
        // Instructions (Slot 49)
        inv.setItem(49, GUIManager.icon(Material.BOOK, "§eHow to create a Zone", 
            List.of("§71. Select corners with Wand", "§72. Type /ag zone create <name> <price>")));

        // Back (Slot 45)
        inv.setItem(45, GUIManager.icon(Material.ARROW, "§fBack", null));
        
        player.openInventory(inv);
    }

    public void handleClick(Player player, InventoryClickEvent e, ZoningHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        if (e.getSlot() == 45) {
            plugin.gui().openMain(player);
            return;
        }
        
        // Handle Zone Deletion (Right Click)
        if (e.isRightClick() && e.getCurrentItem().getType() == Material.IRON_DOOR) {
            String name = e.getCurrentItem().getItemMeta().getDisplayName().replace("§b", "");
            Plot plot = holder.getPlot();
            
            // Find zone by name (simple search)
            Zone target = null;
            for (Zone z : plot.getZones()) {
                if (z.getName().equals(name)) { target = z; break; }
            }
            
            if (target != null) {
                plot.removeZone(target);
                plugin.store().setDirty(true);
                player.sendMessage("§cZone '" + name + "' deleted.");
                open(player, plot); // Refresh
            }
        }
    }
}
