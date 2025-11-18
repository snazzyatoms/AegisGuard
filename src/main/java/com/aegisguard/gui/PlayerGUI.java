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
 * - The main player-facing menu for AegisGuard.
 * - This is the "Ultimate" version, featuring all player-facing GUIs.
 *
 * --- UPGRADE NOTES ---
 * - Slots updated for full 27-slot menu (Flags, Market, Auction, Roles, Settings).
 * - Calls are now synchronized to the correct classes (Roles, PlotFlags, Market).
 * - All sound calls use plugin.effects().
 */
public class PlayerGUI {

    private final AegisGuard plugin;

    public PlayerGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Tag holder so click handler only reacts to this GUI.
     */
    private static class PlayerMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void open(Player player) {
        String title = GUIManager.safeText(
                plugin.msg().get(player, "menu_title"),
                "§b§lAegisGuard §7— Menu"
        );
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 27, title);

        // --- ROW 2: FEATURES ---
        
        // Claim Land - SLOT 10
        inv.setItem(10, GUIManager.icon(
                Material.LIGHTNING_ROD,
                GUIManager.safeText(plugin.msg().get(player, "button_claim_land"), "§aClaim Land"),
                plugin.msg().getList(player, "claim_land_lore")
        ));

        // Plot Flags - SLOT 12
        inv.setItem(12, GUIManager.icon(
                Material.OAK_SIGN,
                GUIManager.safeText(plugin.msg().get(player, "button_plot_flags"), "§6Plot Flags"),
                plugin.msg().getList(player, "plot_flags_lore")
        ));

        // Plot Roles - SLOT 14
        inv.setItem(14, GUIManager.icon(
                Material.PLAYER_HEAD,
                GUIManager.safeText(plugin.msg().get(player, "button_roles"), "§bManage Roles"),
                plugin.msg().getList(player, "roles_lore")
        ));
        
        // Plot Marketplace - SLOT 16
        inv.setItem(16, GUIManager.icon(
                Material.GOLD_INGOT,
                GUIManager.safeText(plugin.msg().get(player, "button_market"), "§ePlot Marketplace"),
                plugin.msg().getList(player, "market_lore", List.of("§7Buy, sell, and rent plots."))
        ));
        
        // --- NEW: Plot Auctions (if enabled) --- SLOT 18
        if (plugin.cfg().isUpkeepEnabled()) {
             inv.setItem(18, GUIManager.icon(
                Material.LAVA_BUCKET,
                GUIManager.safeText(plugin.msg().get(player, "button_auction"), "§cPlot Auctions"),
                plugin.msg().getList(player, "auction_lore", List.of("§7Bid on expired plots."))
            ));
        }
        
        // --- NEW: Player Settings (Personal) --- SLOT 20
        inv.setItem(20, GUIManager.icon(
                Material.COMPARATOR,
                GUIManager.safeText(plugin.msg().get(player, "button_player_settings"), "§9Player Settings"),
                plugin.msg().getList(player, "player_settings_lore")
        ));


        // Info - SLOT 24
        inv.setItem(24, GUIManager.icon(
                Material.WRITABLE_BOOK,
                GUIManager.safeText(plugin.msg().get(player, "button_info"), "§fInfo"),
                plugin.msg().getList(player, "info_lore")
        ));

        // Exit - SLOT 26
        inv.setItem(26, GUIManager.icon(
                Material.BARRIER,
                GUIManager.safeText(plugin.msg().get(player, "button_exit"), "§cExit"),
                plugin.msg().getList(player, "exit_lore")
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    /**
     * This method is called by GUIListener.
     */
    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true); // prevent item pickup/move
        if (e.getCurrentItem() == null) return;

        // Get plot context for flags/settings
        Plot plot = plugin.store().getPlotAt(player.getLocation());

        switch (e.getSlot()) {
            case 10: { // LIGHTNING_ROD (Claim)
                player.closeInventory();
                plugin.selection().confirmClaim(player);
                plugin.effects().playMenuFlip(player);
                break;
            }
            
            case 12: { // OAK_SIGN (Plot Flags)
                if (plot == null || !plot.getOwner().equals(player.getUniqueId())) {
                    plugin.msg().send(player, "no_plot_here");
                    plugin.effects().playError(player);
                    return;
                }
                plugin.gui().flags().open(player, plot);
                plugin.effects().playMenuFlip(player);
                break;
            }
            
            case 14: { // PLAYER_HEAD (Plot Roles)
                plugin.gui().roles().open(player); // Opens the new RolesGUI
                plugin.effects().playMenuFlip(player);
                break;
            }
            
            case 16: { // GOLD_INGOT (Marketplace)
                plugin.gui().market().open(player, 0);
                plugin.effects().playMenuFlip(player);
                break;
            }
            
            case 18: { // LAVA_BUCKET (Auction)
                 if (plugin.cfg().isUpkeepEnabled()) {
                    plugin.gui().auction().open(player, 0);
                    plugin.effects().playMenuFlip(player);
                }
                break;
            }
            
            case 20: { // COMPARATOR (Player Settings)
                // SettingsGUI can handle a null plot, but we pass it if available
                plugin.gui().settings().open(player, plot);
                plugin.effects().playMenuFlip(player);
                break;
            }
            case 24: { // WRITABLE_BOOK (Info)
                plugin.msg().send(player, "info_message", "§7AegisGuard: lightweight land protection...");
                plugin.effects().playMenuFlip(player);
                break;
            }
            case 26: { // BARRIER (Exit)
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                break;
            }
            default: { /* ignore other slots */ }
        }
    }
}
