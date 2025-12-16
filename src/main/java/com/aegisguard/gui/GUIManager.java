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
import java.util.Collections;
import java.util.List;

public class GUIManager {

    private final AegisGuard plugin;

    // Vanilla client + Bukkit safety (string titles can still explode if too long)
    private static final int MAX_GUI_TITLE_LEN = 32;

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

    // New: Plot Status Codex (replaces sidebar)
    private final PlotStatusGUI plotStatusGUI;

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

        // New: Plot Status Codex GUI
        this.plotStatusGUI = new PlotStatusGUI(plugin);
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

    // New: Plot Status Codex
    public PlotStatusGUI plotStatus() { return plotStatusGUI; }

    // Economy
    public PlotMarketGUI market() { return plotMarketGUI; }
    public PlotAuctionGUI auction() { return plotAuctionGUI; }

    // ======================================
    // --- LANGUAGE GATEWAY (Codex Engine) ---
    // ======================================

    /**
     * Centralized text lookup using the Aegis Codex engine.
     *
     * Usage example in other GUIs:
     * String title = plugin.gui().tr(player, "menu_title", "&b⚔ AegisGuard Menu");
     */
    public String tr(Player player, String key, String fallback) {
        try {
            if (plugin.codex() != null) {
                String value = plugin.codex().tr(player, key);
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        } catch (Throwable ignored) {
            // If Codex blows up, we silently fall back to the hardcoded text.
        }
        return fallback;
    }

    /**
     * List/lore variant for language lookups.
     */
    public List<String> trList(Player player, String key, List<String> fallback) {
        try {
            if (plugin.codex() != null) {
                List<String> value = plugin.codex().trList(player, key);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback == null ? Collections.emptyList() : fallback;
    }

    // ======================================
    // --- TITLES (Fixes &-codes + length) ---
    // ======================================

    /**
     * ✅ GUI TITLE HELPER (Use this in every GUI open() method)
     *
     * Fixes:
     * - "&b" showing in inventory titles (now translated properly)
     * - Missing keys fallback
     * - Overlong titles causing Bukkit exceptions
     *
     * Example:
     * String title = plugin.gui().title(player, "menu_title", "&b⚔ AegisGuard Menu");
     */
    public String title(Player player, String key, String fallback) {
        String raw = safeText(tr(player, key, fallback), fallback);
        String colored = color(raw);
        return clampInventoryTitle(colored);
    }

    /**
     * Same as title(), but if you already have the raw string (not a codex key).
     */
    public static String titleRaw(String rawTitle, String fallback) {
        String raw = safeText(rawTitle, fallback);
        String colored = ChatColor.translateAlternateColorCodes('&', raw);
        return clampInventoryTitle(colored);
    }

    /**
     * Inventory title clamp that won't cut a color code in half.
     */
    private static String clampInventoryTitle(String title) {
        if (title == null) return "";
        if (title.length() <= MAX_GUI_TITLE_LEN) return title;

        StringBuilder out = new StringBuilder(MAX_GUI_TITLE_LEN);
        int i = 0;
        while (i < title.length() && out.length() < MAX_GUI_TITLE_LEN) {
            char c = title.charAt(i);

            // Don't end on a dangling color char (§)
            if (c == ChatColor.COLOR_CHAR) {
                // Need room for '§' + code
                if (out.length() + 2 > MAX_GUI_TITLE_LEN) break;

                out.append(c);
                if (i + 1 < title.length()) {
                    out.append(title.charAt(i + 1));
                    i += 2;
                    continue;
                } else {
                    break;
                }
            }

            out.append(c);
            i++;
        }

        // Safety: never end with bare §
        if (out.length() > 0 && out.charAt(out.length() - 1) == ChatColor.COLOR_CHAR) {
            out.deleteCharAt(out.length() - 1);
        }

        return out.toString();
    }

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
     * Signature: (Material, name, lore)
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
     * Convenience overload: (name, Material, lore)
     */
    public static ItemStack createItem(String name, Material mat, List<String> lore) {
        return createItem(mat, name, lore);
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

    /**
     * ✅ NEW: Generate a "Domain Registry" item for the main menu.
     * Shows: Total, Used, Available Claim Blocks.
     * Updated: Uses PAPER icon to avoid conflict with Economy Gold Ingot.
     */
    public ItemStack createLedgerItem(Player p) {
        long total = plugin.getClaimBlockManager().getTotalBlocks(p.getUniqueId());
        long used = plugin.getClaimBlockManager().getUsedBlocks(p.getUniqueId());
        long available = plugin.getClaimBlockManager().getAvailableBlocks(p.getUniqueId());

        String title = tr(p, "ledger_title", "&6📜 Domain Registry");
        List<String> lore = new ArrayList<>();

        lore.add(tr(p, "ledger_available", "&7Available: &a" + available));
        lore.add(tr(p, "ledger_used", "&7Used: &c" + used));
        lore.add(tr(p, "ledger_total", "&7Total Capacity: &e" + total));
        lore.add(" ");
        lore.add("&eClick to view detailed stats.");

        return createItem(Material.PAPER, title, lore);
    }
}
