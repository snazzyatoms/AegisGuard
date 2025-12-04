package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import com.aegisguard.visualization.PlotVisualizerTask;
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

// FIXED IMPORTS
import static com.aegisguard.selection.SelectionService.WAND_KEY;
import static com.aegisguard.selection.SelectionService.SERVER_WAND_KEY;

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

        boolean isWand = false;
        if (newItem != null && newItem.hasItemMeta()) {
            ItemMeta meta = newItem.getItemMeta();
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
            try {
                Object task = scheduleFoliaTask(runnable);
                activeTasks.put(p.getUniqueId(), task);
            } catch (Exception e) {}
        } else {
            BukkitTask task = runnable.runTaskTimerAsynchronously(plugin, 0L, 20L);
            activeTasks.put(p.getUniqueId(), task);
        }
    }

    private void stopVisualizer(Player p) {
        Object task = activeTasks.remove(p.getUniqueId());
        if (task != null) {
            if (task instanceof BukkitTask) ((BukkitTask) task).cancel();
            else if (task instanceof PlotVisualizerTask) ((PlotVisualizerTask) task).cancel();
        }
    }
    
    private Object scheduleFoliaTask(Runnable run) {
        try {
            Object scheduler = org.bukkit.Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            java.lang.reflect.Method method = scheduler.getClass().getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class, long.class);
            return method.invoke(scheduler, plugin, (java.util.function.Consumer<Object>) t -> run.run(), 1L, 20L);
        } catch (Exception e) { return null; }
    }
}
