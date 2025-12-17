package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PlayerGUI
 * - The main dashboard for AegisGuard.
 * - Fully localized via Codex (NO messages.yml usage).
 */
public class PlayerGUI {

    private final AegisGuard plugin;

    // Hex pattern (&#RRGGBB) for chat messages
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public PlayerGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlayerMenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    /* ---------------------------------------------------------
     * Helpers (Codex-safe with fallbacks)
     * --------------------------------------------------------- */

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private void send(Player p, String key, String fallback) {
        // Optional prefix (put this key in core.yml if you want)
        String prefix = t(p, "prefix", "&8[&bAegisGuard&8]&r ");
        String msg = t(p, key, fallback);
        if (msg == null || msg.trim().isEmpty()) return;
        p.sendMessage(color(prefix + msg));
    }

    private String color(String text) {
        if (text == null) return "";
        String msg = text;

        Matcher matcher = HEX_PATTERN.matcher(msg);
        while (matcher.find()) {
            String token = matcher.group(0); // "&#A1B2C3"
            String hex = matcher.group(1);   // "A1B2C3"
            msg = msg.replace(token, net.md_5.bungee.api.ChatColor.of("#" + hex).toString());
            matcher = HEX_PATTERN.matcher(msg);
        }

        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /* ---------------------------------------------------------
     * OPEN
     * --------------------------------------------------------- */

    public void open(Player player) {
        String title = plugin.gui().title(player, "menu_title", "&b⚔ AegisGuard Menu");
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 54, title);

        // --- 1. Glass Borders ---
        ItemStack filler = GUIManager.getFiller();
        int[] borderSlots = {
                0,1,2,3,4,5,6,7,8,
                9,17,
                18,26,
                27,35,
                36,44,
                45,46,47,51,52,53
        };
        for (int i : borderSlots) inv.setItem(i, filler);

        // --- 2. HEADER ---

        // Info (Slot 4)
        inv.setItem(4, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                t(player, "button_info", "&bℹ Info"),
                tl(player, "info_lore", List.of("&7View plugin info and help."))
        ));

        // Plot Status Codex (Slot 11)
        inv.setItem(11, GUIManager.createItem(
                Material.ENCHANTED_BOOK,
                t(player, "plot_status_button_title", "&d📜 Plot Status"),
                tl(player, "plot_status_button_lore", List.of("&7View plot status and details."))
        ));

        // Travel (Slot 13)
        if (plugin.cfg().isTravelSystemEnabled()) {
            inv.setItem(13, GUIManager.createItem(
                    Material.COMPASS,
                    t(player, "visit_gui_title", "&a🧭 Travel"),
                    tl(player, "visit_button_lore", List.of("&7Visit plots and travel quickly."))
            ));
        }

        // --- 3. CORE MANAGEMENT ---

        Plot currentPlot = plugin.store().getPlotAt(player.getLocation());
        boolean isOwner = currentPlot != null
                && currentPlot.getOwner() != null
                && currentPlot.getOwner().equals(player.getUniqueId());

        boolean isAdmin = plugin.isAdmin(player);
        boolean canManage = isOwner || isAdmin;

        // Claim Land (Slot 20)
        boolean hasSelection = plugin.selection().hasSelection(player);
        if (hasSelection) {
            inv.setItem(20, GUIManager.createItem(
                    Material.LIGHTNING_ROD,
                    t(player, "button_claim_land", "&a🛡 Claim Land"),
                    tl(player, "claim_land_ready_lore", List.of("&7Ready to claim your selection.", " ", "&eClick to confirm"))
            ));
        } else {
            inv.setItem(20, GUIManager.createItem(
                    Material.BARRIER,
                    "&c" + t(player, "button_claim_land", "🛡 Claim Land"),
                    tl(player, "claim_land_lore", List.of("&7Select two points with your wand first."))
            ));
        }

        // Flags (Slot 22)
        Material flagIcon = canManage ? Material.OAK_SIGN : Material.OAK_HANGING_SIGN;
        inv.setItem(22, GUIManager.createItem(
                flagIcon,
                t(player, "button_plot_flags", "&6⚙ Plot Flags"),
                tl(player, canManage ? "plot_flags_lore" : "plot_flags_locked_lore",
                        canManage
                                ? List.of("&7Manage protection flags for this plot.")
                                : List.of("&cYou cannot manage flags here."))
        ));

        // Roles (Slot 24)
        Material roleIcon = canManage ? Material.PLAYER_HEAD : Material.SKELETON_SKULL;
        inv.setItem(24, GUIManager.createItem(
                roleIcon,
                t(player, "button_roles", "&e👥 Roles"),
                tl(player, canManage ? "roles_lore" : "roles_locked_lore",
                        canManage
                                ? List.of("&7Manage member roles and permissions.")
                                : List.of("&cYou cannot manage roles here."))
        ));

        // --- 4. ADVANCED FEATURES ---

        // Leveling (Slot 29)
        if (plugin.cfg().isLevelingEnabled()) {
            inv.setItem(29, GUIManager.createItem(
                    Material.EXPERIENCE_BOTTLE,
                    t(player, "level_gui_title", "&a📈 Leveling"),
                    tl(player, "level_button_lore", List.of("&7Level up your plot for perks."))
            ));
        }

        // Zoning (Slot 31)
        if (plugin.cfg().isZoningEnabled()) {
            inv.setItem(31, GUIManager.createItem(
                    Material.IRON_BARS,
                    t(player, "zone_gui_title", "&b🏗 Zoning"),
                    tl(player, "zone_button_lore", List.of("&7Create sub-zones and rentable rooms."))
            ));
        }

