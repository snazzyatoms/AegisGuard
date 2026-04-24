package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.selection.SelectionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import static com.aegisguard.selection.SelectionService.WAND_KEY;
import static com.aegisguard.selection.SelectionService.SERVER_WAND_KEY;

/**
 * WandSafetyListener
 *
 * - Prevents Aegis Wand / Sentinel Scepter from being:
 *   - Dropped to give to other players (drop = vanish)
 *   - Moved into containers (chests, hoppers, etc.)
 *
 * - Combined with a "hasWand" check in your command handler, this
 *   removes the easy duplication / stacking exploits.
 */
public class WandSafetyListener implements Listener {

    private final AegisGuard plugin;

    public WandSafetyListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------
    // DROP: wand / scepter dropped = vanish
    // ------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack stack = e.getItemDrop().getItemStack();
        if (!isAnyWand(stack)) {
            return;
        }

        // Let the drop remove it from inventory, then nuke the entity.
        e.getItemDrop().remove();

        Player p = e.getPlayer();
        plugin.effects().playToggle(p); // or any subtle "poof" sound/effect
    }

    // ------------------------------------------
    // INVENTORY MOVES: prevent moving wand into
    // chests / containers / other inventories
    // ------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        ItemStack current = e.getCurrentItem();
        if (current == null || !isAnyWand(current)) {
            return;
        }

        Player player = (Player) e.getWhoClicked();
        Inventory clicked = e.getClickedInventory();
        Inventory top = e.getView().getTopInventory();

        // If this is a pure player-inventory click in a normal survival inventory,
        // we only care about moves into other inventories.
        boolean isPlayerInv = clicked != null && clicked.equals(player.getInventory());

        // Shift-click tries to push to the "other" inventory (chests, etc.)
        if (e.isShiftClick()) {
            // If there is a non-player inventory open, cancel.
            if (top != null && top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER) {
                e.setCancelled(true);
                plugin.effects().playError(player);
            }
            return;
        }

        // For normal clicks:
        // If target is not the player inventory (i.e., trying to place into a chest),
        // cancel the move.
        if (!isPlayerInv) {
            e.setCancelled(true);
            plugin.effects().playError(player);
        }
    }

    // ------------------------------------------
    // HELPERS
    // ------------------------------------------

    private boolean isAnyWand(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(WAND_KEY, PersistentDataType.BYTE)
                || pdc.has(SERVER_WAND_KEY, PersistentDataType.BYTE);
    }

    /**
     * Utility you can use from commands / services:
     * "Does this player already have any Aegis wand or Sentinel scepter?"
     */
    public static boolean playerHasAnyWand(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (pdc.has(WAND_KEY, PersistentDataType.BYTE)
                    || pdc.has(SERVER_WAND_KEY, PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }
}
