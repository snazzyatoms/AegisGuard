package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
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
 */
public class GUIListener implements Listener {

    private final AegisGuard plugin;

    public GUIListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * --- UPGRADED ---
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
        if (holder instanceof AdminGUI.AdminHolder) {
            // This click belongs to the AdminGUI
            plugin.gui().admin().handleClick(player, e);

        } else if (holder instanceof ExpansionRequestGUI.ExpansionHolder) {
            // This click belongs to the ExpansionRequestGUI
            plugin.gui().expansionRequest().handleClick(player, e);

        } else if (holder instanceof ExpansionRequestAdminGUI.ExpansionAdminHolder) {
            // This click belongs to the ExpansionRequestAdminGUI
            plugin.gui().expansionAdmin().handleClick(player, e);
        
        }
        // ...
        // else if (holder instanceof PlotFlagsGUI.PlotFlagsHolder) {
        //     plugin.gui().plotFlags().handleClick(player, e);
        // }
        // ...
        // else if (holder instanceof MainMenuGUI.MainMenuHolder) {
        //     plugin.gui().mainMenu().handleClick(player, e);
        // }
    }

    /* -----------------------------------
     * Helper methods removed.
     * Title checking is no longer needed.
     * ----------------------------------- */
}
