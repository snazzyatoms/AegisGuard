package com.aegisguard.gui;

import com.aegisguard.AegisGuard;

// --- IMPORTS (Holders) ---
import com.aegisguard.expansions.ExpansionRequestAdminGUI.ExpansionAdminHolder;
import com.aegisguard.expansions.ExpansionRequestGUI.ExpansionHolder;
import com.aegisguard.gui.AdminGUI.AdminHolder;
import com.aegisguard.gui.AdminPlotListGUI.PlotListHolder;
import com.aegisguard.gui.BiomeGUI.BiomeHolder;
import com.aegisguard.gui.InfoGUI.InfoHolder;
import com.aegisguard.gui.LevelingGUI.LevelingHolder;
import com.aegisguard.gui.PlayerGUI.PlayerMenuHolder;
import com.aegisguard.gui.PlotAuctionGUI.PlotAuctionHolder;
import com.aegisguard.gui.PlotCosmeticsGUI.CosmeticsHolder;
import com.aegisguard.gui.PlotFlagsGUI.PlotFlagsHolder;
import com.aegisguard.gui.PlotMarketGUI.PlotMarketHolder;
import com.aegisguard.gui.PlotStatusGUI.PlotStatusHolder;
import com.aegisguard.gui.RolesGUI.PlotSelectorHolder;
import com.aegisguard.gui.RolesGUI.RoleAddHolder;
import com.aegisguard.gui.RolesGUI.RoleManageHolder;
import com.aegisguard.gui.RolesGUI.RolesMenuHolder;
import com.aegisguard.gui.SettingsGUI.SettingsGUIHolder;
import com.aegisguard.gui.VisitGUI.VisitHolder;
import com.aegisguard.gui.ZoningGUI.ZoningHolder;

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
 * - Central click router for ALL AegisGuard GUIs.
 * - Strictly blocks inventory movement while our menus are open.
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
                || holder instanceof PlotStatusHolder;
    }

    /**
     * NOTE: ignoreCancelled=false so GUI buttons still work even if another plugin
     * cancels the click event earlier. We always cancel movement ourselves anyway.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top == null) return;

        InventoryHolder holder = top.getHolder();
        if (holder == null || !isAegisGuiHolder(holder)) return;

        // Always cancel: these are menu inventories, never item-storage.
        e.setCancelled(true);

        // 1) Block ALL shift-click / hotbar swaps / offhand / double-click globally
        ClickType click = e.getClick();
        switch (click) {
            case SHIFT_LEFT,
                 SHIFT_RIGHT,
                 NUMBER_KEY,
                 DOUBLE_CLICK,
                 SWAP_OFFHAND -> {
                     return;
                 }
            default -> { /* continue */ }
        }

        // 2) Block clicks originating in the player's own inventory while GUI open
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(player.getInventory())) {
            return;
        }

        // Only handle clicks in the top GUI
        Inventory clickedInv = e.getClickedInventory();
        if (clickedInv == null || !clickedInv.equals(top)) return;

        // Sanity: ignore bottom inventory raw slots
        if (e.getRawSlot() < 0 || e.getRawSlot() >= top.getSize()) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // Route to the already-initialized GUI instances (no new allocations per click)
        if (holder instanceof PlayerMenuHolder) {
            plugin.gui().player().handleClick(player, e);
        }
        else if (holder instanceof VisitHolder castHolder) {
            plugin.gui().visit().handleClick(player, e, castHolder);
        }
        else if (holder instanceof InfoHolder) {
            plugin.gui().info().handleClick(player, e);
        }
        else if (holder instanceof SettingsGUIHolder) {
            plugin.gui().settings().handleClick(player, e);
        }
        else if (holder instanceof AdminHolder) {
            plugin.gui().admin().handleClick(player, e);
        }
        else if (holder instanceof PlotListHolder castHolder) {
            plugin.gui().plotList().handleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotSelectorHolder castHolder) {
            plugin.gui().roles().handlePlotSelectorClick(player, e, castHolder);
        }
        else if (holder instanceof RolesMenuHolder castHolder) {
            plugin.gui().roles().handleRolesMenuClick(player, e, castHolder);
        }
        else if (holder instanceof RoleAddHolder castHolder) {
            plugin.gui().roles().handleAddTrustedClick(player, e, castHolder);
        }
        else if (holder instanceof RoleManageHolder castHolder) {
            plugin.gui().roles().handleManageRoleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotFlagsHolder castHolder) {
            plugin.gui().flags().handleClick(player, e, castHolder);
        }
        else if (holder instanceof CosmeticsHolder castHolder) {
            plugin.gui().cosmetics().handleClick(player, e, castHolder);
        }
        else if (holder instanceof LevelingHolder castHolder) {
            plugin.gui().leveling().handleClick(player, e, castHolder);
        }
        else if (holder instanceof ZoningHolder castHolder) {
            plugin.gui().zoning().handleClick(player, e, castHolder);
        }
        else if (holder instanceof BiomeHolder castHolder) {
            plugin.gui().biomes().handleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotMarketHolder castHolder) {
            plugin.gui().market().handleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotAuctionHolder castHolder) {
            plugin.gui().auction().handleClick(player, e, castHolder);
        }
        else if (holder instanceof ExpansionHolder) {
            plugin.gui().expansionRequest().handleClick(player, e);
        }
        else if (holder instanceof ExpansionAdminHolder) {
            plugin.gui().expansionAdmin().handleClick(player, e);
        }
        else if (holder instanceof PlotStatusHolder castHolder) {
            plugin.gui().plotStatus().handleClick(player, e, castHolder);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top == null) return;

        InventoryHolder holder = top.getHolder();
        if (holder == null || !isAegisGuiHolder(holder)) return;

        // Strict: block ALL drags while our GUI is open.
        e.setCancelled(true);
    }
}
