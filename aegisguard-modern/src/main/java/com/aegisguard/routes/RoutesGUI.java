package com.aegisguard.routes;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.gui.HubOriginHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Milestone 6 - player-facing route browser. Shows available routes, discovery progress,
 * and the next destination. Optional teleport is offered only when configured; never forced.
 */
public class RoutesGUI {

    private final AegisGuard plugin;

    public RoutesGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class RoutesMenuHolder implements HubOriginHolder {
        private final int page;
        private boolean fromHub;
        public RoutesMenuHolder(int page) { this.page = page; }
        public int getPage() { return page; }
        @Override public boolean isFromHub() { return fromHub; }
        @Override public void setFromHub(boolean fromHub) { this.fromHub = fromHub; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class RouteDetailHolder implements HubOriginHolder {
        private final Route route;
        private boolean fromHub;
        public RouteDetailHolder(Route route) { this.route = route; }
        public Route getRoute() { return route; }
        @Override public boolean isFromHub() { return fromHub; }
        @Override public void setFromHub(boolean fromHub) { this.fromHub = fromHub; }
        @Override public Inventory getInventory() { return null; }
    }

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private String t(Player p, String key, Map<String, String> vars, String fallback) {
        String out = plugin.gui().tr(p, key, fallback);
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }

    private boolean isTopClick(InventoryClickEvent e) {
        return e.getClickedInventory() != null && e.getClickedInventory() == e.getView().getTopInventory();
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        if (!plugin.routes().isEnabled()) {
            plugin.msg().send(player, "routes_disabled");
            plugin.effects().playError(player);
            return;
        }

        List<Route> routes = sortedRoutes();
        int perPage = 21;
        int maxPages = Math.max(1, (int) Math.ceil(routes.size() / (double) perPage));
        int safePage = Math.max(0, Math.min(page, maxPages - 1));

        String title = plugin.gui().title(player, "routes_menu_title", "&aRoutes");
        RoutesMenuHolder menuHolder = new RoutesMenuHolder(safePage);
        menuHolder.setFromHub(GUIManager.hubOriginActive(player));
        Inventory inv = Bukkit.createInventory(menuHolder, 54, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        if (routes.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER,
                    t(player, "routes_none_title", "&7No Routes Yet"),
                    tl(player, "routes_none_lore", List.of(
                            "&7Staff have not published any", "&7exploration routes yet."))));
        } else {
            int start = safePage * perPage;
            for (int i = 0; i < perPage && start + i < routes.size(); i++) {
                Route route = routes.get(start + i);
                inv.setItem(i, buildRouteItem(player, route));
            }
        }

        if (safePage > 0) {
            inv.setItem(45, GUIManager.createItem(Material.ARROW,
                    t(player, "button_prev", "&fPrevious"),
                    tl(player, "routes_prev_lore", List.of("&7Previous page."))));
        }
        if (safePage < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(Material.ARROW,
                    t(player, "button_next", "&fNext"),
                    tl(player, "routes_next_lore", List.of("&7Next page."))));
        }

        if (menuHolder.isFromHub()) {
            inv.setItem(47, GUIManager.createItem(Material.NETHER_STAR,
                    t(player, "button_back_menu", "&fReturn to Menu"),
                    tl(player, "back_menu_lore", List.of("&7Go back to the main menu."))));
            inv.setItem(48, GUIManager.createItem(Material.COMPASS,
                    t(player, "button_back_hub", "&fBack to Travel Hub"),
                    tl(player, "back_hub_lore", List.of("&7Return to the travel hub."))));
        } else {
            inv.setItem(48, GUIManager.createItem(Material.ARROW,
                    t(player, "button_back", "&fBack"),
                    tl(player, "back_lore", List.of("&7Return to the main menu."))));
        }
        inv.setItem(49, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    /** Single source for the displayed order — list and click-resolution must agree. */
    private List<Route> sortedRoutes() {
        List<Route> routes = plugin.routes().enabledRoutes();
        if (plugin.seasons() != null && plugin.seasons().isEnabled()) {
            routes = plugin.seasons().sortRoutes(routes);
        }
        return routes;
    }

    /** Ten-block progress bar, e.g. "&a■■■&7□□□□□□□". */
    private String progressBar(int done, int total) {
        if (total <= 0) return "";
        int filled = Math.min(10, (int) Math.round(done * 10.0 / total));
        return GUIManager.color("&a" + "■".repeat(filled) + "&7" + "□".repeat(10 - filled));
    }

    private ItemStack buildRouteItem(Player player, Route route) {
        RouteProgress progress = plugin.routes().progressOf(player.getUniqueId(), route.getId());
        int done = progress.getDiscoveredCount();
        int total = route.size();
        boolean complete = total > 0 && done >= total;
        Checkpoint next = route.nextAfter(done);
        Route active = plugin.routes().activeRoute(player.getUniqueId());
        boolean tracking = active != null && active.getId().equals(route.getId());

        List<String> lore = new ArrayList<>();
        if (route.getDescription() != null && !route.getDescription().isBlank()) {
            lore.add(GUIManager.color("&7" + route.getDescription()));
            lore.add(" ");
        }
        lore.add(GUIManager.color(t(player, "routes_progress_line",
                Map.of("COUNT", String.valueOf(done), "TOTAL", String.valueOf(total)),
                "&7Progress: &f{COUNT}/{TOTAL}"))
                + "  " + progressBar(done, total));
        if (tracking) {
            lore.add(GUIManager.color(t(player, "routes_tracking_line", "&a● Tracking this route")));
        }
        if (next != null) {
            lore.add(GUIManager.color(t(player, "routes_next_checkpoint_line",
                    Map.of("NAME", next.getName()),
                    "&7Next: &e{NAME}")));
        } else if (complete) {
            lore.add(GUIManager.color(t(player, "routes_complete_line", "&a✔ Route complete")));
        }
        if (route.getRewardMoney() > 0 || route.getRewardClaimBlocks() > 0) {
            lore.add(GUIManager.color(t(player, "routes_reward_line",
                    Map.of("MONEY", String.valueOf(route.getRewardMoney()),
                            "BLOCKS", String.valueOf(route.getRewardClaimBlocks())),
                    "&7Reward: &6{MONEY} &7+ &b{BLOCKS} blocks")));
        }
        if (plugin.seasons() != null && plugin.seasons().isEnabled()
                && plugin.seasons().isFeaturedRoute(route.getId())) {
            lore.add(GUIManager.color(t(player, "routes_season_featured_line", "&6★ Featured on the staff season")));
        }
        lore.add(" ");
        lore.add(GUIManager.color(t(player, "routes_click_detail", "&eClick for details.")));
        Material icon = done == 0 ? Material.MAP : Material.FILLED_MAP;
        ItemStack item = GUIManager.createItem(icon,
                t(player, "routes_entry_name", Map.of("NAME", route.getName()), "&a🗺 {NAME}"), lore);
        if (complete) glow(item);
        return item;
    }

    public void openDetail(Player player, Route route) {
        if (route == null) { plugin.effects().playError(player); return; }
        plugin.routes().setActiveRoute(player.getUniqueId(), route.getId());

        String title = plugin.gui().title(player, "routes_detail_title", "&aRoute Details");
        RouteDetailHolder detailHolder = new RouteDetailHolder(route);
        detailHolder.setFromHub(GUIManager.hubOriginActive(player));
        Inventory inv = Bukkit.createInventory(detailHolder, 27, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        RouteProgress progress = plugin.routes().progressOf(player.getUniqueId(), route.getId());
        Checkpoint next = route.nextAfter(progress.getDiscoveredCount());

        List<String> header = new ArrayList<>();
        header.add(GUIManager.color(t(player, "routes_progress_line",
                Map.of("COUNT", String.valueOf(progress.getDiscoveredCount()),
                        "TOTAL", String.valueOf(route.size())),
                "&7Progress: &f{COUNT}/{TOTAL}"))
                + "  " + progressBar(progress.getDiscoveredCount(), route.size()));
        Route active = plugin.routes().activeRoute(player.getUniqueId());
        if (active != null && active.getId().equals(route.getId())) {
            header.add(GUIManager.color(t(player, "routes_tracking_line", "&a● Tracking this route")));
        }
        if (route.getRewardMoney() > 0 || route.getRewardClaimBlocks() > 0) {
            header.add(GUIManager.color(t(player, "routes_reward_line",
                    Map.of("MONEY", String.valueOf(route.getRewardMoney()),
                            "BLOCKS", String.valueOf(route.getRewardClaimBlocks())),
                    "&7Reward: &6{MONEY} &7+ &b{BLOCKS} blocks")));
        }
        // Checkpoint checklist: ✔ discovered · ● next · ○ ahead — capped so lore stays readable.
        if (!route.getCheckpoints().isEmpty()) {
            header.add(" ");
            List<Checkpoint> checkpoints = route.getCheckpoints();
            int shown = Math.min(checkpoints.size(), 8);
            for (int i = 0; i < shown; i++) {
                Checkpoint cp = checkpoints.get(i);
                String marker = progress.hasDiscovered(cp.getId()) ? "&a✔ "
                        : cp.equals(next) ? "&e● " : "&7○ ";
                header.add(GUIManager.color(marker + cp.getName()));
            }
            if (checkpoints.size() > shown) {
                header.add(GUIManager.color(t(player, "routes_more_checkpoints",
                        Map.of("COUNT", String.valueOf(checkpoints.size() - shown)),
                        "&7… and {COUNT} more")));
            }
        }
        ItemStack headerItem = GUIManager.createItem(Material.FILLED_MAP,
                t(player, "routes_entry_name", Map.of("NAME", route.getName()), "&a🗺 {NAME}"), header);
        inv.setItem(4, headerItem);

        if (next != null) {
            List<String> nextLore = new ArrayList<>();
            nextLore.add(GUIManager.color("&7" + next.getWorld()
                    + " @ " + (int) next.getX() + ", " + (int) next.getY() + ", " + (int) next.getZ()));
            nextLore.add(" ");
            nextLore.add(GUIManager.color(t(player, "routes_walk_hint",
                    "&7Walk near this checkpoint to discover it.")));
            if (plugin.getConfig().getBoolean("routes.allow_optional_teleport", false)) {
                nextLore.add(GUIManager.color(t(player, "routes_teleport_hint",
                        "&eClick to teleport nearby (optional).")));
            }
            inv.setItem(13, GUIManager.createItem(Material.COMPASS,
                    t(player, "routes_next_name", Map.of("NAME", next.getName()), "&eNext: {NAME}"), nextLore));
        } else {
            inv.setItem(13, GUIManager.createItem(Material.LIME_DYE,
                    t(player, "routes_complete_title", "&aRoute Complete"),
                    tl(player, "routes_complete_lore", List.of("&7You have discovered every", "&7checkpoint on this route."))));
        }

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the route list."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleMenuClick(Player player, InventoryClickEvent e, RoutesMenuHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        if (slot == 47 && holder.isFromHub()) { plugin.gui().openMain(player); return; }
        if (slot == 48) {
            if (holder.isFromHub()) plugin.gui().travelHub().open(player);
            else plugin.gui().openMain(player);
            return;
        }
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 45) { open(player, holder.getPage() - 1); return; }
        if (slot == 53) { open(player, holder.getPage() + 1); return; }

        List<Route> routes = sortedRoutes();
        int index = holder.getPage() * 21 + slot;
        if (slot < 0 || slot >= 21 || index >= routes.size()) return;
        openDetail(player, routes.get(index));
    }

    public void handleDetailClick(Player player, InventoryClickEvent e, RouteDetailHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        if (slot == 18) { open(player); return; }
        if (slot == 20) { player.closeInventory(); return; }

        if (slot == 13 && plugin.getConfig().getBoolean("routes.allow_optional_teleport", false)) {
            Checkpoint next = plugin.routes().nextCheckpoint(player.getUniqueId(), holder.getRoute());
            if (next == null) { plugin.effects().playError(player); return; }
            Location loc = plugin.routes().toLocation(next);
            if (loc == null) {
                plugin.msg().send(player, "routes_world_missing");
                plugin.effects().playError(player);
                return;
            }
            var result = plugin.safeTravel().travel(player, loc, com.aegisguard.travel.SafeTravelService.Kind.ROUTE);
            if (!result.isSuccess()) return;
            player.closeInventory();
            plugin.msg().send(player, "routes_teleported", Map.of("NAME", next.getName()));
        }
    }

    private void glow(ItemStack item) {
        var meta = item.getItemMeta();
        if (meta == null) return;
        try {
            var ench = org.bukkit.enchantments.Enchantment.getByName("UNBREAKING");
            if (ench == null) ench = org.bukkit.enchantments.Enchantment.getByName("DURABILITY");
            if (ench != null) meta.addEnchant(ench, 1, true);
        } catch (Throwable ignored) {}
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }
}
