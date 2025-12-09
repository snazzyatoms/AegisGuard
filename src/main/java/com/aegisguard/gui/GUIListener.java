package com.aegisguard.gui;

import com.aegisguard.AegisGuard;

// --- IMPORTS (Holders) ---
import com.aegisguard.gui.AdminGUI.AdminHolder;
import com.aegisguard.gui.PlayerGUI.PlayerMenuHolder;
import com.aegisguard.gui.SettingsGUI.SettingsGUIHolder;
import com.aegisguard.gui.RolesGUI.PlotSelectorHolder;
import com.aegisguard.gui.RolesGUI.RolesMenuHolder;
import com.aegisguard.gui.RolesGUI.RoleAddHolder;
import com.aegisguard.gui.RolesGUI.RoleManageHolder;
import com.aegisguard.gui.PlotFlagsGUI.PlotFlagsHolder;
import com.aegisguard.gui.PlotMarketGUI.PlotMarketHolder;
import com.aegisguard.gui.PlotAuctionGUI.PlotAuctionHolder;
import com.aegisguard.gui.PlotCosmeticsGUI.CosmeticsHolder;
import com.aegisguard.gui.AdminPlotListGUI.PlotListHolder;
import com.aegisguard.gui.InfoGUI.InfoHolder;
import com.aegisguard.gui.VisitGUI.VisitHolder;
import com.aegisguard.gui.PlotStatusGUI.PlotStatusHolder; // Added this import

// v1.1.0 Features
import com.aegisguard.gui.LevelingGUI.LevelingHolder;
import com.aegisguard.gui.ZoningGUI.ZoningHolder;
import com.aegisguard.gui.BiomeGUI.BiomeHolder;

import com.aegisguard.expansions.ExpansionRequestGUI.ExpansionHolder;
import com.aegisguard.expansions.ExpansionRequestAdminGUI.ExpansionAdminHolder;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * GUIListener
 * - The central "switchboard" for ALL plugin GUI clicks.
 * - Also hard-blocks moving items into/out of AegisGuard menus.
 */
public class GUIListener implements Listener {

    private final AegisGuard plugin;

    public GUIListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // Helper: is this one of our GUIs?
    private boolean isAegisGuiHolder(InventoryHolder holder) {
        return holder instanceof PlayerMenuHolder
                || holder instanceof VisitHolder
                || holder instanceof InfoHolder
                || holder instanceof SettingsGUIHolder
                || holder instanceof AdminHolder
                || holder instanceof PlotListHolder
                || holder instanceof PlotSelectorHolder
                || holder instanceof RolesMenuHolder
                || holder instanceof RoleAddHolder
                || holder instanceof RoleManageHolder
                || holder instanceof PlotFlagsHolder
                || holder instanceof CosmeticsHolder
                || holder instanceof LevelingHolder
                || holder instanceof ZoningHolder
                || holder instanceof BiomeHolder
                || holder instanceof PlotMarketHolder
                || holder instanceof PlotAuctionHolder
                || holder instanceof ExpansionHolder
                || holder instanceof ExpansionAdminHolder
                || holder instanceof PlotStatusHolder; // Registered here
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = player.getOpenInventory().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (holder == null || !isAegisGuiHolder(holder)) {
            // Not one of our GUIs, ignore.
            return;
        }

        // --- Global protection while ANY Aegis GUI is open ---

        // 1) Block ALL shift-click / hotbar swaps / offhand / double-click,
        //    regardless of which inventory was clicked.
        ClickType click = e.getClick();
        switch (click) {
            case SHIFT_LEFT,
                 SHIFT_RIGHT,
                 NUMBER_KEY,
                 DOUBLE_CLICK,
                 SWAP_OFFHAND -> {
                     e.setCancelled(true);
                     return;
                 }
            default -> { /* fall through */ }
        }

        // 2) Block ALL clicks that originate in the player's own inventory
        //    while the GUI is open.
        //    This effectively freezes the player's inventory so they cannot
        //    pick up items to move them into the GUI.
        if (e.getClickedInventory() != null
                && e.getClickedInventory().equals(player.getInventory())) {
            e.setCancelled(true);
            return;
        }

        // From here on, we only care about clicks in the top inventory (the GUI itself).
        Inventory clickedInv = e.getClickedInventory();
        if (clickedInv == null || !clickedInv.equals(top)) {
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            e.setCancelled(true);
            return;
        }

        // Let individual GUIs handle the button logic.
        if (holder instanceof PlayerMenuHolder) {
            new PlayerGUI(plugin).handleClick(player, e);
        }
        else if (holder instanceof VisitHolder castHolder) {
            new VisitGUI(plugin).handleClick(player, e, castHolder);
        }
        else if (holder instanceof InfoHolder) {
            new InfoGUI(plugin).handleClick(player, e);
        }
        else if (holder instanceof SettingsGUIHolder) {
            new SettingsGUI(plugin).handleClick(player, e);
        }
        else if (holder instanceof AdminHolder) {
            new AdminGUI(plugin).handleClick(player, e);
        }
        else if (holder instanceof PlotListHolder castHolder) {
            new AdminPlotListGUI(plugin).handleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotSelectorHolder castHolder) {
            new RolesGUI(plugin).handlePlotSelectorClick(player, e, castHolder);
        }
        else if (holder instanceof RolesMenuHolder castHolder) {
            new RolesGUI(plugin).handleRolesMenuClick(player, e, castHolder);
        }
        else if (holder instanceof RoleAddHolder castHolder) {
            new RolesGUI(plugin).handleAddTrustedClick(player, e, castHolder);
        }
        else if (holder instanceof RoleManageHolder castHolder) {
            new RolesGUI(plugin).handleManageRoleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotFlagsHolder castHolder) {
            new PlotFlagsGUI(plugin).handleClick(player, e, castHolder);
        }
        else if (holder instanceof CosmeticsHolder castHolder) {
            new PlotCosmeticsGUI(plugin).handleClick(player, e, castHolder);
        }
        else if (holder instanceof LevelingHolder castHolder) {
            new LevelingGUI(plugin).handleClick(player, e, castHolder);
        }
        else if (holder instanceof ZoningHolder castHolder) {
            new ZoningGUI(plugin).handleClick(player, e, castHolder);
        }
        else if (holder instanceof BiomeHolder castHolder) {
            new BiomeGUI(plugin).handleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotMarketHolder castHolder) {
            new PlotMarketGUI(plugin).handleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotAuctionHolder castHolder) {
            new PlotAuctionGUI(plugin).handleClick(player, e, castHolder);
        }
        else if (holder instanceof ExpansionHolder) {
            new com.aegisguard.expansions.ExpansionRequestGUI(plugin).handleClick(player, e);
        }
        else if (holder instanceof ExpansionAdminHolder) {
            new com.aegisguard.expansions.ExpansionRequestAdminGUI(plugin).handleClick(player, e);
        }
        else if (holder instanceof PlotStatusHolder castHolder) {
            new PlotStatusGUI(plugin).handleClick(player, e, castHolder);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = player.getOpenInventory().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (holder == null || !isAegisGuiHolder(holder)) return;

        // Strict: If you try to drag ANY items while this GUI is open, cancel it.
        // This prevents picking up an item in the bottom inv and "swiping" it across the top.
        e.setCancelled(true);
    }
}
