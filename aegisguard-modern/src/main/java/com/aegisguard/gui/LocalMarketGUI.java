package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.hooks.market.MarketBridgeManager.BridgeEntry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class LocalMarketGUI {

    static final int SLOT_PLOT = 4;
    static final int SLOT_GUIDE = 10;
    static final int SLOT_PULSE = 13;
    static final int SLOT_CREATE = 16;
    static final int SLOT_REAL_ESTATE = 20;
    static final int SLOT_ZONE_RENTALS = 22;
    static final int SLOT_MANAGE = 24;
    static final int SLOT_MERGE = 29;
    static final int SLOT_MY_RENTALS = 31;
    static final int SLOT_MY_TENANTS = 33;
    static final int SLOT_GIFT = 35;
    static final int SLOT_STALLS = 40;
    static final int SLOT_BACK = 45;
    static final int SLOT_REFRESH = 49;
    static final int SLOT_EXIT = 53;
    static final int[] BRIDGE_SLOTS = {36, 37, 38, 39, 41, 42, 43, 44};

    private final AegisGuard plugin;

    public LocalMarketGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class LocalMarketHolder implements InventoryHolder {
        private final Plot plot;
        private final List<String> bridgeIds;

        public LocalMarketHolder(Plot plot, List<String> bridgeIds) {
            this.plot = plot;
            this.bridgeIds = bridgeIds == null ? new ArrayList<>() : bridgeIds;
        }

        public Plot getPlot() {
            return plot;
        }

        public List<String> getBridgeIds() {
            return bridgeIds;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void open(Player player) {
        if (player == null) return;
        open(player, plugin.store().getPlotAt(player.getLocation()));
    }

    public void open(Player player, Plot plot) {
        if (player == null) return;
        if (plot == null) {
            plugin.gui().market().open(player, 0);
            return;
        }

        List<BridgeEntry> bridges = plugin.marketBridges() == null
                ? List.of()
                : plugin.marketBridges().getActiveBridgeEntries(plot, true);
        List<String> bridgeIds = new ArrayList<>();
        for (BridgeEntry bridge : bridges) {
            bridgeIds.add(bridge.id());
        }

        Inventory inv = Bukkit.createInventory(
                new LocalMarketHolder(plot, bridgeIds),
                54,
                plugin.gui().title(player, "local_market_title", "&6Local Market")
        );

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);
        ItemStack marketGlass = GUIManager.createItem(Material.ORANGE_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : new int[]{9, 11, 12, 14, 15, 17, 18, 19, 21, 23, 25, 26, 27, 28}) {
            inv.setItem(slot, marketGlass);
        }

        String plotName = plot.getPlotName() == null || plot.getPlotName().isBlank()
                ? plot.getOwnerName() + "'s Plot"
                : plot.getPlotName();

        inv.setItem(SLOT_PLOT, GUIManager.createItem(
                Material.NAME_TAG,
                tr(player, "local_market_plot_name", "&e{PLOT}").replace("{PLOT}", plotName),
                List.of(
                        GUIManager.color(tr(player, "local_market_plot_world", "&7World: &f{WORLD}")
                                .replace("{WORLD}", plot.getWorld())),
                        GUIManager.color(tr(player, "local_market_plot_mode", "&7Shop access: &f{STATE}")
                                .replace("{STATE}", plot.getFlag("shop-interact", false)
                                        ? tr(player, "local_market_state_enabled", "&aEnabled")
                                        : tr(player, "local_market_state_disabled", "&cDisabled")))
                )
        ));

        inv.setItem(SLOT_GUIDE, GUIManager.createItem(
                Material.WRITTEN_BOOK,
                tr(player, "local_market_guide_name", "&eMarket District Guide"),
                trList(player, "local_market_guide_lore", List.of(
                        "&7Real Estate lists complete plots.",
                        "&7Zone Rentals lists rooms and sub-zones.",
                        "&7Trade Stalls lists local shopfronts.",
                        "&7External shops can be linked if installed.",
                        " ",
                        "&8Only available services can be opened."
                ))
        ));

        inv.setItem(SLOT_REAL_ESTATE, GUIManager.createItem(
                Material.GOLD_INGOT,
                tr(player, "local_market_global_name", "&6Real Estate"),
                trList(player, "local_market_global_lore", List.of(
                        "&7Browse plots for sale, public",
                        "&7rentals, and other listings."
                ))
        ));

        boolean hasZones = plot.hasBrowsableZonesFor(player);
        boolean tradeStallsEnabled = plugin.tradeStalls() != null && plugin.tradeStalls().isEnabledFor(plot);
        boolean hasStalls = tradeStallsEnabled && plot.hasBrowsableStalls();
        boolean canManage = plot.canManage(player, plugin);
        boolean vaultReady = plugin.vault() != null && plugin.vault().isEnabled();
        int zoneCount = plot.getZones() == null ? 0 : plot.getZones().size();
        List<String> installedShops = plugin.marketBridges() == null
                ? List.of()
                : plugin.marketBridges().getInstalledShopPluginNames();

        inv.setItem(SLOT_PULSE, GUIManager.createItem(
                Material.BELL,
                tr(player, "local_market_pulse_name", "&6District Pulse"),
                trList(player, "local_market_pulse_lore", List.of(
                        "&7Managed zones: &f{ZONES}",
                        "&7Linked markets: &f{BRIDGES}",
                        "&7TradeStalls: {STALLS}"
                )).stream().map(line -> line
                        .replace("{ZONES}", String.valueOf(zoneCount))
                        .replace("{BRIDGES}", String.valueOf(bridges.size()))
                        .replace("{STALLS}", hasStalls
                                ? tr(player, "status_enabled", "&aAvailable")
                                : tr(player, "status_disabled", "&cUnavailable"))).toList()
        ));
        inv.setItem(SLOT_ZONE_RENTALS, GUIManager.createItem(
                hasZones ? Material.EMERALD_BLOCK : Material.GRAY_DYE,
                tr(player, "local_market_rentals_name", "&aZone Rentals"),
                hasZones
                        ? trList(player, "local_market_rentals_lore", List.of(
                        "&7Browse rentable subplots and",
                        "&7managed rooms on this plot."
                ))
                        : trList(player, "local_market_rentals_locked_lore", List.of(
                        "&7This plot has no public",
                        "&7rentable sub-zones right now."
                ))
        ));

        if (tradeStallsEnabled) {
            boolean canCreateStall = canManage || plugin.getConfig().getBoolean("market_stalls.allow_zone_renters", true);
            List<String> createLore;
            Material createMat;
            if (!canCreateStall) {
                createMat = Material.GRAY_DYE;
                createLore = trList(player, "local_market_create_stall_locked_lore", List.of(
                        "&7Owners and zone renters can create a stall:",
                        "&7place a chest, then an adjacent sign",
                        "&7with &e[stall]&7 or &e[shop]&7 on line 1."
                ));
            } else {
                createMat = Material.OAK_SIGN;
                createLore = trList(player, "local_market_create_stall_lore", List.of(
                        "&7Place a chest or barrel, then an",
                        "&7adjacent sign with &e[stall]&7 or &e[shop]&7.",
                        " ",
                        "&eClick to start a bind: the next",
                        "&eright-click on a chest or sign",
                        "&eregisters this TradeStall."
                ));
            }
            inv.setItem(SLOT_CREATE, GUIManager.createItem(
                    createMat,
                    tr(player, "local_market_create_stall_name", "&eCreate TradeStall"),
                    createLore
            ));
        }

        if (plugin.modules().on(com.aegisguard.config.Modules.Id.RENTALS)) {
            inv.setItem(SLOT_MY_RENTALS, GUIManager.createItem(
                    Material.GOLDEN_HOE,
                    tr(player, "local_market_my_rentals_name", "&6My Rentals"),
                    trList(player, "local_market_my_rentals_lore", List.of(
                            "&7Manage your active full-plot and",
                            "&7zone rentals from one place."
                    ))
            ));
            inv.setItem(SLOT_MY_TENANTS, GUIManager.createItem(
                    Material.PLAYER_HEAD,
                    tr(player, "local_market_my_tenants_name", "&bMy Tenants"),
                    trList(player, "local_market_my_tenants_lore", List.of(
                            "&7Review renters and rental zones",
                            "&7on plots you manage."
                    ))
            ));
        }
        boolean mergeEnabled = plugin.modules().on(com.aegisguard.config.Modules.Id.CLAIM_MERGE);
        if (mergeEnabled) {
            inv.setItem(SLOT_MERGE, GUIManager.createItem(
                    canManage ? Material.SLIME_BALL : Material.GRAY_DYE,
                    tr(player, "button_claim_merge", "&aMerge Claims"),
                    trList(player, canManage ? "claim_merge_button_lore" : "claim_merge_button_locked_lore",
                            canManage
                                    ? List.of("&7Combine adjacent owned claims.")
                                    : List.of("&cOnly managers can merge this plot."))
            ));
        }
        if (plugin.modules().on(com.aegisguard.config.Modules.Id.CLAIM_BLOCKS)
                && plugin.getConfig().getBoolean("claim_blocks.gift.enabled", true)) {
            inv.setItem(SLOT_GIFT, GUIManager.createItem(
                    Material.GOLD_INGOT,
                    tr(player, "button_giftblocks", "&aGift ClaimBlocks"),
                    trList(player, "giftblocks_button_lore", List.of(
                            "&7Gift available ClaimBlocks",
                            "&7to a nearby player."
                    ))
            ));
        }

        List<String> stallLore;
        Material stallMat;
        if (!tradeStallsEnabled) {
            stallMat = Material.GRAY_DYE;
            stallLore = trList(player, "local_market_stalls_external_lore", List.of(
                    "&7TradeStalls are turned off here",
                    "&7because an external market bridge",
                    "&7is taking priority on this plot.",
                    "&7You can still use a listed",
                    "&7third-party shop plugin."
            ));
        } else if (!hasStalls) {
            stallMat = Material.CHEST;
            stallLore = canManage
                    ? trList(player, "local_market_stalls_empty_lore", List.of(
                    "&7No TradeStalls are stocked yet.",
                    "&7Create one with a chest and a",
                    "&7sign marked &e[stall]&7 or &e[shop]&7."
            ))
                    : trList(player, "local_market_stalls_locked_lore", List.of(
                    "&7No TradeStalls are",
                    "&7available on this plot right now."
            ));
        } else if (!vaultReady && plugin.tradeStalls().getDefaultCurrency() == com.aegisguard.economy.CurrencyType.VAULT) {
            stallMat = Material.CHEST;
            stallLore = new ArrayList<>(trList(player, "local_market_stalls_lore", List.of(
                    "&7Browse built-in TradeStalls and",
                    "&7shopfronts on this plot."
            )));
            stallLore.add(" ");
            stallLore.addAll(trList(player, "local_market_stalls_no_vault_lore", List.of(
                    "&eVault money is unavailable.",
                    "&7Sellers can still list Claim Blocks."
            )));
        } else {
            stallMat = Material.CHEST;
            stallLore = new ArrayList<>(trList(player, "local_market_stalls_lore", List.of(
                    "&7Browse built-in TradeStalls and",
                    "&7shopfronts on this plot."
            )));
            if (!bridges.isEmpty()) {
                stallLore.add(" ");
                stallLore.addAll(trList(player, "local_market_stalls_coexist_lore", List.of(
                        "&7Built-in stalls and linked external",
                        "&7shops can both be used on this plot.",
                        "&8You are only charged by the shop you open."
                )));
            }
        }
        inv.setItem(SLOT_STALLS, GUIManager.createItem(
                stallMat,
                tr(player, "local_market_stalls_name", "&6Trade Stalls"),
                stallLore
        ));

        inv.setItem(SLOT_MANAGE, GUIManager.createItem(
                canManage ? Material.IRON_BARS : Material.GRAY_STAINED_GLASS_PANE,
                tr(player, "local_market_manage_name", "&bManage Local Market"),
                canManage
                        ? trList(player, "local_market_manage_lore", List.of(
                        "&7Create rental zones, register",
                        "&7TradeStalls, and manage rooms."
                ))
                        : trList(player, "local_market_manage_locked_lore", List.of(
                        "&7You need management access to",
                        "&7edit this plot's local market."
                ))
        ));

        if (bridges.isEmpty()) {
            if (!installedShops.isEmpty() || canManage) {
                List<String> externalLore = new ArrayList<>(trList(player, "local_market_external_lore", List.of(
                        "&7Optional: QuickShop, ChestShop,",
                        "&7Shopkeepers, or ExcellentShop.",
                        "&7Add a bridge entry in config.yml",
                        "&7under market_hub.external_bridges."
                )));
                if (!installedShops.isEmpty()) {
                    externalLore.add(" ");
                    externalLore.add(GUIManager.color(tr(player, "local_market_external_detected",
                            "&aDetected: &f{PLUGINS}").replace("{PLUGINS}", String.join(", ", installedShops))));
                    externalLore.addAll(trList(player, "local_market_external_unconfigured_lore", List.of(
                            "&7Detected plugins are not linked yet.",
                            "&7Staff can add commands with player:",
                            "&7or console: prefixes in config.yml."
                    )));
                }
                inv.setItem(BRIDGE_SLOTS[0], GUIManager.createItem(
                        Material.ENDER_CHEST,
                        tr(player, "local_market_external_name", "&bExternal Shops"),
                        externalLore
                ));
            }
        } else {
            int index = 0;
            for (BridgeEntry bridge : bridges) {
                if (index >= BRIDGE_SLOTS.length) break;
                List<String> lore = new ArrayList<>();
                if (!bridge.loreOrEmpty().isEmpty()) {
                    for (String line : bridge.loreOrEmpty()) {
                        lore.add(GUIManager.color(line));
                    }
                } else {
                    lore.add(GUIManager.color(tr(player, "local_market_bridge_default_lore",
                            "&7Open this linked market or shop system.")));
                }
                if (tradeStallsEnabled) {
                    lore.add(" ");
                    lore.add(GUIManager.color(tr(player, "local_market_bridge_coexist_line",
                            "&8Built-in TradeStalls stay available too.")));
                }
                lore.add(" ");
                if (bridge.accessible()) {
                    lore.add(GUIManager.color(tr(player, "local_market_bridge_click", "&eClick to open.")));
                } else {
                    lore.add(GUIManager.color(tr(player, "local_market_bridge_requires_shop",
                            "&cEnable Shop Interact on this plot first.")));
                }
                inv.setItem(BRIDGE_SLOTS[index++], GUIManager.createItem(
                        bridge.accessible() ? bridge.icon() : Material.GRAY_DYE,
                        bridge.displayName(),
                        lore
                ));
            }
        }

        inv.setItem(SLOT_BACK, GUIManager.createItem(
                Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to the previous page."))
        ));
        inv.setItem(SLOT_REFRESH, GUIManager.createItem(
                Material.COMPASS,
                tr(player, "button_refresh", "&bRefresh"),
                trList(player, "refresh_lore", List.of("&7Reload this menu."))
        ));
        inv.setItem(SLOT_EXIT, GUIManager.createItem(
                Material.BARRIER,
                tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, LocalMarketHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        Plot plot = holder.getPlot();
        if (plot == null) {
            plugin.gui().market().open(player, 0);
            return;
        }

        boolean tradeStallsEnabled = plugin.tradeStalls() != null && plugin.tradeStalls().isEnabledFor(plot);

        int slot = rawSlot;
        if (slot == SLOT_BACK) {
            plugin.gui().openMain(player);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == SLOT_REFRESH) {
            open(player, plot);
            return;
        }
        if (slot == SLOT_EXIT) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }

        if (slot == SLOT_REAL_ESTATE) {
            plugin.gui().market().open(player, 0, plot);
            plugin.effects().playMenuFlip(player);
            return;
        }

        if (slot == SLOT_ZONE_RENTALS) {
            if (plot.hasBrowsableZonesFor(player)) {
                plugin.gui().zoneBrowse().open(player, plot, MarketNav.LOCAL_MARKET);
                plugin.effects().playMenuFlip(player);
            } else {
                plugin.effects().playError(player);
                send(player, "zone_browse_none", "&cThere are no rentable zones here right now.");
            }
            return;
        }

        if (slot == SLOT_MY_RENTALS) {
            if (!plugin.modules().on(com.aegisguard.config.Modules.Id.RENTALS)) return;
            plugin.gui().myRentals().openFrom(player, 0, MarketNav.LOCAL_MARKET, plot);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == SLOT_MY_TENANTS) {
            if (!plugin.modules().on(com.aegisguard.config.Modules.Id.RENTALS)) return;
            plugin.gui().myTenants().openFrom(player, MarketNav.LOCAL_MARKET, plot);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == SLOT_MERGE) {
            if (plugin.modules().on(com.aegisguard.config.Modules.Id.CLAIM_MERGE) && plot.canManage(player, plugin)) {
                plugin.gui().claimMerge().openFrom(player, MarketNav.LOCAL_MARKET, plot);
            } else {
                plugin.effects().playError(player);
                send(player, "claim_merge_disabled", "&cClaim merging is unavailable.");
            }
            return;
        }
        if (slot == SLOT_GIFT) {
            if (!(plugin.modules().on(com.aegisguard.config.Modules.Id.CLAIM_BLOCKS)
                    && plugin.getConfig().getBoolean("claim_blocks.gift.enabled", true))) return;
            plugin.gui().giftBlocks().openFrom(player, MarketNav.LOCAL_MARKET, plot);
            return;
        }

        if (slot == SLOT_MANAGE) {
            if (plot.canManage(player, plugin)) {
                plugin.gui().zoning().open(player, plot, MarketNav.LOCAL_MARKET);
                plugin.effects().playMenuFlip(player);
            } else {
                plugin.effects().playError(player);
                send(player, "no_perm", "&cYou cannot manage this plot.");
            }
            return;
        }

        if (slot == SLOT_CREATE) {
            if (!tradeStallsEnabled) {
                plugin.effects().playError(player);
                send(player, "market_stall_external_override",
                        "&eTradeStalls are disabled here because this plot is using an external market integration.");
                return;
            }
            boolean canCreate = plot.canManage(player, plugin)
                    || plugin.getConfig().getBoolean("market_stalls.allow_zone_renters", true);
            if (!canCreate) {
                plugin.effects().playError(player);
                send(player, "market_stall_no_access",
                        "&cYou need plot management access or a rented zone to create a TradeStall.");
                return;
            }
            plugin.tradeStalls().startCreateBind(player);
            player.closeInventory();
            plugin.effects().playConfirm(player);
            send(player, "market_stall_bind_started",
                    "&aRight-click a chest or adjacent sign within 30s to register a TradeStall. Line 1 of a new sign can be [stall] or [shop].");
            send(player, "market_stall_create_guide",
                    "&7Place a single chest or barrel, then a sign next to it with [stall] or [shop] on the first line.");
            return;
        }

        if (slot == SLOT_STALLS) {
            if (!tradeStallsEnabled) {
                plugin.effects().playError(player);
                send(player, "market_stall_external_override",
                        "&eTradeStalls are disabled here because this plot is using an external market integration.");
            } else {
                plugin.gui().stallBrowse().openList(player, plot);
                plugin.effects().playMenuFlip(player);
            }
            return;
        }

        int bridgeIndex = bridgeIndex(slot);
        if (bridgeIndex >= 0) {
            if (bridgeIndex >= holder.getBridgeIds().size()) return;

            BridgeEntry bridge = plugin.marketBridges() == null
                    ? null
                    : plugin.marketBridges().getBridgeEntry(holder.getBridgeIds().get(bridgeIndex), plot, true);
            if (bridge == null) return;

            if (!bridge.accessible()) {
                plugin.effects().playError(player);
                send(player, "local_market_bridge_requires_shop", "&cEnable Shop Interact on this plot first.");
                return;
            }

            if (plugin.marketBridges().dispatchBridge(player, plot, bridge)) {
                plugin.effects().playConfirm(player);
                player.closeInventory();
            } else {
                plugin.effects().playError(player);
                send(player, "local_market_bridge_failed", "&cUnable to open that market integration right now.");
            }
        }
    }

    static int bridgeIndex(int slot) {
        for (int i = 0; i < BRIDGE_SLOTS.length; i++) {
            if (BRIDGE_SLOTS[i] == slot) return i;
        }
        return -1;
    }

    private String tr(Player player, String key, String fallback) {
        return plugin.gui().tr(player, key, fallback);
    }

    private List<String> trList(Player player, String key, List<String> fallback) {
        return plugin.gui().trList(player, key, fallback);
    }

    private void send(Player player, String key, String fallback) {
        String msg = tr(player, key, fallback);
        if (msg == null || msg.isBlank()) return;
        player.sendMessage(GUIManager.color(msg));
    }
}
