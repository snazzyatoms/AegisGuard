package com.aegisguard.gui;

import com.aegisguard.AegisGuard;

// --- IMPORTS FOR ALL HOLDERS ---
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

// --- NEW v1.1.0 IMPORTS ---
import com.aegisguard.gui.LevelingGUI.LevelingHolder;
import com.aegisguard.gui.ZoningGUI.ZoningHolder;

// Expansions
import com.aegisguard.expansions.ExpansionRequestGUI.ExpansionHolder;
import com.aegisguard.expansions.ExpansionRequestAdminGUI.ExpansionAdminHolder;

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

        Inventory top = player.getOpenInventory().getTopInventory();
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        InventoryHolder holder = top.getHolder();
        if (holder == null) return;

        // ==============================================================
        // 1. PLAYER MAIN MENU (Exit Button Works Here)
        // ==============================================================
        if (holder instanceof PlayerMenuHolder) {
            new PlayerGUI(plugin).handleClick(player, e);
        }

        // ==============================================================
        // 2. TRAVEL SYSTEM
        // ==============================================================
        else if (holder instanceof VisitHolder castHolder) {
            new VisitGUI(plugin).handleClick(player, e, castHolder);
        }

        // ==============================================================
        // 3. CODEX / INFO
        // ==============================================================
        else if (holder instanceof InfoHolder) {
            new InfoGUI(plugin).handleClick(player, e);
        }

        // ==============================================================
        // 4. SETTINGS & ADMIN
        // ==============================================================
        else if (holder instanceof SettingsGUIHolder) {
            new SettingsGUI(plugin).handleClick(player, e);
        }
        else if (holder instanceof AdminHolder) {
            new AdminGUI(plugin).handleClick(player, e);
        }

        // ==============================================================
        // 5. ROLES & PERMISSIONS
        // ==============================================================
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

        // ==============================================================
        // 6. PLOT MANAGEMENT
        // ==============================================================
        else if (holder instanceof PlotFlagsHolder castHolder) {
            new PlotFlagsGUI(plugin).handleClick(player, e, castHolder);
        }
        else if (holder instanceof CosmeticsHolder castHolder) {
            new PlotCosmeticsGUI(plugin).handleClick(player, e, castHolder);
        }

        // ==============================================================
        // 7. ECONOMY
        // ==============================================================
        else if (holder instanceof PlotMarketHolder castHolder) {
            new PlotMarketGUI(plugin).handleClick(player, e, castHolder);
        }
        else if (holder instanceof PlotAuctionHolder castHolder) {
            new PlotAuctionGUI(plugin).handleClick(player, e, castHolder);
        }

        // ==============================================================
        // 8. EXPANSIONS & ADMIN LISTS
        // ==============================================================
        else if (holder instanceof ExpansionHolder) {
            new com.aegisguard.expansions.ExpansionRequestGUI(plugin).handleClick(player, e);
        }
        else if (holder instanceof ExpansionAdminHolder) {
            new com.aegisguard.expansions.ExpansionRequestAdminGUI(plugin).handleClick(player, e);
        }
        else if (holder instanceof PlotListHolder castHolder) {
            new AdminPlotListGUI(plugin).handleClick(player, e, castHolder);
        }

        // ==============================================================
        // 9. v1.1.0 NEW FEATURES (Leveling & Zoning)
        // ==============================================================
        else if (holder instanceof LevelingHolder castHolder) {
            new LevelingGUI(plugin).handleClick(player, e, castHolder);
        }
        else if (holder instanceof ZoningHolder castHolder) {
            new ZoningGUI(plugin).handleClick(player, e, castHolder);
        }
    }
}
