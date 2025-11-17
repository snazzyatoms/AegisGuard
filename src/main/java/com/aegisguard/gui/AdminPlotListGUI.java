package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotStore;
import com.aegisguard.data.PlotStore.Plot;
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
 * AdminPlotListGUI
 * - A paginated GUI for admins to view all plots on the server.
 * - Allows admins to teleport to or delete any plot.
 * - This is feature #4 from the next-steps list.
 */
public class AdminPlotListGUI {

    private final AegisGuard plugin;
    private final int PLOTS_PER_PAGE = 45; // 5 rows * 9 slots

    public AdminPlotListGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Reliable holder that stores the plot list and current page.
     */
    public static class PlotListHolder implements InventoryHolder {
        private final int page;
        private final List<Plot> plots;

        public PlotListHolder(List<Plot> plots, int page) {
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
        // Get a fresh list of all plots and sort them by owner name
        List<Plot> allPlots = new ArrayList<>(plugin.store().getAllPlots());
        allPlots.sort((p1, p2) -> p1.getOwnerName().compareToIgnoreCase(p2.getOwnerName()));

        int maxPages = (int) Math.ceil((double) allPlots.size() / (double) PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (page >= maxPages) page = maxPages - 1;

        String title = GUIManager.safeText(plugin.msg().get(player, "admin_plot_list_title"), "§cAll Plots")
                + " §8(Page " + (page + 1) + "/" + Math.max(1, maxPages) + ")";

        Inventory inv = Bukkit.createInventory(new PlotListHolder(allPlots, page), 54, title);

        // Fill background
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null));
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
                meta.setDisplayName("§bPlot by: §f" + plot.getOwnerName());
                meta.setLore(List.of(
                        "§7ID: §e" + plot.getPlotId().toString().substring(0, 8),
                        "§7World: §f" + plot.getWorld(),
                        "§7Bounds: §a(" + plot.getX1() + ", " + plot.getZ1() + ")",
                        "§7      to §a(" + plot.getX2() + ", " + plot.getZ2() + ")",
                        " ",
                        "§eLeft-Click: §7Teleport to plot",
                        "§cRight-Click: §7Delete plot"
                ));
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, GUIManager.icon(Material.ARROW, "§aPrevious Page", List.of("§7Go to page " + page)));
        }
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
    public void handleClick(Player player, InventoryClickEvent e, PlotListHolder holder) {
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
                // Teleport
                Location loc = new Location(
                        Bukkit.getWorld(plot.getWorld()),
                        (plot.getX1() + plot.getX2()) / 2.0,
                        player.getLocation().getY(), // Keep admin's Y level
                        (plot.getZ1() + plot.getZ2()) / 2.0
                );
                player.teleport(loc);
                player.closeInventory();
                plugin.msg().send(player, "admin_plot_teleport", Map.of("PLAYER", plot.getOwnerName()));
                plugin.effects().playConfirm(player);
            } else if (e.getClick().isRightClick()) {
                // Delete
                plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
                plugin.msg().send(player, "admin_plot_deleted", Map.of("PLAYER", plot.getOwnerName()));
                plugin.effects().playError(player);
                open(player, currentPage); // Refresh the GUI
            }
        }
    }
}
