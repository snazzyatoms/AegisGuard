package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * PlayerGUI
 * - Main dashboard for AegisGuard.
 *
 * 1.2.6 QoL Pass:
 * - Hard ignore bottom-inventory clicks (prevents edge-case movement / hotbar swaps).
 * - Ignore filler clicks cleanly.
 * - Add a PDC-tagged Reload/Refresh entry point for admins (consistent with GUIListener strict detection).
 * - Safer “service enabled” checks (cfg can be null during reload windows).
 * - Keeps 1.2.5 layout/structure, only improves reliability + UX.
 */
public class PlayerGUI {

    private final AegisGuard plugin;

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
        String prefix = "&8[&bAegisGuard&8]&r ";
        try {
            if (plugin.msg() != null) {
                String px = plugin.msg().get(p, "prefix");
                if (px != null && !px.isBlank() && !px.equalsIgnoreCase("prefix")) {
                    prefix = px;
                }
            }
        } catch (Throwable ignored) {}

        String msg = null;
        try {
            if (plugin.msg() != null) msg = plugin.msg().get(p, key);
        } catch (Throwable ignored) {}

        if (msg == null || msg.isBlank() || msg.equalsIgnoreCase(key) || msg.contains("[Missing")) {
            msg = fallback;
        }

        if (msg == null || msg.trim().isEmpty()) return;
        p.sendMessage(GUIManager.color(prefix + msg));
    }

    private boolean cfgBool(java.util.function.BooleanSupplier s, boolean def) {
        try { return s.getAsBoolean(); } catch (Throwable ignored) { return def; }
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
                46,47,48,50,51
        };
        for (int i : borderSlots) inv.setItem(i, filler);

        // --- 2. HEADER ---

        // Info (Slot 4)
        inv.setItem(4, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                t(player, "button_info", "&bℹ Info"),
                tl(player, "info_lore", List.of("&7Read the basics, commands,", "&7and protection tips."))
        ));

        // Plot Status Codex (Slot 11)
        inv.setItem(11, GUIManager.createItem(
                Material.ENCHANTED_BOOK,
                t(player, "plot_status_button_title", "&d📜 Plot Status"),
                tl(player, "plot_status_button_lore", List.of("&7Review ownership, protections,", "&7bonuses, and plot health."))
        ));

        // Travel (Slot 13)
        boolean travelEnabled = plugin.cfg() != null && cfgBool(() -> plugin.cfg().isTravelSystemEnabled(), false);
        if (travelEnabled) {
            inv.setItem(13, GUIManager.createItem(
                    Material.COMPASS,
                    t(player, "visit_gui_title", "&a🧭 Travel"),
                    tl(player, "visit_button_lore", List.of("&7Visit plots, warps, and", "&7trusted destinations."))
            ));
        } else {
            // Optional: keep slot empty when disabled (matches 1.2.5 behavior)
        }

        // --- 3. CORE MANAGEMENT ---

        Plot currentPlot = plugin.store().getPlotAt(player.getLocation());
        boolean isAdmin = plugin.isAdmin(player);
        boolean canManage = currentPlot != null && currentPlot.canManage(player, plugin);
        com.aegisguard.data.Zone currentRentedZone = currentPlot == null ? null : currentPlot.getRentedZoneAt(player.getLocation());
        boolean rentingCurrentZone = currentRentedZone != null && currentRentedZone.isRentedBy(player.getUniqueId());

        // Flags (Slot 22)
        Material flagIcon = canManage ? Material.OAK_SIGN : Material.OAK_HANGING_SIGN;
        inv.setItem(20, GUIManager.createItem(
                flagIcon,
                t(player, "button_plot_flags", "&6⚙ Plot Flags"),
                tl(player, canManage ? "plot_flags_lore" : "plot_flags_locked_lore",
                        canManage
                                ? List.of("&7Control who can enter, use,", "&7damage, or automate this claim.")
                                : List.of("&cStand inside a claim you manage", "&cto edit these protections."))
        ));

        // Roles (Slot 24)
        Material roleIcon = canManage ? Material.PLAYER_HEAD : Material.SKELETON_SKULL;
        inv.setItem(22, GUIManager.createItem(
                roleIcon,
                t(player, "button_roles", "&e👥 Roles"),
                tl(player, canManage ? "roles_lore" : "roles_locked_lore",
                        canManage
                                ? List.of("&7Grant or revoke access for", "&7friends, helpers, and visitors.")
                                : List.of("&cStand inside a claim you manage", "&cto edit member access."))
        ));

        // Expansion (Slot 24)
        inv.setItem(24, GUIManager.createItem(
                Material.DIAMOND_PICKAXE,
                t(player, "button_expand", "&b⛏ Expand"),
                tl(player, "expand_lore", List.of("&7Request more land for this", "&7claim when you outgrow it."))
        ));

        // --- 4. ADVANCED FEATURES ---

        // Leveling (Slot 29)
        boolean levelingEnabled = plugin.cfg() != null && cfgBool(() -> plugin.cfg().isLevelingEnabled(), false);
        if (levelingEnabled) {
            inv.setItem(29, GUIManager.createItem(
                    Material.EXPERIENCE_BOTTLE,
                    t(player, "level_gui_title", "&a📈 Leveling"),
                    tl(player, "level_button_lore", List.of("&7Upgrade your plot to unlock", "&7perks and stronger bonuses."))
            ));
        }

        // Zoning (Slot 31)
        boolean zoningEnabled = plugin.cfg() != null && cfgBool(() -> plugin.cfg().isZoningEnabled(), false);
        if (zoningEnabled) {
            inv.setItem(31, GUIManager.createItem(
                    rentingCurrentZone ? Material.ENDER_PEARL : Material.IRON_BARS,
                    t(player, rentingCurrentZone ? "zone_tenant_button_name" : "zone_gui_title",
                            rentingCurrentZone ? "&bRoom Controls" : "&b🏗 Zoning"),
                    tl(player, rentingCurrentZone ? "zone_tenant_button_lore" : "zone_button_lore",
                            rentingCurrentZone
                                    ? List.of("&7Manage your rented room,", "&7approved guests, and room spawn.")
                                    : List.of("&7Create sub-zones, rentals,", "&7and managed rooms."))
            ));
        }

        // Biomes (Slot 33)
        boolean biomesEnabled = plugin.cfg() != null && cfgBool(() -> plugin.cfg().isBiomesEnabled(), false);
        if (biomesEnabled) {
            inv.setItem(33, GUIManager.createItem(
                    Material.SPORE_BLOSSOM,
                    t(player, "biome_gui_title", "&d🌿 Biomes"),
                    tl(player, "biome_button_lore", List.of("&7Change the biome style", "&7inside your plot."))
            ));
        }

        // --- 5. ECONOMY ---

        // Market (Slot 38)
        boolean localMarketAvailable = currentPlot != null
                && plugin.marketBridges() != null
                && plugin.marketBridges().preferLocalWhenInPlot()
                && plugin.marketBridges().plotQualifiesForLocalMarket(currentPlot, player);
        inv.setItem(38, GUIManager.createItem(
                localMarketAvailable ? Material.CHEST : Material.GOLD_INGOT,
                t(player, localMarketAvailable ? "button_market_local" : "button_market",
                        localMarketAvailable ? "&6Local Market" : "&6💰 Market"),
                tl(player, localMarketAvailable ? "market_local_lore" : "market_lore",
                        localMarketAvailable
                                ? List.of("&7Open this plot's rentals, shop", "&7tools, and market options.")
                                : List.of("&7Browse listed claims and", "&7market activity."))
        ));

        // ✅ ClaimBlocks Exchange (Slot 39)
        boolean exchangeService = plugin.exchange() != null;
        boolean exchangeEnabled = false;
        try {
            exchangeEnabled = plugin.cfg() != null && plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false);
        } catch (Throwable ignored) {}

        if (exchangeService && exchangeEnabled) {
            inv.setItem(39, GUIManager.createItem(
                    Material.EMERALD,
                    t(player, "button_claimblocks_exchange", "&a💱 ClaimBlocks Exchange"),
                    tl(player, "claimblocks_exchange_lore",
                            List.of("&7Buy or sell Claim Blocks with", "&7your server economy.", " ", "&eClick to open."))
            ));
        } else {
            inv.setItem(39, GUIManager.createItem(
                    Material.GRAY_DYE,
                    t(player, "button_claimblocks_exchange_disabled", "&7💱 ClaimBlocks Exchange"),
                    tl(player, "claimblocks_exchange_disabled_lore",
                            List.of("&7This feature is currently unavailable.", "&8Enable: claim_blocks.exchange.enabled: true"))
            ));
        }

        // Auctions (Slot 42)
        boolean upkeepEnabled = plugin.cfg() != null && cfgBool(() -> plugin.cfg().isUpkeepEnabled(), false);
        if (upkeepEnabled) {
            inv.setItem(42, GUIManager.createItem(
                    Material.LAVA_BUCKET,
                    t(player, "button_auction", "&c🔥 Auctions"),
                    tl(player, "auction_lore", List.of("&7Bid on auctioned claims", "&7and time-limited listings."))
            ));
        }

        // --- 6. FOOTER / NAVIGATION ---

        // Settings (Slot 45)
        inv.setItem(45, GUIManager.createItem(
                Material.COMPARATOR,
                t(player, "button_player_settings", "&e⚙ Settings"),
                tl(player, "player_settings_lore", List.of("&7Adjust language, sounds,", "&7and notification settings."))
        ));

        // Admin (Slot 49)
        if (isAdmin) {
            ItemStack adminItem = GUIManager.createItem(
                    Material.REDSTONE_BLOCK,
                    t(player, "admin_menu_title", "&c🛠 Admin Menu"),
                    tl(player, "admin_menu_lore", List.of("&7Operator Access Only"))
            );
            inv.setItem(49, adminItem);

            // Small admin reload hub.
            ItemStack reloadHub = GUIManager.createItem(
                    Material.REDSTONE,
                    t(player, "button_reload_all_settings",
                            t(player, "button_admin_reload", "&eReload All Settings")),
                    tl(player, "reload_all_settings_lore",
                            tl(player, "admin_reload_lore", List.of("&7Reload configs + language packs.")))
            );
            // PDC tag so GUIListener detects it reliably
            try { plugin.gui().tagAction(reloadHub, "reload_all"); } catch (Throwable ignored) {}
            inv.setItem(53, reloadHub);
        }

        // Exit (far-right footer)
        inv.setItem(52, GUIManager.createItem(
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

        // ✅ 1.2.6 QoL: ignore clicks from bottom inventory (hard safety)
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= e.getInventory().getSize()) return;

        int slot = raw;
        if (slot < 0 || slot >= 54) return;

        // ✅ Ignore filler clicks quietly
        if (GUIManager.isFiller(e.getCurrentItem())) return;

        // Reload hub (admins only) is handled by GUIListener (PDC tag),
        // but we can safely ignore it here too.
        String action = null;
        try { action = plugin.gui().getAction(e.getCurrentItem()); } catch (Throwable ignored) {}
        if (action != null && (action.equals("reload_all") || action.equals("refresh_lang") || action.equals("reload"))) {
            return; // GUIListener will intercept reload triggers globally
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        boolean isAdmin = plugin.isAdmin(player);
        boolean canManage = plot != null && plot.canManage(player, plugin);

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
                boolean travelEnabled = plugin.cfg() != null && cfgBool(() -> plugin.cfg().isTravelSystemEnabled(), false);
                if (travelEnabled) {
                    plugin.gui().visit().open(player, 0, false);
                } else {
                    send(player, "travel_system_disabled", "&cTravel is disabled.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 20 -> {
                if (plot != null && canManage) plugin.gui().flags().open(player, plot);
                else {
                    send(player, plot == null ? "no_plot_here" : "not_plot_owner",
                            plot == null
                                    ? "&cYou must be standing inside a plot to do that."
                                    : "&cYou cannot manage this plot.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 22 -> {
                if (plot != null && canManage) plugin.gui().roles().openRolesMenu(player, plot);
                else {
                    send(player, plot == null ? "no_plot_here" : "not_plot_owner",
                            plot == null
                                    ? "&cYou must be standing inside a plot to do that."
                                    : "&cYou cannot manage this plot.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 24 -> plugin.gui().expansionRequest().open(player);

            // Advanced Features
            case 29 -> {
                boolean levelingEnabled = plugin.cfg() != null && cfgBool(() -> plugin.cfg().isLevelingEnabled(), false);
                if (levelingEnabled) {
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
                boolean zoningEnabled = plugin.cfg() != null && cfgBool(() -> plugin.cfg().isZoningEnabled(), false);
                if (zoningEnabled) {
                    com.aegisguard.data.Zone rentedZone = plot == null ? null : plot.getRentedZoneAt(player.getLocation());
                    if (plot != null && rentedZone != null && rentedZone.isRentedBy(player.getUniqueId())) {
                        plugin.gui().zoneTenant().open(player, plot, rentedZone);
                    }
                    else if (plot != null && canManage) plugin.gui().zoning().open(player, plot);
                    else if (plot != null && plot.hasBrowsableZonesFor(player)) plugin.gui().zoneBrowse().open(player, plot);
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
                boolean biomesEnabled = plugin.cfg() != null && cfgBool(() -> plugin.cfg().isBiomesEnabled(), false);
                if (biomesEnabled) {
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
            case 38 -> {
                boolean preferLocal = plot != null
                        && plugin.marketBridges() != null
                        && plugin.marketBridges().preferLocalWhenInPlot()
                        && plugin.marketBridges().plotQualifiesForLocalMarket(plot, player);
                if (preferLocal) plugin.gui().localMarket().open(player, plot);
                else plugin.gui().market().open(player, 0);
            }

            // ✅ Exchange
            case 39 -> {
                boolean exchangeOk = plugin.exchange() != null;
                boolean exchangeEnabled = false;
                try {
                    exchangeEnabled = plugin.cfg() != null && plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false);
                } catch (Throwable ignored) {}

                if (exchangeOk && exchangeEnabled) {
                    plugin.gui().openClaimBlockExchange(player);
                } else {
                    send(player, "claimblocks_exchange_unavailable", "&cClaimBlocks Exchange is unavailable right now.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 42 -> {
                boolean upkeepEnabled = plugin.cfg() != null && cfgBool(() -> plugin.cfg().isUpkeepEnabled(), false);
                if (upkeepEnabled) plugin.gui().auction().open(player, 0);
            }

            // System
            case 45 -> plugin.gui().settings().open(player);

            case 49 -> {
                if (isAdmin) plugin.gui().admin().open(player);
            }

            case 52 -> player.closeInventory();

            case 53 -> {
                if (isAdmin) {
                    // Reload hub is handled centrally by GUIListener via PDC tag.
                    return;
                }
            }
        }

        if (slot != 49 && slot != 52 && slot != 53) {
            GUIManager.playClick(player);
        }
    }
}
