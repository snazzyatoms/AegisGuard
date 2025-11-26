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
 * - UPDATED: Admins can now edit Server Plot flags/roles.
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
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 54, title); // 54 Slots

        // --- 1. Glass Borders ---
        int[] borderSlots = {0,1,2,3,4,5,6,7,8, 9,17, 18,26, 27,35, 36,44, 45,46,47,51,52,53};
        for (int i : borderSlots) {
            inv.setItem(i, GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        // --- 2. Top Row: Utility ---
        // Travel System (Slot 13)
        if (plugin.cfg().raw().getBoolean("travel_system.enabled", true)) {
             inv.setItem(13, GUIManager.icon(
                Material.COMPASS,
                GUIManager.safeText(plugin.msg().get(player, "visit_gui_title"), "§bTravel System"),
                List.of("§7Visit trusted plots and server warps.")
            ));
        }

        // Codex (Slot 4)
        inv.setItem(4, GUIManager.icon(Material.WRITABLE_BOOK, 
            GUIManager.safeText(plugin.msg().get(player, "button_info"), "§fGuardian Codex"), 
            plugin.msg().getList(player, "info_lore")));

        // --- 3. Middle Row: Plot Management ---
        // Claim Land (Slot 20)
        boolean hasSelection = plugin.selection().hasSelection(player);
        if (hasSelection) {
            inv.setItem(20, GUIManager.icon(Material.LIGHTNING_ROD, 
                GUIManager.safeText(plugin.msg().get(player, "button_claim_land"), "§aClaim Land"), 
                plugin.msg().getList(player, "claim_land_lore")));
        } else {
            inv.setItem(20, GUIManager.icon(Material.BARRIER, 
                "§cClaim Land (Locked)", 
                List.of("§7You must select 2 corners with wand first!", " ", "§eStatus: §cNo Selection")
            ));
        }

        // Plot Flags (Slot 22)
        inv.setItem(22, GUIManager.icon(Material.OAK_SIGN, 
            GUIManager.safeText(plugin.msg().get(player, "button_plot_flags"), "§6Plot Flags"), 
            plugin.msg().getList(player, "plot_flags_lore")));

        // Roles (Slot 24)
        inv.setItem(24, GUIManager.icon(Material.PLAYER_HEAD, 
            GUIManager.safeText(plugin.msg().get(player, "button_roles"), "§bManage Roles"), 
            plugin.msg().getList(player, "roles_lore")));
        
        // --- 4. Bottom Row: Economy & Expansion ---
        // Marketplace (Slot 38)
        inv.setItem(38, GUIManager.icon(Material.GOLD_INGOT, 
            GUIManager.safeText(plugin.msg().get(player, "button_market"), "§ePlot Marketplace"), 
            plugin.msg().getList(player, "market_lore", List.of("§7Buy, sell, and rent plots."))));
        
        // Expansion (Slot 40)
        inv.setItem(40, GUIManager.icon(Material.DIAMOND_PICKAXE, 
            GUIManager.safeText(plugin.msg().get(player, "button_expand"), "§dRequest Land Expansion"), 
            plugin.msg().getList(player, "expand_lore", List.of("§7Apply to increase your claim size."))));

        // Auctions (Slot 42)
        if (plugin.cfg().isUpkeepEnabled()) {
             inv.setItem(42, GUIManager.icon(Material.LAVA_BUCKET, 
                GUIManager.safeText(plugin.msg().get(player, "button_auction"), "§cPlot Auctions"), 
                plugin.msg().getList(player, "auction_lore", List.of("§7Bid on expired plots."))));
        }

        // --- 5. Footer: Settings & Exit ---
        // Settings (Slot 48)
        inv.setItem(48, GUIManager.icon(Material.COMPARATOR, 
            GUIManager.safeText(plugin.msg().get(player, "button_player_settings"), "§9Player Settings"), 
            plugin.msg().getList(player, "player_settings_lore")));

        // Admin Panel (Slot 49)
        if (plugin.isAdmin(player)) {
            inv.setItem(49, GUIManager.icon(Material.REDSTONE_BLOCK, "§c§lAdmin Panel", List.of("§7Open server management tools.")));
        }

        // Exit (Slot 50)
        inv.setItem(50, GUIManager.icon(Material.BARRIER, GUIManager.safeText(plugin.msg().get(player, "button_exit"), "§cExit"), plugin.msg().getList(player, "exit_lore")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        boolean isOwner = plot != null && plot.getOwner().equals(player.getUniqueId());
        boolean isAdmin = plugin.isAdmin(player);

        switch (e.getSlot()) {
            case 13: // Travel
                if (plugin.cfg().raw().getBoolean("travel_system.enabled", true)) {
                     plugin.gui().visit().open(player, 0, false); 
                     plugin.effects().playMenuFlip(player);
                }
                break;
            
            case 4: plugin.gui().info().open(player); plugin.effects().playMenuFlip(player); break;

            case 20: // Claim
                if (plugin.selection().hasSelection(player)) {
                    player.closeInventory();
                    plugin.selection().confirmClaim(player);
                    plugin.effects().playMenuFlip(player);
                } else {
                    plugin.effects().playError(player);
                }
                break;

            case 22: // Flags
                // FIX: Allow if Owner OR Admin (Server Plots)
                if (!isOwner && !isAdmin) { 
                    plugin.msg().send(player, "no_plot_here"); 
                    plugin.effects().playError(player); 
                    return; 
                }
                plugin.gui().flags().open(player, plot); 
                plugin.effects().playMenuFlip(player); 
                break;
                
            case 24: // Roles
                // FIX: Allow if Owner OR Admin (Add builders to spawn)
                if (!isOwner && !isAdmin && plot == null) {
                     // If no plot, RolesGUI usually shows list of owned plots.
                     // If standing in Server Plot as Admin, we want to manage THAT plot.
                     plugin.gui().roles().open(player); 
                } else if (plot != null && (isOwner || isAdmin)) {
                     // Ideally pass the plot directly, but RolesGUI defaults to "Select Plot" menu
                     // or current plot context. For v1.1, opening the menu is safe.
                     plugin.gui().roles().open(player);
                } else {
                     plugin.gui().roles().open(player); // Fallback to standard menu
                }
                plugin.effects().playMenuFlip(player); 
                break;
                
            case 38: // Market
                plugin.gui().market().open(player, 0); 
                plugin.effects().playMenuFlip(player); 
                break;
                
            case 40: // Expansion
                plugin.gui().expansionRequest().open(player); 
                plugin.effects().playMenuFlip(player); 
                break;
                
            case 42: // Auction
                if (plugin.cfg().isUpkeepEnabled()) { 
                    plugin.gui().auction().open(player, 0); 
                    plugin.effects().playMenuFlip(player); 
                } 
                break;
            
            case 48: // Settings
                plugin.gui().settings().open(player); 
                plugin.effects().playMenuFlip(player); 
                break;
            
            case 49: // Admin
                if (isAdmin) {
                    plugin.gui().admin().open(player);
                    plugin.effects().playMenuFlip(player);
                }
                break;

            case 50: // Exit
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                break;
        }
    }
}
