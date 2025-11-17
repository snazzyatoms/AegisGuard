package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

/**
 * RolesGUI
 * - Placeholder GUI for a future roles feature.
 *
 * --- UPGRADE NOTES ---
 * - Added a reliable InventoryHolder (RolesGUIHolder).
 * - Switched to slot-based clicks.
 * - Now uses the central GUIManager.icon() helper.
 */
public class RolesGUI {

    private final AegisGuard plugin;

    public RolesGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * --- NEW ---
     * Tag holder so click handler only reacts to this GUI.
     */
    private static class RolesGUIHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void open(Player player) {
        String title = GUIManager.safeText(
                plugin.msg().get(player, "roles_menu_title"),
                "§bAegisGuard — Roles"
        );
        // --- MODIFIED ---
        Inventory inv = Bukkit.createInventory(new RolesGUIHolder(), 27, title);

        // Placeholder “Coming Soon” icon - SLOT 13
        // --- MODIFIED --- (Now uses GUIManager.icon)
        inv.setItem(13, GUIManager.icon(
                Material.NAME_TAG,
                GUIManager.safeText(plugin.msg().get(player, "button_roles"), "§6Plot Roles"),
                Arrays.asList(
                        GUIManager.safeText(plugin.msg().get(player, "roles_lore_1"), "§7Feature not yet available"),
                        GUIManager.safeText(plugin.msg().get(player, "roles_lore_2"), "§e📌 Coming in a future update")
                )
        ));

        // Exit - SLOT 26
        inv.setItem(26, GUIManager.icon(
                Material.BARRIER,
                GUIManager.safeText(plugin.msg().get(player, "button_exit"), "§cExit"),
                plugin.msg().getList(player, "exit_lore")
        ));

        player.openInventory(inv);
        plugin.sounds().playMenuOpen(player);
    }

    /**
     * This method is called by GUIListener
     */
    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);

        if (e.getCurrentItem() == null) return;

        // --- RELIABILITY FIX ---
        // Switched from Material-based to Slot-based
        switch (e.getSlot()) {
            case 26: { // BARRIER (Exit)
                player.closeInventory();
                plugin.sounds().playMenuClose(player);
                break;
            }
            case 13: { // NAME_TAG (Placeholder)
                // Subtle thud effect — placeholder only
                plugin.sounds().playMenuClose(player);
                break;
            }
            default: { /* ignore */ }
        }
    }
}
