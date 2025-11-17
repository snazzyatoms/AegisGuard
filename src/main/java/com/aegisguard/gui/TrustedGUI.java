package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotStore;
import com.aegisguard.data.PlotStore.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TrustedGUI
 * - Manages the trusted player menus.
 *
 * --- UPGRADE NOTES ---
 * - CRITICAL FIX: Now correctly handles multiple plots via a "Plot Selector" GUI.
 * - CRITICAL FIX: Now uses InventoryHolders for all 4 GUI states.
 * - CRITICAL FIX: All data changes now go through PlotStore's API (addTrusted/removeTrusted)
 * to be compatible with the async auto-saver (isDirty flag).
 * - RELIABILITY FIX: All clicks are now slot-based.
 * - CLEANUP: Removed all local helper methods (createItem, m, l, sounds).
 */
public class TrustedGUI {

    private final AegisGuard plugin;

    public TrustedGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /* -----------------------------
     * Inventory Holders
     * ----------------------------- */
    private static class PlotSelectorHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static class TrustedMenuHolder implements InventoryHolder {
        private final Plot plot;
        public TrustedMenuHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    private static class AddTrustedHolder implements InventoryHolder {
        private final Plot plot;
        public AddTrustedHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    private static class RemoveTrustedHolder implements InventoryHolder {
        private final Plot plot;
        public RemoveTrustedHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    /* -----------------------------
     * Main Entry Point
     * ----------------------------- */

    /**
     * Opens the Trusted GUI system.
     * This is the main entry point from PlayerGUI.
     */
    public void open(Player owner) {
        List<Plot> plots = plugin.store().getPlots(owner.getUniqueId());
        if (plots == null || plots.isEmpty()) {
            plugin.msg().send(owner, "no_plot_here");
            return;
        }

        // --- CRITICAL FIX ---
        // If the player has more than one plot, ask them which one to manage.
        if (plots.size() > 1) {
            openPlotSelector(owner, plots);
        } else {
            // Otherwise, open the menu for their only plot.
            openTrustedMenu(owner, plots.get(0));
        }
    }

    /* -----------------------------
     * GUI: Plot Selector
     * ----------------------------- */
    private void openPlotSelector(Player owner, List<Plot> plots) {
        String title = GUIManager.safeText(plugin.msg().get(owner, "trusted_plot_selector_title"), "§bSelect a Plot to Manage");
        Inventory inv = Bukkit.createInventory(new PlotSelectorHolder(), 54, title);

        for (int i = 0; i < plots.size(); i++) {
            if (i >= 54) break;
            Plot plot = plots.get(i);
            inv.setItem(i, GUIManager.icon(
                    Material.GRASS_BLOCK,
                    "§aPlot #" + (i + 1),
                    List.of(
                            "§7World: §f" + plot.getWorld(),
                            "§7Bounds: §e(" + plot.getX1() + ", " + plot.getZ1() + ")",
                            "§7Click to manage trusted players."
                    )
            ));
        }
        owner.openInventory(inv);
        plugin.sounds().playMenuOpen(owner);
    }

    /* -----------------------------
     * GUI: Main Trusted Menu
     * ----------------------------- */
    public void openTrustedMenu(Player owner, Plot plot) {
        String title = GUIManager.safeText(plugin.msg().get(owner, "trusted_menu_title"), "§bAegisGuard — Trusted");
        Inventory inv = Bukkit.createInventory(new TrustedMenuHolder(plot), 54, title);

        // Trusted players list
        int slot = 0;
        for (UUID trustedId : plot.getTrusted()) {
            if (slot >= 45) break;

            OfflinePlayer trusted = Bukkit.getOfflinePlayer(trustedId);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();

            if (meta != null) {
                meta.setOwningPlayer(trusted);
                String playerName = trusted.getName() != null ? trusted.getName() : "Unknown";
                meta.setDisplayName(GUIManager.safeText(plugin.msg().get(owner, "trusted_head_name"), "§a{PLAYER}")
                        .replace("{PLAYER}", playerName));
                meta.setLore(plugin.msg().getList(owner, "trusted_menu_lore", List.of("§7Click a head to remove from trusted.")));
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        // SLOT 45: Add Trusted
        inv.setItem(45, GUIManager.icon(
                Material.EMERALD,
                GUIManager.safeText(plugin.msg().get(owner, "button_add_trusted"), "§aAdd Trusted"),
                plugin.msg().getList(owner, "add_trusted_lore", List.of("§7Pick an online player to add."))));

        // SLOT 46: Remove Trusted
        inv.setItem(46, GUIManager.icon(
                Material.REDSTONE_BLOCK,
                GUIManager.safeText(plugin.msg().get(owner, "button_remove_trusted"), "§cRemove Trusted"),
                plugin.msg().getList(owner, "remove_trusted_lore", List.of("§7Choose someone to remove."))));

        // SLOT 47: Roles (placeholder)
        inv.setItem(47, GUIManager.icon(
                Material.NAME_TAG,
                GUIManager.safeText(plugin.msg().get(owner, "button_roles"), "§eRoles (Soon)"),
                plugin.msg().getList(owner, "roles_lore", List.of("§7Role-based permissions are coming."))));

        // SLOT 51: Info & Guide
        inv.setItem(51, GUIManager.icon(
                Material.WRITABLE_BOOK,
                GUIManager.safeText(plugin.msg().get(owner, "button_info"), "§fInfo"),
                plugin.msg().getList(owner, "info_trusted_lore", List.of("§7Trusted players can help build on your land."))));

        // SLOT 52: Back
        inv.setItem(52, GUIManager.icon(
                Material.ARROW,
                GUIManager.safeText(plugin.msg().get(owner, "button_back"), "§7Back"),
                plugin.msg().getList(owner, "back_lore", List.of("§7Go back to the main menu."))));

        // SLOT 53: Exit
        inv.setItem(53, GUIManager.icon(
                Material.BARRIER,
                GUIManager.safeText(plugin.msg().get(owner, "button_exit"), "§cExit"),
                plugin.msg().getList(owner, "exit_lore", List.of("§7Close this menu."))));

        owner.openInventory(inv);
        plugin.sounds().playMenuOpen(owner);
    }

    /* -----------------------------
     * GUI: Add Trusted Menu
     * ----------------------------- */
    private void openAddMenu(Player player, Plot plot) {
        String addTitle = GUIManager.safeText(plugin.msg().get(player, "add_trusted_title"), "§bAegisGuard — Add Trusted");
        Inventory addMenu = Bukkit.createInventory(new AddTrustedHolder(plot), 54, addTitle);

        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 54) break;
            // Don't show the owner or already-trusted players
            if (online.getUniqueId().equals(player.getUniqueId())) continue;
            if (plot.getTrusted().contains(online.getUniqueId())) continue;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(online);
                meta.setDisplayName("§e" + online.getName());
                meta.setLore(plugin.msg().getList(player, "add_trusted_lore", List.of("§7Click to add to trusted.")));
                head.setItemMeta(meta);
            }
            addMenu.setItem(slot++, head);
        }
        player.openInventory(addMenu);
        plugin.sounds().playMenuFlip(player);
    }

    /* -----------------------------
     * GUI: Remove Trusted Menu
     * ----------------------------- */
    private void openRemoveMenu(Player player, Plot plot) {
        String removeTitle = GUIManager.safeText(plugin.msg().get(player, "remove_trusted_title"), "§bAegisGuard — Remove Trusted");
        Inventory removeMenu = Bukkit.createInventory(new RemoveTrustedHolder(plot), 54, removeTitle);

        int slot = 0;
        for (UUID trustedId : plot.getTrusted()) {
            if (slot >= 54) break;
            OfflinePlayer trusted = Bukkit.getOfflinePlayer(trustedId);

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(trusted);
                String name = trusted.getName() != null ? trusted.getName() : "Unknown";
                meta.setDisplayName("§c" + name);
                meta.setLore(plugin.msg().getList(player, "remove_trusted_lore", List.of("§7Click to remove from trusted.")));
                head.setItemMeta(meta);
            }
            removeMenu.setItem(slot++, head);
        }
        player.openInventory(removeMenu);
        plugin.sounds().playMenuFlip(player);
    }

