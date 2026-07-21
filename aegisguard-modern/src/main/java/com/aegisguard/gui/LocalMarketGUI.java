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
        for (int slot : new int[]{9, 11, 12, 14, 15, 17, 18, 19, 21, 23, 25, 26, 27, 28, 36, 37, 38, 39, 41, 42, 43, 44}) {
            inv.setItem(slot, marketGlass);
        }

        String plotName = plot.getPlotName() == null || plot.getPlotName().isBlank()
                ? plot.getOwnerName() + "'s Plot"
                : plot.getPlotName();

        inv.setItem(4, GUIManager.createItem(
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

        inv.setItem(10, GUIManager.createItem(
                Material.WRITTEN_BOOK,
                tr(player, "local_market_guide_name", "&eMarket District Guide"),
                trList(player, "local_market_guide_lore", List.of(
                        "&7Real Estate lists complete plots.",
                        "&7Rentals lists rooms and sub-zones.",
                        "&7TradeStalls lists local shopfronts.",
                        " ",
                        "&8Only available services can be opened."
                ))
        ));

        inv.setItem(20, GUIManager.createItem(
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
        int zoneCount = plot.getZones() == null ? 0 : plot.getZones().size();
        inv.setItem(13, GUIManager.createItem(
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
        inv.setItem(22, GUIManager.createItem(
                hasZones ? Material.EMERALD_BLOCK : Material.GRAY_DYE,
                tr(player, "local_market_rentals_name", "&aStalls & Rentals"),
                hasZones
                        ? trList(player, "local_market_rentals_lore", List.of(
                        "&7Browse rentable subplots, market",
                        "&7stalls, and managed rooms."
                ))
                        : trList(player, "local_market_rentals_locked_lore", List.of(
                        "&7This plot has no public stalls or",
                        "&7rentable sub-zones right now."
                ))
        ));

        inv.setItem(40, GUIManager.createItem(
                hasStalls ? Material.CHEST : Material.GRAY_DYE,
                tr(player, "local_market_stalls_name", "&6Trade Stalls"),
                hasStalls
                        ? trList(player, "local_market_stalls_lore", List.of(
                        "&7Browse built-in TradeStalls and",
                        "&7shopfronts on this plot."
                ))
                        : !tradeStallsEnabled
                        ? trList(player, "local_market_stalls_external_lore", List.of(
                        "&7TradeStalls are turned off here",
                        "&7because an external market bridge",
                        "&7is taking priority on this plot."
                ))
                        : trList(player, "local_market_stalls_locked_lore", List.of(
                        "&7No TradeStalls are",
                        "&7available on this plot right now."
                ))
        ));

        inv.setItem(24, GUIManager.createItem(
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

        int bridgeSlot = 29;
        for (BridgeEntry bridge : bridges) {
            if (bridgeSlot >= 36) break;

            List<String> lore = new ArrayList<>();
            if (!bridge.loreOrEmpty().isEmpty()) {
                for (String line : bridge.loreOrEmpty()) {
                    lore.add(GUIManager.color(line));
                }
            } else {
                lore.add(GUIManager.color(tr(player, "local_market_bridge_default_lore",
                        "&7Open this linked market or shop system.")));
            }

            lore.add(" ");
            if (bridge.accessible()) {
                lore.add(GUIManager.color(tr(player, "local_market_bridge_click", "&eClick to open.")));
            } else {
                lore.add(GUIManager.color(tr(player, "local_market_bridge_requires_shop",
                        "&cEnable Shop Interact on this plot first.")));
            }

            inv.setItem(bridgeSlot++, GUIManager.createItem(
                    bridge.accessible() ? bridge.icon() : Material.GRAY_DYE,
                    bridge.displayName(),
                    lore
            ));
        }

        inv.setItem(45, GUIManager.createItem(
                Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to the previous page."))
        ));
        inv.setItem(49, GUIManager.createItem(
                Material.COMPASS,
                tr(player, "button_refresh", "&bRefresh"),
                trList(player, "refresh_lore", List.of("&7Reload this menu."))
        ));
        inv.setItem(53, GUIManager.createItem(
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
        if (slot == 45) {
            plugin.gui().openMain(player);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == 49) {
            open(player, plot);
            return;
        }
        if (slot == 53) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }

        if (slot == 20) {
            plugin.gui().market().open(player, 0);
            plugin.effects().playMenuFlip(player);
            return;
        }

        if (slot == 22) {
            if (plot.hasBrowsableZonesFor(player)) {
                plugin.gui().zoneBrowse().open(player, plot);
                plugin.effects().playMenuFlip(player);
            } else {
                plugin.effects().playError(player);
                send(player, "zone_browse_none", "&cThere are no rentable zones here right now.");
            }
            return;
        }

        if (slot == 24) {
            if (plot.canManage(player, plugin)) {
                plugin.gui().zoning().open(player, plot);
                plugin.effects().playMenuFlip(player);
            } else {
                plugin.effects().playError(player);
                send(player, "no_perm", "&cYou cannot manage this plot.");
            }
            return;
        }

        if (slot == 40) {
            if (!tradeStallsEnabled) {
                plugin.effects().playError(player);
                send(player, "market_stall_external_override",
                        "&eTradeStalls are disabled here because this plot is using an external market integration.");
            } else if (plot.hasBrowsableStalls()) {
                plugin.gui().stallBrowse().openList(player, plot);
                plugin.effects().playMenuFlip(player);
            } else {
                plugin.effects().playError(player);
                send(player, "market_stall_none", "&cThere are no TradeStalls available here right now.");
            }
            return;
        }

        if (slot >= 29 && slot < 36) {
            int index = slot - 29;
            if (index >= holder.getBridgeIds().size()) return;

            BridgeEntry bridge = plugin.marketBridges() == null
                    ? null
                    : plugin.marketBridges().getBridgeEntry(holder.getBridgeIds().get(index), plot, true);
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
