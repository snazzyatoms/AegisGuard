package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotStore;
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
 * - Provides access to GUI instances.
 * - Contains static helper methods for creating icons
 * to reduce code duplication (DRY principle).
 *
 * --- UPGRADE NOTES ---
 * - Removed the title-based handleClick() method. This logic is now
 * handled *exclusively* by GUIListener.java using reliable InventoryHolders.
 */
public class GUIManager {

    private final AegisGuard plugin;

    // Sub GUIs
    private final PlayerGUI playerGUI;
    private final TrustedGUI trustedGUI;
    private final SettingsGUI settingsGUI;
    private final AdminGUI adminGUI;
    private final ExpansionRequestGUI expansionRequestGUI; // --- ADDED ---
    private final ExpansionRequestAdminGUI expansionAdminGUI;

    public GUIManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.playerGUI = new PlayerGUI(plugin);
        this.trustedGUI = new TrustedGUI(plugin);
        this.settingsGUI = new SettingsGUI(plugin);
        this.adminGUI = new AdminGUI(plugin);
        this.expansionRequestGUI = new ExpansionRequestGUI(plugin); // --- ADDED ---
        this.expansionAdminGUI = new ExpansionRequestAdminGUI(plugin);
    }

    // --- Accessors ---
    public PlayerGUI player() { return playerGUI; }
    public TrustedGUI trusted() { return trustedGUI; }
    public SettingsGUI settings() { return settingsGUI; }
    public AdminGUI admin() { return adminGUI; }
    public ExpansionRequestGUI expansionRequest() { return expansionRequestGUI; } // --- ADDED ---
    public ExpansionRequestAdminGUI expansionAdmin() { return expansionAdminGUI; }

    /* -----------------------------
     * Open Main Menu (Player GUI)
     * ----------------------------- */
    public void openMain(Player player) {
        // --- MODIFIED ---
        // This is now the main entry point for /aegis
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
            // --- IMPROVEMENT --- Added all flags
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_POTION_EFFECTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * --- NEW ---
     * Safely gets a message string or returns a fallback.
     * Prevents [Missing message] from appearing in GUIs.
     */
    public static String safeText(String fromMsg, String fallback) {
        if (fromMsg == null) return fallback;
        if (fromMsg.contains("[Missing")) return fallback;
        return fromMsg;
    }
}
