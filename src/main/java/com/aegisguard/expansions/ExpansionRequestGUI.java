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

/**
 * ExpansionRequestGUI
 * - Allows players to submit requests to increase their plot size.
 * - Updated to use CodexEngine (v1.2.4)
 */
public class ExpansionRequestGUI {

    private final AegisGuard plugin;

    public ExpansionRequestGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ExpansionHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null; // Marker holder
        }
    }

    public void open(Player player) {
        // ✅ FIX: Use GUIManager.title() so & / hex colors render in inventory titles
        // and missing keys don’t leak into the UI.
        String title = plugin.gui().title(player, "expansion_gui_title", "&dLand Expansion Request");

        Inventory inv = Bukkit.createInventory(new ExpansionHolder(), 36, title);

        // Background filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, filler);
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());

        // --- TIER 1: (+5) ---
        inv.setItem(11, GUIManager.createItem(
                Material.WOODEN_PICKAXE,
                plugin.codex().tr(player, "expansion_tier1_name"),
                plugin.codex().trList(player, "expansion_tier1_lore")
        ));

        // --- TIER 2: (+10) ---
        inv.setItem(12, GUIManager.createItem(
                Material.STONE_PICKAXE,
                plugin.codex().tr(player, "expansion_tier2_name"),
                plugin.codex().trList(player, "expansion_tier2_lore")
        ));

        // --- TIER 3: (+20) ---
        inv.setItem(13, GUIManager.createItem(
                Material.IRON_PICKAXE,
                plugin.codex().tr(player, "expansion_tier3_name"),
                plugin.codex().trList(player, "expansion_tier3_lore")
        ));

        // --- TIER 4: (+35) ---
        inv.setItem(14, GUIManager.createItem(
                Material.GOLDEN_PICKAXE,
                plugin.codex().tr(player, "expansion_tier4_name"),
                plugin.codex().trList(player, "expansion_tier4_lore")
        ));

        // --- TIER 5: (+50) ---
        inv.setItem(15, GUIManager.createItem(
                Material.DIAMOND_PICKAXE,
                plugin.codex().tr(player, "expansion_tier5_name"),
                plugin.codex().trList(player, "expansion_tier5_lore")
        ));

        // --- ADMIN VIEW (Slot 31) ---
        if (plugin.isAdmin(player)) {
            inv.setItem(31, GUIManager.createItem(
                    plugin.codex().tr(player, "button_view_requests_admin"),
                    Material.COMPASS,
                    plugin.codex().trList(player, "view_requests_admin_lore")
            ));
        }

        // --- BACK BUTTON (Slot 27) ---
        inv.setItem(27, GUIManager.createItem(
                Material.NETHER_STAR,
                plugin.codex().tr(player, "button_back_menu"),
                plugin.codex().trList(player, "back_menu_lore")
        ));

        // --- EXIT BUTTON (Slot 35) ---
        inv.setItem(35, GUIManager.createItem(
                Material.BARRIER,
                plugin.codex().tr(player, "button_exit"),
                plugin.codex().trList(player, "exit_lore")
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        int currentRadius = (plot != null) ? (plot.getX2() - plot.getX1()) / 2 : 0;

        switch (e.getSlot()) {
            case 11: // Tier 1
                if (validatePlot(player, plot)) submit(player, plot, currentRadius + 5);
                break;
            case 12: // Tier 2
                if (validatePlot(player, plot)) submit(player, plot, currentRadius + 10);
                break;
            case 13: // Tier 3
                if (validatePlot(player, plot)) submit(player, plot, currentRadius + 20);
                break;
            case 14: // Tier 4
                if (validatePlot(player, plot)) submit(player, plot, currentRadius + 35);
                break;
            case 15: // Tier 5
                if (validatePlot(player, plot)) submit(player, plot, currentRadius + 50);
                break;
            case 31: // Admin View
                if (plugin.isAdmin(player)) {
                    plugin.gui().expansionAdmin().open(player);
                    plugin.effects().playMenuFlip(player);
                }
                break;
            case 27: // Back to Main
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
                break;
            case 35: // Exit
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                break;
        }
    }

    private void submit(Player player, Plot plot, int newRadius) {
        plugin.getExpansionRequestManager().createRequest(player, plot, newRadius);
        // Note: The Manager handles the success message, so we just play sound/menu here
        plugin.effects().playConfirm(player);
        plugin.gui().openMain(player);
    }

    private boolean validatePlot(Player player, Plot plot) {
        if (plot == null || !plot.getOwner().equals(player.getUniqueId())) {
            plugin.msg().send(player, "no_plot_here");
            plugin.effects().playError(player);
            return false;
        }
        if (plugin.getExpansionRequestManager().hasPendingRequest(player.getUniqueId())) {
            plugin.msg().send(player, "expansion_exists");
            plugin.effects().playError(player);
            return false;
        }
        return true;
    }
}
