package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.util.TeleportUtil; // ✅ Folia-safe teleports
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * PlotAuctionGUI
 * - Allows players to bid on expired plots.
 * - Fully localized.
 *
 * ✅ Title fix: translate & colors + safe fallback + clamp length (including page suffix)
 */
public class PlotAuctionGUI {

    private final AegisGuard plugin;
    private final int PLOTS_PER_PAGE = 45;

    public PlotAuctionGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlotAuctionHolder implements InventoryHolder {
        private final int page;
        private final List<Plot> plots;

        public PlotAuctionHolder(List<Plot> plots, int page) {
            this.plots = plots;
            this.page = page;
        }

        public int getPage() { return page; }
        public List<Plot> getPlots() { return plots; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, int page) {
        List<Plot> allPlots = new ArrayList<>(plugin.store().getPlotsForAuction());
        allPlots.sort(Comparator.comparingDouble(Plot::getCurrentBid));

        int maxPages = (int) Math.ceil((double) allPlots.size() / PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;
        if (maxPages == 0) page = 0;

        // ✅ Base title uses centralized formatter (colors + fallback + clamp)
        String baseTitle = plugin.gui().title(
                player,
                "auction_gui_title",
                "&6🏷 Plot Auctions"
        );

        // ✅ Add page suffix, then clamp again (page suffix can push over limits)
        String fullTitle = clampTitle(baseTitle + " &8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")");
        Inventory inv = Bukkit.createInventory(new PlotAuctionHolder(allPlots, page), 54, fullTitle);

        // Background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Listings
        int startIndex = page * PLOTS_PER_PAGE;
        for (int i = 0; i < PLOTS_PER_PAGE; i++) {
            int plotIndex = startIndex + i;
            if (plotIndex >= allPlots.size()) break;

            Plot plot = allPlots.get(plotIndex);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());
            OfflinePlayer currentBidder = plot.getCurrentBidder() != null
                    ? Bukkit.getOfflinePlayer(plot.getCurrentBidder())
                    : null;

            String bidderName = currentBidder != null
                    ? (currentBidder.getName() != null ? currentBidder.getName() : "Unknown")
                    : "None";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                try { meta.setOwningPlayer(owner); } catch (Throwable ignored) {}

                // Localized Item Name (✅ now colorized)
                String itemName = plugin.msg().get(player, "auction_item_name", Map.of("OWNER", plot.getOwnerName()));
                if (itemName == null || itemName.isBlank()) itemName = "&ePlot Auction (&f" + plot.getOwnerName() + "&e)";
                meta.setDisplayName(color(itemName));

                // Localized Lore (✅ now colorized)
                List<String> lore = new ArrayList<>();
                lore.add(color("&7World: &f" + plot.getWorld()));
                lore.add(color("&7Size: &a" + (plot.getX2() - plot.getX1() + 1) + "x" + (plot.getZ2() - plot.getZ1() + 1)));
                lore.add(" ");

                String bidStr = plugin.eco().format(plot.getCurrentBid(), CurrencyType.VAULT);

                String bidLine = plugin.msg().get(player, "auction_current_bid", Map.of("AMOUNT", bidStr));
                String bidderLine = plugin.msg().get(player, "auction_highest_bidder", Map.of("PLAYER", bidderName));

                lore.add(color(bidLine != null ? bidLine : "&7Current Bid: &e" + bidStr));
                lore.add(color(bidderLine != null ? bidderLine : "&7Highest Bidder: &f" + bidderName));
                lore.add(" ");

                List<String> hints = plugin.msg().getList(player, "auction_item_lore");
                if (hints != null) {
                    for (String s : hints) lore.add(color(s));
                }

                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        // Navigation (GUIManager.createItem already translates & in name/lore)
        if (page > 0) {
            inv.setItem(45, GUIManager.createItem(
                    Material.ARROW,
                    plugin.msg().get(player, "button_prev_page"),
                    null
            ));
        }

        inv.setItem(48, GUIManager.createItem(
                Material.NETHER_STAR,
                plugin.msg().get(player, "button_back_menu"),
                plugin.msg().getList(player, "back_menu_lore")
        ));

        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(
                    Material.ARROW,
                    plugin.msg().get(player, "button_next_page"),
                    null
            ));
        }

        inv.setItem(49, GUIManager.createItem(
                Material.BARRIER,
                plugin.msg().get(player, "button_exit"),
                plugin.msg().getList(player, "exit_lore")
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotAuctionHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        int currentPage = holder.getPage();

        // Nav
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) { open(player, currentPage - 1); return; }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) { open(player, currentPage + 1); return; }
        if (slot == 48) { plugin.gui().openMain(player); return; }
        if (slot == 49) { player.closeInventory(); return; }

        // Listing Click
        if (slot < PLOTS_PER_PAGE && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            int index = (currentPage * PLOTS_PER_PAGE) + slot;
            if (index >= holder.getPlots().size()) return;

            Plot plot = holder.getPlots().get(index);
            if (plot == null) return;

            if (e.getClick().isLeftClick()) {
                Location loc = plot.getCenter(plugin);
                if (loc != null) {
                    TeleportUtil.safeTeleport(plugin, player, loc);
                    player.closeInventory();
                    plugin.msg().send(player, "market-teleport", Map.of("PLAYER", plot.getOwnerName()));
                    plugin.effects().playConfirm(player);
                }
            } else if (e.getClick().isRightClick()) {
                bidOnPlot(player, plot);
                open(player, currentPage);
            }
        }
    }

