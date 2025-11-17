package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import com.aegisguard.selection.SelectionService;
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

/**
 * Manages the starting and stopping of PlotVisualizerTasks
 * based on whether a player is holding the Aegis Scepter.
 */
public class WandEquipListener implements Listener {

    private final AegisGuard plugin;
    private final Map<UUID, BukkitTask> activeVisualizers = new HashMap<>();

    public WandEquipListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        stopVisualizer(e.getPlayer());
    }

    @EventHandler
    public void onPlayerChangeItem(PlayerItemHeldEvent e) {
        Player player = e.getPlayer();
        ItemStack newItem = player.getInventory().getItem(e.getNewSlot());

        if (isAegisWand(newItem)) {
            startVisualizer(player);
        } else {
            stopVisualizer(player);
        }
    }

    private boolean isAegisWand(ItemStack item) {
        if (item == null || item.getType() != Material.LIGHTNING_ROD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(SelectionService.WAND_KEY, PersistentDataType.BYTE);
    }

    private void startVisualizer(Player player) {
        // Don't start if one is already running
        if (activeVisualizers.containsKey(player.getUniqueId())) {
            return;
        }

        // Create the task
        PlotVisualizerTask task = new PlotVisualizerTask(plugin, player);

        // Schedule it (Folia-safe, since this event is on the player's thread)
        long interval = plugin.cfg().raw().getLong("visualization.interval_ticks", 40L); // 2 seconds
        
        task.runTaskTimer(plugin, 0L, interval);
        
        activeVisualizers.put(player.getUniqueId(), task);
    }

    private void stopVisualizer(Player player) {
        BukkitTask existingTask = activeVisualizers.remove(player.getUniqueId());
        if (existingTask != null) {
            existingTask.cancel();
        }
    }
}
