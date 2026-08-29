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
import java.util.Map;

/**
 * Player dashboard: a sparse hub with daily shortcuts plus category pages.
 */
public class PlayerGUI {

    public enum Page {
        HUB, TERRITORY, ACCESS, ECONOMY, EXPLORE
    }

    private static final int SLOT_INFO = 4;
    private static final int SLOT_SHORTCUT_FLAGS = 20;
    private static final int SLOT_SHORTCUT_STATUS = 21;
    private static final int SLOT_SHORTCUT_TRAVEL = 23;
    private static final int SLOT_SHORTCUT_MARKET = 24;
    private static final int SLOT_DOOR_TERRITORY = 29;
    private static final int SLOT_DOOR_ACCESS = 30;
    private static final int SLOT_DOOR_ECONOMY = 32;
    private static final int SLOT_DOOR_EXPLORE = 33;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_SETTINGS = 47;
    private static final int SLOT_ADMIN = 49;
    private static final int SLOT_EXIT = 52;
    private static final int SLOT_RELOAD = 53;

    private static final int SLOT_CAT_A = 20;
    private static final int SLOT_CAT_B = 21;
    private static final int SLOT_CAT_C = 22;
    private static final int SLOT_CAT_D = 23;
    private static final int SLOT_CAT_E = 24;

    private final AegisGuard plugin;

