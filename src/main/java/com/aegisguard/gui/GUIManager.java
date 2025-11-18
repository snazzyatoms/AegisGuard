package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.expansions.ExpansionRequestAdminGUI;
import com.aegisguard.expansions.ExpansionRequestGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * GUIManager
 * - Central hub for all plugin GUIs.
 * - Provides access to all GUI instances.
 * - Contains static helper methods for creating icons.
 *
 * --- UPGRADE NOTES ---
 * - This is the "Ultimate" version.
 * - Removed the title-based handleClick() method (now handled by GUIListener).
 * - Removed obsolete TrustedGUI.
 * - Added all new GUIs (Roles, Flags, Cosmetics, Market, Auction, AdminList).
 */
public class GUIManager {

    private final AegisGuard plugin;

    // Sub GUIs
    private final PlayerGUI playerGUI;
    private final SettingsGUI settingsGUI;
    private final AdminGUI adminGUI;
    private final ExpansionRequestGUI expansionRequestGUI;
    private final ExpansionRequestAdminGUI expansionAdminGUI;
    private final RolesGUI rolesGUI;
    private final PlotFlagsGUI plotFlagsGUI;
    private final AdminPlotListGUI adminPlotListGUI;
    private final PlotCosmeticsGUI plotCosmeticsGUI;
    private final PlotMarketGUI plotMarketGUI;
    private final PlotAuctionGUI plotAuctionGUI;

    public GUIManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.playerGUI = new PlayerGUI(plugin);
        this.settingsGUI = new SettingsGUI(plugin);
        this.adminGUI = new AdminGUI(plugin);
        this.expansionRequestGUI = new ExpansionRequestGUI(plugin);
        this.expansionAdminGUI = new ExpansionRequestAdminGUI(plugin);
        this.rolesGUI = new RolesGUI(plugin); // Replaces TrustedGUI
        this.plotFlagsGUI = new PlotFlagsGUI(plugin);
        this.adminPlotListGUI = new AdminPlotListGUI(plugin);
        this.plotCosmeticsGUI = new PlotCosmeticsGUI(plugin);
        this.plotMarketGUI = new PlotMarketGUI(plugin);
        this.plotAuctionGUI = new PlotAuctionGUI(plugin);
    }

    // --- Accessors ---
    public PlayerGUI player() { return playerGUI; }
    public SettingsGUI settings() { return settingsGUI; }
    public AdminGUI admin() { return adminGUI; }
    public ExpansionRequestGUI expansionRequest() { return expansionRequestGUI; }
    public ExpansionRequestAdminGUI expansionAdmin() { return expansionAdminGUI; }
    public RolesGUI roles() { return rolesGUI; }
    public PlotFlagsGUI flags() { return plotFlagsGUI; }
    public AdminPlotListGUI plotList() { return adminPlotListGUI; }
    public PlotCosmeticsGUI cosmetics() { return plotCosmeticsGUI; }
    public PlotMarketGUI market() { return plotMarketGUI; }
    public PlotAuctionGUI auction() { return plotAuctionGUI; }

    /* -----------------------------
     * Open Main Menu (Player GUI)
     * ----------------------------- */
    public void openMain(Player player) {
        playerGUI.open(player);
    }

    /* -----------------------------
     * Placeholder for /ag admin diag
     * ----------------------------- */
    public void openDiagnostics(Player player) {
        plugin.msg().send(player, "admin_diagnostics_placeholder");
    }

    /* -----------------------------
     * Helper: Build Icon
     * (Used by all GUIs)
     * ----------------------------- */
    public static ItemStack icon(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_POTION_EFFECTS, ItemFlag.HIDE_ITEM_SPECIFICS);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Safely gets a message string or returns a fallback.
     * Prevents [Missing message] from appearing in GUIs.
     */
    public static String safeText(String fromMsg, String fallback) {
        if (fromMsg == null) return fallback;
        if (fromMsg.contains("[Missing")) return fallback;
        return fromMsg;
    }
}
