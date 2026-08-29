package com.aegisguard.season;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.routes.Route;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SeasonAdminGUI {
    private final AegisGuard plugin;

    public SeasonAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void open(Player player) {
        if (!plugin.isAdmin(player) && !player.hasPermission("aegis.admin.season")) {
            plugin.msg().send(player, "no_perm");
            return;
        }
        SeasonService seasons = plugin.seasons();
        Inventory inv = Bukkit.createInventory(new Holder(), 54,
                plugin.gui().title(player, "season_admin_title", "&6Staff Season"));
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        String title = seasons.title().isBlank()
                ? plugin.gui().tr(player, "season_untitled", "&7No season title yet")
                : "&6" + seasons.title();
        List<String> header = new ArrayList<>();
        if (!seasons.description().isBlank()) header.add("&7" + seasons.description());
        header.add("&7Featured plots: &f" + seasons.featuredPlots().size()
                + "&7/&f" + seasons.maxFeaturedPlots());
        header.add("&7Featured routes: &f" + seasons.featuredRoutes().size()
                + "&7/&f" + seasons.maxFeaturedRoutes());
        header.add(" ");
        header.add("&e/agadmin season title <name>");
        header.add("&e/agadmin season desc <text>");
        inv.setItem(4, GUIManager.createItem(Material.GOLDEN_HELMET,
                plugin.gui().tr(player, "season_admin_header", title),
                color(header)));

        Plot here = plugin.store().getPlotAt(player.getLocation());
        boolean featuredHere = here != null && seasons.isFeaturedPlot(here.getPlotId());
        inv.setItem(11, GUIManager.createItem(
                featuredHere ? Material.GLOWSTONE : Material.SUNFLOWER,
                plugin.gui().tr(player, featuredHere ? "season_unfeature_plot_name" : "season_feature_plot_name",
                        featuredHere ? "&eUnfeature this plot" : "&aFeature this plot"),
                plugin.gui().trList(player, "season_feature_plot_lore", List.of(
                        "&7Stand inside a plot and click.",
                        "&7Season features pin first on the Atlas."
                ))
        ));
        inv.setItem(15, GUIManager.createItem(Material.BARRIER,
                plugin.gui().tr(player, "season_clear_name", "&cClear season"),
                plugin.gui().trList(player, "season_clear_lore", List.of(
                        "&7Removes the title, description,",
                        "&7and every featured plot and route."
                ))
        ));

        int slot = 19;
        for (UUID plotId : seasons.featuredPlots()) {
            if (slot > 25) break;
            Plot plot = plugin.store().getPlotById(plotId);
            String name = plot == null
                    ? plotId.toString().substring(0, 8)
                    : (plot.getPlotName() == null || plot.getPlotName().isBlank()
                    ? plot.getOwnerName() : plot.getPlotName());
            inv.setItem(slot++, GUIManager.createItem(Material.MAP,
                    plugin.gui().tr(player, "season_featured_plot_name", "&6★ {NAME}",
                            Map.of("NAME", name == null ? "Plot" : name)),
                    plugin.gui().trList(player, "season_featured_plot_lore", List.of(
                            "&7Pinned on Discover and Destinations."
                    ))
            ));
        }

        slot = 28;
        for (UUID routeId : seasons.featuredRoutes()) {
            if (slot > 34) break;
            Route route = plugin.routes() == null ? null : plugin.routes().getRoute(routeId);
            String name = route == null ? routeId.toString().substring(0, 8) : route.getName();
            inv.setItem(slot++, GUIManager.createItem(Material.FILLED_MAP,
                    plugin.gui().tr(player, "season_featured_route_name", "&a★ {NAME}",
                            Map.of("NAME", name == null ? "Route" : name)),
                    plugin.gui().trList(player, "season_featured_route_lore", List.of(
                            "&7Pinned first in the Routes browser."
                    ))
            ));
        }

        inv.setItem(48, GUIManager.createItem(Material.ARROW,
                plugin.gui().tr(player, "button_back", "&fBack"),
                plugin.gui().trList(player, "back_lore", List.of("&7Return to staff tools."))));
        inv.setItem(49, GUIManager.createItem(Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&cClose"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getRawSlot();
        SeasonService seasons = plugin.seasons();
        if (slot == 48) {
            plugin.gui().admin().open(player);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }
        if (slot == 11) {
            Plot plot = plugin.store().getPlotAt(player.getLocation());
            if (plot == null) {
                player.sendMessage(GUIManager.color(plugin.gui().tr(player, "season_need_plot",
                        "&cStand inside the plot you want to feature.")));
                plugin.effects().playError(player);
                return;
            }
            if (seasons.isFeaturedPlot(plot.getPlotId())) {
                seasons.unfeaturePlot(plot.getPlotId());
                plugin.effects().playConfirm(player);
            } else if (!seasons.featurePlot(plot.getPlotId())) {
                player.sendMessage(GUIManager.color(plugin.gui().tr(player, "season_plot_limit",
                        "&cSeason plot limit reached.")));
                plugin.effects().playError(player);
                return;
            } else {
                plugin.territoryLife().setFeatured(plot.getPlotId(), true);
                plugin.effects().playConfirm(player);
            }
            open(player);
            return;
        }
        if (slot == 15) {
            seasons.clear();
            plugin.effects().playConfirm(player);
            open(player);
        }
    }

    private List<String> color(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) out.add(GUIManager.color(line));
        return out;
    }
}
