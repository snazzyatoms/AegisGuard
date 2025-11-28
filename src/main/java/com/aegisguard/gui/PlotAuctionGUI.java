package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
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
 * - Handles refunds for outbid players.
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
        // Sort by bid (Ascending)
        allPlots.sort(Comparator.comparingDouble(Plot::getCurrentBid));

        int maxPages = (int) Math.ceil((double) allPlots.size() / PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;

        String title = GUIManager.safeText(plugin.msg().get(player, "auction_gui_title"), "§6Plot Auctions")
                + " §8(Page " + (page + 1) + "/" + Math.max(1, maxPages) + ")";

        Inventory inv = Bukkit.createInventory(new PlotAuctionHolder(allPlots, page), 54, title);

        // Fill background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // Add plot heads
        int startIndex = page * PLOTS_PER_PAGE;
        for (int i = 0; i < PLOTS_PER_PAGE; i++) {
            int plotIndex = startIndex + i;
            if (plotIndex >= allPlots.size()) break;

            Plot plot = allPlots.get(plotIndex);
            OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());
            OfflinePlayer currentBidder = plot.getCurrentBidder() != null ? Bukkit.getOfflinePlayer(plot.getCurrentBidder()) : null;
            String bidderName = currentBidder != null ? (currentBidder.getName() != null ? currentBidder.getName() : "Unknown") : "None";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(owner);
                meta.setDisplayName("§ePlot Auction (Formerly " + plot.getOwnerName() + "'s)");
                
                List<String> lore = new ArrayList<>();
                lore.add("§7World: §f" + plot.getWorld());
                lore.add("§7Size: §a" + (plot.getX2() - plot.getX1() + 1) + "x" + (plot.getZ2() - plot.getZ1() + 1));
                lore.add(" ");
                lore.add("§7Current Bid: §e" + plugin.eco().format(plot.getCurrentBid(), CurrencyType.VAULT));
                lore.add("§7Highest Bidder: §f" + bidderName);
                lore.add(" ");
                lore.add("§eLeft-Click: §7Teleport to preview");
                lore.add("§aRight-Click: §7Bid on this plot");
                
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, GUIManager.createItem(Material.ARROW, "§aPrevious Page", List.of("§7Go to page " + page)));
        }
        
        inv.setItem(48, GUIManager.createItem(Material.NETHER_STAR, "§fBack to Menu", List.of("§7Return to the main menu.")));

        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(Material.ARROW, "§aNext Page", List.of("§7Go to page " + (page + 2))));
        }

        inv.setItem(49, GUIManager.createItem(Material.BARRIER, "§cClose", null));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotAuctionHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        int currentPage = holder.getPage();

        // Navigation
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) {
            open(player, currentPage - 1);
            return;
        }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) {
            open(player, currentPage + 1);
            return;
        }
        if (slot == 48 && e.getCurrentItem().getType() == Material.NETHER_STAR) {
            plugin.gui().openMain(player);
            return;
        }
        if (slot == 49 && e.getCurrentItem().getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // Listings
        if (slot < PLOTS_PER_PAGE && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            int plotIndex = (currentPage * PLOTS_PER_PAGE) + slot;
            if (plotIndex >= holder.getPlots().size()) return;

            Plot plot = holder.getPlots().get(plotIndex);
            if (plot == null) return;

            if (e.getClick().isLeftClick()) {
                Location loc = plot.getCenter(plugin);
                if (loc != null) {
                    player.teleport(loc);
                    player.closeInventory();
                    plugin.msg().send(player, "market-teleport", Map.of("PLAYER", plot.getOwnerName()));
                    plugin.effects().playConfirm(player);
                }
            } else if (e.getClick().isRightClick()) {
                bidOnPlot(player, plot);
                open(player, currentPage); // Refresh
            }
        }
    }

    private void bidOnPlot(Player bidder, Plot plot) {
        // 1. Validation
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
        
        // 2. Calculate New Bid
        double currentBid = plot.getCurrentBid();
        double minIncrease = plugin.cfg().raw().getDouble("auction.min_bid_increase", 100.0);
        double newBid = (currentBid == 0) ? minIncrease : currentBid + minIncrease;

        // 3. Charge Player
        if (!plugin.eco().withdraw(bidder, newBid, CurrencyType.VAULT)) {
            plugin.msg().send(bidder, "need_vault", Map.of("AMOUNT", plugin.eco().format(newBid, CurrencyType.VAULT)));
            plugin.effects().playError(bidder);
            return;
        }
        
        // 4. Refund Old Bidder
        if (plot.getCurrentBidder() != null) {
            OfflinePlayer oldBidder = Bukkit.getOfflinePlayer(plot.getCurrentBidder());
            plugin.eco().deposit(oldBidder.getPlayer(), currentBid, CurrencyType.VAULT); // Handles offline via Vault hook internally usually
            
            if (oldBidder.isOnline()) {
                plugin.msg().send(oldBidder.getPlayer(), "auction-outbid", Map.of("PLAYER", bidder.getName()));
            }
        }

        // 5. Update Plot
        plot.setCurrentBid(newBid, bidder.getUniqueId());
        plugin.store().setDirty(true);

        // 6. Notify
        plugin.msg().send(bidder, "auction-bid-success", Map.of("AMOUNT", plugin.eco().format(newBid, CurrencyType.VAULT)));
        plugin.effects().playConfirm(bidder);
    }
}
