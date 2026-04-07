package com.aegisguard.claimblocks;

import com.aegisguard.AegisGuard;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClaimBlockTask implements Runnable, Listener {

    private final AegisGuard plugin;

    // Activity tracking for anti-AFK
    private final Map<UUID, PlayerActivity> activityMap = new ConcurrentHashMap<>();

    public ClaimBlockTask(AegisGuard plugin) {
        this.plugin = plugin;
        // Register this as a listener for activity tracking
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void run() {
        // Master toggle + playtime toggle
        boolean claimBlocksEnabled = plugin.cfg().raw().getBoolean("claim_blocks.enabled", true);
        if (!claimBlocksEnabled) return;

        boolean enabled = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.enabled", true);
        if (!enabled) return;

        boolean playerOptOutAllowed = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.player_opt_out_allowed", true);

        long amount = plugin.cfg().raw().getLong("claim_blocks.earn.playtime.blocks_per_interval", 50L);
        if (amount <= 0) return;

        boolean notify = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.notify", false);

        // Anti-AFK settings
        boolean antiAfkEnabled = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.anti_afk.enabled", true);
        long requiredActivitySeconds = plugin.cfg().raw().getLong("claim_blocks.earn.playtime.anti_afk.required_activity_seconds", 300L);
        long afkTimeoutMillis = plugin.cfg().raw().getLong("claim_blocks.earn.playtime.anti_afk.afk_timeout_minutes", 5L) * 60_000L;

        long now = System.currentTimeMillis();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("aegis.earn.blocks")) continue;
            if (playerOptOutAllowed && !plugin.getClaimBlockManager().isPlaytimeEarningEnabled(p.getUniqueId())) continue;

            // Check if player is active (anti-AFK)
            if (antiAfkEnabled) {
                PlayerActivity activity = activityMap.get(p.getUniqueId());
                
                if (activity == null) {
                    // First time seeing this player, initialize
                    activity = new PlayerActivity();
                    activity.lastActivity = now;
                    activity.lastLocationUpdate = now;
                    activity.lastLocation = p.getLocation();
                    activityMap.put(p.getUniqueId(), activity);
                    continue; // Skip reward on first interval
                }

                // Check if player has been AFK too long
                long timeSinceActivity = now - activity.lastActivity;
                if (timeSinceActivity > afkTimeoutMillis) {
                    // Player is AFK, skip reward
                    if (notify) {
                        String afkMsg = plugin.codex().tr(p, "claim_blocks_afk_warning",
                                Map.of("MINUTES", String.valueOf(afkTimeoutMillis / 60000))
                        );
                        if (afkMsg != null && !afkMsg.isEmpty()) {
                            afkMsg = ChatColor.translateAlternateColorCodes('&', afkMsg);
                            p.spigot().sendMessage(TextComponent.fromLegacyText(afkMsg));
                        }
                    }
                    continue;
                }

                // Check if player has enough activity time
                long activityDuration = activity.getActivityDuration(now);
                if (activityDuration < requiredActivitySeconds * 1000L) {
                    // Not enough activity, skip reward
                    continue;
                }

                // Reset activity duration for next interval
                activity.resetInterval();
            }

            // Award blocks
            plugin.getClaimBlockManager().addEarned(p.getUniqueId(), amount);

            if (notify) {
                long total = plugin.getClaimBlockManager().getTotalBlocks(p.getUniqueId());

                String msg = plugin.codex().tr(p, "claim_blocks_earned_playtime",
                        Map.of(
                                "AMOUNT", String.valueOf(amount),
                                "TOTAL", String.valueOf(total)
                        )
                );

                msg = ChatColor.translateAlternateColorCodes('&', msg);
                p.spigot().sendMessage(TextComponent.fromLegacyText(msg));
            }
        }

        // Cleanup disconnected players
        activityMap.keySet().removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null);
    }

    // =========================================================================
    // Activity Tracking Events
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.anti_afk.track_movement", true)) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null || from.getWorld() == null || to.getWorld() == null) {
            return;
        }

        PlayerActivity activity = getOrCreateActivity(event.getPlayer());
        long now = System.currentTimeMillis();

        // World change / teleport-like move: count it once and reset the movement anchor.
        if (!from.getWorld().equals(to.getWorld())) {
            activity.recordActivity(now);
            activity.lastLocation = to.clone();
            activity.lastLocationUpdate = now;
            return;
        }

        // Only count as activity if player actually changed block position.
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        // Prevent movement spam (only count if moved a reasonable distance or enough time passed)
        if (activity.lastLocation != null) {
            if (activity.lastLocation.getWorld() == null || !activity.lastLocation.getWorld().equals(to.getWorld())) {
                activity.recordActivity(now);
                activity.lastLocation = to.clone();
                activity.lastLocationUpdate = now;
                return;
            }

            double distanceSquared = activity.lastLocation.distanceSquared(to);
            long timeSinceLastUpdate = now - activity.lastLocationUpdate;

            if (distanceSquared >= 9.0 || timeSinceLastUpdate > 2000L) {
                activity.recordActivity(now);
                activity.lastLocation = to.clone();
                activity.lastLocationUpdate = now;
            }
        } else {
            activity.lastLocation = to.clone();
            activity.lastLocationUpdate = now;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.anti_afk.track_interactions", true)) return;
        
        PlayerActivity activity = getOrCreateActivity(event.getPlayer());
        activity.recordActivity(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.anti_afk.track_interactions", true)) return;
        
        PlayerActivity activity = getOrCreateActivity(event.getPlayer());
        activity.recordActivity(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.anti_afk.track_interactions", true)) return;
        
        PlayerActivity activity = getOrCreateActivity(event.getPlayer());
        activity.recordActivity(System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.anti_afk.track_combat", true)) return;
        
        if (event.getDamager() instanceof Player) {
            PlayerActivity activity = getOrCreateActivity((Player) event.getDamager());
            activity.recordActivity(System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Initialize activity tracking for new player
        PlayerActivity activity = new PlayerActivity();
        long now = System.currentTimeMillis();
        activity.lastActivity = now;
        activity.lastLocation = event.getPlayer().getLocation().clone();
        activity.lastLocationUpdate = now;
        activityMap.put(event.getPlayer().getUniqueId(), activity);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clean up activity data (optional, also done in run())
        activityMap.remove(event.getPlayer().getUniqueId());
    }

    private PlayerActivity getOrCreateActivity(Player player) {
        return activityMap.computeIfAbsent(player.getUniqueId(), k -> {
            PlayerActivity activity = new PlayerActivity();
            long now = System.currentTimeMillis();
            activity.lastActivity = now;
            activity.lastLocation = player.getLocation().clone();
            activity.lastLocationUpdate = now;
            return activity;
        });
    }

    // =========================================================================
    // PlayerActivity Inner Class
    // =========================================================================

    private static class PlayerActivity {
        long lastActivity;              // Last time any activity was recorded
        long intervalStart;             // Start of current reward interval
        long activeTimeInInterval;      // Accumulated active time in current interval
        Location lastLocation;          // Last known location (for movement tracking)
        long lastLocationUpdate;        // Last time location was updated

        PlayerActivity() {
            this.intervalStart = System.currentTimeMillis();
            this.activeTimeInInterval = 0;
            this.lastActivity = System.currentTimeMillis();
            this.lastLocationUpdate = System.currentTimeMillis();
        }

        /**
         * Record activity and accumulate time since last activity.
         */
        void recordActivity(long now) {
            if (lastActivity > 0) {
                long timeSinceLastActivity = now - lastActivity;
                // Only count reasonable time gaps (prevent manipulation)
                if (timeSinceLastActivity > 0 && timeSinceLastActivity < 60_000L) {
                    activeTimeInInterval += timeSinceLastActivity;
                }
            }
            lastActivity = now;
        }

        /**
         * Get total activity duration in current interval.
         */
        long getActivityDuration(long now) {
            // Add time from last activity to now if recent
            long duration = activeTimeInInterval;
            if (lastActivity > 0) {
                long timeSinceLastActivity = now - lastActivity;
                if (timeSinceLastActivity > 0 && timeSinceLastActivity < 60_000L) {
                    duration += timeSinceLastActivity;
                }
            }
            return duration;
        }

        /**
         * Reset interval for next reward period.
         */
        void resetInterval() {
            intervalStart = System.currentTimeMillis();
            activeTimeInInterval = 0;
        }
    }
}
