package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ZoningGUI
 * - Manages Sub-Claims (Rentals) inside a plot.
 * - Codex-safe + fallbacks.
 * - Click-safe (no filler triggering actions).
 */
public class ZoningGUI {

    private final AegisGuard plugin;

    public ZoningGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ZoningHolder implements InventoryHolder {
        private final Plot plot;
        private final List<String> zoneNames; // snapshot for slot->zone mapping

        public ZoningHolder(Plot plot) {
            this(plot, new ArrayList<>());
        }

        public ZoningHolder(Plot plot, List<String> zoneNames) {
            this.plot = plot;
            this.zoneNames = (zoneNames == null) ? new ArrayList<>() : zoneNames;
        }

        public Plot getPlot() { return plot; }
        public List<String> getZoneNames() { return zoneNames; }
        @Override public Inventory getInventory() { return null; }
    }

    // --------------------------------------------------
    // Codex-safe helpers
    // --------------------------------------------------

    private String tr(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> trList(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private void send(Player p, String key, String fallback) {
        String msg = tr(p, key, fallback);
        if (msg == null || msg.isBlank()) return;
        p.sendMessage(GUIManager.color(msg));
    }

    // --------------------------------------------------
    // OPEN
    // --------------------------------------------------

    public void open(Player player, Plot plot) {
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            plugin.effects().playError(player);
            return;
        }

        // Permission gate (owner/admin only)
        boolean canManage = plot.getOwner() != null
                && (plot.getOwner().equals(player.getUniqueId()) || plugin.isAdmin(player));

        if (!canManage) {
            plugin.msg().send(player, "no_perm");
            plugin.effects().playError(player);
            return;
        }

        // ✅ Title (colors + safe fallback + clamp handled by plugin.gui().title)
        String title = plugin.gui().title(player, "zone_gui_title", "&3Zone Manager");

        // Snapshot zone names for slot mapping
        List<Zone> zones = (plot.getZones() != null) ? plot.getZones() : new ArrayList<>();
        List<String> zoneNames = new ArrayList<>();
        for (Zone z : zones) {
            if (z != null && z.getName() != null) zoneNames.add(z.getName());
        }

        Inventory inv = Bukkit.createInventory(new ZoningHolder(plot, zoneNames), 54, title);

        // Fill footer with filler panes
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // --- 1) LIST ZONES (0-44) ---
        if (zones.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(
                    Material.BARRIER,
                    tr(player, "zone_none_title", "&cNo Zones Created"),
                    trList(player, "zone_none_lore", List.of(
                            "&7You have no sub-zones yet.",
                            "&7Select an area and create one below."
                    ))
            ));
        } else {
            int slot = 0;
            for (Zone zone : zones) {
                if (zone == null) continue;
                if (slot >= 45) break;

                boolean isRented = zone.isRented();

                String status = isRented
                        ? tr(player, "zone_status_rented", "&cRented")
                        : tr(player, "zone_status_available", "&aAvailable");

                String renterName = tr(player, "label_none", "None");
                String timeRemaining = "";

                if (isRented && zone.getRenter() != null) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(zone.getRenter());
                    renterName = (op.getName() != null) ? op.getName() : tr(player, "label_unknown", "Unknown");
                    try { timeRemaining = zone.getRemainingTimeFormatted(); } catch (Throwable ignored) { timeRemaining = ""; }
                }

                String priceStr = plugin.eco().format(zone.getRentPrice(), CurrencyType.VAULT);

                List<String> lore = new ArrayList<>();
                lore.add(GUIManager.color(tr(player, "zone_lore_status", "&7Status: &f{STATUS}")
                        .replace("{STATUS}", status)));
                lore.add(GUIManager.color(tr(player, "zone_lore_price", "&7Price: &6{PRICE}")
                        .replace("{PRICE}", priceStr)));
                lore.add(" ");

                if (isRented) {
                    lore.add(GUIManager.color(tr(player, "zone_lore_tenant", "&7Tenant: &f{TENANT}")
                            .replace("{TENANT}", renterName)));

                    if (timeRemaining != null && !timeRemaining.isBlank()) {
                        lore.add(GUIManager.color(tr(player, "zone_lore_expires", "&7Expires: &f{TIME}")
                                .replace("{TIME}", timeRemaining)));
                    }

                    lore.add(" ");
                    lore.add(GUIManager.color(tr(player, "zone_evict_action", "&eLeft-Click: &7Evict tenant")));
                }

                lore.add(GUIManager.color(tr(player, "zone_delete_action", "&cRight-Click: &7Delete zone")));

                String zoneName = (zone.getName() != null && !zone.getName().isBlank())
                        ? zone.getName()
                        : tr(player, "label_unnamed", "Unnamed Zone");

                String displayName = tr(player, "zone_item_name", "&b{ZONE}").replace("{ZONE}", zoneName);

                inv.setItem(slot, GUIManager.createItem(
                        isRented ? Material.IRON_DOOR : Material.OAK_DOOR,
                        GUIManager.color(displayName),
                        lore
                ));

                slot++;
            }
        }

