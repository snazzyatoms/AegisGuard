package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
 * - Fully localized for dynamic language switching.
 */
public class ZoningGUI {

    private final AegisGuard plugin;

    public ZoningGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ZoningHolder implements InventoryHolder {
        private final Plot plot;
        public ZoningHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        String title = GUIManager.safeText(
                line(player, "zone_gui_title", "§3Sub-Claim Manager"),
                "§3Sub-Claim Manager"
        );
        Inventory inv = Bukkit.createInventory(new ZoningHolder(plot), 54, title);

        // --- 1. LIST ZONES ---
        List<Zone> zones = plot.getZones();
        int slot = 0;

        for (Zone zone : zones) {
            if (slot >= 45) break;

            boolean isRented = zone.isRented();
            String status = isRented
                    ? line(player, "zone_status_rented", "§cRented")
                    : line(player, "zone_status_available", "§aAvailable");

            String renterName = "None";
            String timeRemaining = "";

            if (isRented) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(zone.getRenter());
                renterName = (op.getName() != null) ? op.getName() : "Unknown";
                timeRemaining = zone.getRemainingTimeFormatted();
            }

            // Localized Lore
            List<String> lore = new ArrayList<>();
            lore.add("§7Status: " + status);
            lore.add("§7Price: §6" + plugin.eco().format(
                    zone.getRentPrice(),
                    com.aegisguard.economy.CurrencyType.VAULT
            ));
            lore.add(" ");

            if (isRented) {
                lore.add("§7Tenant: §f" + renterName);
                lore.add("§7Expires: §f" + timeRemaining);
                lore.add(" ");

                String evictAction = line(player, "zone_evict_action", "§eLeft-Click to Evict");
                if (!evictAction.isEmpty()) {
                    lore.add(evictAction);
                }
            }

            String deleteAction = line(player, "zone_delete_action", "§cRight-Click to Delete");
            if (!deleteAction.isEmpty()) {
                lore.add(deleteAction);
            }

            inv.setItem(slot, GUIManager.createItem(
                    isRented ? Material.IRON_DOOR : Material.OAK_DOOR,
                    "§b" + zone.getName(),
                    lore
            ));

            slot++;
        }

        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        // --- 2. ACTIONS ---

        // Create Button (Slot 49)
        boolean hasSelection = plugin.selection().hasSelection(player);

        String createName = line(player, "button_zone_create", "§aCreate New Zone");
        List<String> readyLore = lines(player, "zone_create_ready_lore", List.of(
                "§7Your wand selection will be used",
                "§7to define this sub-claim.",
                "",
                "§eClick to create a new zone."
        ));
        List<String> lockedLore = lines(player, "zone_create_locked_lore", List.of(
                "§cYou must select an area",
                "§cwith the Aegis wand first."
        ));

        inv.setItem(49, GUIManager.createItem(
                Material.EMERALD_BLOCK,
                createName,
                hasSelection ? readyLore : lockedLore
        ));

        // Back (Slot 45)
        String backName = line(player, "button_back", "§fBack");
        List<String> backLore = lines(player, "back_lore", List.of("§7Return to Aegis menu."));
        inv.setItem(45, GUIManager.createItem(Material.ARROW, backName, backLore));

        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, ZoningHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();

        // --- NAVIGATION ---
        if (e.getSlot() == 45) {
            plugin.gui().openMain(player);
            return;
        }

        // --- CREATE ZONE ---
        if (e.getSlot() == 49) {
            if (plugin.selection().hasSelection(player)) {
                String name = "Zone-" + (plot.getZones().size() + 1);
                player.performCommand("ag zone create " + name + " 100");
                player.closeInventory();
            } else {
                plugin.effects().playError(player);
                player.sendMessage(plugin.msg().get(player, "must_select"));
            }
            return;
        }

        // --- ZONE MANAGEMENT ---
        if (e.getSlot() < 45
                && e.getCurrentItem().getType() != Material.AIR
                && e.getCurrentItem().getType() != Material.GRAY_STAINED_GLASS_PANE) {

            String name = ChatColor.stripColor(e.getCurrentItem().getItemMeta().getDisplayName());
            Zone target = null;

            for (Zone z : plot.getZones()) {
                if (z.getName().equalsIgnoreCase(name)) {
                    target = z;
                    break;
                }
            }

            if (target == null) return;

            // Delete
            if (e.isRightClick()) {
                plot.removeZone(target);
                plugin.store().setDirty(true);
                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_BREAK, 1f, 1f);
                plugin.msg().send(player, "zone_deleted", Map.of("ZONE", target.getName()));
                open(player, plot);
            }
            // Evict
            else if (e.isLeftClick()) {
                if (target.isRented()) {
                    target.evict();
                    plugin.store().setDirty(true);
                    plugin.msg().send(player, "zone_evicted", Map.of("ZONE", target.getName()));
                    open(player, plot);
                } else {
                    plugin.msg().send(player, "zone_not_rented");
                }
            }
        }
    }

    // --------------------------------------------------
    // LANGUAGE HELPERS (New Engine – hardened)
    // --------------------------------------------------

    private String line(Player player, String key, String fallback) {
        String raw;
        try {
            raw = plugin.msg().get(player, key);
        } catch (Throwable ignored) {
            raw = null;
        }

        if (raw == null || raw.isEmpty()
                || raw.equalsIgnoreCase(key)
                || raw.startsWith("[Missing:")) {
            return fallback;
        }
        return raw;
    }

    private String line(Player player, String key, String fallback, Map<String, String> placeholders) {
        String raw;
        try {
            raw = plugin.msg().get(player, key, placeholders);
        } catch (Throwable ignored) {
            raw = null;
        }

        if (raw == null || raw.isEmpty()
                || raw.equalsIgnoreCase(key)
                || raw.startsWith("[Missing:")) {
            return fallback;
        }
        return raw;
    }

    private List<String> lines(Player player, String key, List<String> fallback) {
        List<String> result;
        try {
            result = plugin.msg().getList(player, key);
        } catch (Throwable ignored) {
            result = null;
        }

        if (result == null || result.isEmpty()) {
            return fallback;
        }

        List<String> cleaned = new ArrayList<>();
        for (String s : result) {
            if (s == null) continue;
            if (s.startsWith("[Missing:")) continue;
            cleaned.add(s);
        }

        if (cleaned.isEmpty()) {
            return fallback;
        }
        return cleaned;
    }
}
