package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * PlayerGUI
 * - The main player-facing menu for AegisGuard.
 * - Opened via /aegis
 *
 * --- UPGRADE NOTES ---
 * - Now uses a reliable InventoryHolder (PlayerMenuHolder).
 * - Switched to slot-based clicks for 100% reliability.
 * - Removed all local helper methods (createItem, m, l, play...)
 * and now uses the central GUIManager and SoundUtil.
 */
public class PlayerGUI {

    private final AegisGuard plugin;

    public PlayerGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * --- NEW ---
     * Tag holder so click handler only reacts to this GUI.
     */
    private static class PlayerMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void open(Player player) {
        String title = GUIManager.safeText(
                plugin.msg().get(player, "menu_title"),
                "§b§lAegisGuard §7— Menu"
        );
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 27, title);

        // Claim Land - SLOT 11
        inv.setItem(11, GUIManager.icon(
                Material.LIGHTNING_ROD,
                GUIManager.safeText(plugin.msg().get(player, "button_claim_land"), "§aClaim Land"),
                plugin.msg().getList(player, "claim_land_lore", List.of("§7Select a region and confirm."))
        ));

        // Trusted Players - SLOT 13
        inv.setItem(13, GUIManager.icon(
                Material.PLAYER_HEAD,
                GUIManager.safeText(plugin.msg().get(player, "button_trusted_players"), "§bTrusted Players"),
                plugin.msg().getList(player, "trusted_players_lore", List.of("§7Manage who can build on your land."))
        ));

        // Settings - SLOT 15
        inv.setItem(15, GUIManager.icon(
                Material.COMPARATOR,
                GUIManager.safeText(plugin.msg().get(player, "button_settings"), "§eSettings"),
                plugin.msg().getList(player, "settings_lore", List.of("§7Toggle options for your claims."))
        ));

        // Info - SLOT 22
        inv.setItem(22, GUIManager.icon(
                Material.WRITABLE_BOOK,
                GUIManager.safeText(plugin.msg().get(player, "button_info"), "§fInfo"),
                plugin.msg().getList(player, "info_lore", List.of("§7Learn about AegisGuard features."))
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
     * This method is called by GUIListener.
     */
    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true); // prevent item pickup/move
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // --- RELIABILITY FIX ---
        // Switched from Material-based to Slot-based
        switch (e.getSlot()) {
            case 11: { // LIGHTNING_ROD (Claim)
                player.closeInventory();
                plugin.selection().confirmClaim(player);
                plugin.sounds().playMenuFlip(player);
                break;
            }
            case 13: { // PLAYER_HEAD (Trusted)
                plugin.gui().trusted().open(player);
                plugin.sounds().playMenuFlip(player);
                break;
            }
            case 15: { // COMPARATOR (Settings)
                plugin.gui().settings().open(player);
                plugin.sounds().playMenuFlip(player);
                break;
            }
            case 22: { // WRITABLE_BOOK (Info)
                plugin.msg().send(player, "info_message", "§7AegisGuard: lightweight land protection...");
                plugin.sounds().playMenuFlip(player);
                break;
            }
            case 26: { // BARRIER (Exit)
                player.closeInventory();
                plugin.sounds().playMenuClose(player);
                break;
            }
            default: { /* ignore other slots */ }
        }
    }

    /* -----------------------------------
     * All local helper methods (createItem, m, l, sounds)
     * have been removed and are now handled by
     * GUIManager and SoundUtil.
     * ----------------------------------- */
}
