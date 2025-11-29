package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.expansions.ExpansionRequestAdminGUI;
import com.aegisguard.expansions.ExpansionRequestGUI;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class GUIManager {

    private final AegisGuard plugin;

    // --- SUB-MENUS ---
    private final PlayerGUI playerGUI;
    private final SettingsGUI settingsGUI;
    private final RolesGUI rolesGUI;
    private final InfoGUI infoGUI;
    private final VisitGUI visitGUI;
    
    // Admin
    private final AdminGUI adminGUI;
    private final AdminPlotListGUI adminPlotListGUI;
    private final ExpansionRequestGUI expansionRequestGUI;
    private final ExpansionRequestAdminGUI expansionAdminGUI;
    
    // Plot Management
    private final PlotFlagsGUI plotFlagsGUI;
    private final PlotCosmeticsGUI plotCosmeticsGUI;
    
    // Economy
    private final PlotMarketGUI plotMarketGUI;
    private final PlotAuctionGUI plotAuctionGUI;
    
    // New v1.1.0+ Features
    private final LevelingGUI levelingGUI;
    private final ZoningGUI zoningGUI;
    private final BiomeGUI biomeGUI;

    public GUIManager(AegisGuard plugin) {
        this.plugin = plugin;
        
        // Initialize all sub-menus
        this.playerGUI = new PlayerGUI(plugin);
        this.settingsGUI = new SettingsGUI(plugin);
        this.adminGUI = new AdminGUI(plugin);
        this.expansionRequestGUI = new ExpansionRequestGUI(plugin);
        this.expansionAdminGUI = new ExpansionRequestAdminGUI(plugin);
        this.rolesGUI = new RolesGUI(plugin); 
        this.plotFlagsGUI = new PlotFlagsGUI(plugin);
        this.adminPlotListGUI = new AdminPlotListGUI(plugin);
        this.plotCosmeticsGUI = new PlotCosmeticsGUI(plugin);
        this.plotMarketGUI = new PlotMarketGUI(plugin);
        this.plotAuctionGUI = new PlotAuctionGUI(plugin);
        this.infoGUI = new InfoGUI(plugin);
        this.visitGUI = new VisitGUI(plugin);
        
        // New Features
        this.levelingGUI = new LevelingGUI(plugin);
        this.zoningGUI = new ZoningGUI(plugin);
        this.biomeGUI = new BiomeGUI(plugin);
    }

    // --- OPENERS ---
    
    public void openMain(Player player) {
        if (playerGUI != null) {
            playClick(player);
            playerGUI.open(player);
        }
    }
    
    /**
     * Placeholder method for Diagnostics GUI (Fixes AdminGUI error).
     */
    public void openDiagnostics(Player player) {
        player.sendMessage("§b[AegisGuard] §7Diagnostics: All systems nominal (Stub).");
    }

    // --- GETTERS (Categorized) ---

    // Core
    public PlayerGUI player() { return playerGUI; }
    public SettingsGUI settings() { return settingsGUI; }
    public InfoGUI info() { return infoGUI; }
    public VisitGUI visit() { return visitGUI; }

    // Admin & Staff
    public AdminGUI admin() { return adminGUI; }
    public AdminPlotListGUI plotList() { return adminPlotListGUI; }
    public ExpansionRequestGUI expansionRequest() { return expansionRequestGUI; }
    public ExpansionRequestAdminGUI expansionAdmin() { return expansionAdminGUI; }

    // Plot Management
    public RolesGUI roles() { return rolesGUI; }
    public PlotFlagsGUI flags() { return plotFlagsGUI; }
    public PlotCosmeticsGUI cosmetics() { return plotCosmeticsGUI; }
    public LevelingGUI leveling() { return levelingGUI; }
    public ZoningGUI zoning() { return zoningGUI; }
    public BiomeGUI biomes() { return biomeGUI; }

    // Economy
    public PlotMarketGUI market() { return plotMarketGUI; }
    public PlotAuctionGUI auction() { return plotAuctionGUI; }

    // ======================================
    // --- UTILITIES (Static Helpers) ---
    // ======================================

    /**
     * RESTORED: Converts null or placeholder strings to a safe fallback.
     * Used by all GUIs for reliable display names/titles.
     */
    public static String safeText(String fromMsg, String fallback) {
        if (fromMsg == null) return fallback;
        if (fromMsg.contains("[Missing") || fromMsg.contains("null")) return fallback;
        return fromMsg;
    }

    /**
     * Creates a standardized GUI Item with color translation.
     */
    public static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(color(name));
            if (lore != null) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) coloredLore.add(color(line));
                meta.setLore(coloredLore);
            }
            // Hide everything for a clean UI look
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a filler item (Gray Glass Pane) for empty slots.
     */
    public static ItemStack getFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" "); // Empty name
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Plays a standard UI click sound.
     */
    public static void playClick(Player p) {
        try {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        } catch (Exception ignored) {}
    }
    
    /**
     * Plays a success/purchase sound.
     */
    public static void playSuccess(Player p) {
        try {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
        } catch (Exception ignored) {}
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
