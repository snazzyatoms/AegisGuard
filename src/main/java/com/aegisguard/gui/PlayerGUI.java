package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * PlayerGUI
 * - The main dashboard for AegisGuard.
 * - Fully localized: Text updates based on player language setting.
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
        // Title from config
        String title = GUIManager.safeText(plugin.msg().get(player, "menu_title"), "§8AegisGuard Dashboard");
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 54, title);

        // --- 1. Glass Borders ---
        ItemStack filler = GUIManager.getFiller();
        int[] borderSlots = {0,1,2,3,4,5,6,7,8, 9,17, 18,26, 27,35, 36,44, 45,46,47,51,52,53};
        for (int i : borderSlots) inv.setItem(i, filler);

        // --- 2. HEADER ---
        
        // Codex (Slot 4)
        inv.setItem(4, GUIManager.createItem(Material.WRITABLE_BOOK, 
            plugin.msg().get(player, "button_info"), 
            plugin.msg().getList(player, "info_lore")));

        // Travel (Slot 13)
        if (plugin.cfg().isTravelSystemEnabled()) {
             inv.setItem(13, GUIManager.createItem(Material.COMPASS,
                plugin.msg().get(player, "visit_gui_title"),
                plugin.msg().getList(player, "visit_button_lore")));
        }

        // --- 3. CORE MANAGEMENT ---
        
        Plot currentPlot = plugin.store().getPlotAt(player.getLocation());
        boolean isOwner = currentPlot != null && currentPlot.getOwner().equals(player.getUniqueId());
        boolean isAdmin = plugin.isAdmin(player);
        boolean canManage = isOwner || isAdmin;

        // Claim Land (Slot 20)
        boolean hasSelection = plugin.selection().hasSelection(player);
        if (hasSelection) {
            inv.setItem(20, GUIManager.createItem(Material.LIGHTNING_ROD, 
                plugin.msg().get(player, "button_claim_land"), 
                plugin.msg().getList(player, "claim_land_ready_lore")
            ));
        } else {
            inv.setItem(20, GUIManager.createItem(Material.BARRIER, 
                // Fallback to red text if specific locked key missing, though button_claim_land usually has colors
                "§c" + plugin.msg().get(player, "button_claim_land"), 
                plugin.msg().getList(player, "claim_land_lore") // Default lore explains how to get wand
            ));
        }

        // Flags (Slot 22)
        Material flagIcon = canManage ? Material.OAK_SIGN : Material.OAK_HANGING_SIGN;
        inv.setItem(22, GUIManager.createItem(flagIcon, 
            plugin.msg().get(player, "button_plot_flags"), 
            plugin.msg().getList(player, canManage ? "plot_flags_lore" : "plot_flags_locked_lore")
        ));

        // Roles (Slot 24)
        Material roleIcon = canManage ? Material.PLAYER_HEAD : Material.SKELETON_SKULL;
        inv.setItem(24, GUIManager.createItem(roleIcon, 
            plugin.msg().get(player, "button_roles"), 
            plugin.msg().getList(player, canManage ? "roles_lore" : "roles_locked_lore")
        ));
        
        // --- 4. ADVANCED FEATURES ---
        
        // Leveling (Slot 29)
        if (plugin.cfg().isLevelingEnabled()) {
            inv.setItem(29, GUIManager.createItem(Material.EXPERIENCE_BOTTLE, 
                plugin.msg().get(player, "level_gui_title"), 
                plugin.msg().getList(player, "level_button_lore")
            ));
        }
        
        // Zoning (Slot 31)
        if (plugin.cfg().isZoningEnabled()) {
             inv.setItem(31, GUIManager.createItem(Material.IRON_BARS, 
                plugin.msg().get(player, "zone_gui_title"), 
                plugin.msg().getList(player, "zone_button_lore")
            ));
        }
        
        // Biomes (Slot 33)
        if (plugin.cfg().isBiomesEnabled()) {
             inv.setItem(33, GUIManager.createItem(Material.SPORE_BLOSSOM, 
                plugin.msg().get(player, "biome_gui_title"), 
                plugin.msg().getList(player, "biome_button_lore")
            ));
        }

        // --- 5. ECONOMY & EXPANSION ---
        
        // Market (Slot 38)
        inv.setItem(38, GUIManager.createItem(Material.GOLD_INGOT, 
            plugin.msg().get(player, "button_market"), 
            plugin.msg().getList(player, "market_lore")
        ));

        // Expansion (Slot 40)
        inv.setItem(40, GUIManager.createItem(Material.DIAMOND_PICKAXE, 
            plugin.msg().get(player, "button_expand"), 
            plugin.msg().getList(player, "expand_lore")
        ));

        // Auctions (Slot 42)
        if (plugin.cfg().isUpkeepEnabled()) {
             inv.setItem(42, GUIManager.createItem(Material.LAVA_BUCKET, 
                plugin.msg().get(player, "button_auction"), 
                plugin.msg().getList(player, "auction_lore")
            ));
        }

        // --- 6. FOOTER ---
        
        // Settings (Slot 48)
        inv.setItem(48, GUIManager.createItem(Material.COMPARATOR, 
            plugin.msg().get(player, "button_player_settings"), 
            plugin.msg().getList(player, "player_settings_lore")
        ));

        // Admin (Slot 49)
        if (plugin.isAdmin(player)) {
            inv.setItem(49, GUIManager.createItem(Material.REDSTONE_BLOCK, 
                plugin.msg().get(player, "admin_menu_title"), 
                List.of("§7Operator Access Only"))); // Safe to hardcode or add admin_access_lore key
        }

        // Exit (Slot 50)
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, 
            plugin.msg().get(player, "button_exit"), 
            plugin.msg().getList(player, "exit_lore")
        ));

        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        boolean isOwner = plot != null && plot.getOwner().equals(player.getUniqueId());
        boolean isAdmin = plugin.isAdmin(player);
        boolean canManage = isOwner || isAdmin;

        switch (e.getSlot()) {
            case 4: plugin.gui().info().open(player); break;
            
            case 13: 
                if (plugin.cfg().isTravelSystemEnabled()) plugin.gui().visit().open(player, 0, false); 
                break;

            case 20: // Claim
                if (plugin.selection().hasSelection(player)) {
                    player.closeInventory();
                    plugin.selection().confirmClaim(player);
                } else {
                    GUIManager.playSuccess(player); 
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                break;

            case 22: // Flags
                if (canManage) plugin.gui().flags().open(player, plot);
                else plugin.msg().send(player, "no_plot_here");
                break;

            case 24: // Roles
                if (plot != null && canManage) plugin.gui().roles().open(player);
                else plugin.msg().send(player, "no_plot_here");
                break;

            // Advanced Features
            case 29: // Leveling
                if (plugin.cfg().isLevelingEnabled()) {
                    if (plot != null && canManage) plugin.gui().leveling().open(player, plot);
                    else plugin.msg().send(player, "no_plot_here");
                }
                break;
                
            case 31: // Zoning
                if (plugin.cfg().isZoningEnabled()) {
                    if (plot != null && canManage) plugin.gui().zoning().open(player, plot);
                    else plugin.msg().send(player, "no_plot_here");
                }
                break;
                
            case 33: // Biomes
                if (plugin.cfg().isBiomesEnabled()) {
                    if (plot != null && canManage) plugin.gui().biomes().open(player, plot);
                    else plugin.msg().send(player, "no_plot_here");
                }
                break;

            // Economy
            case 38: plugin.gui().market().open(player, 0); break;
            case 40: plugin.gui().expansionRequest().open(player); break;
            case 42: if (plugin.cfg().isUpkeepEnabled()) plugin.gui().auction().open(player, 0); break;

            // System
            case 48: plugin.gui().settings().open(player); break;
            case 49: if (isAdmin) plugin.gui().admin().open(player); break;
            case 50: player.closeInventory(); break;
        }
        
        if (e.getSlot() != 20 && e.getSlot() != 50) {
             GUIManager.playClick(player);
        }
    }
}
