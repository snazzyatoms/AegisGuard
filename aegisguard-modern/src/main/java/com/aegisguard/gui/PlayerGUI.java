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

    private void addSectionFrame(Player player, Inventory inv, Material material,
                                 String titleKey, String titleFallback,
                                 String loreKey, String loreFallback,
                                 int... slots) {
        String title = t(player, titleKey, titleFallback);
        List<String> lore = tl(player, loreKey, List.of(loreFallback));
        for (int slot : slots) {
            ItemStack marker = GUIManager.createItem(material, title, lore);
            try { plugin.gui().tagAction(marker, "section_marker"); } catch (Throwable ignored) {}
            inv.setItem(slot, marker);
        }
    }

    /* ---------------------------------------------------------
     * OPEN
     * --------------------------------------------------------- */

    private boolean mod(com.aegisguard.config.Modules.Id id) {
        try {
            return plugin.modules().on(id);
        } catch (Throwable ignored) {
            return id.defaultOn();
        }
    }

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
                45,46,47,48,49,50,51,52,53
        };
        for (int i : borderSlots) inv.setItem(i, filler);

        // --- 2. HEADER ---

        // Info (Slot 4)
        inv.setItem(4, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                t(player, "button_info", "&bℹ Info"),
                tl(player, "info_lore", List.of("&7Read the basics, commands,", "&7and protection tips."))
        ));

        Plot currentPlot = plugin.store().getPlotAt(player.getLocation());
        boolean isAdmin = plugin.isAdmin(player);
        boolean canManage = currentPlot != null && currentPlot.canManage(player, plugin);
        com.aegisguard.data.Zone currentRentedZone = currentPlot == null ? null : currentPlot.getRentedZoneAt(player.getLocation());
        boolean rentingCurrentZone = currentRentedZone != null && currentRentedZone.isRentedBy(player.getUniqueId());

        boolean showRealm = mod(com.aegisguard.config.Modules.Id.REALM_PROFILES);
        boolean showExpand = mod(com.aegisguard.config.Modules.Id.EXPANSIONS);
        boolean showZoning = mod(com.aegisguard.config.Modules.Id.ZONING);
        boolean showGuests = mod(com.aegisguard.config.Modules.Id.GUEST_PASSES);
        boolean showAlliance = mod(com.aegisguard.config.Modules.Id.ALLIANCE_ACCESS);
        boolean showLockdown = mod(com.aegisguard.config.Modules.Id.LOCKDOWN);
        boolean showMarket = mod(com.aegisguard.config.Modules.Id.MARKET)
                || mod(com.aegisguard.config.Modules.Id.MARKET_STALLS)
                || mod(com.aegisguard.config.Modules.Id.RENTALS);
        boolean showMerge = mod(com.aegisguard.config.Modules.Id.CLAIM_MERGE);
        boolean showGift = mod(com.aegisguard.config.Modules.Id.CLAIM_BLOCKS)
                && plugin.getConfig().getBoolean("claim_blocks.gift.enabled", true);
        boolean showRentals = mod(com.aegisguard.config.Modules.Id.RENTALS);
        boolean showExchange = false;
        try {
            showExchange = mod(com.aegisguard.config.Modules.Id.CLAIM_BLOCKS)
                    && plugin.exchange() != null
                    && plugin.cfg() != null
                    && plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false);
        } catch (Throwable ignored) {}
        boolean showLeveling = mod(com.aegisguard.config.Modules.Id.LEVELING);
        boolean showAuction = mod(com.aegisguard.config.Modules.Id.AUCTION);
        boolean showRoutes = mod(com.aegisguard.config.Modules.Id.ROUTES);
        boolean showTravel = mod(com.aegisguard.config.Modules.Id.TRAVEL);
        boolean showArena = mod(com.aegisguard.config.Modules.Id.ARENA) && plugin.arena() != null;

        // Same 54-slot chrome every time. Optional buttons overlay these panes when
        // their module is on; when it is off the slot stays this section glass.
        addSectionFrame(player, inv, Material.CYAN_STAINED_GLASS_PANE,
                "main_section_territory_name", "&bTerritory",
                "main_section_territory_lore", "&7Your claim, profile, and land controls.",
                9, 10, 11, 12, 13, 14, 15, 16, 17);
        addSectionFrame(player, inv, Material.PURPLE_STAINED_GLASS_PANE,
                "main_section_access_name", "&dAccess & Safety",
                "main_section_access_lore", "&7Members, temporary access, and protection.",
                18, 19, 20, 21, 22, 23, 24, 25, 26);
        addSectionFrame(player, inv, Material.ORANGE_STAINED_GLASS_PANE,
                "main_section_economy_name", "&6Economy & Progress",
                "main_section_economy_lore", "&7Market, ClaimBlocks, upgrades, and auctions.",
                27, 28, 29, 30, 31, 32, 33, 34, 35);
        addSectionFrame(player, inv, Material.LIME_STAINED_GLASS_PANE,
                "main_section_explore_name", "&aExplore",
                "main_section_explore_lore", "&7Routes and server travel.",
                36, 37, 38, 39, 40, 41, 42, 43, 44);

        // The dashboard is grouped by purpose: territory, access, economy, then exploration.
        // This keeps every existing action one click away while making the first screen easier to scan.

        // --- 3. TERRITORY ---

        // Realm Profile (Slot 11)
        if (showRealm) {
            inv.setItem(11, GUIManager.createItem(
                    Material.NAME_TAG,
                    t(player, "button_realm_profile", "&3📜 Realm Profile"),
                    tl(player, canManage ? "realm_profile_button_lore" : "realm_profile_button_view_lore",
                            canManage
                                    ? List.of("&7Manage this plot's name, category,", "&7greeting, and noticeboard.")
                                    : List.of("&7View this plot's public identity", "&7and noticeboard."))
            ));
        }

        // Flags (Slot 12)
        Material flagIcon = canManage ? Material.OAK_SIGN : Material.OAK_HANGING_SIGN;
        inv.setItem(12, GUIManager.createItem(
                flagIcon,
                t(player, "button_plot_flags", "&6⚙ Claim Settings"),
                tl(player, canManage ? "plot_flags_lore" : "plot_flags_locked_lore",
                        canManage
                                ? List.of("&7Control who can enter, use,", "&7damage, or automate this claim.")
                                : List.of("&cStand inside a claim you manage", "&cto edit these protections."))
        ));

        // Expansion (Slot 13)
        if (showExpand) {
            inv.setItem(13, GUIManager.createItem(
                    Material.DIAMOND_PICKAXE,
                    t(player, "button_expand", "&b⛏ Expand"),
                    tl(player, "expand_lore", List.of("&7Request more land for this", "&7claim when you outgrow it."))
            ));
        }

        // Zoning (Slot 14)
        if (showZoning) {
            inv.setItem(14, GUIManager.createItem(
                    rentingCurrentZone ? Material.ENDER_PEARL : Material.IRON_BARS,
                    t(player, rentingCurrentZone ? "zone_tenant_button_name" : "zone_gui_title",
                            rentingCurrentZone ? "&bRoom Controls" : "&b🏗 Zoning"),
                    tl(player, rentingCurrentZone ? "zone_tenant_button_lore" : "zone_button_lore",
                            rentingCurrentZone
                                    ? List.of("&7Manage your rented room,", "&7approved guests, and room spawn.")
                                    : List.of("&7Create sub-zones, rentals,", "&7and managed rooms."))
            ));
        }

        // Plot Status (Slot 15) — near territory controls; also hosts merge/transfer entry points
        inv.setItem(15, GUIManager.createItem(
                currentPlot != null ? Material.BOOK : Material.GRAY_DYE,
                t(player, "button_plot_status", "&b📊 Claim Status"),
                tl(player, currentPlot != null ? "plot_status_button_lore" : "plot_status_button_locked_lore",
                        currentPlot != null
                                ? List.of("&7A snapshot of this plot: owner, protections,",
                                "&7blessings, growth, ClaimBlocks, and access.",
                                " ",
                                "&eClick to open.")
                                : List.of("&cStand inside a plot to view status."))
        ));

        // --- 4. ACCESS & SAFETY ---

        // Roles (Slot 20)
        Material roleIcon = canManage ? Material.PLAYER_HEAD : Material.SKELETON_SKULL;
        inv.setItem(20, GUIManager.createItem(
                roleIcon,
                t(player, "button_roles", "&e👥 Roles"),
                tl(player, canManage ? "roles_lore" : "roles_locked_lore",
                        canManage
                                ? List.of("&7Grant or revoke access for", "&7friends, helpers, and visitors.")
                                : List.of("&cStand inside a claim you manage", "&cto edit member access."))
        ));

        // Guest Passes (Slot 21)
        if (showGuests) {
            Material guestPassIcon = canManage ? Material.NAME_TAG : Material.PAPER;
            inv.setItem(21, GUIManager.createItem(
                    guestPassIcon,
                    t(player, "button_guest_passes", "&d🎫 Guest Passes"),
                    tl(player, canManage ? "guest_passes_lore" : "guest_passes_locked_lore",
                            canManage
                                    ? List.of("&7Grant temporary, self-expiring", "&7access without permanent trust.")
                                    : List.of("&cStand inside a claim you manage", "&cto issue Guest Passes."))
            ));
        }

        // Alliance Access (Slot 22) — grayed until this plot joins an alliance
        boolean allianceJoined = currentPlot != null && currentPlot.getAllianceId() != null;
        if (showAlliance) {
            Material allianceIcon = allianceJoined ? Material.SHIELD : Material.GRAY_DYE;
            inv.setItem(22, GUIManager.createItem(
                    allianceIcon,
                    t(player, "button_alliance_access", "&6🛡 Alliance Access"),
                    tl(player, allianceJoined ? "alliance_button_lore" : "alliance_button_grayed_lore",
                            allianceJoined
                                    ? List.of("&7Manage this plot's alliance", "&7access toggles.")
                                    : List.of("&7This plot has not joined an alliance.",
                                    "&7Create or join one, then opt this",
                                    "&7plot in. Risky toggles stay OFF."))
            ));
        }

        // Emergency Lockdown (Slot 23)
        if (showLockdown) {
            boolean lockdownActive = currentPlot != null && currentPlot.isLockdownActive();
            Material lockdownIcon = lockdownActive ? Material.RED_STAINED_GLASS_PANE
                    : (canManage ? Material.IRON_BARS : Material.GRAY_STAINED_GLASS_PANE);
            inv.setItem(23, GUIManager.createItem(
                    lockdownIcon,
                    t(player, lockdownActive ? "button_lockdown_active" : "button_lockdown", "&cEmergency Lockdown"),
                    tl(player, canManage
                                    ? (lockdownActive ? "lockdown_button_active_lore" : "lockdown_button_lore")
                                    : "lockdown_button_locked_lore",
                            lockdownActive
                                    ? List.of("&cThis plot is locked down.", "&7Click to view status or unlock.")
                                    : canManage
                                    ? List.of("&7A fast, reversible safety switch", "&7for griefing, disputes, or maintenance.")
                                    : List.of("&cStand inside a claim you manage", "&cto use Emergency Lockdown."))
            ));
        }

        // --- 5. ECONOMY & PROGRESS ---

        if (showMarket) {
            boolean localMarketAvailable = currentPlot != null
                    && plugin.marketBridges() != null
                    && plugin.marketBridges().preferLocalWhenInPlot()
                    && plugin.marketBridges().plotQualifiesForLocalMarket(currentPlot, player);
            inv.setItem(29, GUIManager.createItem(
                    localMarketAvailable ? Material.CHEST : Material.GOLD_INGOT,
                    t(player, localMarketAvailable ? "button_market_local" : "button_market",
                            localMarketAvailable ? "&6Local Market" : "&6💰 Market"),
                    tl(player, localMarketAvailable ? "market_local_lore" : "market_lore",
                            localMarketAvailable
                                    ? List.of("&7Open this plot's rentals, shop", "&7tools, and market options.")
                                    : List.of("&7Browse listed claims and", "&7market activity."))
            ));
        }

        if (showMerge) {
            inv.setItem(28, GUIManager.createItem(
                    Material.SLIME_BALL,
                    t(player, "button_claim_merge", "&aMerge Claims"),
                    tl(player, "claim_merge_button_lore",
                            List.of("&7Combine adjacent owned claims", "&7into one larger plot."))
            ));
        }

        if (showGift) {
            inv.setItem(34, GUIManager.createItem(
                    Material.GOLD_INGOT,
                    t(player, "button_giftblocks", "&aGift ClaimBlocks"),
                    tl(player, "giftblocks_button_lore",
                            List.of("&7Gift available ClaimBlocks", "&7to a nearby player."))
            ));
        }

        if (showRentals) {
            inv.setItem(33, GUIManager.createItem(
                    Material.GOLDEN_HOE,
                    t(player, "button_my_rentals", "&6My Rentals"),
                    tl(player, "my_rentals_button_lore", List.of(
                            "&7View full-plot and zone rentals,",
                            "&7renew, extend, or cancel contracts."))
            ));
        }

        if (showExchange) {
            inv.setItem(30, GUIManager.createItem(
                    Material.EMERALD,
                    t(player, "button_claimblocks_exchange", "&a💱 ClaimBlocks Exchange"),
                    tl(player, "claimblocks_exchange_lore",
                            List.of("&7Buy or sell Claim Blocks with", "&7your server economy.", " ", "&eClick to open."))
            ));
        }

        if (showLeveling) {
            inv.setItem(31, GUIManager.createItem(
                    Material.EXPERIENCE_BOTTLE,
                    t(player, "level_gui_title", "&a📈 Leveling"),
                    tl(player, "level_button_lore", List.of("&7Upgrade your plot to unlock", "&7perks and stronger bonuses."))
            ));
        }

        if (showAuction) {
            inv.setItem(32, GUIManager.createItem(
                    Material.LAVA_BUCKET,
                    t(player, "button_auction", "&c🔥 Auctions"),
                    tl(player, "auction_lore", List.of("&7Bid on auctioned claims", "&7and time-limited listings."))
            ));
        }

        // --- 6. EXPLORE ---

        if (showRoutes) {
            inv.setItem(39, GUIManager.createItem(
                    Material.FILLED_MAP,
                    t(player, "button_routes", "&a🗺 Routes"),
                    tl(player, "routes_button_lore", List.of(
                            "&7Browse staff-made exploration routes",
                            "&7and see your next checkpoint."))
            ));
        }

        if (showTravel) {
            inv.setItem(40, GUIManager.createItem(
                    Material.COMPASS,
                    t(player, "visit_gui_title", "&a🧭 Travel"),
                    tl(player, "visit_button_lore", List.of("&7Visit plots, warps, and", "&7trusted destinations."))
            ));
        }

        if (showArena) {
            inv.setItem(41, GUIManager.createItem(
                    Material.DIAMOND_SWORD,
                    t(player, "button_arena", "&c⚔ Arenas"),
                    tl(player, "arena_button_lore", List.of(
                            "&7Join cooperative dungeon runs,",
                            "&7parties, and Lava Dungeon challenges."))
            ));
        }

        // --- 7. FOOTER / NAVIGATION ---

        // Settings (Slot 47)
        inv.setItem(47, GUIManager.createItem(
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
        if (action != null && (action.equals("reload_all") || action.equals("refresh_lang")
                || action.equals("reload") || action.equals("section_marker"))) {
            return; // GUIListener will intercept reload triggers globally
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        boolean isAdmin = plugin.isAdmin(player);
        boolean canManage = plot != null && plot.canManage(player, plugin);

        switch (slot) {
            case 4 -> plugin.gui().info().open(player);

            case 39 -> {
                if (!mod(com.aegisguard.config.Modules.Id.ROUTES)) return;
                plugin.gui().routes().open(player);
            }

            case 40 -> {
                if (!mod(com.aegisguard.config.Modules.Id.TRAVEL)) return;
                plugin.gui().visit().open(player, 0, VisitGUI.VisitMode.WARPS);
            }

            case 41 -> {
                if (!(mod(com.aegisguard.config.Modules.Id.ARENA) && plugin.gui().arena() != null)) return;
                plugin.gui().arena().open(player);
            }

            case 29 -> {
                if (!(mod(com.aegisguard.config.Modules.Id.MARKET)
                        || mod(com.aegisguard.config.Modules.Id.MARKET_STALLS)
                        || mod(com.aegisguard.config.Modules.Id.RENTALS))) {
                    return;
                }
                boolean preferLocal = plot != null
                        && plugin.marketBridges() != null
                        && plugin.marketBridges().preferLocalWhenInPlot()
                        && plugin.marketBridges().plotQualifiesForLocalMarket(plot, player);
                if (preferLocal) plugin.gui().localMarket().open(player, plot);
                else plugin.gui().market().open(player, 0);
            }

            case 33 -> {
                if (mod(com.aegisguard.config.Modules.Id.RENTALS)) plugin.gui().myRentals().open(player);
            }

            case 12 -> {
                if (plot != null && canManage) plugin.gui().flags().open(player, plot);
                else {
                    send(player, plot == null ? "no_plot_here" : "not_plot_owner",
                            plot == null
                                    ? "&cYou must be standing inside a plot to do that."
                                    : "&cYou cannot manage this plot.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 20 -> {
                if (plot != null && canManage) plugin.gui().roles().openRolesMenu(player, plot);
                else {
                    send(player, plot == null ? "no_plot_here" : "not_plot_owner",
                            plot == null
                                    ? "&cYou must be standing inside a plot to do that."
                                    : "&cYou cannot manage this plot.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 23 -> {
                if (!mod(com.aegisguard.config.Modules.Id.LOCKDOWN)) return;
                if (plot != null && canManage) plugin.gui().lockdownGui().open(player);
                else {
                    send(player, plot == null ? "no_plot_here" : "not_plot_owner",
                            plot == null
                                    ? "&cYou must be standing inside a plot to do that."
                                    : "&cYou cannot manage this plot.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 21 -> {
                if (!mod(com.aegisguard.config.Modules.Id.GUEST_PASSES)) return;
                if (plot != null && canManage) plugin.gui().guestPasses().open(player);
                else {
                    send(player, plot == null ? "no_plot_here" : "not_plot_owner",
                            plot == null
                                    ? "&cYou must be standing inside a plot to do that."
                                    : "&cYou cannot manage this plot.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 22 -> {
                if (!mod(com.aegisguard.config.Modules.Id.ALLIANCE_ACCESS)) return;
                plugin.gui().allianceAccess().openMenu(player, plot);
            }

            case 11 -> {
                if (!mod(com.aegisguard.config.Modules.Id.REALM_PROFILES)) return;
                if (plot != null) plugin.gui().realmProfile().open(player);
                else {
                    send(player, "no_plot_here", "&cYou must be standing inside a plot to do that.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 13 -> {
                if (!mod(com.aegisguard.config.Modules.Id.EXPANSIONS)) return;
                plugin.gui().expansionRequest().open(player);
            }

            case 15 -> {
                if (plot != null) plugin.gui().plotStatus().open(player, plot);
                else {
                    send(player, "no_plot_here", "&cYou must be standing inside a plot to do that.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            case 28 -> {
                if (!mod(com.aegisguard.config.Modules.Id.CLAIM_MERGE)) return;
                plugin.gui().claimMerge().open(player);
            }

            case 34 -> {
                if (!(mod(com.aegisguard.config.Modules.Id.CLAIM_BLOCKS)
                        && plugin.getConfig().getBoolean("claim_blocks.gift.enabled", true))) return;
                plugin.gui().giftBlocks().open(player);
            }

            // Advanced Features
            case 31 -> {
                boolean levelingEnabled = mod(com.aegisguard.config.Modules.Id.LEVELING);
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

            case 14 -> {
                boolean zoningEnabled = mod(com.aegisguard.config.Modules.Id.ZONING);
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

            case 30 -> {
                boolean exchangeOk = plugin.exchange() != null;
                boolean exchangeEnabled = false;
                try {
                    exchangeEnabled = plugin.cfg() != null && plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false);
                } catch (Throwable ignored) {}

                if (exchangeOk && exchangeEnabled && mod(com.aegisguard.config.Modules.Id.CLAIM_BLOCKS)) {
                    plugin.gui().openClaimBlockExchange(player);
                } else {
                    send(player, "claimblocks_exchange_unavailable", "&cClaimBlocks Exchange is unavailable right now.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                }
            }

            // Economy
            case 32 -> {
                if (mod(com.aegisguard.config.Modules.Id.AUCTION)) plugin.gui().auction().open(player, 0);
            }

            // System
            case 47 -> plugin.gui().settings().open(player);

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
