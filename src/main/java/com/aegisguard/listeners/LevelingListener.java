package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotLevelUpEvent;
import com.aegisguard.data.Plot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class LevelingListener implements Listener {

    private final AegisGuard plugin;
    private final HashMap<UUID, Plot> activePlotCache = new HashMap<>();

    public LevelingListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLevelUp(PlotLevelUpEvent e) {
        Plot plot = e.getPlot();
        int newLevel = e.getNewLevel();
        List<String> rewards = plugin.cfg().getLevelRewards(newLevel);

        if (rewards == null) return;

        for (String reward : rewards) {
            if (reward.startsWith("RADIUS:")) {
                try {
                    int amount = Integer.parseInt(reward.split(":")[1]);
                    plot.expand(amount);
                    e.getPlayer().sendMessage("§a⚡ Your land boundaries have expanded by " + amount + " blocks!");
                } catch (Exception ex) {}
            }
            else if (reward.startsWith("MEMBERS:")) {
                try {
                    int amount = Integer.parseInt(reward.split(":")[1]);
                    int currentMax = plot.getMaxMembers();
                    plot.setMaxMembers(currentMax + amount);
                    e.getPlayer().sendMessage("§a⚡ You can now trust " + amount + " more players!");
                } catch (Exception ex) {}
            }
        }
        // This method relies on the Interface update below
        plugin.store().savePlot(plot);
        
        if (plot.contains(e.getPlayer().getLocation())) {
            applyBuffs(e.getPlayer(), plot);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockY() == e.getTo().getBlockY() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        handleMovement(e.getPlayer(), e.getTo());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        handleMovement(e.getPlayer(), e.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        removeBuffs(e.getPlayer());
        activePlotCache.remove(e.getPlayer().getUniqueId());
    }

    private void handleMovement(Player player, Location to) {
        Plot currentPlot = plugin.store().getPlotAt(to); 
        Plot cachedPlot = activePlotCache.get(player.getUniqueId());

        if (currentPlot != null && cachedPlot == null) {
            activePlotCache.put(player.getUniqueId(), currentPlot);
            if (isAllowed(player, currentPlot)) applyBuffs(player, currentPlot);
        }
        else if (currentPlot == null && cachedPlot != null) {
            removeBuffs(player);
            activePlotCache.remove(player.getUniqueId());
        }
        else if (currentPlot != null && cachedPlot != null && !currentPlot.equals(cachedPlot)) {
            removeBuffs(player);
            activePlotCache.put(player.getUniqueId(), currentPlot);
            if (isAllowed(player, currentPlot)) applyBuffs(player, currentPlot);
        }
    }

    private boolean isAllowed(Player player, Plot plot) {
        return plot.isOwner(player) || plot.isTrusted(player);
    }

    private void applyBuffs(Player player, Plot plot) {
        int level = plot.getLevel();
        for (int i = 1; i <= level; i++) {
            List<String> rewards = plugin.cfg().getLevelRewards(i);
            if (rewards == null) continue;

            for (String reward : rewards) {
                if (reward.equalsIgnoreCase("FLIGHT") || reward.equalsIgnoreCase("FLY")) {
                    if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                        player.setAllowFlight(true);
                        player.sendMessage("§b☁ Flight Enabled (Zone Bonus)");
                    }
                }
                else if (reward.startsWith("EFFECT:")) {
                    try {
                        String[] parts = reward.split(":");
                        PotionEffectType type = PotionEffectType.getByName(parts[1]);
                        int amplifier = Integer.parseInt(parts[2]) - 1;
                        if (type != null) {
                            player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier, true, false));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void removeBuffs(Player player) {
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            if (player.getAllowFlight()) {
                player.setAllowFlight(false);
                player.setFlying(false);
                player.sendMessage("§c☁ Flight Disabled (Leaving Zone)");
            }
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getDuration() > 100000) {
                player.removePotionEffect(effect.getType());
            }
        }
    }
}
