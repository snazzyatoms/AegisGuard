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
 * - Uses 5 Tiers and an Exit / Back to Menu button.
 * - Fully localized via the language engine with safe fallbacks.
 */
public class ExpansionRequestGUI {

    private final AegisGuard plugin;

    public ExpansionRequestGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ExpansionHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null; // Marker holder; inventory is managed externally
        }
    }

    public void open(Player player) {
        String title = GUIManager.safeText(
                line(player, "expansion_gui_title", "§dLand Expansion Petition"),
                "§dLand Expansion Petition"
        );
        Inventory inv = Bukkit.createInventory(new ExpansionHolder(), 36, title);

        // Background filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, filler);
        }

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        boolean validPlot = (plot != null && plot.getOwner().equals(player.getUniqueId())); // reserved if you later want disabled tiers

        // --- TIER 1: (+5) ---
        inv.setItem(11, GUIManager.createItem(
                Material.WOODEN_PICKAXE,
                line(player, "expansion_tier1_name", "§aSmall Upgrade (+5)"),
                lines(player, "expansion_tier1_lore", List.of(
                        "§7Expand your dominion by §a+5§7 radius.",
                        "§eClick to submit a request."
                ))
        ));

        // --- TIER 2: (+10) ---
        inv.setItem(12, GUIManager.createItem(
                Material.STONE_PICKAXE,
                line(player, "expansion_tier2_name", "§eMedium Upgrade (+10)"),
                lines(player, "expansion_tier2_lore", List.of(
                        "§7Expand your dominion by §e+10§7 radius.",
                        "§eClick to submit a request."
                ))
        ));

        // --- TIER 3: (+20) ---
        inv.setItem(13, GUIManager.createItem(
                Material.IRON_PICKAXE,
                line(player, "expansion_tier3_name", "§fLarge Upgrade (+20)"),
                lines(player, "expansion_tier3_lore", List.of(
                        "§7Expand your dominion by §f+20§7 radius.",
                        "§eClick to submit a request."
                ))
        ));

        // --- TIER 4: (+35) ---
        inv.setItem(14, GUIManager.createItem(
                Material.GOLDEN_PICKAXE,
                line(player, "expansion_tier4_name", "§6Huge Upgrade (+35)"),
                lines(player, "expansion_tier4_lore", List.of(
                        "§7Expand your dominion by §6+35§7 radius.",
                        "§eClick to submit a request."
                ))
        ));

        // --- TIER 5: (+50) ---
        inv.setItem(15, GUIManager.createItem(
                Material.DIAMOND_PICKAXE,
                line(player, "expansion_tier5_name", "§bMassive Upgrade (+50)"),
                lines(player, "expansion_tier5_lore", List.of(
                        "§7Expand your dominion by §b+50§7 radius.",
                        "§eClick to submit a request."
                ))
        ));

        // --- ADMIN VIEW (Slot 31) ---
        if (plugin.isAdmin(player)) {
            inv.setItem(31, GUIManager.createItem(
                    line(player, "button_view_requests_admin", "§bView Requests"),
                    Material.COMPASS,
                    lines(player, "view_requests_admin_lore", List.of(
                            "§7Open the admin expansion",
                            "§7request review panel."
                    ))
            ));
        }

        // Back to Guardian Codex / Main Menu
        inv.setItem(27, GUIManager.createItem(
                Material.NETHER_STAR,
                line(player, "button_back_menu", "§fBack to Menu"),
                lines(player, "back_menu_lore", List.of(
                        "§7Return to the Guardian Codex."
                ))
        ));

        // Exit Button
        inv.setItem(35, GUIManager.createItem(
                Material.BARRIER,
                line(player, "button_exit", "§cExit"),
                lines(player, "exit_lore", List.of(
                        "§7Close this menu."
                ))
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
            case 11: // Tier 1 (+5)
                if (validatePlot(player, plot)) {
                    submit(player, plot, currentRadius + 5);
                }
                break;

            case 12: // Tier 2 (+10)
                if (validatePlot(player, plot)) {
                    submit(player, plot, currentRadius + 10);
                }
                break;

            case 13: // Tier 3 (+20)
                if (validatePlot(player, plot)) {
                    submit(player, plot, currentRadius + 20);
                }
                break;

            case 14: // Tier 4 (+35)
                if (validatePlot(player, plot)) {
                    submit(player, plot, currentRadius + 35);
                }
                break;

            case 15: // Tier 5 (+50)
                if (validatePlot(player, plot)) {
                    submit(player, plot, currentRadius + 50);
                }
                break;

            case 31: // Admin View
                if (plugin.isAdmin(player)) {
                    plugin.gui().expansionAdmin().open(player);
                    plugin.effects().playMenuFlip(player);
                }
                break;

            case 27: // Back to main menu
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
                break;

            case 35: // Exit
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                break;

            default:
                break;
        }
    }

    private void submit(Player player, Plot plot, int newRadius) {
        // Create the request
        plugin.getExpansionRequestManager().createRequest(player, plot, newRadius);

        // Feedback & UX:
        plugin.msg().send(player, "expansion_submitted");
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

    // --------------------------------
    // LANGUAGE HELPERS (New Engine)
    // --------------------------------

    private String line(Player p, String key, String fallback) {
        String s = plugin.msg().get(p, key);
        return (s == null || s.isEmpty()) ? fallback : s;
    }

    private java.util.List<String> lines(Player p, String key, java.util.List<String> fallback) {
        java.util.List<String> list = plugin.msg().getList(p, key);
        return (list == null || list.isEmpty()) ? fallback : list;
    }
}
