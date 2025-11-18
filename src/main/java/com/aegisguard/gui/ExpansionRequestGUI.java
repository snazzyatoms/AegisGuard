package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * ExpansionRequestGUI (Community placeholder)
 * ------------------------------------------------------------
 * - Pretty, tone-aware UI for "Expansion Requests"
 *
 * --- UPGRADE NOTES ---
 * - Corrected Plot import.
 * - Switched all sound calls to plugin.effects().
 * - Added owner check in handleClick.
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
        private final Plot plot;

        public ExpansionHolder(Plot plot) {
            this.plot = plot;
        }

        public Plot getPlot() {
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
        String raw = plugin.msg().get(player, "expansion_request_title");
        if (raw != null && !raw.contains("[Missing")) return raw;
        return "§b📏 Expansion Request";
    }

    /* -----------------------------
     * Simple background filler
     * ----------------------------- */
    private ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            // Use GUIManager's helper to ensure flags are correctly set
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /* -----------------------------
     * Open GUI
     * ----------------------------- */
    public void open(Player player, Plot plot) {
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }
        
        if (!plot.getOwner().equals(player.getUniqueId())) {
             plugin.msg().send(player, "no_perm");
             return;
        }

        // Use our new, reliable InventoryHolder to store the plot
        Inventory inv = Bukkit.createInventory(new ExpansionHolder(plot), 27, title(player));

        // Fill background for a polished look
        ItemStack bg = filler();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg);

        boolean enabled = plugin.getConfig().getBoolean("expansions.enabled", false);

        // --- Info card (center-left) --- SLOT 11
        List<String> infoLore = new ArrayList<>();
        // --- MODIFIED: Show bounds instead of radius (Fixes data mismatch) ---
        infoLore.add("§7Owner: §f" + plot.getOwnerName());
        infoLore.add("§7World: §f" + plot.getWorld());
        infoLore.add("§7Bounds: §e(" + plot.getX1() + ", " + plot.getZ1() + ") §7→ §a(" + plot.getX2() + ", " + plot.getZ2() + ")");
        String note = plugin.msg().get(player, "expansion_info_note");
        if (note != null && !note.contains("[Missing")) {
            infoLore.add("§8" + note);
        } else {
            infoLore.add("§8Your request will be reviewed by an admin.");
        }

        inv.setItem(11, GUIManager.icon( 
                Material.PAPER,
                GUIManager.safeText(plugin.msg().get(player, "expansion_info_title"), "§bExpansion Details"), 
                infoLore
        ));

        // --- Confirm (center) --- SLOT 13
        inv.setItem(13, GUIManager.icon(
                Material.EMERALD_BLOCK,
                GUIManager.safeText(plugin.msg().get(player, "expansion_confirm_button"), "§aConfirm Expansion"),
                List.of(GUIManager.safeText(plugin.msg().get(player, "expansion_confirm_lore"), "§7Click to submit your expansion request."))
        ));

        // --- Cancel (center-right) --- SLOT 15
        inv.setItem(15, GUIManager.icon(
                Material.REDSTONE_BLOCK,
                GUIManager.safeText(plugin.msg().get(player, "expansion_cancel_button"), "§cCancel"),
                List.of(GUIManager.safeText(plugin.msg().get(player, "expansion_cancel_lore"), "§7Return without sending your request."))
        ));

        // --- Exit (bottom-center) --- SLOT 22
        inv.setItem(22, GUIManager.icon(
                Material.BARRIER,
                plugin.msg().get(player, "button_exit"),
                plugin.msg().getList(player, "exit_lore")
        ));

        // If expansions are disabled, show a subtle disabled tile in the bottom-left
        if (!enabled) {
            // SLOT 18
            inv.setItem(18, GUIManager.icon(
                    Material.GRAY_DYE,
                    "§7Requests Disabled",
                    List.of("§8An admin has disabled expansion requests.")
            ));
        }

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    /* -----------------------------
     * Handle Clicks
     * ----------------------------- */
    public void handleClick(Player player, InventoryClickEvent e) {
        // Use InventoryHolder to identify the GUI, not the title.
        if (!(e.getInventory().getHolder() instanceof ExpansionHolder holder)) {
            return;
        }

        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        // Get the plot from the holder, not the player's location.
        Plot plot = holder.getPlot();
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }
        
        // --- NEW: Ownership Check ---
        if (!plot.getOwner().equals(player.getUniqueId())) {
            plugin.msg().send(player, "no_perm");
            player.closeInventory();
            plugin.effects().playError(player);
            return;
        }

        // Switch on slot, not material
        switch (e.getSlot()) {
            case 13: { // EMERALD_BLOCK (Confirm)
                boolean enabled = plugin.getConfig().getBoolean("expansions.enabled", false);
                if (!enabled) {
                    plugin.msg().send(player, "expansion_invalid");
                    plugin.effects().playError(player);
                    return;
                }

                // Placeholder “submitted” flow (no backend yet)
                // When implemented, this would call:
                // plugin.getExpansionRequestManager().createRequest(player, plot, newRadius); 
                plugin.msg().send(player, "expansion_submitted");
                plugin.effects().playMenuFlip(player);
                player.closeInventory();
                break;
            }
            case 15: { // REDSTONE_BLOCK (Cancel)
                // Soft-cancel path (no backend state to clear)
                plugin.effects().playMenuClose(player);
                player.closeInventory();
                break;
            }
            case 22: { // BARRIER (Exit)
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                break;
            }
            default: { /* ignore other clicks */ }
        }
    }
}
