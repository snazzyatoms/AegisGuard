package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PlotAuctionGUI
 * - A paginated GUI for players to browse and bid on expired plots.
 */
public class PlotAuctionGUI {

    private final AegisGuard plugin;
    private final int PLOTS_PER_PAGE = 45; // 5 rows * 9 slots

    public PlotAuctionGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Reliable holder that stores the plot list and current page.
     * Must be PUBLIC STATIC for GUIListener.
     */
    public static class PlotAuctionHolder implements InventoryHolder {
        private final int page;
        private final List<Plot> plots;

        public PlotAuctionHolder(List<Plot> plots, int page) {
            this.plots = plots;
            this.page = page;
        }

        public int getPage() { return page; }
        public List<Plot> getPlots() { return plots; }
        public int getMaxPages() { return (int) Math.ceil((double) plots.size() / 45.0); }

        @Override
        public Inventory getInventory() { return null; }
    }

    /* -----------------------------
     * Open GUI
     * ----------------------------- */
    public void open(Player player, int page) {
        // Get a fresh list of all plots FOR AUCTION
        List<Plot> allPlots = new ArrayList<>(plugin.store().getPlotsForAuction());
        allPlots.sort((p1, p2) -> Double.compare(p1.getCurrentBid(), p2.getCurrentBid())); // Sort by bid

        int maxPages = (int) Math.ceil((double) allPlots.size() / (double) PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;

        String title = GUIManager.safeText(plugin.msg().get(player, "auction_gui_title"), "§6Plot Auctions")
                + " §8(Page " + (page + 1) + "/" + Math.max(1, maxPages) + ")";

        Inventory inv = Bukkit.createInventory(new PlotAuctionHolder(allPlots, page), 54, title);

        // Fill background
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, GUIManager.icon(Material.BLACK_STAINED_GLASS_PANE, " ", null));
        }

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
                meta.setLore(List.of(
                        "§7World: §f" + plot.getWorld(),
                        "§7Size: §a" + (plot.getX2() - plot.getX1() + 1) + "x" + (plot.getZ2() - plot.getZ1() + 1),
                        " ",
                        "§7Current Bid: §e" + plugin.vault().format(plot.getCurrentBid()),
                        "§7Highest Bidder: §f" + bidderName,
                        " ",
                        "§eLeft-Click: §7Teleport to preview",
                        "§aRight-Click: §7Bid on this plot"
                ));
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, GUIManager.icon(Material.ARROW, "§aPrevious Page", List.of("§7Go to page " + page)));
        }
        
        // --- FIX: Back Button (Slot 48) ---
        inv.setItem(48, GUIManager.icon(Material.NETHER_STAR, "§fBack to Menu", List.of("§7Return to the main AegisGuard menu.")));

        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.icon(Material.ARROW, "§aNext Page", List.of("§7Go to page " + (page + 2))));
        }

        inv.setItem(49, GUIManager.icon(Material.BARRIER, "§cClose", null));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    /* -----------------------------
     * Handle Clicks
     * ----------------------------- */
    public void handleClick(Player player, InventoryClickEvent e, PlotAuctionHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        int currentPage = holder.getPage();

        // Handle navigation
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) { // Previous Page
            open(player, currentPage - 1);
            plugin.effects().playMenuFlip(player);
            return;
        }
        
        // --- FIX: Handle Back to Menu ---
        if (slot == 48 && e.getCurrentItem().getType() == Material.NETHER_STAR) {
            plugin.gui().openMain(player);
            plugin.effects().playMenuFlip(player);
            return;
        }

        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) { // Next Page
            open(player, currentPage + 1);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == 49 && e.getCurrentItem().getType() == Material.BARRIER) { // Close
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }

        // Handle plot clicks
        if (slot < PLOTS_PER_PAGE && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            int plotIndex = (currentPage * PLOTS_PER_PAGE) + slot;
            if (plotIndex >= holder.getPlots().size()) return;

            Plot plot = holder.getPlots().get(plotIndex);
            if (plot == null) return;

            if (e.getClick().isLeftClick()) {
                // Teleport to preview
                Location loc = plot.getCenter(plugin);
                if (loc != null) {
                    player.teleport(loc);
                    player.closeInventory();
                    plugin.msg().send(player, "market-teleport", Map.of("PLAYER", plot.getOwnerName()));
                    plugin.effects().playConfirm(player);
                }
            } else if (e.getClick().isRightClick()) {
                // Bid on plot
                bidOnPlot(player, plot);
                open(player, currentPage); // Refresh GUI
            }
        }
    }

    private void bidOnPlot(Player bidder, Plot plot) {
        // 1. Check if they are bidding on their own plot (or an expired one they owned)
        if (plot.getOwner().equals(bidder.getUniqueId())) {
            plugin.msg().send(bidder, "auction-bid-own");
            plugin.effects().playError(bidder);
            return;
        }

        // 2. Check if they are already the highest bidder
        if (bidder.getUniqueId().equals(plot.getCurrentBidder())) {
            plugin.msg().send(bidder, "auction-already-highest");
            plugin.effects().playError(bidder);
            return;
        }
        
        // 3. Calculate new bid
        double currentBid = plot.getCurrentBid();
        double minIncrease = plugin.cfg().raw().getDouble("auction.min_bid_increase", 100.0);
        double newBid = (currentBid == 0) ? minIncrease : currentBid + minIncrease;

        // 4. Check balance
        if (!plugin.vault().charge(bidder, newBid)) {
            plugin.msg().send(bidder, "need_vault", Map.of("AMOUNT", plugin.vault().format(newBid)));
            plugin.effects().playError(bidder);
            return;
        }
        
        // 5. Refund old bidder
        if (plot.getCurrentBidder() != null) {
            OfflinePlayer oldBidder = Bukkit.getOfflinePlayer(plot.getCurrentBidder());
            plugin.vault().give(oldBidder, currentBid); // Give their money back
            if (oldBidder.isOnline()) {
                plugin.msg().send(oldBidder.getPlayer(), "auction-outbid", Map.of("PLAYER", bidder.getName()));
            }
        }

        // 6. Set new bidder
        plot.setCurrentBid(newBid, bidder.getUniqueId());
        plugin.store().setDirty(true); // Mark for save

        // 7. Notify bidder
        plugin.msg().send(bidder, "auction-bid-success", Map.of("AMOUNT", plugin.vault().format(newBid)));
        plugin.effects().playConfirm(bidder);
    }
}
