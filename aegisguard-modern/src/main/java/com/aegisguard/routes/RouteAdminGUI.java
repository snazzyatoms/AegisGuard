package com.aegisguard.routes;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Milestone 6 - staff route editor. Create routes, append the player's current location as the
 * next checkpoint, toggle enabled, and set small completion rewards. Never alters claim boundaries.
 */
public class RouteAdminGUI {

    private final AegisGuard plugin;

    public RouteAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class RouteAdminHolder implements InventoryHolder {
        private final int page;
        public RouteAdminHolder(int page) { this.page = page; }
        public int getPage() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class RouteEditHolder implements InventoryHolder {
        private final Route route;
        public RouteEditHolder(Route route) { this.route = route; }
        public Route getRoute() { return route; }
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

    private boolean canEdit(Player player) {
        return player != null && (player.hasPermission("aegis.admin.routes") || plugin.isAdmin(player));
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        if (!canEdit(player)) {
            plugin.msg().send(player, "no_perm");
            plugin.effects().playError(player);
            return;
        }

        List<Route> routes = new ArrayList<>(plugin.routes().allRoutes());
        routes.sort(Comparator.comparing(Route::getName, String.CASE_INSENSITIVE_ORDER));
        int perPage = 21;
        int maxPages = Math.max(1, (int) Math.ceil(routes.size() / (double) perPage));
        int safePage = Math.max(0, Math.min(page, maxPages - 1));

        String title = plugin.gui().title(player, "routes_admin_title", "&cRoute Editor");
        Inventory inv = Bukkit.createInventory(new RouteAdminHolder(safePage), 54, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        int start = safePage * perPage;
        for (int i = 0; i < perPage && start + i < routes.size(); i++) {
            Route route = routes.get(start + i);
            List<String> lore = new ArrayList<>();
            lore.add(GUIManager.color(route.isEnabled()
                    ? t(player, "routes_admin_enabled", "&aEnabled")
                    : t(player, "routes_admin_disabled", "&cDisabled")));
            lore.add(GUIManager.color(t(player, "routes_admin_checkpoints_line",
                    Map.of("COUNT", String.valueOf(route.size())),
                    "&7Checkpoints: &f{COUNT}")));
            lore.add(" ");
            lore.add(GUIManager.color(t(player, "routes_admin_click_edit", "&eClick to edit.")));
            inv.setItem(i, GUIManager.createItem(
                    route.isEnabled() ? Material.FILLED_MAP : Material.MAP,
                    "&e" + route.getName(), lore));
        }

        inv.setItem(40, GUIManager.createItem(Material.EMERALD,
                t(player, "routes_admin_create_name", "&a＋ Create Route Here"),
                tl(player, "routes_admin_create_lore", List.of(
                        "&7Creates a new enabled route named",
                        "&7after your current world, starting",
                        "&7with a checkpoint at your feet."))));

        inv.setItem(45, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the admin panel."))));
        inv.setItem(49, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void openEdit(Player player, Route route) {
        if (!canEdit(player) || route == null) {
            plugin.effects().playError(player);
            return;
        }

        String title = plugin.gui().title(player, "routes_admin_edit_title", "&cEdit Route");
        Inventory inv = Bukkit.createInventory(new RouteEditHolder(route), 27, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<String> header = new ArrayList<>();
        header.add(GUIManager.color("&7" + route.getName()));
        header.add(GUIManager.color(t(player, "routes_admin_checkpoints_line",
                Map.of("COUNT", String.valueOf(route.size())),
                "&7Checkpoints: &f{COUNT}")));
        inv.setItem(4, GUIManager.createItem(Material.NAME_TAG, "&e" + route.getName(), header));

        inv.setItem(10, GUIManager.createItem(
                route.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE,
                route.isEnabled()
                        ? t(player, "routes_admin_enabled", "&aEnabled")
                        : t(player, "routes_admin_disabled", "&cDisabled"),
                tl(player, "routes_admin_toggle_lore", List.of("&eClick to toggle visibility."))));

        inv.setItem(12, GUIManager.createItem(Material.COMPASS,
                t(player, "routes_admin_add_checkpoint_name", "&b＋ Add Checkpoint Here"),
                tl(player, "routes_admin_add_checkpoint_lore", List.of(
                        "&7Appends your current location as",
                        "&7the next ordered checkpoint."))));

        inv.setItem(14, GUIManager.createItem(Material.GOLD_INGOT,
                t(player, "routes_admin_reward_money_name",
                        Map.of("AMOUNT", String.valueOf(route.getRewardMoney())),
                        "&6Money Reward: {AMOUNT}"),
                tl(player, "routes_admin_reward_money_lore", List.of(
                        "&7Click to cycle: 0 → 10 → 25 → 50 → 100."))));

        inv.setItem(16, GUIManager.createItem(Material.EMERALD,
                t(player, "routes_admin_reward_blocks_name",
                        Map.of("AMOUNT", String.valueOf(route.getRewardClaimBlocks())),
                        "&bClaim Blocks: {AMOUNT}"),
                tl(player, "routes_admin_reward_blocks_lore", List.of(
                        "&7Click to cycle: 0 → 25 → 50 → 100 → 250."))));

        inv.setItem(22, GUIManager.createItem(Material.TNT,
                t(player, "routes_admin_delete_name", "&cDelete Route"),
                tl(player, "routes_admin_delete_lore", List.of(
                        "&cRemoves this route and all", "&cplayer progress for it."))));

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the route list."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleListClick(Player player, InventoryClickEvent e, RouteAdminHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || !canEdit(player)) return;

        int slot = e.getRawSlot();
        if (slot == 45) { plugin.gui().admin().open(player); return; }
        if (slot == 49) { player.closeInventory(); return; }

        if (slot == 40) {
            Location loc = player.getLocation();
            String name = loc.getWorld() == null ? "New Route" : loc.getWorld().getName() + " Route";
            Route route = plugin.routes().createRoute(name, player);
            route.addCheckpoint(Checkpoint.at("Start",
                    loc.getWorld() == null ? "world" : loc.getWorld().getName(),
                    loc.getX(), loc.getY(), loc.getZ(),
                    plugin.routes().defaultCheckpointRadius()));
            plugin.routes().saveRoute(route);
            plugin.msg().send(player, "routes_admin_created", Map.of("NAME", route.getName()));
            plugin.effects().playConfirm(player);
            openEdit(player, route);
            return;
        }

        List<Route> routes = new ArrayList<>(plugin.routes().allRoutes());
        routes.sort(Comparator.comparing(Route::getName, String.CASE_INSENSITIVE_ORDER));
        int index = holder.getPage() * 21 + slot;
        if (slot < 0 || slot >= 21 || index >= routes.size()) return;
        openEdit(player, routes.get(index));
    }

    public void handleEditClick(Player player, InventoryClickEvent e, RouteEditHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || !canEdit(player)) return;

        Route route = holder.getRoute();
        int slot = e.getRawSlot();
        if (slot == 18) { open(player); return; }
        if (slot == 20) { player.closeInventory(); return; }

        if (slot == 10) {
            route.setEnabled(!route.isEnabled());
            plugin.routes().saveRoute(route);
            plugin.effects().playMenuFlip(player);
            openEdit(player, route);
            return;
        }

        if (slot == 12) {
            Location loc = player.getLocation();
            route.addCheckpoint(Checkpoint.at(
                    "Stop " + (route.size() + 1),
                    loc.getWorld() == null ? "world" : loc.getWorld().getName(),
                    loc.getX(), loc.getY(), loc.getZ(),
                    plugin.routes().defaultCheckpointRadius()));
            plugin.routes().saveRoute(route);
            plugin.msg().send(player, "routes_admin_checkpoint_added",
                    Map.of("COUNT", String.valueOf(route.size())));
            plugin.effects().playConfirm(player);
            openEdit(player, route);
            return;
        }

        if (slot == 14) {
            double next = cycleMoney(route.getRewardMoney());
            route.setRewardMoney(next);
            plugin.routes().saveRoute(route);
            plugin.effects().playMenuFlip(player);
            openEdit(player, route);
            return;
        }

        if (slot == 16) {
            int next = cycleBlocks(route.getRewardClaimBlocks());
            route.setRewardClaimBlocks(next);
            plugin.routes().saveRoute(route);
            plugin.effects().playMenuFlip(player);
            openEdit(player, route);
            return;
        }

        if (slot == 22) {
            plugin.routes().deleteRoute(route.getId());
            plugin.msg().send(player, "routes_admin_deleted", Map.of("NAME", route.getName()));
            plugin.effects().playConfirm(player);
            open(player);
        }
    }

    private static double cycleMoney(double current) {
        if (current < 10) return 10;
        if (current < 25) return 25;
        if (current < 50) return 50;
        if (current < 100) return 100;
        return 0;
    }

    private static int cycleBlocks(int current) {
        if (current < 25) return 25;
        if (current < 50) return 50;
        if (current < 100) return 100;
        if (current < 250) return 250;
        return 0;
    }
}
