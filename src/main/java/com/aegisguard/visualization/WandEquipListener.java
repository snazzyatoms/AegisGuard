package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static com.aegisguard.selection.SelectionService.WAND_KEY;
import static com.aegisguard.selection.SelectionService.SERVER_WAND_KEY;

public class WandEquipListener implements Listener {

    private final AegisGuard plugin;
    /**
     * Stores:
     *  - BukkitTask (non-Folia)
     *  - Region scheduler handle with cancel() (Folia)
     */
    private final Map<UUID, Object> activeTasks = new HashMap<>();

    public WandEquipListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSlotChange(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        ItemStack newItem = p.getInventory().getItem(e.getNewSlot());
        boolean holdingWand = isAnyAegisWand(newItem);

        if (holdingWand) {
            // Already running for this player? do nothing.
            if (!activeTasks.containsKey(id)) {
                startVisualizer(p);
            }
        } else {
            // No longer holding a wand -> stop border visualizer
            stopVisualizer(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stopVisualizer(e.getPlayer());
    }

    // --------------------------------------------------
    // INTERNAL
    // --------------------------------------------------

    private boolean isAnyAegisWand(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        var container = meta.getPersistentDataContainer();
        return container.has(WAND_KEY, PersistentDataType.BYTE)
                || container.has(SERVER_WAND_KEY, PersistentDataType.BYTE);
    }

    private void startVisualizer(Player p) {
        UUID id = p.getUniqueId();

        // Safety: stop any stale task
        stopVisualizer(p);

        long interval = plugin.cfg().raw().getLong("visualization.interval_ticks", 20L);
        if (interval < 1L) interval = 20L;

        PlotVisualizerTask runnable = new PlotVisualizerTask(plugin, p);

        if (plugin.isFolia()) {
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                var runMethod = scheduler.getClass().getMethod(
                        "runAtFixedRate",
                        Plugin.class,
                        Consumer.class,
                        long.class,
                        long.class
                );

                Object handle = runMethod.invoke(
                        scheduler,
                        plugin,
                        (Consumer<Object>) t -> runnable.run(),
                        0L,
                        interval
                );

                activeTasks.put(id, handle);
            } catch (Exception ex) {
                plugin.getLogger().warning("[Visualization] Failed to schedule wand visualizer on Folia: " + ex.getMessage());
            }
        } else {
            // Normal Paper/Spigot: main-thread repeating task
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                    plugin,
                    runnable,
                    0L,
                    interval
            );
            activeTasks.put(id, task);
        }
    }

    private void stopVisualizer(Player p) {
        UUID id = p.getUniqueId();
        Object handle = activeTasks.remove(id);
        if (handle == null) return;

        if (handle instanceof BukkitTask bukkitTask) {
            bukkitTask.cancel();
            return;
        }

        // Folia handle with cancel()
        try {
            handle.getClass().getMethod("cancel").invoke(handle);
        } catch (Exception ignored) {
        }
    }
}
