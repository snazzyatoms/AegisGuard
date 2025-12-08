package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import com.aegisguard.selection.SelectionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

        boolean isWand = isAegisWand(newItem);

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

    /**
     * Checks if the given item is an Aegis wand / scepter (player or server),
     * using the persistent NBT keys from SelectionService.
     */
    private boolean isAegisWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        var container = meta.getPersistentDataContainer();
        return container.has(SelectionService.WAND_KEY, PersistentDataType.BYTE)
                || container.has(SelectionService.SERVER_WAND_KEY, PersistentDataType.BYTE);
    }

    private void startVisualizer(Player p) {
        UUID id = p.getUniqueId();
        if (activeTasks.containsKey(id)) {
            return; // already running for this player
        }

        PlotVisualizerTask runnable = new PlotVisualizerTask(plugin, p);

        if (plugin.isFolia()) {
            // Folia / region-thread: use the player scheduler at fixed rate
            Object scheduled = p.getScheduler().runAtFixedRate(
                    plugin,
                    scheduledTask -> runnable.run(),
                    0L,
                    20L
            );
            activeTasks.put(id, scheduled);
        } else {
            // Classic Bukkit/Paper: run on the main thread every 20 ticks
            BukkitTask task = runnable.runTaskTimer(plugin, 0L, 20L);
            activeTasks.put(id, task);
        }
    }

    private void stopVisualizer(Player p) {
        UUID id = p.getUniqueId();
        Object task = activeTasks.remove(id);
        if (task == null) {
            return;
        }

        // Normal Bukkit/Paper task
        if (task instanceof BukkitTask bukkitTask) {
            bukkitTask.cancel();
            return;
        }

        // Direct BukkitRunnable (fallback)
        if (task instanceof BukkitRunnable bukkitRunnable) {
            bukkitRunnable.cancel();
            return;
        }

        // Folia ScheduledTask or any other scheduler with cancel()
        try {
            task.getClass().getMethod("cancel").invoke(task);
        } catch (Exception ignored) {
        }
    }
}
