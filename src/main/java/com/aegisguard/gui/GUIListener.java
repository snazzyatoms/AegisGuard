package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
// --- IMPORTS FOR HOLDERS ---
import com.aegisguard.gui.AdminGUI.AdminHolder;
import com.aegisguard.gui.RolesGUI.PlotSelectorHolder;
import com.aegisguard.gui.RolesGUI.RolesMenuHolder;
import com.aegisguard.gui.RolesGUI.RoleAddHolder;
import com.aegisguard.gui.RolesGUI.RoleManageHolder;
// Import these if you have created the files. If not, keep them commented or compilation will fail.
// import com.aegisguard.expansions.ExpansionRequestGUI.ExpansionHolder;
// import com.aegisguard.expansions.ExpansionRequestAdminGUI.ExpansionAdminHolder;
// import com.aegisguard.gui.SettingsGUI.SettingsGUIHolder;
// import com.aegisguard.gui.PlayerGUI.PlayerMenuHolder;

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
 */
public class GUIListener implements Listener {

    private final AegisGuard plugin;

    public GUIListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        // Only handle clicks in the TOP inventory
        Inventory top = player.getOpenInventory().getTopInventory();
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // Get the Holder to identify which GUI this is
        InventoryHolder holder = top.getHolder();
        if (holder == null) return;

        // --- ROUTING ---

        // 1. Admin GUI (Fixed)
        if (holder instanceof AdminHolder) {
            // We instantiate a new instance to handle the logic safely
            new AdminGUI(plugin).handleClick(player, e);
            return;
        }

        // 2. Roles GUIs (Fixed)
        if (holder instanceof PlotSelectorHolder castHolder) {
            new RolesGUI(plugin).handlePlotSelectorClick(player, e, castHolder);
            return;
        } 
        else if (holder instanceof RolesMenuHolder castHolder) {
            new RolesGUI(plugin).handleRolesMenuClick(player, e, castHolder);
            return;
        } 
        else if (holder instanceof RoleAddHolder castHolder) {
            new RolesGUI(plugin).handleAddTrustedClick(player, e, castHolder);
            return;
        } 
        else if (holder instanceof RoleManageHolder castHolder) {
            new RolesGUI(plugin).handleManageRoleClick(player, e, castHolder);
            return;
        }

        /* * --- UNCOMMENT THE BLOCKS BELOW AS YOU FIX/ADD THESE FILES ---
         * ensuring the Holder classes inside them are "public static"
         */

        // if (holder instanceof PlayerMenuHolder) {
        //     plugin.gui().player().handleClick(player, e);
        // }
        
        // else if (holder instanceof SettingsGUIHolder) {
        //     plugin.gui().settings().handleClick(player, e);
        // }

        // else if (holder instanceof PlotFlagsHolder) {
        //     plugin.gui().flags().handleClick(player, e, (PlotFlagsHolder) holder);
        // }

        // else if (holder instanceof ExpansionHolder) {
        //     // plugin.gui().expansionRequest().handleClick(player, e);
        // }
    }
}
