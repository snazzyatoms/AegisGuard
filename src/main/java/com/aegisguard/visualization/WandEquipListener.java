package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WandEquipListener implements Listener {

    private final AegisGuard plugin;
    // Can hold either a BukkitTask or a Folia scheduler handle (Object)
    private final Map<UUID, Object> activeTasks = new HashMap<>();

    public WandEquipListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        ItemStack newItem = p.getInventory().getItem(e.getNewSlot());

        boolean isWand = false;
        if (newItem != null) {
            // Use ItemManager for all identity checks so anvils/renames can't fake it
            if (plugin.getItemManager().isPlayerWand(newItem)
                    || plugin.getItemManager().isSentinelScepter(newItem)) {
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
        UUID uuid = p.getUniqueId();
        if (activeTasks.containsKey(uuid)) return;

        PlotVisualizerTask runnable = new PlotVisualizerTask(plugin, p);

        if (plugin.isFolia()) {
            try {
                Object task = scheduleFoliaTask(runnable);
                if (task != null) {
                    activeTasks.put(uuid, task);
                }
            } catch (Exception ignored) {}
        } else {
            BukkitTask task = runnable.runTaskTimerAsynchronously(plugin, 0L, 20L);
            activeTasks.put(uuid, task);
        }
    }

    private void stopVisualizer(Player p) {
        UUID uuid = p.getUniqueId();
        Object task = activeTasks.remove(uuid);
        if (task == null) return;

        // Normal Paper/Spigot scheduler
        if (task instanceof BukkitTask) {
            ((BukkitTask) task).cancel();
            return;
        }

        // Folia scheduler handle – try to call cancel() reflectively if present
        try {
            task.getClass().getMethod("cancel").invoke(task);
        } catch (Exception ignored) {
            // Worst case, it just keeps running until server stop
        }
    }

    /**
     * Schedules a Folia global region repeating task via reflection.
     * Returns the scheduler handle (usually a ScheduledTask).
     */
    private Object scheduleFoliaTask(Runnable run) {
        try {
            Object scheduler = org.bukkit.Bukkit.class
                    .getMethod("getGlobalRegionScheduler")
                    .invoke(null);

            java.lang.reflect.Method method = scheduler.getClass().getMethod(
                    "runAtFixedRate",
                    org.bukkit.plugin.Plugin.class,
                    java.util.function.Consumer.class,
                    long.class,
                    long.class
            );

            // Folia returns a ScheduledTask (or equivalent handle)
            return method.invoke(
                    scheduler,
                    plugin,
                    (java.util.function.Consumer<Object>) t -> run.run(),
                    1L,
                    20L
            );
        } catch (Exception e) {
            return null;
        }
    }
}