    /* -----------------------------
     * Click Handlers (Called by GUIListener)
     * ----------------------------- */

    public void handlePlotSelectorClick(Player player, InventoryClickEvent e, PlotSelectorHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        List<Plot> plots = plugin.store().getPlots(player.getUniqueId());
        int slot = e.getSlot();

        if (slot >= 0 && slot < plots.size()) {
            Plot selectedPlot = plots.get(slot);
            openTrustedMenu(player, selectedPlot);
            plugin.sounds().playMenuFlip(player);
        }
    }

    public void handleTrustedMenuClick(Player player, InventoryClickEvent e, TrustedMenuHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot(); // Get context from holder
        if (plot == null) {
            player.closeInventory();
            return;
        }

        int slot = e.getSlot();

        // Handle player head clicks (quick remove)
        if (slot < 45 && e.getCurrentItem().getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
            if (meta != null) {
                OfflinePlayer target = meta.getOwningPlayer();
                if (target != null) {
                    // --- ASYNC-SAFE DATA CHANGE ---
                    plugin.store().removeTrusted(plot.getOwner(), plot.getPlotId(), target.getUniqueId());
                    String name = target.getName() != null ? target.getName() : "Unknown";
                    plugin.msg().send(player, "trusted_removed", Map.of("PLAYER", name));
                    plugin.sounds().playMenuFlip(player);
                    openTrustedMenu(player, plot); // Refresh
                }
            }
            return;
        }

        // Handle navigation clicks
        switch (slot) {
            case 45: // Add Trusted
                openAddMenu(player, plot);
                plugin.sounds().playMenuFlip(player);
                break;
            case 46: // Remove Trusted
                openRemoveMenu(player, plot);
                plugin.sounds().playMenuFlip(player);
                break;
            case 47: // Roles (Placeholder)
                plugin.sounds().playError(player);
                break;
            case 51: // Info
                for (String line : plugin.msg().getList(player, "info_trusted_lore", List.of("§7Trusted players..."))) {
                    player.sendMessage(line);
                }
                plugin.sounds().playMenuFlip(player);
                break;
            case 52: // Back
                plugin.gui().openMain(player);
                plugin.sounds().playMenuFlip(player);
                break;
            case 53: // Exit
                player.closeInventory();
                plugin.sounds().playMenuClose(player);
                break;
            default: // Ignore filler
                break;
        }
    }