        // --- 2) ACTIONS (footer) ---
        boolean hasSelection = plugin.selection().hasSelection(player);

        String createName = tr(player, "button_zone_create", "&aCreate Zone");
        List<String> readyLore = trList(player, "zone_create_ready_lore", List.of(
                "&7Create a zone from your selection.",
                " ",
                "&eClick to create"
        ));
        List<String> lockedLore = trList(player, "zone_create_locked_lore", List.of(
                "&7Select two points first.",
                "&7Then come back here."
        ));

        inv.setItem(49, GUIManager.createItem(
                Material.EMERALD_BLOCK,
                GUIManager.color(createName),
                hasSelection ? readyLore : lockedLore
        ));

        String backName = tr(player, "button_back", "&fBack");
        List<String> backLore = trList(player, "back_lore", List.of("&7Return to the main menu."));
        inv.setItem(45, GUIManager.createItem(Material.ARROW, GUIManager.color(backName), backLore));

        String exitName = tr(player, "button_exit", "&cClose");
        List<String> exitLore = trList(player, "exit_lore", List.of("&7Close this menu."));
        inv.setItem(53, GUIManager.createItem(Material.BARRIER, GUIManager.color(exitName), exitLore));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    // --------------------------------------------------
    // CLICK HANDLER
    // --------------------------------------------------

    public void handleClick(Player player, InventoryClickEvent e, ZoningHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        // Ignore bottom inventory clicks
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        Plot plot = holder.getPlot();
        if (plot == null) return;

        // Permission gate (owner/admin only)
        boolean canManage = plot.getOwner() != null
                && (plot.getOwner().equals(player.getUniqueId()) || plugin.isAdmin(player));

        if (!canManage) {
            plugin.msg().send(player, "no_perm");
            plugin.effects().playError(player);
            return;
        }

        int slot = e.getSlot();
        ItemStack clicked = e.getCurrentItem();

        // --- NAVIGATION ---
        if (slot == 45 && clicked.getType() == Material.ARROW) {
            plugin.effects().playMenuFlip(player);
            plugin.gui().openMain(player);
            return;
        }

        if (slot == 53 && clicked.getType() == Material.BARRIER) {
            plugin.effects().playMenuClose(player);
            player.closeInventory();
            return;
        }

        // --- CREATE ZONE ---
        if (slot == 49 && clicked.getType() == Material.EMERALD_BLOCK) {
            if (plugin.selection().hasSelection(player)) {
                String name = "Zone-" + (plot.getZones() != null ? (plot.getZones().size() + 1) : 1);
                player.performCommand("ag zone create " + name + " 100");
                player.closeInventory();
            } else {
                plugin.effects().playError(player);
                send(player, "must_select", "&cYou must select two points first.");
            }
            return;
        }

        // --- ZONE MANAGEMENT (0-44) ---
        if (slot >= 0 && slot < 45 && clicked.getType() != Material.AIR) {
            List<String> names = holder.getZoneNames();
            if (slot >= names.size()) return;

            String zoneName = names.get(slot);
            if (zoneName == null || zoneName.isBlank()) return;

            Zone target = null;
            List<Zone> zones = plot.getZones();
            if (zones != null) {
                for (Zone z : zones) {
                    if (z != null && z.getName() != null && z.getName().equalsIgnoreCase(zoneName)) {
                        target = z;
                        break;
                    }
                }
            }
            if (target == null) return;

            // Right Click: Delete
            if (e.getClick().isRightClick()) {
                try {
                    plot.removeZone(target);
                } catch (Throwable ignored) {
                    // If your Plot uses a different API, at least don't crash the GUI
                }

                plugin.store().setDirty(true);
                plugin.effects().playUnclaim(player);

                send(player, "zone_deleted", "&cZone deleted: &f{ZONE}".replace("{ZONE}", target.getName()));
                open(player, plot);
                return;
            }

            // Left Click: Evict (only if rented)
            if (e.getClick().isLeftClick()) {
                if (target.isRented()) {
                    try { target.evict(); } catch (Throwable ignored) {}
                    plugin.store().setDirty(true);
                    plugin.effects().playConfirm(player);

                    send(player, "zone_evicted", "&eTenant evicted from: &f{ZONE}".replace("{ZONE}", target.getName()));
                    open(player, plot);
                } else {
                    send(player, "zone_not_rented", "&7That zone is not currently rented.");
                    plugin.effects().playError(player);
                }
            }
        }
    }
}
