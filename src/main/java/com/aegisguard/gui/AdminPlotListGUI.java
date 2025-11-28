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
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * AdminPlotListGUI
 * - A paginated GUI for admins to view and manage all plots.
 * - Supports teleporting and deletion.
 */
public class AdminPlotListGUI {

    private final AegisGuard plugin;
    private final int PLOTS_PER_PAGE = 45;

    public AdminPlotListGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlotListHolder implements InventoryHolder {
        private final int page;
        private final List<Plot> plots;

        public PlotListHolder(List<Plot> plots, int page) {
            this.plots = plots;
            this.page = page;
        }

        public int getPage() { return page; }
        public List<Plot> getPlots() { return plots; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, int page) {
        List<Plot> allPlots = new ArrayList<>(plugin.store().getAllPlots());
        // Sort by Owner Name A-Z
        allPlots.sort(Comparator.comparing(Plot::getOwnerName, String.CASE_INSENSITIVE_ORDER));

        int maxPages = (int) Math.ceil((double) allPlots.size() / PLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;

        String title = "§cGlobal Plot List §8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")";
        Inventory inv = Bukkit.createInventory(new PlotListHolder(allPlots, page), 54, title);

        // Fill background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

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
                meta.setDisplayName("§bOwner: §f" + plot.getOwnerName());
                
                List<String> lore = new ArrayList<>();
                lore.add("§7ID: §e" + plot.getPlotId().toString().substring(0, 8));
                lore.add("§7World: §f" + plot.getWorld());
                lore.add("§7Bounds: §a" + plot.getX1() + ", " + plot.getZ1());
                lore.add("§7       to §a" + plot.getX2() + ", " + plot.getZ2());
                if (plot.isServerZone()) lore.add("§c[SERVER ZONE]");
                lore.add(" ");
                lore.add("§eLeft-Click: §7Teleport");
                lore.add("§cRight-Click: §7Delete Plot");
                
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, GUIManager.createItem(Material.ARROW, "§aPrevious Page", null));
        }
        
        inv.setItem(48, GUIManager.createItem(Material.NETHER_STAR, "§fBack to Admin Menu", null));

        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(Material.ARROW, "§aNext Page", null));
        }

        inv.setItem(49, GUIManager.createItem(Material.BARRIER, "§cClose", null));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotListHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        int currentPage = holder.getPage();

        // Nav
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) {
            open(player, currentPage - 1);
            return;
        }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) {
            open(player, currentPage + 1);
            return;
        }
        if (slot == 48) { // Back
            plugin.gui().admin().open(player);
            return;
        }
        if (slot == 49) { // Close
            player.closeInventory();
            return;
        }

        // Listing
        if (slot < PLOTS_PER_PAGE && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            int plotIndex = (currentPage * PLOTS_PER_PAGE) + slot;
            if (plotIndex >= holder.getPlots().size()) return;

            Plot plot = holder.getPlots().get(plotIndex);
            if (plot == null) {
                player.sendMessage("§cPlot no longer exists.");
                open(player, currentPage); // Refresh
                return;
            }

            if (e.getClick().isLeftClick()) {
                // Teleport
                Location loc = plot.getCenter(plugin);
                if (loc != null && loc.getWorld() != null) {
                    // Safe Y calculation
                    int y = loc.getWorld().getHighestBlockYAt(loc);
                    loc.setY(y + 1);
                    player.teleport(loc);
                    
                    plugin.msg().send(player, "admin_plot_teleport", Map.of("PLAYER", plot.getOwnerName()));
                    plugin.effects().playConfirm(player);
                    player.closeInventory();
                } else {
                    player.sendMessage("§cInvalid world or location.");
                }
            } else if (e.getClick().isRightClick()) {
                // Delete
                plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
                plugin.msg().send(player, "admin_plot_deleted", Map.of("PLAYER", plot.getOwnerName()));
                plugin.effects().playUnclaim(player);
                open(player, currentPage); // Refresh list
            }
        }
    }
}
