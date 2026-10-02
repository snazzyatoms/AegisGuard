package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.travel.SafeTravelService;
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
 * Unified travel hub — the single entry point that routes players to the visit atlas,
 * teleport beacons, and exploration routes, plus one-click quick travel (home, server
 * spawn, unstuck). Previously each surface lived behind its own command/GUI and most
 * players never discovered them.
 */
public class TravelHubGUI {

    private final AegisGuard plugin;

    public TravelHubGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class TravelHubHolder implements InventoryHolder {
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
        if (!plugin.cfg().isTravelSystemEnabled()) {
            plugin.msg().send(player, "travel_system_disabled");
            plugin.effects().playError(player);
            return;
        }

        String title = plugin.gui().title(player, "travel_hub_title", "&bTravel");
        Inventory inv = Bukkit.createInventory(new TravelHubHolder(), 27, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(4, GUIManager.createItem(Material.COMPASS,
                t(player, "travel_hub_header", "&bTravel Hub"),
                tl(player, "travel_hub_header_lore", List.of(
                        "&7Every way to get around,", "&7in one place."))));

        inv.setItem(10, GUIManager.createItem(Material.MAP,
                t(player, "travel_hub_atlas", "&eTravel Atlas"),
                tl(player, "travel_hub_atlas_lore", List.of(
                        "&7Browse plots, warps, featured", "&7and public destinations."))));

        if (plugin.beacons() != null && plugin.beacons().isEnabled()) {
            inv.setItem(12, GUIManager.createItem(Material.BEACON,
                    t(player, "travel_hub_beacons", "&bTeleport Beacons"),
                    tl(player, "travel_hub_beacons_lore", List.of(
                            "&7Manage and link your", "&7teleport beacons."))));
        }

        if (plugin.routes() != null && plugin.routes().isEnabled()) {
            inv.setItem(14, GUIManager.createItem(Material.FILLED_MAP,
                    t(player, "travel_hub_routes", "&aExploration Routes"),
                    tl(player, "travel_hub_routes_lore", List.of(
                            "&7Walk server routes and", "&7discover checkpoints."))));
        }

        inv.setItem(16, GUIManager.createItem(Material.ENDER_PEARL,
                t(player, "travel_hub_discover", "&6Discover Plots"),
                tl(player, "travel_hub_discover_lore", List.of(
                        "&7Public plots you can", "&7visit right now."))));

        // Quick actions
        inv.setItem(20, GUIManager.createItem(Material.RED_BED,
                t(player, "travel_hub_home", "&aGo Home"),
                tl(player, "travel_hub_home_lore", List.of(
                        "&7Teleport to your plot home."))));

        inv.setItem(22, GUIManager.createItem(Material.GRASS_BLOCK,
                t(player, "travel_hub_spawn", "&aServer Spawn"),
                tl(player, "travel_hub_spawn_lore", List.of(
                        "&7Teleport to the world spawn."))));

        inv.setItem(24, GUIManager.createItem(Material.LADDER,
                t(player, "travel_hub_unstuck", "&eUnstuck"),
                tl(player, "travel_hub_unstuck_lore", List.of(
                        "&7Escape a claim you are", "&7stuck inside of."))));

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the main menu."))));
        inv.setItem(26, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, TravelHubHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        switch (e.getRawSlot()) {
            case 18 -> { plugin.gui().openMain(player); return; }
            case 26 -> { player.closeInventory(); return; }
            case 10 -> { plugin.gui().visit().open(player, 0, false); return; }
            case 16 -> { plugin.gui().visit().open(player, 0, VisitGUI.VisitMode.DISCOVER); return; }
            case 12 -> {
                if (plugin.beacons() != null && plugin.beacons().isEnabled()) {
                    plugin.gui().beacons().openManager(player);
                } else {
                    plugin.effects().playError(player);
                }
                return;
            }
            case 14 -> {
                if (plugin.routes() != null && plugin.routes().isEnabled()) {
                    plugin.gui().routes().open(player);
                } else {
                    plugin.effects().playError(player);
                }
                return;
            }
            case 20 -> { player.closeInventory(); runCommand(player, "home"); return; }
            case 22 -> { travelToSpawn(player); return; }
            case 24 -> { player.closeInventory(); runCommand(player, "stuck"); return; }
            default -> { /* filler or header */ }
        }
    }

    /**
     * Home/stuck reuse the real command handlers (permission checks, edge-case messaging,
     * safe-travel integration) instead of duplicating their logic here.
     */
    private void runCommand(Player player, String sub) {
        try {
            Bukkit.dispatchCommand(player, "aegis " + sub);
        } catch (Throwable ignored) {
            plugin.effects().playError(player);
        }
    }

    private void travelToSpawn(Player player) {
        Location spawn = player.getWorld().getSpawnLocation();
        var result = plugin.safeTravel().travel(player, spawn, SafeTravelService.Kind.SPAWN);
        if (result.isSuccess()) {
            player.closeInventory();
        }
    }
}
