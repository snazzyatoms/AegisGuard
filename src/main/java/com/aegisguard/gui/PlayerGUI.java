package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * PlayerGUI
 * - The main player-facing menu for AegisGuard.
 */
public class PlayerGUI {

    private final AegisGuard plugin;

    public PlayerGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlayerMenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        String title = GUIManager.safeText(plugin.msg().get(player, "menu_title"), "§b§lAegisGuard §7— Menu");
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 27, title);

        // --- NEW: Travel System (Slot 8 - Top Right) ---
        if (plugin.cfg().raw().getBoolean("travel_system.enabled", true)) {
             inv.setItem(8, GUIManager.icon(
                Material.COMPASS,
                GUIManager.safeText(plugin.msg().get(player, "visit_gui_title"), "§bTravel System"),
                List.of("§7Visit trusted plots and server warps.")
            ));
        }

        // Claim Land - SLOT 10
        inv.setItem(10, GUIManager.icon(Material.LIGHTNING_ROD, 
            GUIManager.safeText(plugin.msg().get(player, "button_claim_land"), "§aClaim Land"), 
            plugin.msg().getList(player, "claim_land_lore")));

        // Plot Flags - SLOT 12
        inv.setItem(12, GUIManager.icon(Material.OAK_SIGN, 
            GUIManager.safeText(plugin.msg().get(player, "button_plot_flags"), "§6Plot Flags"), 
            plugin.msg().getList(player, "plot_flags_lore")));

        // Plot Roles - SLOT 14
        inv.setItem(14, GUIManager.icon(Material.PLAYER_HEAD, 
            GUIManager.safeText(plugin.msg().get(player, "button_roles"), "§bManage Roles"), 
            plugin.msg().getList(player, "roles_lore")));
        
        // Plot Marketplace - SLOT 16
        inv.setItem(16, GUIManager.icon(Material.GOLD_INGOT, 
            GUIManager.safeText(plugin.msg().get(player, "button_market"), "§ePlot Marketplace"), 
            plugin.msg().getList(player, "market_lore", List.of("§7Buy, sell, and rent plots."))));
        
        // Plot Auctions - SLOT 18
        if (plugin.cfg().isUpkeepEnabled()) {
             inv.setItem(18, GUIManager.icon(Material.LAVA_BUCKET, 
                GUIManager.safeText(plugin.msg().get(player, "button_auction"), "§cPlot Auctions"), 
                plugin.msg().getList(player, "auction_lore", List.of("§7Bid on expired plots."))));
        }
        
        // Player Settings - SLOT 20
        inv.setItem(20, GUIManager.icon(Material.COMPARATOR, 
            GUIManager.safeText(plugin.msg().get(player, "button_player_settings"), "§9Player Settings"), 
            plugin.msg().getList(player, "player_settings_lore")));

        // Expansion Request - SLOT 22
        inv.setItem(22, GUIManager.icon(Material.DIAMOND_PICKAXE, 
            GUIManager.safeText(plugin.msg().get(player, "button_expand"), "§dRequest Land Expansion"), 
            plugin.msg().getList(player, "expand_lore", List.of("§7Apply to increase your claim size."))));

        // Info - SLOT 24
        inv.setItem(24, GUIManager.icon(Material.WRITABLE_BOOK, 
            GUIManager.safeText(plugin.msg().get(player, "button_info"), "§fInfo"), 
            plugin.msg().getList(player, "info_lore")));

        // --- ADMIN PANEL (Slot 25 - NEW POSITION) ---
        if (plugin.isAdmin(player)) {
            inv.setItem(25, GUIManager.icon(Material.REDSTONE_BLOCK, "§c§lAdmin Panel", List.of("§7Open server management tools.")));
        }

        // --- EXIT BUTTON (Slot 26 - ALWAYS VISIBLE) ---
        inv.setItem(26, GUIManager.icon(Material.BARRIER, GUIManager.safeText(plugin.msg().get(player, "button_exit"), "§cExit"), plugin.msg().getList(player, "exit_lore")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        boolean isOwner = plot != null && plot.getOwner().equals(player.getUniqueId());

        switch (e.getSlot()) {
            case 8: // Travel System
                if (plugin.cfg().raw().getBoolean("travel_system.enabled", true)) {
                     plugin.gui().visit().open(player, 0, false); // Open Friends Tab by default
                     plugin.effects().playMenuFlip(player);
                }
                break;
                
            case 10: player.closeInventory(); plugin.selection().confirmClaim(player); plugin.effects().playMenuFlip(player); break;
            case 12: 
                if (!isOwner) { plugin.msg().send(player, "no_plot_here"); plugin.effects().playError(player); return; }
                plugin.gui().flags().open(player, plot); plugin.effects().playMenuFlip(player); break;
            case 14: plugin.gui().roles().open(player); plugin.effects().playMenuFlip(player); break;
            case 16: plugin.gui().market().open(player, 0); plugin.effects().playMenuFlip(player); break;
            case 18: if (plugin.cfg().isUpkeepEnabled()) { plugin.gui().auction().open(player, 0); plugin.effects().playMenuFlip(player); } break;
            case 20: plugin.gui().settings().open(player); plugin.effects().playMenuFlip(player); break;
            case 22: plugin.gui().expansionRequest().open(player); plugin.effects().playMenuFlip(player); break;
            case 24: plugin.gui().info().open(player); plugin.effects().playMenuFlip(player); break;
            
            case 25: // --- NEW: ADMIN BUTTON ---
                if (plugin.isAdmin(player)) {
                    plugin.gui().admin().open(player);
                    plugin.effects().playMenuFlip(player);
                }
                break;

            case 26: // --- EXIT BUTTON ---
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                break;
        }
    }
}
