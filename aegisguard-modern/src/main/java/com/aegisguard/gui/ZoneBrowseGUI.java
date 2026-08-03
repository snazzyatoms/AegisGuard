package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.groups.PlotGroup;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ZoneBrowseGUI {

    private final AegisGuard plugin;

    public ZoneBrowseGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ZoneBrowseHolder implements InventoryHolder {
        private final Plot plot;
        private final List<String> zoneNames;

        public ZoneBrowseHolder(Plot plot, List<String> zoneNames) {
            this.plot = plot;
            this.zoneNames = zoneNames == null ? new ArrayList<>() : zoneNames;
        }

        public Plot getPlot() {
            return plot;
        }

        public List<String> getZoneNames() {
            return zoneNames;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void open(Player player) {
        if (player == null) return;
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            plugin.gui().openMain(player);
            return;
        }
        open(player, plot);
    }

    public void open(Player player, Plot plot) {
        if (player == null || plot == null) return;

        String title = plugin.gui().title(player, "zone_browse_title", "&2Available Zones");
        List<Zone> zones = getBrowsableZones(plot, player);
        List<String> zoneNames = new ArrayList<>();
        for (Zone zone : zones) {
            zoneNames.add(zone.getName());
        }

        Inventory inv = Bukkit.createInventory(new ZoneBrowseHolder(plot, zoneNames), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler);
        }

        if (zones.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(
                    Material.BARRIER,
                    tr(player, "zone_browse_none_title", "&cNo Zones Available"),
                    trList(player, "zone_browse_none_lore", List.of(
                            "&7This plot has no rentable zones right now.",
                            "&7Check back later or ask the owner."
                    ))
            ));
        } else {
            int slot = 0;
            for (Zone zone : zones) {
                if (slot >= 45) break;

                boolean rented = zone.isRented();
                boolean mine = zone.isRentedBy(player.getUniqueId());
                boolean listed = zone.isListedForRent() && !rented;

                Material icon = mine ? Material.GOLD_BLOCK : listed ? Material.EMERALD : Material.IRON_BARS;
                String status = mine
                        ? tr(player, "zone_browse_status_yours", "&aYour rental")
                        : listed
                        ? tr(player, "zone_status_listed", "&eListed")
                        : tr(player, "zone_browse_status_occupied", "&cOccupied");

                List<String> lore = new ArrayList<>();
                lore.add(GUIManager.color(tr(player, "zone_lore_status", "&7Status: &f{STATUS}")
                        .replace("{STATUS}", status)));
                lore.add(GUIManager.color(tr(player, "zone_lore_dimensions", "&7Size: &f{WIDTH}x{HEIGHT}x{DEPTH}")
                        .replace("{WIDTH}", String.valueOf(zone.getWidth()))
                        .replace("{HEIGHT}", String.valueOf(zone.getHeight()))
                        .replace("{DEPTH}", String.valueOf(zone.getDepth()))));
                lore.add(GUIManager.color(tr(player, "zone_lore_price", "&7Price: &6{PRICE}")
                        .replace("{PRICE}", plugin.eco().format(zone.getRentPrice(), CurrencyType.VAULT))));

                if (rented) {
                    OfflinePlayer renter = Bukkit.getOfflinePlayer(zone.getRenter());
                    String renterName = renter.getName() != null ? renter.getName() : tr(player, "label_unknown", "Unknown");
                    lore.add(GUIManager.color(tr(player, "zone_lore_tenant", "&7Tenant: &f{TENANT}")
                            .replace("{TENANT}", renterName)));
                    lore.add(GUIManager.color(tr(player, "zone_lore_expires", "&7Expires: &f{TIME}")
                            .replace("{TIME}", zone.getRemainingTimeFormatted())));
                }

                lore.add(" ");
                lore.add(GUIManager.color(tr(player, "zone_browse_action_preview", "&eLeft-Click: &7Preview this zone")));
                if (mine) {
                    lore.add(GUIManager.color(tr(player, "zone_browse_action_manage", "&aRight-Click: &7Manage this room")));
                    lore.add(GUIManager.color(tr(player, "zone_browse_action_extend", "&eShift-Right: &7Extend this rental")));
                } else if (listed) {
                    lore.add(GUIManager.color(tr(player, "zone_browse_action_rent", "&aRight-Click: &7Rent this zone")));
                } else {
                    lore.add(GUIManager.color(tr(player, "zone_browse_action_unavailable", "&8Right-Click: &7Currently unavailable")));
                }

                inv.setItem(slot++, GUIManager.createItem(
                        icon,
                        tr(player, "zone_item_name", "&b{ZONE}").replace("{ZONE}", safeZoneName(zone)),
                        lore
                ));
            }
        }

        inv.setItem(45, GUIManager.createItem(
                Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to the previous page."))
        ));
        inv.setItem(49, GUIManager.createItem(
                Material.COMPASS,
                tr(player, "button_refresh", "&bRefresh"),
                trList(player, "refresh_lore", List.of("&7Reload this menu."))
        ));
        inv.setItem(53, GUIManager.createItem(
                Material.BARRIER,
                tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, ZoneBrowseHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        int slot = rawSlot;
        Plot plot = holder.getPlot();
        if (plot == null) return;

        if (slot == 45) {
            plugin.effects().playMenuFlip(player);
            plugin.gui().openMain(player);
            return;
        }

        if (slot == 49) {
            open(player, plot);
            return;
        }

        if (slot == 53) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }

        if (slot < 0 || slot >= 45) return;
        if (slot >= holder.getZoneNames().size()) return;

        Zone zone = plot.getZone(holder.getZoneNames().get(slot));
        if (zone == null) {
            plugin.effects().playError(player);
            open(player, plot);
            return;
        }

        if (e.getClick().isLeftClick()) {
            if (zone.getTeleportLocation() != null) {
                var result = plugin.safeTravel().travel(player, zone.getTeleportLocation(),
                        com.aegisguard.travel.SafeTravelService.Kind.ZONE);
                if (!result.isSuccess()) return;
                plugin.effects().playMenuFlip(player);
            }
            return;
        }

        if (!e.getClick().isRightClick()) return;

        if (zone.isRented()) {
            if (!zone.isRentedBy(player.getUniqueId())) {
                plugin.effects().playError(player);
                send(player, "zone_already_rented", "&cThat zone is already rented by another player.");
                return;
            }
            if (!e.isShiftClick()) {
                plugin.gui().zoneTenant().open(player, plot, zone);
                plugin.effects().playMenuFlip(player);
                return;
            }
            plugin.gui().rentConfirm().openZoneRent(player, plot, zone, true, "zone_browse");
            return;
        }

        if (!zone.isListedForRent() || zone.getRentPrice() <= 0.0D) {
            plugin.effects().playError(player);
            send(player, "zone_not_rented", "&7Zone is currently available.");
            return;
        }

        plugin.gui().rentConfirm().openZoneRent(player, plot, zone, false, "zone_browse");
    }

    public void executeRentOrExtend(Player player, Plot plot, Zone zone, boolean extend) {
        if (player == null || plot == null || zone == null) return;

        if (extend) {
            if (!zone.isRentedBy(player.getUniqueId())) {
                plugin.effects().playError(player);
                send(player, "zone_already_rented", "&cThat zone is already rented by another player.");
                return;
            }
            if (!tryCharge(player, zone.getRentPrice())) {
                return;
            }
            zone.extendRent(rentalDurationMillis());
            payOwner(plot, zone.getRentPrice());
            save(plot);
            plugin.effects().playConfirm(player);
            send(player, "zone_extend_success",
                    "&aRental extended for &f{ZONE}&a. New expiry: &f{TIME}"
                            .replace("{ZONE}", safeZoneName(zone))
                            .replace("{TIME}", zone.getRemainingTimeFormatted()));
            plugin.gui().myRentals().open(player);
            return;
        }

        if (zone.isRented() || !zone.isListedForRent() || zone.getRentPrice() <= 0.0D) {
            plugin.effects().playError(player);
            send(player, "zone_already_rented", "&cThat zone is already rented by another player.");
            return;
        }
        if (!tryCharge(player, zone.getRentPrice())) {
            return;
        }

        zone.rentTo(player.getUniqueId(), rentalDurationMillis());
        payOwner(plot, zone.getRentPrice());
        save(plot);
        plugin.effects().playConfirm(player);

        send(player, "zone_rented_success",
                "&aYou are now renting &f{ZONE}&a for &6{PRICE}&a."
                        .replace("{ZONE}", safeZoneName(zone))
                        .replace("{PRICE}", plugin.eco().format(zone.getRentPrice(), CurrencyType.VAULT)));

        Player ownerOnline = Bukkit.getPlayer(plot.getOwner());
        if (ownerOnline != null && !ownerOnline.getUniqueId().equals(player.getUniqueId())) {
            plugin.msg().send(ownerOnline, "zone_rented",
                    java.util.Map.of(
                            "PLAYER", player.getName(),
                            "ZONE", safeZoneName(zone)
                    ));
        }

        plugin.gui().myRentals().open(player);
    }

    private List<Zone> getBrowsableZones(Plot plot, Player viewer) {
        List<Zone> zones = new ArrayList<>();
        if (plot == null || plot.getZones() == null) return zones;

        for (Zone zone : plot.getZones()) {
            if (zone == null) continue;
            if (zone.isListedForRent() || zone.isRented()) {
                zones.add(zone);
            }
        }

        zones.sort(Comparator
                .comparing((Zone zone) -> !zone.isRentedBy(viewer.getUniqueId()))
                .thenComparing((Zone zone) -> !zone.isListedForRent())
                .thenComparing(zone -> safeZoneName(zone).toLowerCase(Locale.ROOT)));
        return zones;
    }

    private boolean tryCharge(Player player, double amount) {
        if (amount <= 0.0D) return true;
        if (plugin.eco().withdraw(player, amount, CurrencyType.VAULT)) {
            return true;
        }

        plugin.effects().playError(player);
        send(player, "zone_rent_insufficient_funds",
                "&cYou do not have enough money to rent that zone.");
        return false;
    }

    private void payOwner(Plot plot, double amount) {
        if (plot == null || amount <= 0.0D) return;

        if (plot.isGroupPlot()) {
            plot.setTreasuryBalance(plot.getTreasuryBalance() + amount);
            PlotGroup group = plugin.groups() == null ? null : plugin.groups().getGroup(plot.getGroupId());
            if (group != null) {
                group.setTreasuryBalance(plot.getTreasuryBalance());
                plugin.groups().setDirty(true);
            }
            return;
        }

        if (plugin.vault() != null) {
            try {
                plugin.vault().deposit(Bukkit.getOfflinePlayer(plot.getOwner()), amount);
            } catch (Throwable ignored) {
            }
        }
    }

    private void save(Plot plot) {
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
    }

    private long rentalDurationMillis() {
        long days = Math.max(1L, plugin.getConfig().getLong("zoning.default_rental_days", 7L));
        return days * 24L * 60L * 60L * 1000L;
    }

    private String safeZoneName(Zone zone) {
        String zoneName = zone == null ? null : zone.getName();
        return (zoneName == null || zoneName.isBlank()) ? "Zone" : zoneName;
    }

    private String tr(Player player, String key, String fallback) {
        return plugin.gui().tr(player, key, fallback);
    }

    private List<String> trList(Player player, String key, List<String> fallback) {
        return plugin.gui().trList(player, key, fallback);
    }

    private void send(Player player, String key, String fallback) {
        String raw = tr(player, key, fallback);
        if (raw == null || raw.isBlank()) return;
        player.sendMessage(GUIManager.color(raw));
    }
}
