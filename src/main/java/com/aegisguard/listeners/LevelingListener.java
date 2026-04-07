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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LevelingListener implements Listener {

    private final AegisGuard plugin;
    private final Map<UUID, UUID> activePlotCache = new ConcurrentHashMap<>();

    public LevelingListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLevelUp(PlotLevelUpEvent e) {
        Plot plot = e.getPlot();
        int newLevel = e.getNewLevel();
        Player p = e.getPlayer();
        List<String> rewards = plugin.cfg().getLevelRewards(newLevel);

        // 1. Give Global Claim Block Reward (Configured in config.yml)
        if (plugin.cfg().raw().getBoolean("claim_blocks.earn.level_up.enabled", true)) {
            int globalReward = plugin.cfg().raw().getInt("claim_blocks.earn.level_up.per_level", 500);
            if (globalReward > 0) {
                plugin.getClaimBlockManager().getOrCreate(p.getUniqueId()).addEarnedBlocks(globalReward);
                // Send feedback via Codex
                String msg = plugin.codex().tr(p, "claim_blocks_earned_level", 
                        Map.of("AMOUNT", String.valueOf(globalReward), "LEVEL", String.valueOf(newLevel)));
                // Fallback if key missing
                if (msg.equals("claim_blocks_earned_level")) msg = "§dAscension Bonus: §e+" + globalReward + " Claim Blocks!";
                p.sendMessage(msg);
            }
        }

        if (rewards == null) return;

        for (String reward : rewards) {
            // 2. Existing Rewards
            if (reward.startsWith("RADIUS:")) {
                try {
                    int amount = Integer.parseInt(reward.split(":")[1]);
                    plot.expand(amount);
                    p.sendMessage("§a⚡ Your land boundaries have expanded by " + amount + " blocks!");
                } catch (Exception ex) {}
            }
            else if (reward.startsWith("MEMBERS:")) {
                try {
                    int amount = Integer.parseInt(reward.split(":")[1]);
                    int currentMax = plot.getMaxMembers();
                    plot.setMaxMembers(currentMax + amount);
                    p.sendMessage("§a⚡ You can now trust " + amount + " more players!");
                } catch (Exception ex) {}
            }
            // 3. Specific Level Reward (Optional override: CLAIM_BLOCKS:1000)
            else if (reward.startsWith("CLAIM_BLOCKS:")) {
                try {
                    int amount = Integer.parseInt(reward.split(":")[1]);
                    plugin.getClaimBlockManager().getOrCreate(p.getUniqueId()).addEarnedBlocks(amount);
                    p.sendMessage("§e⚡ Extra Bonus: +" + amount + " Claim Blocks!");
                } catch (Exception ex) {}
            }
        }
        
        plugin.store().savePlot(plot);
        
        // Re-apply buffs instantly if they are standing in it
        if (plot.contains(p.getLocation())) {
            applyBuffs(p, plot);
        }
        
        // Save block data immediately
        plugin.getClaimBlockManager().saveAsync();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null) return;
        if (!e.getFrom().getWorld().equals(to.getWorld())) {
            handleMovement(e.getPlayer(), to);
            return;
        }
        if (e.getFrom().getBlockX() == to.getBlockX() &&
            e.getFrom().getBlockY() == to.getBlockY() &&
            e.getFrom().getBlockZ() == to.getBlockZ()) return;
        handleMovement(e.getPlayer(), to);
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
        if (to == null || to.getWorld() == null) return;

        Plot currentPlot = plugin.store().getPlotAt(to);
        UUID currentPlotId = currentPlot == null ? null : currentPlot.getPlotId();
        UUID cachedPlotId = activePlotCache.get(player.getUniqueId());

        if (currentPlotId != null && cachedPlotId == null) {
            activePlotCache.put(player.getUniqueId(), currentPlotId);
            if (isAllowed(player, currentPlot)) applyBuffs(player, currentPlot);
        }
        else if (currentPlotId == null && cachedPlotId != null) {
            removeBuffs(player);
            activePlotCache.remove(player.getUniqueId());
        }
        else if (currentPlotId != null && cachedPlotId != null && !currentPlotId.equals(cachedPlotId)) {
            removeBuffs(player);
            activePlotCache.put(player.getUniqueId(), currentPlotId);
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
