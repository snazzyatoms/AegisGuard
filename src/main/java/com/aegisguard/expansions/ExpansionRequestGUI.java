package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * ExpansionRequestGUI
 * - Allows players to submit requests to increase their plot size.
 * - Updated with 5 Tiers and Exit Button.
 */
public class ExpansionRequestGUI {

    private final AegisGuard plugin;

    public ExpansionRequestGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ExpansionHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        String title = GUIManager.safeText(plugin.msg().get(player, "expansion_gui_title"), "§dLand Expansion Request");
        Inventory inv = Bukkit.createInventory(new ExpansionHolder(), 36, title); // Increased to 36 for better spacing

        // Background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        boolean validPlot = (plot != null && plot.getOwner().equals(player.getUniqueId()));
        
        // Helper to get lore based on validity
        String statusLoreKey = validPlot ? "expansion_click_submit" : "expansion_locked_lore";

        // --- TIER 1: TIER 1 (+5) ---
        inv.setItem(11, GUIManager.createItem(Material.WOODEN_PICKAXE, 
            plugin.msg().get(player, "expansion_tier1_name", "§aTier 1 Expansion"), 
            plugin.msg().getList(player, "expansion_tier1_lore")
        ));

        // --- TIER 2: TIER 2 (+10) ---
        inv.setItem(12, GUIManager.createItem(Material.STONE_PICKAXE, 
            plugin.msg().get(player, "expansion_tier2_name", "§7Tier 2 Expansion"), 
            plugin.msg().getList(player, "expansion_tier2_lore")
        ));

        // --- TIER 3: TIER 3 (+20) ---
        inv.setItem(13, GUIManager.createItem(Material.IRON_PICKAXE, 
            plugin.msg().get(player, "expansion_tier3_name", "§fTier 3 Expansion"), 
            plugin.msg().getList(player, "expansion_tier3_lore")
        ));

        // --- TIER 4: TIER 4 (+35) ---
        inv.setItem(14, GUIManager.createItem(Material.GOLDEN_PICKAXE, 
            plugin.msg().get(player, "expansion_tier4_name", "§6Tier 4 Expansion"), 
            plugin.msg().getList(player, "expansion_tier4_lore")
        ));

        // --- TIER 5: TIER 5 (+50) ---
        inv.setItem(15, GUIManager.createItem(Material.DIAMOND_PICKAXE, 
            plugin.msg().get(player, "expansion_tier5_name", "§bTier 5 Expansion"), 
            plugin.msg().getList(player, "expansion_tier5_lore")
        ));

        // --- ADMIN VIEW (Slot 31) ---
        if (plugin.isAdmin(player)) {
            inv.setItem(31, GUIManager.createItem(Material.COMPASS, 
                plugin.msg().get(player, "button_view_requests_admin"), 
                plugin.msg().getList(player, "view_requests_admin_lore")
            ));
        }

        // Navigation
        inv.setItem(27, GUIManager.createItem(Material.NETHER_STAR, 
            plugin.msg().get(player, "button_back_menu"), 
            plugin.msg().getList(player, "back_menu_lore")
        ));
        
        // Exit Button (Added as requested)
        inv.setItem(35, GUIManager.createItem(Material.BARRIER, 
            plugin.msg().get(player, "button_exit"), 
            plugin.msg().getList(player, "exit_lore")
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        int currentRadius = (plot != null) ? (plot.getX2() - plot.getX1()) / 2 : 0;

        switch (e.getSlot()) {
            case 11: // Tier 1 (+5)
                if (validatePlot(player, plot)) submit(player, plot, currentRadius + 5);
                break;
            case 12: // Tier 2 (+10)
                if (validatePlot(player, plot)) submit(player, plot, currentRadius + 10);
                break;
            case 13: // Tier 3 (+20)
                if (validatePlot(player, plot)) submit(player, plot, currentRadius + 20);
                break;
            case 14: // Tier 4 (+35)
                if (validatePlot(player, plot)) submit(player, plot, currentRadius + 35);
                break;
            case 15: // Tier 5 (+50)
                if (validatePlot(player, plot)) submit(player, plot, currentRadius + 50);
                break;

            case 31: // Admin View
                if (plugin.isAdmin(player)) {
                    plugin.gui().expansionAdmin().open(player);
                    plugin.effects().playMenuFlip(player);
                }
                break;

            case 27: // Back
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
                break;
                
            case 35: // Exit
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                break;
        }
    }
    
    private void submit(Player player, Plot plot, int newRadius) {
        plugin.getExpansionRequestManager().createRequest(player, plot, newRadius);
        player.closeInventory();
        plugin.effects().playConfirm(player);
    }

    private boolean validatePlot(Player player, Plot plot) {
        if (plot == null || !plot.getOwner().equals(player.getUniqueId())) {
            plugin.msg().send(player, "no_plot_here");
            plugin.effects().playError(player);
            return false;
        }
        
        if (plugin.getExpansionRequestManager().hasPendingRequest(player.getUniqueId())) {
            plugin.msg().send(player, "expansion_exists"); 
            plugin.effects().playError(player);
            return false;
        }
        return true;
    }
}
