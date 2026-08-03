package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.selection.Selection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

        boolean canManage = plot.canManage(player, plugin);

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
                boolean isListed = !isRented && zone.isListedForRent();

                String status = isRented
                        ? tr(player, "zone_status_rented", "&cRented")
                        : isListed
                        ? tr(player, "zone_status_listed", "&eListed")
                        : tr(player, "zone_status_private", "&7Private");

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
                lore.add(GUIManager.color(tr(player, "zone_lore_dimensions", "&7Size: &f{WIDTH}x{HEIGHT}x{DEPTH}")
                        .replace("{WIDTH}", String.valueOf(zone.getWidth()))
                        .replace("{HEIGHT}", String.valueOf(zone.getHeight()))
                        .replace("{DEPTH}", String.valueOf(zone.getDepth()))));
                lore.add(GUIManager.color(tr(player, "zone_lore_price", "&7Price: &6{PRICE}")
                        .replace("{PRICE}", priceStr)));
                lore.add(GUIManager.color(tr(player, "zone_lore_area", "&7Footprint: &a{AREA} blocks")
                        .replace("{AREA}", String.valueOf(zone.getFootprintArea()))));
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
                } else {
                    lore.add(GUIManager.color(tr(player, "zone_toggle_rent_action",
                            zone.isListedForRent()
                                    ? "&eLeft-Click: &7Disable rental listing"
                                    : "&eLeft-Click: &7Enable rental listing")));
                }

                lore.add(GUIManager.color(tr(player, "zone_price_increase_action", "&aShift-Left: &7Increase rent price")));
                lore.add(GUIManager.color(tr(player, "zone_price_decrease_action", "&6Shift-Right: &7Decrease rent price")));
                lore.add(GUIManager.color(tr(player, "zone_delete_action", "&cRight-Click: &7Delete zone")));

                String zoneName = (zone.getName() != null && !zone.getName().isBlank())
                        ? zone.getName()
                        : tr(player, "label_unnamed", "Unnamed Zone");

                String displayName = tr(player, "zone_item_name", "&b{ZONE}").replace("{ZONE}", zoneName);

                inv.setItem(slot, GUIManager.createItem(
                        isRented ? Material.IRON_DOOR : isListed ? Material.EMERALD : Material.OAK_DOOR,
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
                hasSelection ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                GUIManager.color(createName),
                hasSelection ? readyLore : lockedLore
        ));

        inv.setItem(46, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                tr(player, "zone_manager_guide_name", "&eZone Planning Guide"),
                trList(player, "zone_manager_guide_lore", List.of(
                        "&7Private zones divide trusted build space.",
                        "&7Set a price to publish a rental room.",
                        "&7Rented zones open tenant and hotel tools.",
                        " ",
                        "&eLeft: &7listing or tenant tools",
                        "&eShift: &7adjust rent price",
                        "&cRight: &7delete safely"
                ))
        ));

        inv.setItem(47, GUIManager.createItem(
                hasSelection ? Material.RECOVERY_COMPASS : Material.COMPASS,
                tr(player, "zone_selection_status_name", "&bSelection Status"),
                hasSelection
                        ? trList(player, "zone_selection_ready_lore", List.of(
                                "&aTwo corners are ready.",
                                "&7Use Create Zone to reserve this space."
                        ))
                        : trList(player, "zone_selection_missing_lore", List.of(
                                "&cNo complete selection.",
                                "&7Use the Aegis Scepter to select",
                                "&7two corners inside this plot."
                        ))
        ));

        inv.setItem(51, GUIManager.createItem(
                plot.hasBrowsableZonesFor(player) ? Material.EMERALD : Material.GRAY_DYE,
                tr(player, "zone_public_preview_name", "&aPublic Rental Preview"),
                plot.hasBrowsableZonesFor(player)
                        ? trList(player, "zone_public_preview_lore", List.of(
                                "&7Open the same rental catalog",
                                "&7visitors see on this plot.",
                                " ",
                                "&eClick to preview."
                        ))
                        : trList(player, "zone_public_preview_empty_lore", List.of(
                                "&8No zones are publicly listed yet."
                        ))
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

        boolean canManage = plot.canManage(player, plugin);

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

        if (slot == 51) {
            if (plot.hasBrowsableZonesFor(player)) {
                plugin.gui().zoneBrowse().open(player, plot);
                plugin.effects().playMenuFlip(player);
            } else {
                plugin.effects().playError(player);
            }
            return;
        }

        // --- CREATE ZONE ---
        if (slot == 49) {
            if (plugin.selection().hasSelection(player)) {
                createZoneFromSelection(player, plot);
                open(player, plot);
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

            if (e.isShiftClick() && e.getClick().isLeftClick()) {
                double nextPrice = target.getRentPrice() + rentPriceStep();
                target.setRentPrice(nextPrice);
                plugin.store().savePlot(plot);
                plugin.store().setDirty(true);
                plugin.effects().playConfirm(player);
                send(player, "zone_rent_price_set",
                        "&aZone rent updated for &f{ZONE}&a: &6{PRICE}"
                                .replace("{ZONE}", target.getName())
                                .replace("{PRICE}", plugin.eco().format(target.getRentPrice(), CurrencyType.VAULT)));
                open(player, plot);
                return;
            }

            if (e.isShiftClick() && e.getClick().isRightClick()) {
                double nextPrice = Math.max(0.0D, target.getRentPrice() - rentPriceStep());
                target.setRentPrice(nextPrice);
                plugin.store().savePlot(plot);
                plugin.store().setDirty(true);
                plugin.effects().playConfirm(player);
                send(player, "zone_rent_price_set",
                        "&aZone rent updated for &f{ZONE}&a: &6{PRICE}"
                                .replace("{ZONE}", target.getName())
                                .replace("{PRICE}", plugin.eco().format(target.getRentPrice(), CurrencyType.VAULT)));
                open(player, plot);
                return;
            }

            // Right Click: Delete
            if (e.getClick().isRightClick()) {
                if (target.isRented() && !allowDeleteWhileRented()) {
                    plugin.effects().playError(player);
                    send(player, "zone_delete_blocked_rented", "&cEvict the tenant before deleting this zone.");
                    return;
                }

                try {
                    plot.removeZone(target);
                } catch (Throwable ignored) {
                    // If your Plot uses a different API, at least don't crash the GUI
                }

                plugin.store().savePlot(plot);
                plugin.store().setDirty(true);
                plugin.effects().playUnclaim(player);

                send(player, "zone_deleted", "&cZone deleted: &f{ZONE}".replace("{ZONE}", target.getName()));
                open(player, plot);
                return;
            }

            // Left Click: Evict (only if rented)
            if (e.getClick().isLeftClick()) {
                if (target.isRented()) {
                    plugin.gui().zoneTenant().open(player, plot, target);
                    plugin.effects().playMenuFlip(player);
                } else {
                    if (target.isListedForRent()) {
                        target.setRentPrice(0.0D);
                        send(player, "zone_rent_disabled", "&eRental listing disabled for &f{ZONE}".replace("{ZONE}", target.getName()));
                    } else {
                        target.setRentPrice(defaultRentPrice());
                        if (target.getDeposit() <= 0.0D) {
                            target.setDeposit(Math.max(0.0D,
                                    plugin.getConfig().getDouble("zoning.default_deposit", 0.0D)));
                        }
                        if (plugin.territoryLife() != null) {
                            plugin.territoryLife().rememberZoneDeposit(plot.getPlotId(), target.getName(),
                                    target.getDeposit(), target.getHeldDeposit());
                        }
                        send(player, "zone_rent_enabled", "&aRental listing enabled for &f{ZONE}".replace("{ZONE}", target.getName()));
                    }
                    plugin.store().savePlot(plot);
                    plugin.store().setDirty(true);
                    plugin.effects().playConfirm(player);
                    open(player, plot);
                }
            }
        }
    }

    public boolean createZoneFromSelection(Player player, Plot plot) {
        return createZoneFromSelection(player, plot, null, true);
    }

    public boolean createZoneFromSelection(Player player, Plot plot, String requestedName) {
        return createZoneFromSelection(player, plot, requestedName, true);
    }

    private boolean createZoneFromSelection(Player player, Plot plot, String requestedName, boolean clearSelection) {
        if (plot == null) return false;

        Selection selection = plugin.selection().get(player.getUniqueId());
        if (selection == null || !selection.isComplete()) {
            plugin.effects().playError(player);
            send(player, "must_select", "&cYou must select two points first.");
            return false;
        }

        Location l1 = selection.getL1();
        Location l2 = selection.getL2();
        if (l1 == null || l2 == null || l1.getWorld() == null || l2.getWorld() == null || !l1.getWorld().equals(l2.getWorld())) {
            plugin.effects().playError(player);
            send(player, "zone_create_invalid", "&cYour selection must be complete and in a single world.");
            return false;
        }

        if (!l1.getWorld().getName().equalsIgnoreCase(plot.getWorld())) {
            plugin.effects().playError(player);
            send(player, "zone_create_outside_plot", "&cThat selection must stay inside your current plot.");
            return false;
        }

        if (plot.getZones().size() >= maxZonesPerPlot()) {
            plugin.effects().playError(player);
            send(player, "zone_create_limit_reached", "&cYou have reached the maximum number of zones for this plot.");
            return false;
        }

        int minX = Math.min(l1.getBlockX(), l2.getBlockX());
        int minY = Math.min(l1.getBlockY(), l2.getBlockY());
        int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
        int maxY = Math.max(l1.getBlockY(), l2.getBlockY());
        int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());

        int footprint = ((maxX - minX) + 1) * ((maxZ - minZ) + 1);
        if (footprint < minZoneArea()) {
            plugin.effects().playError(player);
            send(player, "zone_create_too_small", "&cThat zone selection is too small.");
            return false;
        }

        if (!plot.containsZoneBounds(minX, minZ, maxX, maxZ)) {
            plugin.effects().playError(player);
            send(player, "zone_create_outside_plot", "&cThat selection must stay inside your current plot.");
            return false;
        }

        String baseName = (requestedName == null || requestedName.isBlank()) ? "Zone" : requestedName.trim();
        String zoneName = plot.nextAvailableZoneName(baseName);
        Zone zone = new Zone(plot, zoneName, minX, minY, minZ, maxX, maxY, maxZ);
        if (plot.overlapsZone(zone, null)) {
            plugin.effects().playError(player);
            send(player, "zone_create_overlap", "&cThat selection overlaps an existing zone.");
            return false;
        }

        zone.setRentPrice(defaultRentPrice());
        plot.addZone(zone);
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
        if (clearSelection) {
            plugin.selection().clearSelection(player);
        }
        plugin.effects().playConfirm(player);
        send(player, "zone_created", "&a✔ Zone ''{ZONE}'' created.".replace("{ZONE}", zoneName));
        return true;
    }

    private int maxZonesPerPlot() {
        return Math.max(1, plugin.getConfig().getInt("zoning.max_zones_per_plot", 10));
    }

    private int minZoneArea() {
        return Math.max(1, plugin.getConfig().getInt("zoning.min_zone_area", 9));
    }

    private double defaultRentPrice() {
        return Math.max(0.0D, plugin.getConfig().getDouble("zoning.default_rent_price", 100.0D));
    }

    private double rentPriceStep() {
        return Math.max(1.0D, plugin.getConfig().getDouble("zoning.rent_price_step", 100.0D));
    }

    private boolean allowDeleteWhileRented() {
        return plugin.getConfig().getBoolean("zoning.allow_delete_while_rented", false);
    }
}
