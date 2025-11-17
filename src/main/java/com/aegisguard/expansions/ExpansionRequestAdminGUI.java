package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * ExpansionRequestAdminGUI (Community v1.0.0)
 * ------------------------------------------------------------
 * - Safe, self-contained, and compiles without any expansion backend.
 * - Lets admins toggle a single config switch: expansions.enabled
 * - Shows a polished "About" panel. No references to radius or managers.
 * - Uses only stable APIs (no msg().has()), with sensible fallbacks.
 *
 * NOTE: Keep the real AdminGUI class in com.aegisguard.gui.AdminGUI ONLY.
 * Do NOT duplicate this class name in AdminGUI.java.
 */
public class ExpansionRequestAdminGUI {

    private final AegisGuard plugin;

    public ExpansionRequestAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /* -----------------------------
     * Title helper (fallback-safe)
     * ----------------------------- */
// ... existing code ...
    private String title(Player player) {
// ... existing code ...
        // ... existing code ...
        return "§b🛡 AegisGuard — Expansion Admin";
    }

    /* -----------------------------
     * Filler (subtle glass styling)
     * ----------------------------- */
// ... existing code ...
    private ItemStack filler() {
// ... existing code ...
        // ... existing code ...
        return pane;
    }

    /* -----------------------------
     * Open GUI
     * ----------------------------- */
// ... existing code ...
    public void open(Player player) {
// ... existing code ...
        // ... existing code ...
        Inventory inv = Bukkit.createInventory(null, 27, title(player));

        // Fill background first for a polished look
// ... existing code ...
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg);

        boolean enabled = plugin.getConfig().getBoolean("expansions.enabled", false);

        // Toggle (left) - SLOT 10
        inv.setItem(10, GUIManager.icon(
// ... existing code ...
        // ... existing code ...
        ));

        // About (center) - SLOT 13
        inv.setItem(13, GUIManager.icon(
// ... existing code ...
        // ... existing code ...
        ));

        // Back (right) - SLOT 16
        inv.setItem(16, GUIManager.icon(
// ... existing code ...
        // ... existing code ...
        ));

        // Exit (bottom-center) - SLOT 22
        inv.setItem(22, GUIManager.icon(
// ... existing code ...
        // ... existing code ...
        ));

        player.openInventory(inv);
// ... existing code ...
    }

    /* -----------------------------
     * Handle Clicks
     * ----------------------------- */
    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        // --- IMPROVEMENT ---
        // Switched from Material to Slot for 100% reliability.
        switch (e.getSlot()) {
            case 10 -> { // Toggle (AMETHYST_SHARD or GRAY_DYE)
                boolean cur = plugin.getConfig().getBoolean("expansions.enabled", false);

                // --- IMPROVEMENT ---
                // Set the value on the main thread
                plugin.getConfig().set("expansions.enabled", !cur);
                // Save the config to disk on an async thread to prevent lag
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.saveConfig();
                });

                plugin.sounds().playMenuFlip(player); // safe method that exists today
                open(player); // refresh
            }
            case 13 -> { // About (BOOK)
                // Mirror the "About" lore into chat for clarity
// ... existing code ...
                // ... existing code ...
                for (String line : about) player.sendMessage(line);
                plugin.sounds().playMenuFlip(player);
            }
            case 16 -> { // Back (ARROW)
                // Return to Admin menu
                plugin.gui().admin().open(player); // This assumes plugin.gui() has an admin() getter
                plugin.sounds().playMenuFlip(player);
            }
            case 22 -> { // Exit (BARRIER)
                player.closeInventory();
                plugin.sounds().playMenuClose(player);
            }
            default -> { /* ignore clicks on filler glass */ }
        }
    }
}