        // Biomes (Slot 33)
        if (plugin.cfg().isBiomesEnabled()) {
            inv.setItem(33, GUIManager.createItem(
                    Material.SPORE_BLOSSOM,
                    t(player, "biome_gui_title", "&d🌿 Biomes"),
                    tl(player, "biome_button_lore", List.of("&7Change your plot biome."))
            ));
        }

        // --- 5. ECONOMY & EXPANSION ---

        // Market (Slot 38)
        inv.setItem(38, GUIManager.createItem(
                Material.GOLD_INGOT,
                t(player, "button_market", "&6💰 Market"),
                tl(player, "market_lore", List.of("&7Buy and sell plot goods."))
        ));

        // Expansion (Slot 40)
        inv.setItem(40, GUIManager.createItem(
                Material.DIAMOND_PICKAXE,
                t(player, "button_expand", "&b⛏ Expand"),
                tl(player, "expand_lore", List.of("&7Request land expansions."))
        ));

        // Auctions (Slot 42)
        if (plugin.cfg().isUpkeepEnabled()) {
            inv.setItem(42, GUIManager.createItem(
                    Material.LAVA_BUCKET,
                    t(player, "button_auction", "&c🔥 Auctions"),
                    tl(player, "auction_lore", List.of("&7Bid on plots and listings."))
            ));
        }

        // --- 6. FOOTER ---

        // Settings (Slot 48)
        inv.setItem(48, GUIManager.createItem(
                Material.COMPARATOR,
                t(player, "button_player_settings", "&e⚙ Settings"),
                tl(player, "player_settings_lore", List.of("&7Personal preferences and language."))
        ));

        // Admin (Slot 49)
        if (plugin.isAdmin(player)) {
            inv.setItem(49, GUIManager.createItem(
                    Material.REDSTONE_BLOCK,
                    t(player, "admin_menu_title", "&c🛠 Admin Menu"),
                    tl(player, "admin_menu_lore", List.of("&7Operator Access Only"))
            ));
        }

        // Exit (Slot 50)
        inv.setItem(50, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&c✖ Exit"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    /* ---------------------------------------------------------
     * CLICK HANDLER
     * --------------------------------------------------------- */

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) return; // ignore bottom inventory

        Plot plot = plugin.store().getPlotAt(player.getLocation());

        boolean isOwner = plot != null
                && plot.getOwner() != null
                && plot.getOwner().equals(player.getUniqueId());

        boolean isAdmin = plugin.isAdmin(player);
        boolean canManage = isOwner || isAdmin;

        switch (slot) {
            case 4 -> plugin.gui().info().open(player);

            case 11 -> {
                if (plot != null) {
                    plugin.gui().plotStatus().open(player, plot);
                } else {
                    send(player, "no_plot_here", "&cYou must be standing inside a plot to do that.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 13 -> {
                if (plugin.cfg().isTravelSystemEnabled()) {
                    plugin.gui().visit().open(player, 0, false);
                }
            }

            case 20 -> { // Claim
                if (plugin.selection().hasSelection(player)) {
                    player.closeInventory();
                    plugin.selection().confirmClaim(player);
                } else {
                    send(player, "no_selection", "&cYou need to select two points first.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 22 -> { // Flags
                if (plot != null && canManage) plugin.gui().flags().open(player, plot);
                else {
                    send(player, plot == null ? "no_plot_here" : "not_plot_owner",
                            plot == null
                                    ? "&cYou must be standing inside a plot to do that."
                                    : "&cYou cannot manage this plot.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 24 -> { // Roles
                if (plot != null && canManage) plugin.gui().roles().open(player);
                else {
                    send(player, plot == null ? "no_plot_here" : "not_plot_owner",
                            plot == null
                                    ? "&cYou must be standing inside a plot to do that."
                                    : "&cYou cannot manage this plot.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            // Advanced Features
            case 29 -> {
                if (plugin.cfg().isLevelingEnabled()) {
                    if (plot != null && canManage) plugin.gui().leveling().open(player, plot);
                    else {
                        send(player, plot == null ? "no_plot_here" : "not_plot_owner",
                                plot == null
                                        ? "&cYou must be standing inside a plot to do that."
                                        : "&cYou cannot manage this plot.");
                        if (plugin.effects() != null) plugin.effects().playError(player);
                    }
                }
            }

            case 31 -> {
                if (plugin.cfg().isZoningEnabled()) {
                    if (plot != null && canManage) plugin.gui().zoning().open(player, plot);
                    else {
                        send(player, plot == null ? "no_plot_here" : "not_plot_owner",
                                plot == null
                                        ? "&cYou must be standing inside a plot to do that."
                                        : "&cYou cannot manage this plot.");
                        if (plugin.effects() != null) plugin.effects().playError(player);
                    }
                }
            }

            case 33 -> {
                if (plugin.cfg().isBiomesEnabled()) {
                    if (plot != null && canManage) plugin.gui().biomes().open(player, plot);
                    else {
                        send(player, plot == null ? "no_plot_here" : "not_plot_owner",
                                plot == null
                                        ? "&cYou must be standing inside a plot to do that."
                                        : "&cYou cannot manage this plot.");
                        if (plugin.effects() != null) plugin.effects().playError(player);
                    }
                }
            }

            // Economy
            case 38 -> plugin.gui().market().open(player, 0);
            case 40 -> plugin.gui().expansionRequest().open(player);
            case 42 -> {
                if (plugin.cfg().isUpkeepEnabled()) plugin.gui().auction().open(player, 0);
            }

            // System
            case 48 -> plugin.gui().settings().open(player);
            case 49 -> {
                if (isAdmin) plugin.gui().admin().open(player);
            }
            case 50 -> player.closeInventory();
        }

        if (slot != 20 && slot != 50) {
            GUIManager.playClick(player);
        }
    }
}
