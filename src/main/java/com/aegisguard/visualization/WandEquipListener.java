package com.yourname.aegisguard.visualization;

import com.yourname.aegisguard.AegisGuard;
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

import static com.yourname.aegisguard.selection.SelectionService.WAND_KEY;
import static com.yourname.aegisguard.selection.SelectionService.SERVER_WAND_KEY;

public class WandEquipListener implements Listener {

    private final AegisGuard plugin;
    private final Map<UUID, Object> activeTasks = new HashMap<>();

    public WandEquipListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        ItemStack newItem = p.getInventory().getItem(e.getNewSlot());

        // v1.3.0: Check NBT Tags instead of Materials.
        // This supports Lightning Rods, Blaze Rods, or anything else you config.
        boolean isWand = false;
        
        if (newItem != null && newItem.hasItemMeta()) {
            ItemMeta meta = newItem.getItemMeta();
            // Check for Player Wand (Lightning Rod) OR Admin Wand (Blaze Rod)
            if (meta.getPersistentDataContainer().has(WAND_KEY, PersistentDataType.BYTE) || 
                meta.getPersistentDataContainer().has(SERVER_WAND_KEY, PersistentDataType.BYTE)) {
                isWand = true;
            }
        }

        if (isWand) {
            startVisualizer(p);
        } else {
            stopVisualizer(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stopVisualizer(e.getPlayer());
    }

    private void startVisualizer(Player p) {
        if (activeTasks.containsKey(p.getUniqueId())) return;

        PlotVisualizerTask runnable = new PlotVisualizerTask(plugin, p);
        
        if (plugin.isFolia()) {
            // Folia: Use the Global Region Scheduler via reflection helper
            try {
                // 20 ticks = 1 second refresh rate
                Object task = scheduleFoliaTask(runnable);
                activeTasks.put(p.getUniqueId(), task);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to schedule visualizer on Folia: " + e.getMessage());
            }
        } else {
            // Bukkit: Standard Async Timer
            BukkitTask task = runnable.runTaskTimerAsynchronously(plugin, 0L, 20L);
            activeTasks.put(p.getUniqueId(), task);
        }
    }

    private void stopVisualizer(Player p) {
        Object task = activeTasks.remove(p.getUniqueId());
        if (task != null) {
            if (task instanceof BukkitTask) {
                ((BukkitTask) task).cancel();
            } 
            else if (task instanceof PlotVisualizerTask) {
                ((PlotVisualizerTask) task).cancel();
            }
            else {
                // Reflection cancel for Folia
                try {
                    task.getClass().getMethod("cancel").invoke(task);
                } catch (Exception ignored) {}
            }
        }
    }
    
    // Helper for Folia scheduling to keep code clean
    private Object scheduleFoliaTask(Runnable run) {
        try {
            Object scheduler = org.bukkit.Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            java.lang.reflect.Method method = scheduler.getClass().getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class, long.class);
            return method.invoke(scheduler, plugin, (java.util.function.Consumer<Object>) t -> run.run(), 1L, 20L);
        } catch (Exception e) {
            return null;
        }
    }
}
