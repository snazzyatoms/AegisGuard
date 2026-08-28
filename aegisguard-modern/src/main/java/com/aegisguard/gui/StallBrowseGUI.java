package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.MarketStall;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.market.TradeStallService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StallBrowseGUI {

    private static final int CONTENT_START = 9;
    private static final int CONTENT_END = 35;

    private final AegisGuard plugin;

    public StallBrowseGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Plot plot) {
        openList(player, plot);
    }

    public void open(Player player, Plot plot, Object stallKey) {
        if (player == null || plot == null) return;
        if (stallKey == null) {
            openList(player, plot);
            return;
        }

        MarketStall stall = plot.getStallByKey(String.valueOf(stallKey));
        if (stall == null) {
            openList(player, plot);
            return;
        }

        if (stall.canStock(player, plot, plugin)) {
            openManage(player, plot, stall, firstSelectableSlot(stall));
        } else {
            openPreview(player, plot, stall);
        }
    }

    public static class StallListHolder implements InventoryHolder {
        private final Plot plot;
        private final List<String> stallKeys;

        public StallListHolder(Plot plot, List<String> stallKeys) {
            this.plot = plot;
            this.stallKeys = stallKeys == null ? new ArrayList<>() : stallKeys;
        }

        public Plot getPlot() { return plot; }
        public List<String> getStallKeys() { return stallKeys; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class StallPreviewHolder implements InventoryHolder {
        private final Plot plot;
        private final String stallKey;

        public StallPreviewHolder(Plot plot, String stallKey) {
            this.plot = plot;
            this.stallKey = stallKey;
        }

        public Plot getPlot() { return plot; }
        public String getStallKey() { return stallKey; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class StallManageHolder implements InventoryHolder {
        private final Plot plot;
        private final String stallKey;
        private final int selectedSlot;

        public StallManageHolder(Plot plot, String stallKey, int selectedSlot) {
            this.plot = plot;
            this.stallKey = stallKey;
            this.selectedSlot = selectedSlot;
        }

        public Plot getPlot() { return plot; }
        public String getStallKey() { return stallKey; }
        public int getSelectedSlot() { return selectedSlot; }
        @Override public Inventory getInventory() { return null; }
    }

    public void openList(Player player, Plot plot) {
        if (player == null || plot == null) return;

        List<MarketStall> stalls = getBrowsableStalls(plot, player);
        Inventory inv = Bukkit.createInventory(
                new StallListHolder(plot, stalls.stream().map(MarketStall::getStorageKey).toList()),
                54,
                plugin.gui().title(player, "market_stall_list_title", "&6Trade Stalls")
        );

        ItemStack filler = GUIManager.getFiller();
        fillBottom(inv, filler);

        if (stalls.isEmpty()) {
            boolean canCreate = plot.canManage(player, plugin)
                    || plugin.getConfig().getBoolean("market_stalls.allow_zone_renters", true);
            boolean vaultReady = plugin.vault() != null && plugin.vault().isEnabled();
            List<String> emptyLore = new ArrayList<>(trList(player, "market_stall_none_lore", List.of(
                    "&7There are no registered TradeStalls",
                    "&7available on this plot right now."
            )));
            if (canCreate) {
                emptyLore.add(" ");
                emptyLore.addAll(trList(player, "market_stall_none_create_lore", List.of(
                        "&7Place a chest, then an adjacent sign",
                        "&7with &e[stall]&7 or &e[shop]&7 on line 1."
                )));
            }
            if (!vaultReady) {
                emptyLore.add(" ");
                emptyLore.addAll(trList(player, "market_stall_no_vault_lore", List.of(
                        "&eVault money is unavailable.",
                        "&7Listings can still use Claim Blocks."
                )));
            }
            inv.setItem(22, GUIManager.createItem(
                    Material.BARRIER,
                    tr(player, "market_stall_none_title", "&cNo Trade Stalls"),
                    emptyLore
            ));
        } else {
            int slot = 0;
            for (MarketStall stall : stalls) {
                if (slot >= 45) break;
                int saleCount = countVisibleListings(stall);
                List<String> lore = new ArrayList<>();
                lore.add(tr(player, "market_stall_owner_line", "&7Seller: &f{OWNER}")
                        .replace("{OWNER}", stall.getOwnerName()));
                if (stall.getZoneName() != null && !stall.getZoneName().isBlank()) {
                    lore.add(tr(player, "market_stall_zone_line", "&7Zone: &f{ZONE}")
                            .replace("{ZONE}", stall.getZoneName()));
                }
                lore.add(tr(player, "market_stall_item_count_line", "&7Listed slots: &f{COUNT}")
                        .replace("{COUNT}", String.valueOf(saleCount)));
                lore.add(" ");
                lore.add(tr(player, stall.canStock(player, plot, plugin)
                                ? "market_stall_manage_action"
                                : "market_stall_browse_action",
                        stall.canStock(player, plot, plugin)
                                ? "&eClick to manage this TradeStall."
                                : "&eLeft-click to browse this TradeStall."));
                lore.add(tr(player, "market_stall_visit_action", "&bRight-click to visit this stall."));

                inv.setItem(slot++, GUIManager.createItem(
                        resolveContainerMaterial(stall),
                        stall.getTitle(),
                        lore
                ));
            }
        }

        setNav(inv, player);
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void openPreview(Player player, Plot plot, MarketStall stall) {
        if (player == null || plot == null || stall == null) return;

        Inventory inv = Bukkit.createInventory(
                new StallPreviewHolder(plot, stall.getStorageKey()),
                54,
                plugin.gui().title(player, "market_stall_preview_title", "&6TradeStall: {STALL}",
                        Map.of("STALL", shortenTitle(stall.getTitle())))
        );

        ItemStack filler = GUIManager.getFiller();
        fillStatic(inv, filler);

        inv.setItem(4, GUIManager.createItem(
                resolveContainerMaterial(stall),
                tr(player, "market_stall_preview_info_name", "&e{STALL}").replace("{STALL}", stall.getTitle()),
                buildStallInfoLore(player, stall, countVisibleListings(stall))
        ));

        Inventory source = plugin.tradeStalls().resolveInventory(stall);
        boolean anyListed = false;
        if (source != null) {
            for (int chestSlot = 0; chestSlot < Math.min(27, source.getSize()); chestSlot++) {
                MarketStall.StallListing listing = stall.getListing(chestSlot);
                ItemStack item = source.getItem(chestSlot);
                if (listing == null || !listing.isValid() || item == null || item.getType().isAir()) continue;
                anyListed = true;

                ItemStack preview = item.clone();
                var meta = preview.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    if (!lore.isEmpty()) lore.add(" ");
                    lore.add(tr(player, "market_stall_price_line", "&7Price: &6{PRICE}")
                            .replace("{PRICE}", formatPrice(listing)));
                    lore.add(tr(player, "market_stall_bundle_line", "&7Bundle: &f{COUNT}")
                            .replace("{COUNT}", String.valueOf(listing.getBundleAmount())));
                    lore.add(tr(player, "market_stall_buy_action", "&aClick to confirm this purchase."));
                    meta.setLore(colorize(lore));
                    preview.setItemMeta(meta);
                }
                inv.setItem(guiSlotForChestSlot(chestSlot), preview);
            }
        }

        if (!anyListed) {
            boolean vaultReady = plugin.vault() != null && plugin.vault().isEnabled();
            List<String> emptyLore = new ArrayList<>(trList(player, "market_stall_preview_empty_lore", List.of(
                    "&7There are no displayed items in",
                    "&7this TradeStall right now."
            )));
            if (!vaultReady) {
                emptyLore.add(" ");
                emptyLore.addAll(trList(player, "market_stall_no_vault_lore", List.of(
                        "&eVault money is unavailable.",
                        "&7Listings can still use Claim Blocks."
                )));
            }
            inv.setItem(22, GUIManager.createItem(
                    Material.BARRIER,
                    tr(player, "market_stall_preview_empty_name", "&cThis stall is empty"),
                    emptyLore
            ));
        }

        setPreviewNav(inv, player);
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void openManage(Player player, Plot plot, MarketStall stall, int selectedSlot) {
        if (player == null || plot == null || stall == null) return;
        int safeSelected = Math.max(0, Math.min(26, selectedSlot));

        Inventory inv = Bukkit.createInventory(
                new StallManageHolder(plot, stall.getStorageKey(), safeSelected),
                54,
                plugin.gui().title(player, "market_stall_manage_title", "&6Manage TradeStall")
        );

        ItemStack filler = GUIManager.getFiller();
        fillStatic(inv, filler);

        inv.setItem(4, GUIManager.createItem(
                resolveContainerMaterial(stall),
                tr(player, "market_stall_manage_info_name", "&eManage: {STALL}").replace("{STALL}", stall.getTitle()),
                buildStallInfoLore(player, stall, countVisibleListings(stall))
        ));

        Inventory source = plugin.tradeStalls().resolveInventory(stall);
        if (source != null) {
            for (int chestSlot = 0; chestSlot < Math.min(27, source.getSize()); chestSlot++) {
                ItemStack item = source.getItem(chestSlot);
                if (item == null || item.getType().isAir()) continue;

                ItemStack preview = item.clone();
                var meta = preview.getItemMeta();
                if (meta != null) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    MarketStall.StallListing listing = stall.getListing(chestSlot);
                    lore.add(" ");
                    if (listing != null && listing.isValid()) {
                        lore.add(tr(player, "market_stall_price_line", "&7Price: &6{PRICE}")
                                .replace("{PRICE}", formatPrice(listing)));
                        lore.add(tr(player, "market_stall_bundle_line", "&7Bundle: &f{COUNT}")
                                .replace("{COUNT}", String.valueOf(listing.getBundleAmount())));
                    } else {
                        lore.add(tr(player, "market_stall_not_listed", "&7This slot is not listed yet."));
                    }
                    if (chestSlot == safeSelected) {
                        lore.add(tr(player, "market_stall_selected", "&aSelected for editing."));
                    } else {
                        lore.add(tr(player, "market_stall_select_action", "&eClick to edit this slot."));
                    }
                    meta.setLore(colorize(lore));
                    preview.setItemMeta(meta);
                }
                inv.setItem(guiSlotForChestSlot(chestSlot), preview);
            }
        }

        inv.setItem(36, GUIManager.createItem(
                Material.PAPER,
                tr(player, "market_stall_manage_selected_name", "&bSelected Slot"),
                buildSelectedLore(player, stall, safeSelected, source)
        ));
        inv.setItem(37, GUIManager.createItem(
                Material.GLOW_INK_SAC,
                tr(player, "market_stall_currency_name", "&eCurrency"),
                buildCurrencyLore(player, stall, safeSelected)
        ));
        inv.setItem(38, GUIManager.createItem(Material.RED_DYE, tr(player, "market_stall_price_minus_100", "&c-100"), trList(player, "market_stall_price_adjust_lore", List.of("&7Adjust the listing price."))));
        inv.setItem(39, GUIManager.createItem(Material.RED_DYE, tr(player, "market_stall_price_minus_10", "&c-10"), trList(player, "market_stall_price_adjust_lore", List.of("&7Adjust the listing price."))));
        inv.setItem(40, GUIManager.createItem(Material.RED_DYE, tr(player, "market_stall_price_minus_1", "&c-1"), trList(player, "market_stall_price_adjust_lore", List.of("&7Adjust the listing price."))));
        inv.setItem(41, GUIManager.createItem(Material.LIME_DYE, tr(player, "market_stall_price_plus_1", "&a+1"), trList(player, "market_stall_price_adjust_lore", List.of("&7Adjust the listing price."))));
        inv.setItem(42, GUIManager.createItem(Material.LIME_DYE, tr(player, "market_stall_price_plus_10", "&a+10"), trList(player, "market_stall_price_adjust_lore", List.of("&7Adjust the listing price."))));
        inv.setItem(43, GUIManager.createItem(Material.LIME_DYE, tr(player, "market_stall_price_plus_100", "&a+100"), trList(player, "market_stall_price_adjust_lore", List.of("&7Adjust the listing price."))));
        inv.setItem(44, GUIManager.createItem(
                Material.BARRIER,
                tr(player, "market_stall_clear_name", "&cClear Listing"),
                trList(player, "market_stall_clear_lore", List.of(
                        "&7Remove the price and currency from",
                        "&7the selected slot."
                ))
        ));

        inv.setItem(45, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"), trList(player, "back_lore", List.of("&7Return to the previous page."))));
        inv.setItem(46, GUIManager.createItem(Material.REDSTONE, tr(player, "market_stall_bundle_minus_8", "&cBundle -8"), trList(player, "market_stall_bundle_adjust_lore", List.of("&7Adjust how many items sell at once."))));
        inv.setItem(47, GUIManager.createItem(Material.REDSTONE, tr(player, "market_stall_bundle_minus_1", "&cBundle -1"), trList(player, "market_stall_bundle_adjust_lore", List.of("&7Adjust how many items sell at once."))));
        inv.setItem(48, GUIManager.createItem(Material.REDSTONE, tr(player, "market_stall_bundle_plus_1", "&aBundle +1"), trList(player, "market_stall_bundle_adjust_lore", List.of("&7Adjust how many items sell at once."))));
        inv.setItem(49, GUIManager.createItem(Material.REDSTONE, tr(player, "market_stall_bundle_plus_8", "&aBundle +8"), trList(player, "market_stall_bundle_adjust_lore", List.of("&7Adjust how many items sell at once."))));
        inv.setItem(50, GUIManager.createItem(Material.COMPASS, tr(player, "button_refresh", "&bRefresh"), trList(player, "refresh_lore", List.of("&7Reload this menu."))));
        inv.setItem(53, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"), trList(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleListClick(Player player, InventoryClickEvent e, StallListHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        if (slot == 45) {
            plugin.gui().localMarket().open(player, holder.getPlot());
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == 49) {
            openList(player, holder.getPlot());
            return;
        }
        if (slot == 53) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }
        if (slot >= 45 || slot >= holder.getStallKeys().size()) return;

        MarketStall stall = holder.getPlot() == null ? null : holder.getPlot().getStallByKey(holder.getStallKeys().get(slot));
        if (stall == null) {
            plugin.effects().playError(player);
            send(player, "market_stall_missing", "&cThat TradeStall is no longer available.");
            openList(player, holder.getPlot());
            return;
        }

        if (GuiClicks.alternate(e)) {
            visitStall(player, stall);
            return;
        }

        if (stall.canStock(player, holder.getPlot(), plugin)) {
            openManage(player, holder.getPlot(), stall, firstSelectableSlot(stall));
        } else {
            openPreview(player, holder.getPlot(), stall);
        }
        plugin.effects().playMenuFlip(player);
    }

    public void handlePreviewClick(Player player, InventoryClickEvent e, StallPreviewHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        Plot plot = holder.getPlot();
        if (plot == null) {
            player.closeInventory();
            return;
        }

        MarketStall stall = plot.getStallByKey(holder.getStallKey());
        if (stall == null) {
            send(player, "market_stall_missing", "&cThat TradeStall is no longer available.");
            player.closeInventory();
            return;
        }

        if (slot == 45) {
            openList(player, plot);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == 49) {
            openPreview(player, plot, stall);
            return;
        }
        if (slot == 53) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }

        int chestSlot = chestSlotForGuiSlot(slot);
        if (chestSlot < 0) return;

        plugin.gui().stallBuyConfirm().open(player, plot, stall, chestSlot);
    }

    public void handleManageClick(Player player, InventoryClickEvent e, StallManageHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        Plot plot = holder.getPlot();
        if (plot == null) {
            player.closeInventory();
            return;
        }

        MarketStall stall = plot.getStallByKey(holder.getStallKey());
        if (stall == null) {
            send(player, "market_stall_missing", "&cThat TradeStall is no longer available.");
            player.closeInventory();
            return;
        }

        int selectedSlot = holder.getSelectedSlot();
        int clickedChestSlot = chestSlotForGuiSlot(slot);
        if (clickedChestSlot >= 0) {
            openManage(player, plot, stall, clickedChestSlot);
            plugin.effects().playMenuFlip(player);
            return;
        }

        switch (slot) {
            case 37 -> toggleCurrency(player, plot, stall, selectedSlot);
            case 38 -> adjustPrice(player, plot, stall, selectedSlot, -100.0D);
            case 39 -> adjustPrice(player, plot, stall, selectedSlot, -10.0D);
            case 40 -> adjustPrice(player, plot, stall, selectedSlot, -1.0D);
            case 41 -> adjustPrice(player, plot, stall, selectedSlot, 1.0D);
            case 42 -> adjustPrice(player, plot, stall, selectedSlot, 10.0D);
            case 43 -> adjustPrice(player, plot, stall, selectedSlot, 100.0D);
            case 44 -> clearListing(player, plot, stall, selectedSlot);
            case 45 -> {
                openList(player, plot);
                plugin.effects().playMenuFlip(player);
                return;
            }
            case 46 -> adjustBundle(player, plot, stall, selectedSlot, -8);
            case 47 -> adjustBundle(player, plot, stall, selectedSlot, -1);
            case 48 -> adjustBundle(player, plot, stall, selectedSlot, 1);
            case 49 -> adjustBundle(player, plot, stall, selectedSlot, 8);
            case 50 -> {
                openManage(player, plot, stall, selectedSlot);
                return;
            }
            case 53 -> {
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                return;
            }
            default -> { return; }
        }

        openManage(player, plot, stall, selectedSlot);
    }

    private void toggleCurrency(Player player, Plot plot, MarketStall stall, int chestSlot) {
        if (!ensureEditableSlot(player, plot, stall, chestSlot)) return;
        plugin.tradeStalls().ensureListingDefaults(stall, chestSlot);

        MarketStall.StallListing listing = stall.getListing(chestSlot);
        CurrencyType next = listing.getCurrency() == CurrencyType.VAULT ? CurrencyType.CLAIM_BLOCKS : CurrencyType.VAULT;
        if (!plugin.tradeStalls().isCurrencyAllowed(next)) {
            if (plugin.tradeStalls().isCurrencyAllowed(listing.getCurrency())) {
                send(player, "market_stall_currency_unavailable", "&cThat currency is unavailable right now.");
                return;
            }
            next = plugin.tradeStalls().getDefaultCurrency();
        }
        if (!plugin.tradeStalls().isCurrencyAllowed(next)) {
            send(player, "market_stall_currency_unavailable", "&cThat currency is unavailable right now.");
            return;
        }

        listing.setCurrency(next);
        plugin.store().savePlot(plot);
        plugin.tradeStalls().refreshSign(stall);
        send(player, "market_stall_listing_updated", "&aTradeStall listing updated.");
    }

    private void adjustPrice(Player player, Plot plot, MarketStall stall, int chestSlot, double delta) {
        if (!ensureEditableSlot(player, plot, stall, chestSlot)) return;
        plugin.tradeStalls().ensureListingDefaults(stall, chestSlot);

        MarketStall.StallListing listing = stall.getListing(chestSlot);
        listing.setPrice(Math.max(1.0D, listing.getPrice() + delta));
        plugin.store().savePlot(plot);
        plugin.tradeStalls().refreshSign(stall);
        send(player, "market_stall_listing_updated", "&aTradeStall listing updated.");
    }

    private void adjustBundle(Player player, Plot plot, MarketStall stall, int chestSlot, int delta) {
        if (!ensureEditableSlot(player, plot, stall, chestSlot)) return;
        plugin.tradeStalls().ensureListingDefaults(stall, chestSlot);

        Inventory inventory = plugin.tradeStalls().resolveInventory(stall);
        ItemStack item = inventory == null || chestSlot >= inventory.getSize() ? null : inventory.getItem(chestSlot);
        int itemMax = item == null || item.getType().isAir() ? plugin.tradeStalls().getMaxBundleAmount() : item.getMaxStackSize();

        MarketStall.StallListing listing = stall.getListing(chestSlot);
        int next = Math.max(1, Math.min(Math.min(plugin.tradeStalls().getMaxBundleAmount(), itemMax), listing.getBundleAmount() + delta));
        listing.setBundleAmount(next);
        plugin.store().savePlot(plot);
        plugin.tradeStalls().refreshSign(stall);
        send(player, "market_stall_listing_updated", "&aTradeStall listing updated.");
    }

    private void clearListing(Player player, Plot plot, MarketStall stall, int chestSlot) {
        if (!ensureEditableSlot(player, plot, stall, chestSlot)) return;
        stall.removeListing(chestSlot);
        plugin.store().savePlot(plot);
        plugin.tradeStalls().refreshSign(stall);
        send(player, "market_stall_listing_cleared", "&eTradeStall listing cleared.");
    }

    private boolean ensureEditableSlot(Player player, Plot plot, MarketStall stall, int chestSlot) {
        if (!stall.canStock(player, plot, plugin)) {
            send(player, "no_perm", "&cYou cannot manage this TradeStall.");
            return false;
        }
        Inventory inventory = plugin.tradeStalls().resolveInventory(stall);
        if (inventory == null || chestSlot < 0 || chestSlot >= inventory.getSize()) {
            send(player, "market_stall_missing", "&cThat TradeStall is no longer available.");
            return false;
        }
        ItemStack item = inventory.getItem(chestSlot);
        if (item == null || item.getType().isAir()) {
            send(player, "market_stall_select_item_first", "&cSelect a stocked slot before editing its listing.");
            return false;
        }
        return true;
    }

    private List<String> buildStallInfoLore(Player player, MarketStall stall, int listingCount) {
        List<String> lore = new ArrayList<>();
        lore.add(tr(player, "market_stall_owner_line", "&7Seller: &f{OWNER}").replace("{OWNER}", stall.getOwnerName()));
        if (stall.getZoneName() != null && !stall.getZoneName().isBlank()) {
            lore.add(tr(player, "market_stall_zone_line", "&7Zone: &f{ZONE}").replace("{ZONE}", stall.getZoneName()));
        }
        lore.add(tr(player, "market_stall_item_count_line", "&7Listed slots: &f{COUNT}").replace("{COUNT}", String.valueOf(listingCount)));
        return lore;
    }

    private List<String> buildSelectedLore(Player player, MarketStall stall, int selectedSlot, Inventory source) {
        List<String> lore = new ArrayList<>();
        lore.add(tr(player, "market_stall_selected_slot", "&7Chest slot: &f{SLOT}")
                .replace("{SLOT}", String.valueOf(selectedSlot + 1)));

        ItemStack item = source == null || selectedSlot >= source.getSize() ? null : source.getItem(selectedSlot);
        if (item == null || item.getType().isAir()) {
            lore.add(tr(player, "market_stall_select_item_first", "&cSelect a stocked slot before editing its listing."));
            return lore;
        }

        MarketStall.StallListing listing = stall.getListing(selectedSlot);
        if (listing != null && listing.isValid()) {
            lore.add(tr(player, "market_stall_price_line", "&7Price: &6{PRICE}").replace("{PRICE}", formatPrice(listing)));
            lore.add(tr(player, "market_stall_bundle_line", "&7Bundle: &f{COUNT}").replace("{COUNT}", String.valueOf(listing.getBundleAmount())));
        } else {
            lore.add(tr(player, "market_stall_not_listed", "&7This slot is not listed yet."));
        }
        lore.add(tr(player, "market_stall_manage_hint", "&7Use the buttons around this panel to edit the listing."));
        return lore;
    }

    private List<String> buildCurrencyLore(Player player, MarketStall stall, int selectedSlot) {
        MarketStall.StallListing listing = stall.getListing(selectedSlot);
        CurrencyType current = listing == null ? plugin.tradeStalls().getDefaultCurrency() : listing.getCurrency();

        List<String> lore = new ArrayList<>();
        lore.add(tr(player, "market_stall_currency_current", "&7Current: &f{CURRENCY}")
                .replace("{CURRENCY}", currencyName(player, current)));
        lore.add(" ");
        lore.add(tr(player, "market_stall_currency_toggle", "&eClick to switch currency."));
        return lore;
    }

    private List<MarketStall> getBrowsableStalls(Plot plot, Player player) {
        List<MarketStall> stalls = new ArrayList<>();
        if (plot == null) return stalls;

        for (MarketStall stall : plot.getStalls()) {
            if (stall == null) continue;
            if (plugin.tradeStalls().resolveContainerBlock(stall) == null) continue;
            if (!stall.isActive(plot) && !stall.canStock(player, plot, plugin)) continue;
            stalls.add(stall);
        }
        return stalls;
    }

    private int countVisibleListings(MarketStall stall) {
        Inventory inventory = plugin.tradeStalls().resolveInventory(stall);
        if (inventory == null) return 0;

        int count = 0;
        for (int slot = 0; slot < Math.min(27, inventory.getSize()); slot++) {
            ItemStack item = inventory.getItem(slot);
            MarketStall.StallListing listing = stall.getListing(slot);
            if (item != null && !item.getType().isAir() && listing != null && listing.isValid()) {
                count++;
            }
        }
        return count;
    }

    private int firstSelectableSlot(MarketStall stall) {
        Inventory inventory = plugin.tradeStalls().resolveInventory(stall);
        if (inventory == null) return 0;
        for (int slot = 0; slot < Math.min(27, inventory.getSize()); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) return slot;
        }
        return 0;
    }

    private int guiSlotForChestSlot(int chestSlot) {
        return CONTENT_START + chestSlot;
    }

    private int chestSlotForGuiSlot(int guiSlot) {
        if (guiSlot < CONTENT_START || guiSlot > CONTENT_END) return -1;
        return guiSlot - CONTENT_START;
    }

    private void fillBottom(Inventory inv, ItemStack filler) {
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);
    }

    private void fillStatic(Inventory inv, ItemStack filler) {
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, filler);
        }
        for (int i = 45; i < 54; i++) {
            if (i != 50) inv.setItem(i, filler);
        }
    }

    private void setNav(Inventory inv, Player player) {
        inv.setItem(45, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"), trList(player, "back_lore", List.of("&7Return to the previous page."))));
        inv.setItem(49, GUIManager.createItem(Material.COMPASS, tr(player, "button_refresh", "&bRefresh"), trList(player, "refresh_lore", List.of("&7Reload this menu."))));
        inv.setItem(53, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"), trList(player, "exit_lore", List.of("&7Close this menu."))));
    }

    private void setPreviewNav(Inventory inv, Player player) {
        inv.setItem(45, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"), trList(player, "back_lore", List.of("&7Return to the previous page."))));
        inv.setItem(49, GUIManager.createItem(Material.COMPASS, tr(player, "button_refresh", "&bRefresh"), trList(player, "refresh_lore", List.of("&7Reload this menu."))));
        inv.setItem(53, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"), trList(player, "exit_lore", List.of("&7Close this menu."))));
    }

    private void visitStall(Player player, MarketStall stall) {
        if (plugin.tradeStalls() == null || plugin.safeTravel() == null) {
            plugin.effects().playError(player);
            send(player, "market_stall_visit_failed", "&cCould not travel to that TradeStall.");
            return;
        }
        var dest = plugin.tradeStalls().visitLocation(stall);
        if (dest == null) {
            plugin.effects().playError(player);
            send(player, "market_stall_visit_failed", "&cCould not travel to that TradeStall.");
            return;
        }
        var result = plugin.safeTravel().travel(player, dest, com.aegisguard.travel.SafeTravelService.Kind.STALL);
        if (!result.isSuccess()) return;
        player.closeInventory();
        plugin.effects().playTeleport(player);
        send(player, "market_stall_visit_arrived", "&aArrived at the TradeStall.");
    }

    private Material resolveContainerMaterial(MarketStall stall) {
        Block block = plugin.tradeStalls().resolveContainerBlock(stall);
        return block == null ? Material.CHEST : block.getType();
    }

    private String formatPrice(MarketStall.StallListing listing) {
        if (listing == null) return "?";
        if (plugin.eco() == null) {
            return switch (listing.getCurrency()) {
                case CLAIM_BLOCKS -> Math.round(listing.getPrice()) + " Claim Blocks";
                case VAULT -> String.format(Locale.US, "%.2f", listing.getPrice());
                default -> String.valueOf(listing.getPrice());
            };
        }
        return plugin.eco().format(listing.getPrice(), listing.getCurrency());
    }

    private String currencyName(Player player, CurrencyType currency) {
        return switch (currency) {
            case CLAIM_BLOCKS -> tr(player, "market_stall_currency_claim_blocks", "&bClaim Blocks");
            case VAULT -> tr(player, "market_stall_currency_money", "&6Money");
            default -> currency.name();
        };
    }

    private String shortenTitle(String title) {
        if (title == null || title.isBlank()) return "TradeStall";
        return title.length() <= 16 ? title : title.substring(0, 16);
    }

    private String keyForResult(TradeStallService.ResultType type) {
        return switch (type) {
            case NOT_LISTED -> "market_stall_not_listed_error";
            case OUT_OF_STOCK -> "market_stall_out_of_stock";
            case INSUFFICIENT_FUNDS -> "market_stall_insufficient_funds";
            case CURRENCY_UNAVAILABLE -> "market_stall_currency_unavailable";
            case STALL_INACTIVE -> "market_stall_inactive";
            case DISABLED -> "market_stall_disabled";
            case BUSY -> "market_stall_purchase_busy";
            default -> "market_stall_generic_error";
        };
    }

    private String tr(Player player, String key, String fallback) {
        return plugin.gui().tr(player, key, fallback);
    }

    private List<String> trList(Player player, String key, List<String> fallback) {
        return plugin.gui().trList(player, key, fallback);
    }

    private void send(Player player, String key, String fallback) {
        String resolved = tr(player, key, fallback);
        if (resolved == null || resolved.isBlank()) return;
        player.sendMessage(plugin.msg().prefix() + resolved);
    }

    private List<String> colorize(List<String> lore) {
        List<String> colored = new ArrayList<>();
        for (String line : lore) {
            colored.add(GUIManager.color(line == null ? "" : line));
        }
        return colored;
    }
}
