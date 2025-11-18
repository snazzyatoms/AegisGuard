package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.expansions.ExpansionRequestAdminGUI;
import com.aegisguard.expansions.ExpansionRequestGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * GUIListener
 * - The central "switchboard" for ALL plugin GUI clicks.
 * - This is the *only* GUI listener that should be registered.
 * - It uses the InventoryHolder of the clicked inventory to route
 * the event to the correct GUI class for handling.
 *
 * --- UPGRADE NOTES ---
 * - This is the "Ultimate" version, containing handlers for all GUIs.
 */
public class GUIListener implements Listener {

    private final AegisGuard plugin;

    public GUIListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * This method now uses InventoryHolders for 100% reliable click routing.
     * It is set to HIGH priority to ensure it can cancel clicks before other plugins.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        // Only handle clicks in the TOP inventory (your GUI), not the player's own inventory
        Inventory top = player.getOpenInventory().getTopInventory();
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // --- RELIABILITY FIX ---
        // We get the holder from the inventory itself, not by checking the title.
        InventoryHolder holder = top.getHolder();

        // Route the click to the correct GUI's handler
        
        // --- ADMIN GUIs ---
        if (holder instanceof AdminGUI.AdminHolder) {
            plugin.gui().admin().handleClick(player, e);
        } else if (holder instanceof AdminPlotListGUI.PlotListHolder) {
            plugin.gui().plotList().handleClick(player, e, (AdminPlotListGUI.PlotListHolder) holder);
        
        // --- PLAYER MAIN MENU ---
        } else if (holder instanceof PlayerGUI.PlayerMenuHolder) {
            plugin.gui().player().handleClick(player, e);
            
        // --- PLAYER PERSONAL SETTINGS ---
        } else if (holder instanceof SettingsGUI.SettingsGUIHolder) {
            plugin.gui().settings().handleClick(player, e);
            
        // --- PLOT FLAGS GUI ---
        } else if (holder instanceof PlotFlagsGUI.PlotFlagsHolder) {
            plugin.gui().flags().handleClick(player, e, (PlotFlagsGUI.PlotFlagsHolder) holder);
        
        // --- PLOT ROLES (multi-stage GUI) ---
        } else if (holder instanceof RolesGUI.PlotSelectorHolder) {
            plugin.gui().roles().handlePlotSelectorClick(player, e, (RolesGUI.PlotSelectorHolder) holder);
        } else if (holder instanceof RolesGUI.RolesMenuHolder) {
            plugin.gui().roles().handleRolesMenuClick(player, e, (RolesGUI.RolesMenuHolder) holder);
        } else if (holder instanceof RolesGUI.RoleAddHolder) {
            plugin.gui().roles().handleAddTrustedClick(player, e, (RolesGUI.RoleAddHolder) holder);
        } else if (holder instanceof RolesGUI.RoleManageHolder) {
            plugin.gui().roles().handleManageRoleClick(player, e, (RolesGUI.RoleManageHolder) holder);

        // --- ECONOMY GUIs ---
        } else if (holder instanceof PlotMarketGUI.PlotMarketHolder) {
            plugin.gui().market().handleClick(player, e, (PlotMarketGUI.PlotMarketHolder) holder);
        } else if (holder instanceof PlotAuctionGUI.PlotAuctionHolder) {
            plugin.gui().auction().handleClick(player, e, (PlotAuctionGUI.PlotAuctionHolder) holder);

        // --- COSMETICS GUI ---
        } else if (holder instanceof PlotCosmeticsGUI.CosmeticsHolder) {
            plugin.gui().cosmetics().handleClick(player, e, (PlotCosmeticsGUI.CosmeticsHolder) holder);

        // --- EXPANSION (Placeholder) GUIs ---
        } else if (holder instanceof ExpansionRequestGUI.ExpansionHolder) {
            plugin.gui().expansionRequest().handleClick(player, e);
        } else if (holder instanceof ExpansionRequestAdminGUI.ExpansionAdminHolder) {
            plugin.gui().expansionAdmin().handleClick(player, e);
        }
    }

    /* -----------------------------------
     * Helper methods removed.
     * Title checking is no longer needed.
     * ----------------------------------- */
}