    public void handleAddTrustedClick(Player player, InventoryClickEvent e, AddTrustedHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() != Material.PLAYER_HEAD) return;

        Plot plot = holder.getPlot();
        if (plot == null) {
            player.closeInventory();
            return;
        }

        SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
        if (meta != null) {
            OfflinePlayer target = meta.getOwningPlayer();
            if (target != null) {
                // --- ASYNC-SAFE DATA CHANGE ---
                plugin.store().addTrusted(plot.getOwner(), plot.getPlotId(), target.getUniqueId());
                String name = target.getName() != null ? target.getName() : "Unknown";
                plugin.msg().send(player, "trusted_added", Map.of("PLAYER", name));
                plugin.sounds().playMenuFlip(player);
                openTrustedMenu(player, plot); // Go back to main menu
            }
        }
    }

    public void handleRemoveTrustedClick(Player player, InventoryClickEvent e, RemoveTrustedHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() != Material.PLAYER_HEAD) return;

        Plot plot = holder.getPlot();
        if (plot == null) {
            player.closeInventory();
            return;
        }

        SkullMeta meta = (SkullMeta) e.getCurrentItem().getItemMeta();
        if (meta != null) {
            OfflinePlayer target = meta.getOwningPlayer();
            if (target != null) {
                // --- ASYNC-SAFE DATA CHANGE ---
                plugin.store().removeTrusted(plot.getOwner(), plot.getPlotId(), target.getUniqueId());
                String name = target.getName() != null ? target.getName() : "Unknown";
                plugin.msg().send(player, "trusted_removed", Map.of("PLAYER", name));
                plugin.sounds().playMenuFlip(player);
                openRemoveMenu(player, plot); // Refresh this menu
            }
        }
    }

    /* -----------------------------
     * Helpers
     * --- REMOVED ---
     * (All helpers are now in GUIManager or SoundUtil)
     * ----------------------------- */
}
