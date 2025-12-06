package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.util.TeleportUtil;
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
 * - Fully localized for language switching.
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
        if (page >= maxPages && maxPages > 0) {
            page = maxPages - 1;
        } else if (maxPages == 0) {
            page = 0;
        }

        String title = GUIManager.safeText(plugin.msg().get(player, "admin_plot_list_title"), "§cAll Plots")
                + " §8(" + (page + 1) + "/" + Math.max(1, maxPages) + ")";

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
                try {
                    meta.setOwningPlayer(owner);
                } catch (Exception ex) {
                    // Fallback if offline lookup fails
                }

                String ownerName = plot.getOwnerName() != null ? plot.getOwnerName() : "Unknown";
                // Localized Name
                String nameFormat = plugin.msg().get(player, "admin_plot_item_name", Map.of("OWNER", ownerName));
                if (nameFormat == null) nameFormat = "§bOwner: §f" + ownerName;
                meta.setDisplayName(nameFormat);

                List<String> lore = new ArrayList<>();
                lore.add("§7ID: §e" + plot.getPlotId().toString().substring(0, 8));
                lore.add("§7World: §f" + plot.getWorld());
                lore.add("§7Bounds: §a" + plot.getX1() + ", " + plot.getZ1());
                lore.add("§7       to §a" + plot.getX2() + ", " + plot.getZ2());

                if (plot.isServerZone()) {
                    lore.add(plugin.msg().get(player, "admin_server_zone_tag", "§c[SERVER ZONE]"));
                }
                lore.add(" ");

                // Add localized actions
                lore.addAll(plugin.msg().getList(player, "admin_plot_actions"));

                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        // Navigation
        if (page > 0) {
            inv.setItem(45, GUIManager.createItem(Material.ARROW, plugin.msg().get(player, "button_prev_page"), null));
        }

        inv.setItem(48, GUIManager.createItem(Material.NETHER_STAR,
                plugin.msg().get(player, "button_back_admin"),
                List.of("§7Return to Admin Menu")));

        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(Material.ARROW, plugin.msg().get(player, "button_next_page"), null));
        }

        inv.setItem(49, GUIManager.createItem(Material.BARRIER,
                plugin.msg().get(player, "button_exit"),
                plugin.msg().getList(player, "exit_lore")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotListHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        int currentPage = holder.getPage();

        // Nav
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) { open(player, currentPage - 1); return; }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) { open(player, currentPage + 1); return; }
        if (slot == 48) { plugin.gui().admin().open(player); return; }
        if (slot == 49) { player.closeInventory(); return; }

        // Listing
        if (slot < PLOTS_PER_PAGE && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            int plotIndex = (currentPage * PLOTS_PER_PAGE) + slot;
            if (plotIndex >= holder.getPlots().size()) return;

            Plot plot = holder.getPlots().get(plotIndex);
            if (plot == null) {
                player.sendMessage("§cPlot no longer exists.");
                open(player, currentPage);
                return;
            }

            if (e.getClick().isLeftClick()) {
                // Teleport
                Location loc = plot.getCenter(plugin);
                if (loc != null && loc.getWorld() != null) {
                    int y = loc.getWorld().getHighestBlockYAt(loc);
                    loc.setY(y + 1);

                    TeleportUtil.safeTeleport(plugin, player, loc); // ✅ region-thread safe

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