    private void bidOnPlot(Player bidder, Plot plot) {
        if (plot.getOwner().equals(bidder.getUniqueId())) {
            plugin.msg().send(bidder, "auction-bid-own");
            plugin.effects().playError(bidder);
            return;
        }

        if (bidder.getUniqueId().equals(plot.getCurrentBidder())) {
            plugin.msg().send(bidder, "auction-already-highest");
            plugin.effects().playError(bidder);
            return;
        }

        double currentBid = plot.getCurrentBid();
        double minIncrease = plugin.cfg().raw().getDouble("auction.min_bid_increase", 100.0);
        double newBid = (currentBid == 0) ? minIncrease : currentBid + minIncrease;

        if (!plugin.eco().withdraw(bidder, newBid, CurrencyType.VAULT)) {
            plugin.msg().send(bidder, "need_vault", Map.of("AMOUNT", plugin.eco().format(newBid, CurrencyType.VAULT)));
            plugin.effects().playError(bidder);
            return;
        }

        if (plot.getCurrentBidder() != null) {
            OfflinePlayer oldBidder = Bukkit.getOfflinePlayer(plot.getCurrentBidder());
            if (oldBidder.getPlayer() != null) {
                plugin.eco().deposit(oldBidder.getPlayer(), currentBid, CurrencyType.VAULT);
                if (oldBidder.isOnline()) {
                    plugin.msg().send(oldBidder.getPlayer(), "auction-outbid", Map.of("PLAYER", bidder.getName()));
                }
            }
        }

        plot.setCurrentBid(newBid, bidder.getUniqueId());
        plugin.store().setDirty(true);

        plugin.msg().send(bidder, "auction-bid-success", Map.of("AMOUNT", plugin.eco().format(newBid, CurrencyType.VAULT)));
        plugin.effects().playConfirm(bidder);
    }

    // --------------------------------------------------
    // Helpers
    // --------------------------------------------------

    private String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String clampTitle(String raw) {
        String t = GUIManager.safeText(raw, "&6🏷 Plot Auctions");
        t = ChatColor.translateAlternateColorCodes('&', t);

        // Vanilla inventory title safety clamp
        if (t.length() > 32) t = t.substring(0, 32);
        if (t.endsWith("§")) t = t.substring(0, t.length() - 1);

        return t;
    }
}
