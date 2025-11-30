package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotLevelUpEvent;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
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

/**
 * LevelingListener
 * - Handles the ACTUAL rewards (Effects, Flight, Expansion).
 * - Listens for Level Up events and Movement.
 */
public class LevelingListener implements Listener {

    private final AegisGuard plugin;
    private final HashMap<UUID, Plot> activePlotCache = new HashMap<>();

    public LevelingListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------
    // 1. HANDLE IMMEDIATE REWARDS (Expansion & Members)
    // ------------------------------------------------------------
    @EventHandler
    public void onLevelUp(PlotLevelUpEvent e) {
        Plot plot = e.getPlot();
        int newLevel = e.getNewLevel();
        List<String> rewards = plugin.cfg().getLevelRewards(newLevel);

        if (rewards == null) return;

        for (String reward : rewards) {
            // --- Handle Radius Expansion ---
            if (reward.startsWith("RADIUS:")) {
                try {
                    int amount = Integer.parseInt(reward.split(":")[1]);
                    // Logic to expand plot boundaries
                    // Assuming your Plot class has a method like expand(amount)
                    // If not, you might need to access your RegionManager here.
                    plot.expand(amount); 
                    e.getPlayer().sendMessage("§a⚡ Your land boundaries have expanded by " + amount + " blocks!");
                } catch (Exception ex) {
                    plugin.getLogger().warning("Invalid RADIUS reward in config: " + reward);
                }
            }
            // --- Handle Member Slots ---
            else if (reward.startsWith("MEMBERS:")) {
                try {
                    int amount = Integer.parseInt(reward.split(":")[1]);
                    int currentMax = plot.getMaxMembers();
                    plot.setMaxMembers(currentMax + amount);
                    e.getPlayer().sendMessage("§a⚡ You can now trust " + amount + " more players!");
                } catch (Exception ex) {
                    plugin.getLogger().warning("Invalid MEMBERS reward in config: " + reward);
                }
            }
        }
        // Save changes
        plugin.store().savePlot(plot);
        
        // Re-apply buffs immediately if they are inside
        if (plot.contains(e.getPlayer().getLocation())) {
            applyBuffs(e.getPlayer(), plot);
        }
    }

    // ------------------------------------------------------------
    // 2. HANDLE ENTRY/EXIT (Effects & Flight)
    // ------------------------------------------------------------
    
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        // Optimization: Only check if block changed
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
            e.getFrom().getBlockY() == e.getTo().getBlockY() &&
            e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }
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
        Plot currentPlot = plugin.getRegionManager().getPlotAt(to);
        Plot cachedPlot = activePlotCache.get(player.getUniqueId());

        // Case 1: Entering a plot from Wilderness
        if (currentPlot != null && cachedPlot == null) {
            activePlotCache.put(player.getUniqueId(), currentPlot);
            if (isAllowed(player, currentPlot)) {
                applyBuffs(player, currentPlot);
            }
        }
        // Case 2: Leaving a plot to Wilderness
        else if (currentPlot == null && cachedPlot != null) {
            removeBuffs(player);
            activePlotCache.remove(player.getUniqueId());
        }
        // Case 3: Moving from Plot A to Plot B
        else if (currentPlot != null && cachedPlot != null && !currentPlot.equals(cachedPlot)) {
            removeBuffs(player); // Remove old plot buffs
            activePlotCache.put(player.getUniqueId(), currentPlot);
            if (isAllowed(player, currentPlot)) {
                applyBuffs(player, currentPlot); // Add new plot buffs
            }
        }
    }

    /**
     * Checks if player owns or is trusted in the plot
     */
    private boolean isAllowed(Player player, Plot plot) {
        return plot.isOwner(player) || plot.isTrusted(player);
    }

    private void applyBuffs(Player player, Plot plot) {
        // We need to loop through ALL levels up to current to stack rewards?
        // OR just the current level if your config defines "Total Stats".
        // Based on your config, we need to accumulate effects.
        
        int level = plot.getLevel();
        
        // Loop from Level 1 to Current Level to gather all perks
        for (int i = 1; i <= level; i++) {
            List<String> rewards = plugin.cfg().getLevelRewards(i);
            if (rewards == null) continue;

            for (String reward : rewards) {
                // FLIGHT
                if (reward.equalsIgnoreCase("FLIGHT") || reward.equalsIgnoreCase("FLY")) {
                    if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                        player.setAllowFlight(true);
                        player.sendMessage("§b☁ Flight Enabled (Zone Bonus)");
                    }
                }
                // POTION EFFECTS
                else if (reward.startsWith("EFFECT:")) {
                    try {
                        String[] parts = reward.split(":");
                        PotionEffectType type = PotionEffectType.getByName(parts[1]);
                        int amplifier = Integer.parseInt(parts[2]) - 1; // Config uses 1-based, Bukkit uses 0-based

                        if (type != null) {
                            // Duration very high so it doesn't flicker
                            player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier, true, false));
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private void removeBuffs(Player player) {
        // 1. Disable Flight (if Survival)
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            if (player.getAllowFlight()) {
                player.setAllowFlight(false);
                player.setFlying(false);
                player.sendMessage("§c☁ Flight Disabled (Leaving Zone)");
            }
        }

        // 2. Remove Infinite Effects
        // We only remove effects that have > 100000 duration (infinite ones we added)
        // so we don't remove potions the player drank themselves.
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getDuration() > 100000) {
                player.removePotionEffect(effect.getType());
            }
        }
    }
}
