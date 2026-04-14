package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.util.CompatMaterial;
import com.aegisguard.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ZoneTenantGUI {

    private final AegisGuard plugin;

    public ZoneTenantGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ZoneTenantHolder implements InventoryHolder {
        private final Plot plot;
        private final String zoneName;
        private final List<UUID> guests;
        private final List<UUID> candidates;

        public ZoneTenantHolder(Plot plot, String zoneName, List<UUID> guests, List<UUID> candidates) {
            this.plot = plot;
            this.zoneName = zoneName;
            this.guests = guests == null ? new ArrayList<>() : guests;
            this.candidates = candidates == null ? new ArrayList<>() : candidates;
        }

        public Plot getPlot() { return plot; }
        public String getZoneName() { return zoneName; }
        public List<UUID> getGuests() { return guests; }
        public List<UUID> getCandidates() { return candidates; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot, Zone zone) {
        if (player == null || plot == null || zone == null) return;
        if (!canOpen(player, plot, zone)) {
            plugin.effects().playError(player);
            player.sendMessage(GUIManager.color(tr(player, "no_perm", "&cYou cannot manage this zone.")));
            return;
        }
        boolean roomEditor = canEditRoomSettings(player, zone);

        List<UUID> guests = new ArrayList<>(zone.getGuestAccess().keySet());
        guests.sort(Comparator.comparing(uuid -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            return op.getName() == null ? "~" : op.getName().toLowerCase(Locale.ROOT);
        }));

        List<UUID> candidates = new ArrayList<>();
        if (roomEditor) {
            for (Entity entity : player.getNearbyEntities(20.0, 12.0, 20.0)) {
                if (!(entity instanceof Player other)) continue;
                if (other.getUniqueId().equals(player.getUniqueId())) continue;
                if (zone.isRentedBy(other.getUniqueId())) continue;
                if (plot.canManage(other, plugin)) continue;
                if (zone.hasGuest(other.getUniqueId())) continue;
                candidates.add(other.getUniqueId());
            }
        }
        candidates.sort(Comparator.comparing(uuid -> {
            Player online = Bukkit.getPlayer(uuid);
            return online == null ? Double.MAX_VALUE : online.getLocation().distanceSquared(player.getLocation());
        }));

        Inventory inv = Bukkit.createInventory(
                new ZoneTenantHolder(plot, zone.getName(), guests, candidates),
                54,
                plugin.gui().title(player, "zone_tenant_title", "&3Room Controls: {ZONE}",
                        java.util.Map.of("ZONE", safeZoneName(zone)))
        );

        ItemStack filler = GUIManager.getFiller();
        for (int i = 27; i < 54; i++) inv.setItem(i, filler);

        int slot = 0;
        for (UUID guestId : guests) {
            if (slot >= 18) break;
            inv.setItem(slot++, guestHead(guestId, player, true));
        }

        slot = 18;
        for (UUID candidateId : candidates) {
            if (slot >= 27) break;
            inv.setItem(slot++, guestHead(candidateId, player, false));
        }

        inv.setItem(31, GUIManager.createItem(
                Material.BOOK,
                tr(player, "zone_tenant_info_name", "&eZone Details"),
                buildInfoLore(player, plot, zone)
        ));
        inv.setItem(29, GUIManager.createItem(
                Material.ENDER_PEARL,
                tr(player, "zone_tenant_teleport_name", "&bTeleport to Room"),
                trList(player, "zone_tenant_teleport_lore", List.of(
                        "&7Jump straight to this rented room",
                        "&7or subplot teleport point."
                ))
        ));
        inv.setItem(30, roomEditor
                ? GUIManager.createItem(
                        CompatMaterial.resolve("RESPAWN_ANCHOR", "BEACON"),
                        tr(player, "zone_tenant_set_spawn_name", "&aSet Room Spawn"),
                        trList(player, "zone_tenant_set_spawn_lore", List.of(
                                "&7Set your current position as the",
                                "&7teleport point for this room."
                        )))
                : lockedEditorItem(player, CompatMaterial.resolve("RESPAWN_ANCHOR", "BEACON"), "zone_tenant_set_spawn_name", "&aSet Room Spawn"));
        inv.setItem(32, roomEditor
                ? toggleItem(player, zone.isHotelMode(),
                        "zone_tenant_hotel_mode_name", "&6Hotel Mode",
                        "zone_tenant_hotel_mode_lore", List.of(
                                "&7When enabled, approved guests can",
                                "&7use the room based on your toggles."
                        ))
                : lockedEditorItem(player, Material.GRAY_DYE, "zone_tenant_hotel_mode_name", "&6Hotel Mode"));
        inv.setItem(33, roomEditor
                ? toggleItem(player, zone.getFlag("guest_interact", true),
                        "zone_tenant_guest_interact_name", "&eGuest Interact",
                        "zone_tenant_guest_interact_lore", List.of(
                                "&7Allow approved guests to use doors,",
                                "&7buttons, and general interactions."
                        ))
                : lockedEditorItem(player, Material.GRAY_DYE, "zone_tenant_guest_interact_name", "&eGuest Interact"));
        inv.setItem(34, roomEditor
                ? toggleItem(player, zone.getFlag("guest_containers", true),
                        "zone_tenant_guest_containers_name", "&eGuest Containers",
                        "zone_tenant_guest_containers_lore", List.of(
                                "&7Allow approved guests to open chests",
                                "&7and other storage in this room."
                        ))
                : lockedEditorItem(player, Material.GRAY_DYE, "zone_tenant_guest_containers_name", "&eGuest Containers"));
        inv.setItem(35, roomEditor
                ? toggleItem(player, zone.getFlag("guest_build", false),
                        "zone_tenant_guest_build_name", "&eGuest Build",
                        "zone_tenant_guest_build_lore", List.of(
                                "&7Allow approved guests to place and",
                                "&7break blocks in this rented room."
                        ))
                : lockedEditorItem(player, Material.GRAY_DYE, "zone_tenant_guest_build_name", "&eGuest Build"));

        inv.setItem(45, GUIManager.createItem(Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to the previous page."))));
        inv.setItem(49, GUIManager.createItem(Material.COMPASS,
                tr(player, "button_refresh", "&bRefresh"),
                trList(player, "refresh_lore", List.of("&7Reload this menu."))));
        if (plot.canManage(player, plugin) && zone.isRented()) {
            inv.setItem(50, GUIManager.createItem(Material.IRON_DOOR,
                    tr(player, "zone_tenant_evict_name", "&cEvict Tenant"),
                    trList(player, "zone_tenant_evict_lore", List.of(
                            "&7Remove the current renter and",
                            "&7clear this room's guest access."
                    ))));
        }
        inv.setItem(53, GUIManager.createItem(Material.BARRIER,
                tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, ZoneTenantHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (plot == null) return;
        Zone zone = plot.getZone(holder.getZoneName());
        if (zone == null) {
            player.closeInventory();
            return;
        }
        if (!canOpen(player, plot, zone)) {
            plugin.effects().playError(player);
            player.closeInventory();
            return;
        }

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        if (slot == 45) {
            if (plot.canManage(player, plugin)) plugin.gui().zoning().open(player, plot);
            else plugin.gui().zoneBrowse().open(player, plot);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == 49) {
            open(player, plot, zone);
            return;
        }
        if (slot == 53) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }
        if (slot == 29) {
            Location target = zone.getTeleportLocation();
            if (target != null) {
                player.closeInventory();
                TeleportUtil.safeTeleport(plugin, player, target);
                plugin.effects().playTeleport(player);
            } else {
                plugin.effects().playError(player);
            }
            return;
        }
        if (slot == 30) {
            if (!canEditRoomSettings(player, zone)) {
                plugin.effects().playError(player);
                send(player, "zone_tenant_renter_only", "&cOnly the active renter can change this room's guest settings.");
                return;
            }
            if (!zone.isInside(player.getLocation())) {
                plugin.effects().playError(player);
                send(player, "zone_tenant_spawn_inside", "&cStand inside the rented room before setting its teleport point.");
                return;
            }
            zone.setSpawnLocation(player.getLocation());
            save(plot);
            plugin.effects().playConfirm(player);
            send(player, "zone_tenant_spawn_set", "&aRoom teleport point updated.");
            open(player, plot, zone);
            return;
        }
        if (slot == 32) {
            if (!canEditRoomSettings(player, zone)) {
                plugin.effects().playError(player);
                send(player, "zone_tenant_renter_only", "&cOnly the active renter can change this room's guest settings.");
                return;
            }
            zone.setFlag("hotel_mode", !zone.isHotelMode());
            save(plot);
            plugin.effects().playConfirm(player);
            open(player, plot, zone);
            return;
        }
        if (slot == 33) {
            if (!canEditRoomSettings(player, zone)) {
                plugin.effects().playError(player);
                send(player, "zone_tenant_renter_only", "&cOnly the active renter can change this room's guest settings.");
                return;
            }
            zone.setFlag("guest_interact", !zone.getFlag("guest_interact", true));
            save(plot);
            plugin.effects().playConfirm(player);
            open(player, plot, zone);
            return;
        }
        if (slot == 34) {
            if (!canEditRoomSettings(player, zone)) {
                plugin.effects().playError(player);
                send(player, "zone_tenant_renter_only", "&cOnly the active renter can change this room's guest settings.");
                return;
            }
            zone.setFlag("guest_containers", !zone.getFlag("guest_containers", true));
            save(plot);
            plugin.effects().playConfirm(player);
            open(player, plot, zone);
            return;
        }
        if (slot == 35) {
            if (!canEditRoomSettings(player, zone)) {
                plugin.effects().playError(player);
                send(player, "zone_tenant_renter_only", "&cOnly the active renter can change this room's guest settings.");
                return;
            }
            zone.setFlag("guest_build", !zone.getFlag("guest_build", false));
            save(plot);
            plugin.effects().playConfirm(player);
            open(player, plot, zone);
            return;
        }
        if (slot == 50 && plot.canManage(player, plugin) && zone.isRented()) {
            zone.evict();
            save(plot);
            plugin.effects().playConfirm(player);
            send(player, "zone_evicted", "&eTenant evicted from {ZONE}.".replace("{ZONE}", safeZoneName(zone)));
            plugin.gui().zoning().open(player, plot);
            return;
        }

        if (slot >= 0 && slot < holder.getGuests().size() && slot < 18) {
            if (!canEditRoomSettings(player, zone)) {
                plugin.effects().playError(player);
                send(player, "zone_tenant_renter_only", "&cOnly the active renter can change this room's guest settings.");
                return;
            }
            UUID guestId = holder.getGuests().get(slot);
            zone.removeGuest(guestId);
            save(plot);
            plugin.effects().playConfirm(player);
            send(player, "zone_tenant_guest_removed", "&eRemoved a room guest.");
            open(player, plot, zone);
            return;
        }

        if (slot >= 18 && slot < 27) {
            if (!canEditRoomSettings(player, zone)) {
                plugin.effects().playError(player);
                send(player, "zone_tenant_renter_only", "&cOnly the active renter can change this room's guest settings.");
                return;
            }
            int index = slot - 18;
            if (index >= holder.getCandidates().size()) return;
            UUID guestId = holder.getCandidates().get(index);
            zone.addGuest(guestId);
            save(plot);
            plugin.effects().playConfirm(player);
            send(player, "zone_tenant_guest_added", "&aAdded a room guest.");
            open(player, plot, zone);
        }
    }

    private boolean canOpen(Player player, Plot plot, Zone zone) {
        if (player == null || plot == null || zone == null) return false;
        return plot.canManage(player, plugin) || zone.isRentedBy(player.getUniqueId());
    }

    private boolean canEditRoomSettings(Player player, Zone zone) {
        return player != null && zone != null && zone.isRentedBy(player.getUniqueId());
    }

    private void save(Plot plot) {
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
    }

    private ItemStack guestHead(UUID targetId, Player viewer, boolean existingGuest) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(targetId);
        String name = offline.getName() != null ? offline.getName() : targetId.toString().substring(0, 8);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            try { meta.setOwningPlayer(offline); } catch (Throwable ignored) {}
            meta.setDisplayName(GUIManager.color((existingGuest ? "&a" : "&b") + name));
            List<String> lore = new ArrayList<>();
            if (existingGuest) {
                lore.add(GUIManager.color(tr(viewer, "zone_tenant_guest_remove_lore",
                        "&7Click to remove this guest from the room.")));
            } else {
                lore.add(GUIManager.color(tr(viewer, "zone_tenant_guest_add_lore",
                        "&7Click to allow this nearby player into the room.")));
            }
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack toggleItem(Player player, boolean enabled, String nameKey, String fallbackName, String loreKey, List<String> fallbackLore) {
        return GUIManager.createItem(
                enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                tr(player, nameKey, fallbackName) + GUIManager.color(enabled ? " &aON" : " &cOFF"),
                trList(player, loreKey, fallbackLore)
        );
    }

    private ItemStack lockedEditorItem(Player player, Material material, String nameKey, String fallbackName) {
        return GUIManager.createItem(
                material,
                tr(player, nameKey, fallbackName),
                trList(player, "zone_tenant_locked_lore", List.of(
                        "&7This room setting is managed by",
                        "&7the current renter."
                ))
        );
    }

    private List<String> buildInfoLore(Player player, Plot plot, Zone zone) {
        List<String> lore = new ArrayList<>();
        lore.add(GUIManager.color(tr(player, "zone_lore_dimensions", "&7Size: &f{WIDTH}x{HEIGHT}x{DEPTH}")
                .replace("{WIDTH}", String.valueOf(zone.getWidth()))
                .replace("{HEIGHT}", String.valueOf(zone.getHeight()))
                .replace("{DEPTH}", String.valueOf(zone.getDepth()))));
        lore.add(GUIManager.color(tr(player, "zone_tenant_guest_count", "&7Approved guests: &f{COUNT}")
                .replace("{COUNT}", String.valueOf(zone.getGuestAccess().size()))));
        lore.add(GUIManager.color(tr(player, "zone_tenant_mode_line", "&7Hotel mode: &f{STATE}")
                .replace("{STATE}", zone.isHotelMode() ? "&aEnabled" : "&cDisabled")));
        if (zone.isRented() && zone.getRenter() != null) {
            OfflinePlayer renter = Bukkit.getOfflinePlayer(zone.getRenter());
            lore.add(GUIManager.color(tr(player, "zone_lore_tenant", "&7Tenant: &f{TENANT}")
                    .replace("{TENANT}", renter.getName() == null ? "Unknown" : renter.getName())));
        }
        lore.add(GUIManager.color(tr(player, "zone_tenant_spawn_line", "&7Room spawn: &f{STATE}")
                .replace("{STATE}", zone.getSpawnLocation() == null ? "&cUnset" : "&aSet")));
        return lore;
    }

    private String safeZoneName(Zone zone) {
        return zone == null || zone.getName() == null || zone.getName().isBlank() ? "Zone" : zone.getName();
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
        player.sendMessage(plugin.msg().prefix() + raw);
    }
}
