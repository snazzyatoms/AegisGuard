package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * ExpansionRequestGUI
 * - Allows players to submit requests to increase their plot size.
 */
public class ExpansionRequestGUI {

    private final AegisGuard plugin;

    public ExpansionRequestGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ExpansionHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new ExpansionHolder(), 27, "§d§lLand Expansion Request");

        // Background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        boolean validPlot = (plot != null && plot.getOwner().equals(player.getUniqueId()));

        // --- TIER 1: SMALL (+5) ---
        inv.setItem(11, GUIManager.createItem(Material.IRON_PICKAXE, "§aSmall Expansion", List.of(
            "§7Increase radius by §a+5 blocks§7.",
            " ",
            validPlot ? "§eClick to submit request." : "§cMust stand in your plot."
        )));

        // --- TIER 2: MEDIUM (+15) ---
        inv.setItem(13, GUIManager.createItem(Material.GOLDEN_PICKAXE, "§6Medium Expansion", List.of(
            "§7Increase radius by §6+15 blocks§7.",
            " ",
            validPlot ? "§eClick to submit request." : "§cMust stand in your plot."
        )));

        // --- TIER 3: LARGE (+30) ---
        inv.setItem(15, GUIManager.createItem(Material.DIAMOND_PICKAXE, "§bLarge Expansion", List.of(
            "§7Increase radius by §b+30 blocks§7.",
            " ",
            validPlot ? "§eClick to submit request." : "§cMust stand in your plot."
        )));

        // --- ADMIN VIEW (Slot 26) ---
        if (plugin.isAdmin(player)) {
            inv.setItem(26, GUIManager.createItem(Material.COMPASS, "§cView Pending Requests (Admin)", List.of("§7Manage expansion queue.")));
        }

        // Navigation
        inv.setItem(22, GUIManager.createItem(Material.NETHER_STAR, "§fBack to Menu", List.of("§7Return to the main menu.")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        int currentRadius = (plot != null) ? (plot.getX2() - plot.getX1()) / 2 : 0;

        switch (e.getSlot()) {
            case 11: // Small
                if (validatePlot(player, plot)) {
                    plugin.getExpansionRequestManager().createRequest(player, plot, currentRadius + 5);
                    player.closeInventory();
                    plugin.effects().playConfirm(player);
                }
                break;

            case 13: // Medium
                if (validatePlot(player, plot)) {
                    plugin.getExpansionRequestManager().createRequest(player, plot, currentRadius + 15);
                    player.closeInventory();
                    plugin.effects().playConfirm(player);
                }
                break;

            case 15: // Large
                if (validatePlot(player, plot)) {
                    plugin.getExpansionRequestManager().createRequest(player, plot, currentRadius + 30);
                    player.closeInventory();
                    plugin.effects().playConfirm(player);
                }
                break;

            case 26: // Admin View
                if (plugin.isAdmin(player)) {
                    plugin.gui().expansionAdmin().open(player);
                    plugin.effects().playMenuFlip(player);
                }
                break;

            case 22: // Back
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
                break;
        }
    }

    private boolean validatePlot(Player player, Plot plot) {
        if (plot == null || !plot.getOwner().equals(player.getUniqueId())) {
            plugin.msg().send(player, "no_plot_here");
            plugin.effects().playError(player);
            return false;
        }
        
        // Check if pending request exists
        if (plugin.getExpansionRequestManager().hasPendingRequest(player.getUniqueId())) {
            player.sendMessage("§cYou already have a pending request.");
            plugin.effects().playError(player);
            return false;
        }
        
        return true;
    }
}
