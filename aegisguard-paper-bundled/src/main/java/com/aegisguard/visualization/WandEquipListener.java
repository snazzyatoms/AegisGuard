package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.aegisguard.selection.SelectionService.WAND_KEY;
import static com.aegisguard.selection.SelectionService.SERVER_WAND_KEY;

public class WandEquipListener implements Listener {

    private final AegisGuard plugin;
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();

    public WandEquipListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        ItemStack newItem = p.getInventory().getItem(e.getNewSlot());

        if (isAnyAegisWand(newItem)) {
            startVisualizer(p);
        } else {
            stopVisualizer(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stopVisualizer(e.getPlayer());
    }

    /**
     * Checks if the given item is any Aegis wand / scepter:
     *  - normal player wand (WAND_KEY)
     *  - server/admin wand (SERVER_WAND_KEY)
     */
    private boolean isAnyAegisWand(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        var container = meta.getPersistentDataContainer();
        return container.has(WAND_KEY, PersistentDataType.BYTE)
                || container.has(SERVER_WAND_KEY, PersistentDataType.BYTE);
    }

    private void startVisualizer(Player p) {
        UUID id = p.getUniqueId();
        if (activeTasks.containsKey(id)) {
            return; // already running
        }

        PlotVisualizerTask runnable = new PlotVisualizerTask(plugin, p);

        // Paper / “Folia-ish” friendly: use BukkitRunnable#runTaskTimer
        BukkitTask task = runnable.runTaskTimer(plugin, 0L, 20L); // every 1 second
        activeTasks.put(id, task);
    }

    private void stopVisualizer(Player p) {
        UUID id = p.getUniqueId();
        BukkitTask task = activeTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
    }
}
