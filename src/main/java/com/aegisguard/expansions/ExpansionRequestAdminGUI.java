package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder; // --- NEW IMPORT ---
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * ExpansionRequestAdminGUI (Community v1.0.0)
 * ... (existing comments) ...
 *
 * --- UPGRADE NOTES ---
 * - CRITICAL: Added InventoryHolder for GUIListener to detect clicks.
 * - LAG FIX: Switched to Folia-safe async scheduler.
 * - SOUND FIX: Now uses plugin.effects() instead of plugin.sounds().
 */
public class ExpansionRequestAdminGUI {

    private final AegisGuard plugin;

    public ExpansionRequestAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * --- NEW: Reliable Inventory Holder ---
     */
    public static class ExpansionAdminHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /* -----------------------------
     * Title helper (fallback-safe)
     * ----------------------------- */
    private String title(Player player) {
        String raw = plugin.msg().get(player, "expansion_admin_title");
        if (raw != null && !raw.contains("Missing:")) {
            return raw;
        }
        return "§b🛡 AegisGuard — Expansion Admin";
    }

    /* -----------------------------
     * Filler (subtle glass styling)
     * ----------------------------- */
    private ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            pane.setItemMeta(meta);
        }
        return pane;
    }

    /* -----------------------------
     * Open GUI
     * ----------------------------- */
    public void open(Player player) {
        if (!player.hasPermission("aegis.admin")) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        // --- MODIFIED: Added InventoryHolder ---
        Inventory inv = Bukkit.createInventory(new ExpansionAdminHolder(), 27, title(player));

        // Fill background first for a polished look
        ItemStack bg = filler();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, bg);

        boolean enabled = plugin.getConfig().getBoolean("expansions.enabled", false);

        // Toggle (left) - SLOT 10
        inv.setItem(10, GUIManager.icon(
                enabled ? Material.AMETHYST_SHARD : Material.GRAY_DYE,
                enabled
                        ? "§aExpansion Requests: Enabled"
                        : "§7Expansion Requests: Disabled",
                List.of(
                        "§7Toggle acceptance of expansion requests.",
                        "§8(Placeholder; full system arrives later)"
                )
        ));

        // About (center) - SLOT 13
        inv.setItem(13, GUIManager.icon(
                Material.BOOK,
                "§bAbout Expansions",
                List.of(
                        "§7This is a preview panel.",
                        "§7The complete Expansion workflow",
                        "§7(approve/deny/review/costing) will",
                        "§7ship in a future premium version."
                )
        ));

        // Back (right) - SLOT 16
        inv.setItem(16, GUIManager.icon(
                Material.ARROW,
                plugin.msg().get(player, "button_back"),
                plugin.msg().getList(player, "back_lore")
        ));

        // Exit (bottom-center) - SLOT 22
        inv.setItem(22, GUIManager.icon(
                Material.BARRIER,
                plugin.msg().get(player, "button_exit"),
                plugin.msg().getList(player, "exit_lore")
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player); // --- SOUND FIX ---
    }

    /* -----------------------------
     * Handle Clicks
     * (This is called by GUIListener)
     * ----------------------------- */
    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        // Switched from Material to Slot for 100% reliability.
        switch (e.getSlot()) {
            case 10 -> { // Toggle (AMETHYST_SHARD or GRAY_DYE)
                boolean cur = plugin.getConfig().getBoolean("expansions.enabled", false);

                // Set the value on the main thread
                plugin.getConfig().set("expansions.enabled", !cur);
                
                // --- FOLIA FIX ---
                // Save the config to disk on an async thread
                plugin.runGlobalAsync(() -> {
                    plugin.saveConfig();
                });

                plugin.effects().playMenuFlip(player); // --- SOUND FIX ---
                open(player); // refresh
            }
            case 13 -> { // About (BOOK)
                // Mirror the "About" lore into chat for clarity
                List<String> about = List.of(
                        "§b[Expansions] §7This is a preview.",
                        "§7The complete Expansion workflow (approve/deny/review/costing)",
                        "§7will be available in a future premium release."
                );
                for (String line : about) player.sendMessage(line);
                plugin.effects().playMenuFlip(player); // --- SOUND FIX ---
            }
            case 16 -> { // Back (ARROW)
                // Return to Admin menu
                plugin.gui().admin().open(player);
                plugin.effects().playMenuFlip(player); // --- SOUND FIX ---
            }
            case 22 -> { // Exit (BARRIER)
                player.closeInventory();
                plugin.effects().playMenuClose(player); // --- SOUND FIX ---
            }
            default -> { /* ignore clicks on filler glass */ }
        }
    }
}