    public PlayerGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlayerMenuHolder implements InventoryHolder {
        private final Page page;

        public PlayerMenuHolder() {
            this(Page.HUB);
        }

        public PlayerMenuHolder(Page page) {
            this.page = page == null ? Page.HUB : page;
        }

        public Page getPage() {
            return page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

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

    private boolean mod(com.aegisguard.config.Modules.Id id) {
        try {
            return plugin.modules().on(id);
        } catch (Throwable ignored) {
            return id.defaultOn();
        }
    }

    public void open(Player player) {
        open(player, Page.HUB);
    }

    public void open(Player player, Page page) {
        Page safe = page == null ? Page.HUB : page;
        String title = switch (safe) {
            case TERRITORY -> plugin.gui().title(player, "menu_page_territory_title", "&bTerritory");
            case ACCESS -> plugin.gui().title(player, "menu_page_access_title", "&dAccess & Safety");
            case ECONOMY -> plugin.gui().title(player, "menu_page_economy_title", "&6Economy & Progress");
            case EXPLORE -> plugin.gui().title(player, "menu_page_explore_title", "&aExplore");
            default -> plugin.gui().title(player, "menu_title", "&b⚔ AegisGuard Menu");
        };

        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(safe), 54, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        Context ctx = Context.capture(this, player);

        switch (safe) {
            case HUB -> paintHub(player, inv, ctx);
            case TERRITORY -> paintTerritory(player, inv, ctx);
            case ACCESS -> paintAccess(player, inv, ctx);
            case ECONOMY -> paintEconomy(player, inv, ctx);
            case EXPLORE -> paintExplore(player, inv, ctx);
        }

        paintFooter(player, inv, ctx, safe != Page.HUB);
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    private void paintHub(Player player, Inventory inv, Context ctx) {
        inv.setItem(SLOT_INFO, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                t(player, "button_info", "&bℹ Info"),
                tl(player, "info_lore", List.of("&7Read the basics, commands,", "&7and protection tips."))
        ));

        Material flagIcon = ctx.canManage ? Material.OAK_SIGN : Material.OAK_HANGING_SIGN;
        inv.setItem(SLOT_SHORTCUT_FLAGS, GUIManager.createItem(
                flagIcon,
                t(player, "button_plot_flags", "&6⚙ Claim Settings"),
                tl(player, ctx.canManage ? "plot_flags_lore" : "plot_flags_locked_lore",
                        ctx.canManage
                                ? List.of("&7Control who can enter, use,", "&7damage, or automate this claim.")
                                : List.of("&cStand inside a claim you manage", "&cto edit these protections."))
        ));

        inv.setItem(SLOT_SHORTCUT_STATUS, GUIManager.createItem(
                ctx.plot != null ? Material.BOOK : Material.GRAY_DYE,
                t(player, "button_plot_status", "&b📊 Claim Status"),
                tl(player, ctx.plot != null ? "plot_status_button_lore" : "plot_status_button_locked_lore",
                        ctx.plot != null
                                ? List.of("&7A snapshot of this plot: owner, protections,",
                                "&7blessings, growth, ClaimBlocks, and access.",
                                " ",
                                "&eClick to open.")
                                : List.of("&cStand inside a plot to view status."))
        ));

        if (ctx.showTravel) {
            inv.setItem(SLOT_SHORTCUT_TRAVEL, GUIManager.createItem(
                    Material.COMPASS,
                    t(player, "visit_gui_title", "&a🧭 Travel"),
                    tl(player, "visit_button_lore", List.of("&7Visit plots, warps, and", "&7trusted destinations."))
            ));
        }

        if (ctx.showMarket) {
            inv.setItem(SLOT_SHORTCUT_MARKET, GUIManager.createItem(
                    ctx.localMarket ? Material.CHEST : Material.GOLD_INGOT,
                    t(player, ctx.localMarket ? "button_market_local" : "button_market",
                            ctx.localMarket ? "&6Local Market" : "&6💰 Market"),
                    tl(player, ctx.localMarket ? "market_local_lore" : "market_lore",
                            ctx.localMarket
                                    ? List.of("&7Open this plot's rentals, shop", "&7tools, and market options.")
                                    : List.of("&7Browse listed claims and", "&7market activity."))
            ));
        }

        if (ctx.showRealm || ctx.showExpand || ctx.showZoning || ctx.showMerge) {
            inv.setItem(SLOT_DOOR_TERRITORY, GUIManager.createItem(
                    Material.GRASS_BLOCK,
                    t(player, "hub_category_territory_name", "&bTerritory"),
                    tl(player, "hub_category_territory_lore", List.of(
                            "&7Realm profile, expansion,", "&7zoning, and claim merge."))
            ));
        }

        inv.setItem(SLOT_DOOR_ACCESS, GUIManager.createItem(
                Material.PLAYER_HEAD,
                t(player, "hub_category_access_name", "&dAccess & Safety"),
                tl(player, "hub_category_access_lore", List.of(
                        "&7Roles, guest passes,", "&7alliance access, and lockdown."))
        ));

        if (ctx.showExchange || ctx.showLeveling || ctx.showAuction || ctx.showGift || ctx.showRentals) {
            inv.setItem(SLOT_DOOR_ECONOMY, GUIManager.createItem(
                    Material.EMERALD,
                    t(player, "hub_category_economy_name", "&6Economy & Progress"),
                    tl(player, "hub_category_economy_lore", List.of(
                            "&7Exchange, leveling, auctions,", "&7gifts, and rentals."))
            ));
        }

        if (ctx.showRoutes || ctx.showArena || ctx.showBeacons || ctx.showPlotChat) {
            inv.setItem(SLOT_DOOR_EXPLORE, GUIManager.createItem(
                    Material.FILLED_MAP,
                    t(player, "hub_category_explore_name", "&aExplore"),
                    tl(player, "hub_category_explore_lore", List.of(
                            "&7Staff routes, arenas, beacons,", "&7and plot Frequency chat."))
            ));
        }
    }

    private void paintTerritory(Player player, Inventory inv, Context ctx) {
        if (ctx.showRealm) {
            inv.setItem(SLOT_CAT_A, GUIManager.createItem(
                    Material.NAME_TAG,
                    t(player, "button_realm_profile", "&3📜 Realm Profile"),
                    tl(player, ctx.canManage ? "realm_profile_button_lore" : "realm_profile_button_view_lore",
                            ctx.canManage
                                    ? List.of("&7Manage this plot's name, category,", "&7greeting, and noticeboard.")
                                    : List.of("&7View this plot's public identity", "&7and noticeboard."))
            ));
        }
        if (ctx.showExpand) {
            inv.setItem(SLOT_CAT_B, GUIManager.createItem(
                    Material.DIAMOND_PICKAXE,
                    t(player, "button_expand", "&b⛏ Expand"),
                    tl(player, "expand_lore", List.of("&7Request more land for this", "&7claim when you outgrow it."))
            ));
        }
        if (ctx.showZoning) {
            inv.setItem(SLOT_CAT_C, GUIManager.createItem(
                    ctx.rentingCurrentZone ? Material.ENDER_PEARL : Material.IRON_BARS,
                    t(player, ctx.rentingCurrentZone ? "zone_tenant_button_name" : "zone_gui_title",
                            ctx.rentingCurrentZone ? "&bRoom Controls" : "&b🏗 Zoning"),
                    tl(player, ctx.rentingCurrentZone ? "zone_tenant_button_lore" : "zone_button_lore",
                            ctx.rentingCurrentZone
                                    ? List.of("&7Manage your rented room,", "&7approved guests, and room spawn.")
                                    : List.of("&7Create sub-zones, rentals,", "&7and managed rooms."))
            ));
        }
        if (ctx.showMerge) {
            inv.setItem(SLOT_CAT_D, GUIManager.createItem(
                    Material.SLIME_BALL,
                    t(player, "button_claim_merge", "&aMerge Claims"),
                    tl(player, "claim_merge_button_lore",
                            List.of("&7Combine adjacent owned claims", "&7into one larger plot."))
            ));
        }
    }

    private void paintAccess(Player player, Inventory inv, Context ctx) {
        Material roleIcon = ctx.canManage ? Material.PLAYER_HEAD : Material.SKELETON_SKULL;
        inv.setItem(SLOT_CAT_A, GUIManager.createItem(
                roleIcon,
                t(player, "button_roles", "&e👥 Roles"),
                tl(player, ctx.canManage ? "roles_lore" : "roles_locked_lore",
                        ctx.canManage
                                ? List.of("&7Grant or revoke access for", "&7friends, helpers, and visitors.")
                                : List.of("&cStand inside a claim you manage", "&cto edit member access."))
        ));

        if (ctx.showGuests) {
            Material guestPassIcon = ctx.canManage ? Material.NAME_TAG : Material.PAPER;
            inv.setItem(SLOT_CAT_B, GUIManager.createItem(
                    guestPassIcon,
                    t(player, "button_guest_passes", "&d🎫 Guest Passes"),
                    tl(player, ctx.canManage ? "guest_passes_lore" : "guest_passes_locked_lore",
                            ctx.canManage
                                    ? List.of("&7Grant temporary, self-expiring", "&7access without permanent trust.")
                                    : List.of("&cStand inside a claim you manage", "&cto issue Guest Passes."))
            ));
        }

        if (ctx.showAlliance) {
            Material allianceIcon = ctx.allianceJoined ? Material.SHIELD : Material.GRAY_DYE;
            inv.setItem(SLOT_CAT_C, GUIManager.createItem(
                    allianceIcon,
                    t(player, "button_alliance_access", "&6🛡 Alliance Access"),
                    tl(player, ctx.allianceJoined ? "alliance_button_lore" : "alliance_button_grayed_lore",
                            ctx.allianceJoined
                                    ? List.of("&7Manage this plot's alliance", "&7access toggles.")
                                    : List.of("&7This plot has not joined an alliance.",
                                    "&7Create or join one, then opt this",
                                    "&7plot in. Risky toggles stay OFF."))
            ));
        }

        if (ctx.showLockdown) {
            Material lockdownIcon = ctx.lockdownActive ? Material.RED_STAINED_GLASS_PANE
                    : (ctx.canManage ? Material.IRON_BARS : Material.GRAY_STAINED_GLASS_PANE);
            inv.setItem(SLOT_CAT_D, GUIManager.createItem(
                    lockdownIcon,
                    t(player, ctx.lockdownActive ? "button_lockdown_active" : "button_lockdown", "&cEmergency Lockdown"),
                    tl(player, ctx.canManage
                                    ? (ctx.lockdownActive ? "lockdown_button_active_lore" : "lockdown_button_lore")
                                    : "lockdown_button_locked_lore",
                            ctx.lockdownActive
                                    ? List.of("&cThis plot is locked down.", "&7Click to view status or unlock.")
                                    : ctx.canManage
                                    ? List.of("&7A fast, reversible safety switch", "&7for griefing, disputes, or maintenance.")
                                    : List.of("&cStand inside a claim you manage", "&cto use Emergency Lockdown."))
            ));
        }
    }

    private void paintEconomy(Player player, Inventory inv, Context ctx) {
        if (ctx.showExchange) {
            inv.setItem(SLOT_CAT_A, GUIManager.createItem(
                    Material.EMERALD,
                    t(player, "button_claimblocks_exchange", "&a💱 ClaimBlocks Exchange"),
                    tl(player, "claimblocks_exchange_lore",
                            List.of("&7Buy or sell Claim Blocks with", "&7your server economy.", " ", "&eClick to open."))
            ));
        }
        if (ctx.showLeveling) {
            inv.setItem(SLOT_CAT_B, GUIManager.createItem(
                    Material.EXPERIENCE_BOTTLE,
                    t(player, "level_gui_title", "&a📈 Leveling"),
                    tl(player, "level_button_lore", List.of("&7Upgrade your plot to unlock", "&7perks and stronger bonuses."))
            ));
        }
        if (ctx.showAuction) {
            inv.setItem(SLOT_CAT_C, GUIManager.createItem(
                    Material.LAVA_BUCKET,
                    t(player, "button_auction", "&c🔥 Auctions"),
                    tl(player, "auction_lore", List.of("&7Bid on auctioned claims", "&7and time-limited listings."))
            ));
        }
        if (ctx.showGift) {
            inv.setItem(SLOT_CAT_D, GUIManager.createItem(
                    Material.GOLD_INGOT,
                    t(player, "button_giftblocks", "&aGift ClaimBlocks"),
                    tl(player, "giftblocks_button_lore",
                            List.of("&7Gift available ClaimBlocks", "&7to a nearby player."))
            ));
        }
        if (ctx.showRentals) {
            inv.setItem(SLOT_CAT_E, GUIManager.createItem(
                    Material.GOLDEN_HOE,
                    t(player, "button_my_rentals", "&6My Rentals"),
                    tl(player, "my_rentals_button_lore", List.of(
                            "&7View full-plot and zone rentals,",
                            "&7renew, extend, or cancel contracts."))
            ));
        }
    }

    private void paintExplore(Player player, Inventory inv, Context ctx) {
        if (ctx.showBeacons) {
            inv.setItem(SLOT_CAT_A, GUIManager.createItem(
                    Material.END_PORTAL_FRAME,
                    t(player, "button_beacons", "&bTeleport Beacons"),
                    tl(player, "beacons_button_lore", List.of(
                            "&7Create linked pads, pick who may use them,",
                            "&7and travel with a confirm screen."))
            ));
        }
        if (ctx.showRoutes) {
            inv.setItem(SLOT_CAT_B, GUIManager.createItem(
                    Material.FILLED_MAP,
                    t(player, "button_routes", "&a🗺 Routes"),
                    tl(player, "routes_button_lore", List.of(
                            "&7Browse staff-made exploration routes",
                            "&7and see your next checkpoint."))
            ));
        }
        if (ctx.showPlotChat) {
            inv.setItem(SLOT_CAT_C, GUIManager.createItem(
                    Material.GOAT_HORN,
                    t(player, "button_plot_chat", "&bAegis Frequency"),
                    tl(player, "plot_chat_button_lore", List.of(
                            "&7Toggle private plot chat",
                            "&7for members of this claim."))
            ));
        }
        if (ctx.showArena) {
            inv.setItem(SLOT_CAT_D, GUIManager.createItem(
                    Material.DIAMOND_SWORD,
                    t(player, "button_arena", "&c⚔ Arenas"),
                    tl(player, "arena_button_lore", List.of(
                            "&7Join cooperative dungeon runs,",
                            "&7parties, and Lava Dungeon challenges."))
            ));
        }
    }

    private void paintFooter(Player player, Inventory inv, Context ctx, boolean showBack) {
        if (showBack) {
            inv.setItem(SLOT_BACK, GUIManager.createItem(
                    Material.ARROW,
                    t(player, "button_back", "&e⟵ Back"),
                    tl(player, "back_lore", List.of("&7Return to the previous page."))
            ));
        }

        inv.setItem(SLOT_SETTINGS, GUIManager.createItem(
                Material.COMPARATOR,
                t(player, "button_player_settings", "&e⚙ Settings"),
                tl(player, "player_settings_lore", List.of("&7Adjust language, sounds,", "&7and notification settings."))
        ));

        if (ctx.admin) {
            inv.setItem(SLOT_ADMIN, GUIManager.createItem(
                    Material.REDSTONE_BLOCK,
                    t(player, "admin_menu_title", "&c🛠 Admin Menu"),
                    tl(player, "admin_menu_lore", List.of("&7Operator Access Only"))
            ));
            ItemStack reloadHub = GUIManager.createItem(
                    Material.REDSTONE,
                    t(player, "button_reload_all_settings",
                            t(player, "button_admin_reload", "&eReload All Settings")),
                    tl(player, "reload_all_settings_lore",
                            tl(player, "admin_reload_lore", List.of("&7Reload configs + language packs.")))
            );
            try { plugin.gui().tagAction(reloadHub, "reload_all"); } catch (Throwable ignored) {}
            inv.setItem(SLOT_RELOAD, reloadHub);
        }

        inv.setItem(SLOT_EXIT, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&c✖ Exit"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int raw = e.getRawSlot();
        if (raw < 0 || raw >= e.getInventory().getSize()) return;
        if (raw >= 54) return;
        if (GUIManager.isFiller(e.getCurrentItem())) return;

        String action = null;
        try { action = plugin.gui().getAction(e.getCurrentItem()); } catch (Throwable ignored) {}
        if (action != null && (action.equals("reload_all") || action.equals("refresh_lang")
                || action.equals("reload") || action.equals("section_marker"))) {
            return;
        }

        Page page = Page.HUB;
        if (e.getInventory().getHolder() instanceof PlayerMenuHolder holder) {
            page = holder.getPage();
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        boolean isAdmin = plugin.isAdmin(player);
        boolean canManage = plot != null && plot.canManage(player, plugin);

        if (raw == SLOT_EXIT) {
            player.closeInventory();
            return;
        }
        if (raw == SLOT_SETTINGS) {
            plugin.gui().settings().open(player);
            GUIManager.playClick(player);
            return;
        }
        if (raw == SLOT_ADMIN && isAdmin) {
            plugin.gui().admin().open(player);
            return;
        }
        if (raw == SLOT_RELOAD && isAdmin) {
            return;
        }
        if (raw == SLOT_BACK && page != Page.HUB) {
            open(player, Page.HUB);
            return;
        }

        boolean handled = switch (page) {
            case HUB -> handleHubClick(player, raw, plot, canManage);
            case TERRITORY -> handleTerritoryClick(player, raw, plot, canManage);
            case ACCESS -> handleAccessClick(player, raw, plot, canManage);
            case ECONOMY -> handleEconomyClick(player, raw, plot, canManage);
            case EXPLORE -> handleExploreClick(player, raw);
        };

        if (handled && raw != SLOT_ADMIN && raw != SLOT_EXIT && raw != SLOT_RELOAD) {
            GUIManager.playClick(player);
        }
    }

    private boolean handleHubClick(Player player, int slot, Plot plot, boolean canManage) {
        switch (slot) {
            case SLOT_INFO -> {
                plugin.gui().info().open(player);
                return true;
            }
            case SLOT_SHORTCUT_FLAGS -> {
                openFlags(player, plot, canManage);
                return true;
            }
            case SLOT_SHORTCUT_STATUS -> {
                openStatus(player, plot);
                return true;
            }
            case SLOT_SHORTCUT_TRAVEL -> {
                if (!mod(com.aegisguard.config.Modules.Id.TRAVEL)) return false;
                plugin.gui().visit().open(player, 0, VisitGUI.VisitMode.WARPS);
                return true;
            }
            case SLOT_SHORTCUT_MARKET -> {
                return openMarket(player, plot);
            }
            case SLOT_DOOR_TERRITORY -> {
                open(player, Page.TERRITORY);
                return true;
            }
            case SLOT_DOOR_ACCESS -> {
                open(player, Page.ACCESS);
                return true;
            }
            case SLOT_DOOR_ECONOMY -> {
                open(player, Page.ECONOMY);
                return true;
            }
            case SLOT_DOOR_EXPLORE -> {
                open(player, Page.EXPLORE);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleTerritoryClick(Player player, int slot, Plot plot, boolean canManage) {
        switch (slot) {
            case SLOT_CAT_A -> {
                if (!mod(com.aegisguard.config.Modules.Id.REALM_PROFILES)) return false;
                if (plot != null) plugin.gui().realmProfile().open(player);
                else denyNeedPlot(player, plot, canManage, true);
                return true;
            }
            case SLOT_CAT_B -> {
                if (!mod(com.aegisguard.config.Modules.Id.EXPANSIONS)) return false;
                plugin.gui().expansionRequest().open(player);
                return true;
            }
            case SLOT_CAT_C -> {
                return openZoning(player, plot, canManage);
            }
            case SLOT_CAT_D -> {
                if (!mod(com.aegisguard.config.Modules.Id.CLAIM_MERGE)) return false;
                plugin.gui().claimMerge().open(player);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleAccessClick(Player player, int slot, Plot plot, boolean canManage) {
        switch (slot) {
            case SLOT_CAT_A -> {
                if (plot != null && canManage) plugin.gui().roles().openRolesMenu(player, plot);
                else denyNeedPlot(player, plot, canManage, false);
                return true;
            }
            case SLOT_CAT_B -> {
                if (!mod(com.aegisguard.config.Modules.Id.GUEST_PASSES)) return false;
                if (plot != null && canManage) plugin.gui().guestPasses().open(player);
                else denyNeedPlot(player, plot, canManage, false);
                return true;
            }
            case SLOT_CAT_C -> {
                if (!mod(com.aegisguard.config.Modules.Id.ALLIANCE_ACCESS)) return false;
                plugin.gui().allianceAccess().openMenu(player, plot);
                return true;
            }
            case SLOT_CAT_D -> {
                if (!mod(com.aegisguard.config.Modules.Id.LOCKDOWN)) return false;
                if (plot != null && canManage) plugin.gui().lockdownGui().open(player);
                else denyNeedPlot(player, plot, canManage, false);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleEconomyClick(Player player, int slot, Plot plot, boolean canManage) {
        switch (slot) {
            case SLOT_CAT_A -> {
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
                return true;
            }
            case SLOT_CAT_B -> {
                if (!mod(com.aegisguard.config.Modules.Id.LEVELING)) return false;
                if (plot != null && canManage) plugin.gui().leveling().open(player, plot);
                else denyNeedPlot(player, plot, canManage, false);
                return true;
            }
            case SLOT_CAT_C -> {
                if (!mod(com.aegisguard.config.Modules.Id.AUCTION)) return false;
                plugin.gui().auction().open(player, 0);
                return true;
            }
            case SLOT_CAT_D -> {
                if (!(mod(com.aegisguard.config.Modules.Id.CLAIM_BLOCKS)
                        && plugin.getConfig().getBoolean("claim_blocks.gift.enabled", true))) return false;
                plugin.gui().giftBlocks().open(player);
                return true;
            }
            case SLOT_CAT_E -> {
                if (!mod(com.aegisguard.config.Modules.Id.RENTALS)) return false;
                plugin.gui().myRentals().open(player);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handleExploreClick(Player player, int slot) {
        switch (slot) {
            case SLOT_CAT_A -> {
                if (!mod(com.aegisguard.config.Modules.Id.TELEPORT_BEACONS)) return false;
                plugin.gui().beacons().openManager(player);
                return true;
            }
            case SLOT_CAT_B -> {
                if (!mod(com.aegisguard.config.Modules.Id.ROUTES)) return false;
                plugin.gui().routes().open(player);
                return true;
            }
            case SLOT_CAT_C -> {
                if (!mod(com.aegisguard.config.Modules.Id.PLOT_CHAT) || plugin.plotChat() == null) return false;
                com.aegisguard.chat.PlotChatService chat = plugin.plotChat();
                com.aegisguard.chat.PlotChatService.ToggleResult result = chat.toggle(player);
                com.aegisguard.data.Plot tuned = chat.resolvePlot(player);
                String label = chat.plotLabel(tuned);
                String key;
                String fallback;
                Map<String, String> placeholders = Map.of("PLOT", label);
                switch (result) {
                    case ON -> {
                        key = "plot_chat_on";
                        fallback = "&aAegis Frequency on for &f{PLOT}&a. Your chat stays on this plot.";
                    }
                    case SWITCHED -> {
                        key = "plot_chat_switched";
                        fallback = "&aFrequency switched to &f{PLOT}&a.";
                    }
                    case OFF -> {
                        key = "plot_chat_off";
                        fallback = "&eAegis Frequency off. Chat is public again.";
                        placeholders = Map.of();
                    }
                    default -> {
                        key = "plot_chat_need_member";
                        fallback = "&cStand in a plot you belong to before opening Frequency.";
                        placeholders = Map.of();
                    }
                }
                player.closeInventory();
                player.sendMessage(GUIManager.color(
                        t(player, "prefix", "&8[&bAegisGuard&8]&r ")
                                + plugin.gui().tr(player, key, fallback, placeholders)));
                return true;
            }
            case SLOT_CAT_D -> {
                if (!(mod(com.aegisguard.config.Modules.Id.ARENA) && plugin.gui().arena() != null)) return false;
                plugin.gui().arena().open(player);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void openFlags(Player player, Plot plot, boolean canManage) {
        if (plot != null && canManage) plugin.gui().flags().open(player, plot);
        else denyNeedPlot(player, plot, canManage, false);
    }

    private void openStatus(Player player, Plot plot) {
        if (plot != null) plugin.gui().plotStatus().open(player, plot);
        else {
            send(player, "no_plot_here", "&cYou must be standing inside a plot to do that.");
            if (plugin.effects() != null) plugin.effects().playError(player);
        }
    }

    private boolean openMarket(Player player, Plot plot) {
        if (!(mod(com.aegisguard.config.Modules.Id.MARKET)
                || mod(com.aegisguard.config.Modules.Id.MARKET_STALLS)
                || mod(com.aegisguard.config.Modules.Id.RENTALS))) {
            return false;
        }
        boolean preferLocal = plot != null
                && plugin.marketBridges() != null
                && plugin.marketBridges().preferLocalWhenInPlot()
                && plugin.marketBridges().plotQualifiesForLocalMarket(plot, player);
        if (preferLocal) plugin.gui().localMarket().open(player, plot);
        else plugin.gui().market().open(player, 0);
        return true;
    }

    private boolean openZoning(Player player, Plot plot, boolean canManage) {
        if (!mod(com.aegisguard.config.Modules.Id.ZONING)) return false;
        com.aegisguard.data.Zone rentedZone = plot == null ? null : plot.getRentedZoneAt(player.getLocation());
        if (plot != null && rentedZone != null && rentedZone.isRentedBy(player.getUniqueId())) {
            plugin.gui().zoneTenant().open(player, plot, rentedZone);
        } else if (plot != null && canManage) {
            plugin.gui().zoning().open(player, plot);
        } else if (plot != null && plot.hasBrowsableZonesFor(player)) {
            plugin.gui().zoneBrowse().open(player, plot);
        } else {
            denyNeedPlot(player, plot, canManage, false);
        }
        return true;
    }

    private void denyNeedPlot(Player player, Plot plot, boolean canManage, boolean viewWithoutManage) {
        if (viewWithoutManage && plot == null) {
            send(player, "no_plot_here", "&cYou must be standing inside a plot to do that.");
        } else {
            send(player, plot == null ? "no_plot_here" : "not_plot_owner",
                    plot == null
                            ? "&cYou must be standing inside a plot to do that."
                            : "&cYou cannot manage this plot.");
        }
        if (plugin.effects() != null) plugin.effects().playError(player);
    }

    private static final class Context {
        final Plot plot;
        final boolean admin;
        final boolean canManage;
        final boolean rentingCurrentZone;
        final boolean allianceJoined;
        final boolean lockdownActive;
        final boolean localMarket;
        final boolean showRealm;
        final boolean showExpand;
        final boolean showZoning;
        final boolean showGuests;
        final boolean showAlliance;
        final boolean showLockdown;
        final boolean showMarket;
        final boolean showMerge;
        final boolean showGift;
        final boolean showRentals;
        final boolean showExchange;
        final boolean showLeveling;
        final boolean showAuction;
        final boolean showRoutes;
        final boolean showTravel;
        final boolean showArena;
        final boolean showBeacons;
        final boolean showPlotChat;

        private Context(PlayerGUI gui, Player player) {
            this.plot = gui.plugin.store().getPlotAt(player.getLocation());
            this.admin = gui.plugin.isAdmin(player);
            this.canManage = plot != null && plot.canManage(player, gui.plugin);
            com.aegisguard.data.Zone rented = plot == null ? null : plot.getRentedZoneAt(player.getLocation());
            this.rentingCurrentZone = rented != null && rented.isRentedBy(player.getUniqueId());
            this.allianceJoined = plot != null && plot.getAllianceId() != null;
            this.lockdownActive = plot != null && plot.isLockdownActive();
            this.showRealm = gui.mod(com.aegisguard.config.Modules.Id.REALM_PROFILES);
            this.showExpand = gui.mod(com.aegisguard.config.Modules.Id.EXPANSIONS);
            this.showZoning = gui.mod(com.aegisguard.config.Modules.Id.ZONING);
            this.showGuests = gui.mod(com.aegisguard.config.Modules.Id.GUEST_PASSES);
            this.showAlliance = gui.mod(com.aegisguard.config.Modules.Id.ALLIANCE_ACCESS);
            this.showLockdown = gui.mod(com.aegisguard.config.Modules.Id.LOCKDOWN);
            this.showMarket = gui.mod(com.aegisguard.config.Modules.Id.MARKET)
                    || gui.mod(com.aegisguard.config.Modules.Id.MARKET_STALLS)
                    || gui.mod(com.aegisguard.config.Modules.Id.RENTALS);
            this.showMerge = gui.mod(com.aegisguard.config.Modules.Id.CLAIM_MERGE);
            this.showGift = gui.mod(com.aegisguard.config.Modules.Id.CLAIM_BLOCKS)
                    && gui.plugin.getConfig().getBoolean("claim_blocks.gift.enabled", true);
            this.showRentals = gui.mod(com.aegisguard.config.Modules.Id.RENTALS);
            boolean exchange = false;
            try {
                exchange = gui.mod(com.aegisguard.config.Modules.Id.CLAIM_BLOCKS)
                        && gui.plugin.exchange() != null
                        && gui.plugin.cfg() != null
                        && gui.plugin.cfg().raw().getBoolean("claim_blocks.exchange.enabled", false);
            } catch (Throwable ignored) {}
            this.showExchange = exchange;
            this.showLeveling = gui.mod(com.aegisguard.config.Modules.Id.LEVELING);
            this.showAuction = gui.mod(com.aegisguard.config.Modules.Id.AUCTION);
            this.showRoutes = gui.mod(com.aegisguard.config.Modules.Id.ROUTES);
            this.showTravel = gui.mod(com.aegisguard.config.Modules.Id.TRAVEL);
            this.showArena = gui.mod(com.aegisguard.config.Modules.Id.ARENA) && gui.plugin.gui().arena() != null;
            this.showBeacons = gui.mod(com.aegisguard.config.Modules.Id.TELEPORT_BEACONS);
            this.showPlotChat = gui.mod(com.aegisguard.config.Modules.Id.PLOT_CHAT);
            this.localMarket = plot != null
                    && gui.plugin.marketBridges() != null
                    && gui.plugin.marketBridges().preferLocalWhenInPlot()
                    && gui.plugin.marketBridges().plotQualifiesForLocalMarket(plot, player);
        }

        static Context capture(PlayerGUI gui, Player player) {
            return new Context(gui, player);
        }
    }
}
