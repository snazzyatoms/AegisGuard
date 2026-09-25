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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.aegisguard.selection.SelectionService.WAND_KEY;
import static com.aegisguard.selection.SelectionService.SERVER_WAND_KEY;

public class WandEquipListener implements Listener {

    /** Task handle + runnable so cleanup (border removal) can run on stop. */
    private record ActiveVisualizer(Object taskHandle, PlotVisualizerTask task) {}

    private final AegisGuard plugin;
    private final Map<UUID, ActiveVisualizer> activeTasks = new ConcurrentHashMap<>();

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

        Object task = plugin.runEntityRepeating(p, runnable, 1L, 20L);
        activeTasks.put(id, new ActiveVisualizer(task, runnable));
    }

    private void stopVisualizer(Player p) {
        UUID id = p.getUniqueId();
        ActiveVisualizer active = activeTasks.remove(id);
        if (active != null) {
            try {
                active.task().cleanup();
            } catch (Throwable t) {
                plugin.getLogger().fine("Visualizer cleanup failed for " + p.getName()
                        + ": " + t.getMessage());
            }
            plugin.cancelScheduledTask(active.taskHandle());
        }
    }
}
