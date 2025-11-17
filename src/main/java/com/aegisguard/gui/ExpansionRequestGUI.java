package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * ExpansionRequestGUI (Community placeholder)
 * ------------------------------------------------------------
 * - Pretty, tone-aware UI for "Expansion Requests"
 * - No hard dependency on ExpansionRequestManager or plot radius
 * - Reads a simple toggle from config: expansions.enabled
 * - Safe to ship now; paid version can wire the backend later
 *
 * --- UPGRADE NOTES ---
 * - Now uses a reliable InventoryHolder (ExpansionHolder) to track the GUI and plot.
 * - Fixed bug where click handler would fetch the wrong plot.
 * - Switched to slot-based clicks for 100% reliability.
 * - Moved icon() and safeText() helpers to GUIManager.
 */
public class ExpansionRequestGUI {

    private final AegisGuard plugin;

    public ExpansionRequestGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /** --- NEW ---
     * Tag holder so click handler only reacts to this GUI
     * and correctly holds the plot context.
     */
    private static class ExpansionHolder implements InventoryHolder {
        private final PlotStore.Plot plot;

        public ExpansionHolder(PlotStore.Plot plot) {
            this.plot = plot;
        }

        public PlotStore.Plot getPlot() {
            return plot;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /* -----------------------------
     * Title helper (fallback-safe)
     * ----------------------------- */
    private String title(Player player) {
// ... existing code ...
        // ... (title logic) ...
        return "§b📏 Expansion Request";
    }

    /* -----------------------------
     * Simple background filler
     * ----------------------------- */
    private ItemStack filler() {
// ... existing code ...
        // ... (filler logic) ...
        return pane;
    }

    /* -----------------------------
     * Open GUI
     * ----------------------------- */
    public void open(Player player, PlotStore.Plot plot) {
        if (plot == null) {
// ... existing code ...
            return;
        }

        // --- MODIFIED ---
        // Use our new, reliable InventoryHolder to store the plot
        Inventory inv = Bukkit.createInventory(new ExpansionHolder(plot), 27, title(player));

        // Fill background for a polished look
// ... existing code ...
        // ... (background fill) ...

        boolean enabled = plugin.getConfig().getBoolean("expansions.enabled", false);

        // --- Info card (center-left) --- SLOT 11
        List<String> infoLore = new ArrayList<>();
// ... existing code ...
        // ... (info lore logic) ...
        infoLore.add("§8Your request will be reviewed by an admin.");
        }

        inv.setItem(11, GUIManager.icon( // --- MODIFIED --- (uses GUIManager)
                Material.PAPER,
                GUIManager.safeText(plugin.msg().get(player, "expansion_info_title"), "§bExpansion Details"), // --- MODIFIED ---
                infoLore
        ));

        // --- Confirm (center) --- SLOT 13
        inv.setItem(13, GUIManager.icon( // --- MODIFIED ---
                Material.EMERALD_BLOCK,
                GUIManager.safeText(plugin.msg().get(player, "expansion_confirm_button"), "§aConfirm Expansion"), // --- MODIFIED ---
                List.of(GUIManager.safeText(plugin.msg().get(player, "expansion_confirm_lore"), "§7Click to submit your expansion request.")) // --- MODIFIED ---
        ));

        // --- Cancel (center-right) --- SLOT 15
        inv.setItem(15, GUIManager.icon( // --- MODIFIED ---
                Material.REDSTONE_BLOCK,
                GUIManager.safeText(plugin.msg().get(player, "expansion_cancel_button"), "§cCancel"), // --- MODIFIED ---
                List.of(GUIManager.safeText(plugin.msg().get(player, "expansion_cancel_lore"), "§7Return without sending your request.")) // --- MODIFIED ---
        ));

        // --- Exit (bottom-center) --- SLOT 22
        inv.setItem(22, GUIManager.icon( // --- MODIFIED ---
                Material.BARRIER,
                plugin.msg().get(player, "button_exit"),
                plugin.msg().getList(player, "exit_lore")
        ));

        // If expansions are disabled, show a subtle disabled tile in the bottom-left
        if (!enabled) {
            // SLOT 18
            inv.setItem(18, GUIManager.icon( // --- MODIFIED ---
                    Material.GRAY_DYE,
                    "§7Requests Disabled",
                    List.of("§8An admin has disabled expansion requests.")
            ));
        }

        player.openInventory(inv);
// ... existing code ...
    }

    /* -----------------------------
     * Handle Clicks
     * ----------------------------- */
    public void handleClick(Player player, InventoryClickEvent e) {
        // --- RELIABILITY FIX ---
        // Use InventoryHolder to identify the GUI, not the title.
        if (!(e.getInventory().getHolder() instanceof ExpansionHolder holder)) {
            return;
        }

        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        // --- CONTEXT FIX ---
        // Get the plot from the holder, not the player's location.
        PlotStore.Plot plot = holder.getPlot();
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            player.closeInventory();
            plugin.sounds().playMenuClose(player);
            return;
        }

        // --- RELIABILITY FIX ---
        // Switch on slot, not material
        switch (e.getSlot()) {
            case 13: { // EMERALD_BLOCK (Confirm)
                boolean enabled = plugin.getConfig().getBoolean("expansions.enabled", false);
                if (!enabled) {
// ... existing code ...
                    // ... (disabled logic) ...
                    return;
                }

                // Placeholder “submitted” flow (no backend yet)
                // --- CRITICAL ---
                // We would call the *real* manager here, e.g.:
                // plugin.getExpansionRequestManager().createRequest(player, plot, 10); // (10 is a placeholder radius)
                plugin.msg().send(player, "expansion_submitted");
                plugin.sounds().playMenuFlip(player);
                player.closeInventory();
                break;
            }
            case 15: { // REDSTONE_BLOCK (Cancel)
                // Soft-cancel path (no backend state to clear)
                plugin.sounds().playMenuClose(player);
                player.closeInventory();
                break;
            }
            case 22: { // BARRIER (Exit)
                player.closeInventory();
                plugin.sounds().playMenuClose(player);
                break;
            }
            default: { /* ignore other clicks */ }
        }
    }

    /* -----------------------------
     * Helpers
     * --- REMOVED ---
     * (Moved to GUIManager.java for central use)
     * ----------------------------- */
}
