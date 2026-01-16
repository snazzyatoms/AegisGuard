package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockManager;
import com.aegisguard.expansions.ExpansionRequestAdminGUI;
import com.aegisguard.expansions.ExpansionRequestGUI;
import com.aegisguard.snapshots.SnapshotAdminGUI;
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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // New: Plot Status Codex (replaces sidebar)
    private final PlotStatusGUI plotStatusGUI;

    // ✅ NEW: ClaimBlocks Exchange GUI
    private final ClaimBlockExchangeGUI claimBlockExchangeGUI;

    // ✅ NEW: Snapshot Admin GUI (Rollback System)
    private final SnapshotAdminGUI snapshotAdminGUI;

    // Hex pattern (&#RRGGBB)
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

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

        // ✅ NEW: Exchange (only if service exists)
        if (plugin.exchange() != null) {
            this.claimBlockExchangeGUI = new ClaimBlockExchangeGUI(plugin, plugin.exchange());
        } else {
            this.claimBlockExchangeGUI = null;
        }

        // ✅ NEW: Snapshot Admin GUI (only if SnapshotManager exists)
        if (plugin.getSnapshotManager() != null) {
            this.snapshotAdminGUI = new SnapshotAdminGUI(plugin);
        } else {
            this.snapshotAdminGUI = null;
        }
    }

    // --- OPENERS ---

    public void openMain(Player player) {
        if (playerGUI != null) {
            playClick(player);
            playerGUI.open(player);
        }
    }

    /**
     * ✅ NEW: open ClaimBlocks Exchange (safe wrapper)
     */
    public void openClaimBlockExchange(Player player) {
        if (player == null) return;

        if (claimBlockExchangeGUI == null || plugin.exchange() == null) {
            try {
                player.sendMessage(color("&cClaimBlocks Exchange is unavailable."));
            } catch (Throwable ignored) {}
            return;
        }

        // Optional config gate (UI already grays out, but this makes it bulletproof)
        boolean enabled = false;
        try {
            enabled = plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false);
        } catch (Throwable ignored) {}

        if (!enabled) {
            player.sendMessage(color("&cClaimBlocks Exchange is disabled in config.yml."));
            return;
        }

        claimBlockExchangeGUI.open(player);
    }

    /**
     * ✅ NEW: open Snapshot Admin GUI (safe wrapper)
     */
    public void openSnapshotAdmin(Player player) {
        if (player == null) return;

        if (snapshotAdminGUI == null || plugin.getSnapshotManager() == null) {
            try {
                player.sendMessage(color("&cSnapshot system is unavailable."));
            } catch (Throwable ignored) {}
            return;
        }

        // Optional config gate
        boolean enabled = false;
        try {
            enabled = plugin.cfg().raw().getBoolean("snapshots.enabled", true);
        } catch (Throwable ignored) {}

        if (!enabled) {
            player.sendMessage(color("&cSnapshots are disabled in config.yml."));
            return;
        }

        snapshotAdminGUI.open(player);
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

    // ✅ NEW
    public ClaimBlockExchangeGUI exchange() { return claimBlockExchangeGUI; }

    // ✅ NEW: Snapshot Admin
    public SnapshotAdminGUI snapshotAdmin() { return snapshotAdminGUI; }

    // ======================================
    // --- LANGUAGE GATEWAY (Codex Engine) ---
    // ======================================

    public String tr(Player player, String key, String fallback) {
        String value = null;
        try {
            if (plugin.codex() != null) value = plugin.codex().tr(player, key);
        } catch (Throwable ignored) {}

        return safeText(key, value, fallback);
    }

    public String tr(Player player, String key, String fallback, Map<String, String> placeholders) {
        String value = null;
        try {
            if (plugin.codex() != null) value = plugin.codex().tr(player, key, placeholders);
        } catch (Throwable ignored) {}

        String fb = applyPlaceholders(fallback, placeholders);
        return safeText(key, value, fb);
    }

    public String title(Player player, String key, String fallback) {
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(player, key);
        } catch (Throwable ignored) {}

        String t = safeText(key, raw, fallback);

        if (t.length() > 32) t = t.substring(0, 32);
        if (t.endsWith("§")) t = t.substring(0, t.length() - 1);

        return t;
    }

    public String title(Player player, String key, String fallback, Map<String, String> placeholders) {
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(player, key, placeholders);
        } catch (Throwable ignored) {}

        String fb = applyPlaceholders(fallback, placeholders);
        String t = safeText(key, raw, fb);

        if (t.length() > 32) t = t.substring(0, 32);
        if (t.endsWith("§")) t = t.substring(0, t.length() - 1);

        return t;
    }

    public List<String> trList(Player player, String key, List<String> fallback) {
        try {
            if (plugin.codex() != null) {
                List<String> value = plugin.codex().trList(player, key);
                if (value != null && !value.isEmpty()) return value;
            }
        } catch (Throwable ignored) {}

        return fallback == null ? Collections.emptyList() : fallback;
    }

    public List<String> trList(Player player, String key, List<String> fallback, Map<String, String> placeholders) {
        try {
            if (plugin.codex() != null) {
                List<String> value = plugin.codex().trList(player, key, placeholders);
                if (value != null && !value.isEmpty()) return value;
            }
        } catch (Throwable ignored) {}

        if (fallback == null || fallback.isEmpty()) return Collections.emptyList();

        List<String> out = new ArrayList<>(fallback.size());
        for (String line : fallback) out.add(applyPlaceholders(line, placeholders));
        return out;
    }

    // ======================================
    // --- UTILITIES (Static Helpers) ---
    // ======================================

    public static String safeText(String fromCodex, String fallback) {
        return safeText(null, fromCodex, fallback);
    }

    public static String safeText(String requestedKey, String fromCodex, String fallback) {
        if (fallback == null) fallback = "";

        String out;

        if (fromCodex == null) {
            out = fallback;
        } else {
            String s = fromCodex.trim();
            if (s.isEmpty()) out = fallback;
            else if (s.contains("[Missing") || s.equalsIgnoreCase("null")) out = fallback;
            else if (requestedKey != null && !requestedKey.trim().isEmpty()
                    && s.equalsIgnoreCase(requestedKey.trim())) out = fallback;
            else out = fromCodex;
        }

        return color(out);
    }

    private static String applyPlaceholders(String input, Map<String, String> placeholders) {
        if (input == null || input.isEmpty() || placeholders == null || placeholders.isEmpty()) return input;

        String out = input;
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            String k = e.getKey();
            if (k == null || k.isEmpty()) continue;

            String v = e.getValue() == null ? "" : e.getValue();
            out = out.replace("{" + k + "}", v)
                    .replace("{" + k.toLowerCase(Locale.ROOT) + "}", v)
                    .replace("{" + k.toUpperCase(Locale.ROOT) + "}", v);
        }
        return out;
    }

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
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack createItem(String name, Material mat, List<String> lore) {
        return createItem(mat, name, lore);
    }

    public static ItemStack getFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void playClick(Player p) {
        try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f); }
        catch (Exception ignored) {}
    }

    public static void playSuccess(Player p) {
        try { p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f); }
        catch (Exception ignored) {}
    }

    public static String color(String text) {
        if (text == null) return "";
        String msg = text;

        Matcher matcher = HEX_PATTERN.matcher(msg);
        while (matcher.find()) {
            String token = matcher.group(0);
            String hex = matcher.group(1);
            try {
                msg = msg.replace(token, net.md_5.bungee.api.ChatColor.of("#" + hex).toString());
            } catch (Throwable ignored) {
                msg = msg.replace(token, "");
            }
            matcher = HEX_PATTERN.matcher(msg);
        }

        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public ItemStack createLedgerItem(Player p) {
        String title = tr(p, "ledger_title", "&6📜 Domain Registry");
        List<String> lore = new ArrayList<>();

        ClaimBlockManager cb = plugin.getClaimBlockManager();
        if (cb == null) {
            lore.add(tr(p, "ledger_disabled", "&7Claim Blocks are disabled on this server."));
            lore.add(" ");
            lore.add(tr(p, "ledger_click_disabled", "&8No ledger entries available."));
            return createItem(Material.PAPER, title, lore);
        }

        long total = cb.getTotalBlocks(p.getUniqueId());
        long used = cb.getUsedBlocks(p.getUniqueId());
        long available = cb.getAvailableBlocks(p.getUniqueId());

        lore.add(tr(p, "ledger_available", "&7Available: &a" + available));
        lore.add(tr(p, "ledger_used", "&7Used: &c" + used));
        lore.add(tr(p, "ledger_total", "&7Total Capacity: &e" + total));
        lore.add(" ");
        lore.add(tr(p, "ledger_click", "&eClick to view detailed stats."));

        return createItem(Material.PAPER, title, lore);
    }
}
