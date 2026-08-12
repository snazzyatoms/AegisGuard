package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.MarketStall;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.market.TradeStallService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Confirmation step before a TradeStall GUI purchase, mirroring {@link RentConfirmGUI}.
 */
public class StallPurchaseConfirmGUI {

    private final AegisGuard plugin;

    public StallPurchaseConfirmGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static final class StallBuyConfirmHolder implements InventoryHolder {
        private final Plot plot;
        private final String stallKey;
        private final int chestSlot;

        public StallBuyConfirmHolder(Plot plot, String stallKey, int chestSlot) {
            this.plot = plot;
            this.stallKey = stallKey;
            this.chestSlot = chestSlot;
        }

        public Plot getPlot() { return plot; }
        public String getStallKey() { return stallKey; }
        public int getChestSlot() { return chestSlot; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot, MarketStall stall, int chestSlot) {
        if (player == null || plot == null || stall == null) return;

        MarketStall.StallListing listing = stall.getListing(chestSlot);
        Inventory source = plugin.tradeStalls() == null ? null : plugin.tradeStalls().resolveInventory(stall);
        ItemStack stock = source == null || chestSlot < 0 || chestSlot >= source.getSize()
                ? null : source.getItem(chestSlot);

        Inventory inv = Bukkit.createInventory(
                new StallBuyConfirmHolder(plot, stall.getStorageKey(), chestSlot),
                27,
                plugin.gui().title(player, "stall_buy_confirm_title", "&6Confirm Purchase")
        );

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        String itemName = stock == null || stock.getType().isAir()
                ? tr(player, "market_stall_preview_empty_name", "&cThis stall is empty")
                : prettyItemName(stock);
        String price = listing == null ? "?" : formatPrice(listing);
        int bundle = listing == null ? 1 : listing.getBundleAmount();

        List<String> lore = new ArrayList<>();
        lore.add(GUIManager.color(tr(player, "stall_buy_confirm_item_line", "&7Item: &f{ITEM}")
                .replace("{ITEM}", itemName)));
        lore.add(GUIManager.color(tr(player, "stall_buy_confirm_bundle_line", "&7Bundle: &f{COUNT}")
                .replace("{COUNT}", String.valueOf(bundle))));
        lore.add(GUIManager.color(tr(player, "stall_buy_confirm_price_line", "&7Price: &6{PRICE}")
                .replace("{PRICE}", price)));
        lore.add(GUIManager.color(tr(player, "stall_buy_confirm_seller_line", "&7Seller: &f{OWNER}")
                .replace("{OWNER}", stall.getOwnerName())));
        lore.add(" ");
        lore.add(GUIManager.color(tr(player, "stall_buy_confirm_click_lore", "&aClick to confirm and pay")));

        Material icon = stock == null || stock.getType().isAir() ? Material.EMERALD_BLOCK : stock.getType();
        inv.setItem(13, GUIManager.createItem(
                icon,
                tr(player, "stall_buy_confirm_name", "&aConfirm Purchase"),
                lore
        ));

        inv.setItem(11, GUIManager.createItem(
                Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return without paying."))
        ));
        inv.setItem(15, GUIManager.createItem(
                Material.BARRIER,
                tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, StallBuyConfirmHolder holder) {
        if (player == null || holder == null) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        int slot = e.getRawSlot();
        Plot plot = holder.getPlot();
        MarketStall stall = plot == null ? null : plot.getStallByKey(holder.getStallKey());

        if (slot == 11) {
            if (plot != null && stall != null) {
                plugin.gui().stallBrowse().openPreview(player, plot, stall);
            } else if (plot != null) {
                plugin.gui().stallBrowse().openList(player, plot);
            } else {
                plugin.gui().openMain(player);
            }
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == 15) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }
        if (slot != 13) return;

        if (plot == null || stall == null || plugin.tradeStalls() == null) {
            plugin.effects().playError(player);
            send(player, "market_stall_missing", "&cThat TradeStall is no longer available.");
            player.closeInventory();
            return;
        }

        TradeStallService.Result result = plugin.tradeStalls().purchase(player, plot, stall, holder.getChestSlot());
        if (result.ok()) {
            plugin.effects().playConfirm(player);
            send(player, "market_stall_purchase_success", "&aPurchase complete.");
            plugin.gui().stallBrowse().openPreview(player, plot, stall);
        } else {
            plugin.effects().playError(player);
            send(player, keyForResult(result.type()), result.message());
            plugin.gui().stallBrowse().openPreview(player, plot, stall);
        }
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

    private String formatPrice(MarketStall.StallListing listing) {
        if (listing == null) return "?";
        if (plugin.eco() == null) {
            return switch (listing.getCurrency()) {
                case CLAIM_BLOCKS -> Math.round(listing.getPrice()) + " Claim Blocks";
                case VAULT -> String.format(Locale.US, "%.2f", listing.getPrice());
                default -> String.valueOf(listing.getPrice());
            };
        }
        return plugin.eco().format(listing.getPrice(), listing.getCurrency() == null
                ? CurrencyType.VAULT : listing.getCurrency());
    }

    private String prettyItemName(ItemStack item) {
        if (item == null) return "?";
        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String raw = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (raw.isEmpty()) return item.getType().name();
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
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
}
