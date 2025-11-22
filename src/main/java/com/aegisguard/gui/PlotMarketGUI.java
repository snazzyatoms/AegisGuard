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

/**
 * PlotMarketGUI
 * - A paginated GUI for players to browse and buy plots for sale.
 */
public class PlotMarketGUI {

    private final AegisGuard plugin;
    private final int PLOTS_PER_PAGE = 45; // 5 rows * 9 slots

    public PlotMarketGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Reliable holder that stores the plot list and current page.
     * Must be PUBLIC STATIC for GUIListener.
     */
    public static class PlotMarketHolder implements InventoryHolder {
        private final int page;
        private final List<Plot> plots;

        public PlotMarketHolder(List<Plot> plots, int page) {
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
        // Get a fresh list of all plots FOR SALE
        List<Plot> allPlots = new ArrayList<>(plugin.store().getPlotsForSale());
        allPlots.sort((p1, p2) -> Double.compare(p1.getSalePrice(), p2.getSalePrice())); // Sort by price, low to high

        int maxPages = (int) Math.ceil((double) allPlots.size() / (double) PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;

        String title = GUIManager.safeText(plugin.msg().get(player, "market_gui_title"), "§2Plot Marketplace")
                + " §8(Page " + (page + 1) + "/" + Math.max(1, maxPages) + ")";

        Inventory inv = Bukkit.createInventory(new PlotMarketHolder(allPlots, page), 54, title);

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

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(owner);
                meta.setDisplayName("§aPlot for Sale: §e" + plugin.vault().format(plot.getSalePrice()));
                meta.setLore(List.of(
                        "§7Sold by: §f" + plot.getOwnerName(),
                        "§7World: §f" + plot.getWorld(),
                        "§7Size: §a" + (plot.getX2() - plot.getX1() + 1) + "x" + (plot.getZ2() - plot.getZ1() + 1),
                        " ",
                        "§eLeft-Click: §7Teleport to preview",
                        "§aRight-Click: §7Buy this plot"
                ));
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, GUIManager.icon(Material.ARROW, "§aPrevious Page", List.of("§7Go to page " + page)));
        }
        
        // --- FIX: Add Back Button (Slot 48) ---
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
    public void handleClick(Player player, InventoryClickEvent e, PlotMarketHolder holder) {
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
                // Buy plot
                buyPlot(player, plot);
                player.closeInventory();
            }
        }
    }

    private void buyPlot(Player buyer, Plot plot) {
        // 1. Check if they are buying their own plot
        if (plot.getOwner().equals(buyer.getUniqueId())) {
            plugin.msg().send(buyer, "market-buy-own");
            plugin.effects().playError(buyer);
            return;
        }

        // 2. Check plot limits
        int maxPlots = plugin.cfg().getWorldMaxClaims(buyer.getWorld());
        int currentPlots = plugin.store().getPlots(buyer.getUniqueId()).size();
        if (currentPlots >= maxPlots && maxPlots > 0) {
            plugin.msg().send(buyer, "max_claims_reached", Map.of("AMOUNT", String.valueOf(maxPlots)));
            plugin.effects().playError(buyer);
            return;
        }

        // 3. Transaction
        double price = plot.getSalePrice();
        if (!plugin.vault().charge(buyer, price)) {
            plugin.msg().send(buyer, "need_vault", Map.of("AMOUNT", plugin.vault().format(price)));
            plugin.effects().playError(buyer);
            return;
        }
        
        // 4. Pay seller
        OfflinePlayer seller = Bukkit.getOfflinePlayer(plot.getOwner());
        plugin.vault().give(seller, price);

        // 5. Transfer ownership
        plugin.store().changePlotOwner(plot, buyer.getUniqueId(), buyer.getName());
        
        // 6. Notify players
        plugin.msg().send(buyer, "market-buy-success", Map.of("PRICE", plugin.vault().format(price), "PLAYER", seller.getName()));
        plugin.effects().playClaimSuccess(buyer);

        if (seller.isOnline()) {
            plugin.msg().send(seller.getPlayer(), "market-sold", Map.of("PRICE", plugin.vault().format(price), "PLAYER", buyer.getName()));
            plugin.effects().playConfirm(seller.getPlayer());
        }
    }
}
