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

import java.util.Arrays;
import java.util.List;

/**
 * PlayerGUI
 * - The main dashboard for AegisGuard.
 * - Updated for v1.1.1 (Leveling, Zoning, Biomes).
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
        String title = GUIManager.safeText(plugin.msg().get(player, "menu_title"), "§8AegisGuard Dashboard");
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 54, title);

        // --- 1. Glass Borders (Frame) ---
        ItemStack filler = GUIManager.getFiller();
        int[] borderSlots = {
            0,1,2,3,4,5,6,7,8,    // Top
            9,17,                 // Sides
            18,26,
            27,35,
            36,44,
            45,46,47,51,52,53     // Bottom (leaving space for center buttons)
        };
        
        for (int i : borderSlots) {
            inv.setItem(i, filler);
        }

        // --- 2. HEADER: Info & Travel ---
        
        // Codex (Slot 4)
        inv.setItem(4, GUIManager.createItem(Material.WRITABLE_BOOK, 
            GUIManager.safeText(plugin.msg().get(player, "button_info"), "§fGuardian Codex"), 
            plugin.msg().getList(player, "info_lore")));

        // Travel (Slot 13)
        if (plugin.cfg().isTravelSystemEnabled()) {
             inv.setItem(13, GUIManager.createItem(Material.COMPASS,
                GUIManager.safeText(plugin.msg().get(player, "visit_gui_title"), "§bTravel System"),
                List.of("§7Visit trusted plots and server warps.")
            ));
        }

        // --- 3. CORE: Plot Management ---
        
        Plot currentPlot = plugin.store().getPlotAt(player.getLocation());
        boolean isOwner = currentPlot != null && currentPlot.getOwner().equals(player.getUniqueId());
        boolean isAdmin = plugin.isAdmin(player);
        boolean canManage = isOwner || isAdmin;

        // Claim Land (Slot 20)
        boolean hasSelection = plugin.selection().hasSelection(player);
        if (hasSelection) {
            inv.setItem(20, GUIManager.createItem(Material.LIGHTNING_ROD, 
                "§aClaim Land", 
                List.of("§7Create a new plot from your", "§7current selection.", "", "§eClick to Confirm")
            ));
        } else {
            inv.setItem(20, GUIManager.createItem(Material.BARRIER, 
                "§cClaim Land (Locked)", 
                List.of("§7You must select 2 corners", "§7with the Wand first!", "", "§cStatus: No Selection")
            ));
        }

        // Flags (Slot 22) - Show diff icon if standing in owned plot
        Material flagIcon = canManage ? Material.OAK_SIGN : Material.OAK_HANGING_SIGN;
        String flagTitle = canManage ? "§6Plot Flags" : "§7Plot Flags (Locked)";
        List<String> flagLore = canManage ? 
            List.of("§7Manage protection settings", "§7for this plot.") : 
            List.of("§cYou must be standing inside", "§cyour plot to edit flags.");
            
        inv.setItem(22, GUIManager.createItem(flagIcon, flagTitle, flagLore));

        // Roles (Slot 24)
        Material roleIcon = canManage ? Material.PLAYER_HEAD : Material.SKELETON_SKULL;
        String roleTitle = canManage ? "§bManage Roles" : "§7Manage Roles (Locked)";
        List<String> roleLore = canManage ? 
            List.of("§7Trust friends and manage", "§7permissions.") : 
            List.of("§cYou must be standing inside", "§cyour plot to edit roles.");

        inv.setItem(24, GUIManager.createItem(roleIcon, roleTitle, roleLore));
        
        // --- 4. ADVANCED: Leveling & Zones (v1.1.0) ---
        
        // Leveling (Slot 29)
        if (plugin.cfg().isLevelingEnabled()) {
            inv.setItem(29, GUIManager.createItem(Material.EXPERIENCE_BOTTLE, 
                "§dPlot Leveling", 
                List.of("§7Upgrade your plot to unlock", "§7larger borders and more members.")
            ));
        }
        
        // Zoning (Slot 31)
        if (plugin.cfg().isZoningEnabled()) {
             inv.setItem(31, GUIManager.createItem(Material.IRON_BARS, 
                "§eSub-Zones (Rentals)", 
                List.of("§7Create rentable areas inside", "§7your plot for other players.")
            ));
        }
        
        // Biomes (Slot 33)
        if (plugin.cfg().isBiomesEnabled()) {
             inv.setItem(33, GUIManager.createItem(Material.SPORE_BLOSSOM, 
                "§aBiome Changer", 
                List.of("§7Change the biome of your", "§7claimed land.")
            ));
        }

        // --- 5. MARKET & UTILS ---
        
        // Marketplace (Slot 38)
        inv.setItem(38, GUIManager.createItem(Material.GOLD_INGOT, 
            "§eReal Estate Market", 
            List.of("§7Buy, sell, and rent plots.")
        ));

        // Expansion Request (Slot 40)
        inv.setItem(40, GUIManager.createItem(Material.DIAMOND_PICKAXE, 
            "§bRequest Expansion", 
            List.of("§7Apply to increase your", "§7claim limits manually.")
        ));

        // Auctions (Slot 42)
        if (plugin.cfg().isUpkeepEnabled()) {
             inv.setItem(42, GUIManager.createItem(Material.LAVA_BUCKET, 
                "§cPlot Auctions", 
                List.of("§7Bid on expired plots.")
            ));
        }

        // --- 6. FOOTER ---
        
        // Settings (Slot 48)
        inv.setItem(48, GUIManager.createItem(Material.COMPARATOR, "§9Player Settings", List.of("§7Configure notifications and chat.")));

        // Admin Panel (Slot 49)
        if (plugin.isAdmin(player)) {
            inv.setItem(49, GUIManager.createItem(Material.REDSTONE_BLOCK, "§c§lAdmin Panel", List.of("§7Open server management tools.")));
        }

        // Exit (Slot 50)
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, "§cExit", List.of("§7Close the menu.")));

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
            case 4: // Info
                plugin.gui().info().open(player); 
                break;
                
            case 13: // Travel
                if (plugin.cfg().isTravelSystemEnabled()) {
                     plugin.gui().visit().open(player, 0, false); 
                }
                break;

            case 20: // Claim
                if (plugin.selection().hasSelection(player)) {
                    player.closeInventory();
                    plugin.selection().confirmClaim(player);
                } else {
                    GUIManager.playSuccess(player); // Error sound replacement logic handled in GUIManager usually, or play raw sound here
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                break;

            case 22: // Flags
                if (!canManage) { 
                    plugin.msg().send(player, "no_plot_here"); 
                    return; 
                }
                plugin.gui().flags().open(player, plot); 
                break;
                
            case 24: // Roles
                // For roles, usually generic roles GUI or specific plot roles? Assuming generic manager for now
                // Or if it's plot specific:
                if (plot != null && canManage) {
                     plugin.gui().roles().open(player); 
                } else {
                     plugin.msg().send(player, "no_plot_here");
                }
                break;
                
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

            case 38: // Market
                plugin.gui().market().open(player, 0); 
                break;
                
            case 40: // Expansion
                plugin.gui().expansionRequest().open(player); 
                break;
                
            case 42: // Auction
                if (plugin.cfg().isUpkeepEnabled()) plugin.gui().auction().open(player, 0); 
                break;
            
            case 48: // Settings
                plugin.gui().settings().open(player); 
                break;
            
            case 49: // Admin
                if (isAdmin) plugin.gui().admin().open(player);
                break;

            case 50: // Exit
                player.closeInventory();
                break;
        }
        
        if (e.getSlot() != 20 && e.getSlot() != 50) {
             GUIManager.playClick(player);
        }
    }
}
