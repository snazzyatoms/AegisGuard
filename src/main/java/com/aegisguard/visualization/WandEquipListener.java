package com.aegisguard.visualization;

import com.aegisguard.AegisGuard;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
        boolean isWand = false;
        if (p.getInventory().getItem(e.getNewSlot()) != null) {
            if (p.getInventory().getItem(e.getNewSlot()).getType() == Material.GOLDEN_HOE) {
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
            // Folia: Run it, don't store the specific Task object to avoid type mismatch
            runnable.runTaskTimer(plugin, 0L, 20L);
            activeTasks.put(p.getUniqueId(), runnable); 
        } else {
            // Bukkit: Store the BukkitTask
            BukkitTask task = runnable.runTaskTimerAsynchronously(plugin, 0L, 20L);
            activeTasks.put(p.getUniqueId(), task);
        }
    }

    private void stopVisualizer(Player p) {
        Object task = activeTasks.remove(p.getUniqueId());
        if (task != null) {
            if (task instanceof BukkitTask) {
                ((BukkitTask) task).cancel();
            } else if (task instanceof PlotVisualizerTask) {
                ((PlotVisualizerTask) task).cancel();
            }
        }
    }
}
