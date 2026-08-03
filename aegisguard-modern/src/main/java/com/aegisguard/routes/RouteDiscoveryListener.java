package com.aegisguard.routes;

import com.aegisguard.AegisGuard;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milestone 6 - lightweight proximity discovery. Throttled so move events cannot spam discovery
 * checks; never teleports and never alters claims.
 */
public class RouteDiscoveryListener implements Listener {

    private static final long THROTTLE_MILLIS = 750L;

    private final AegisGuard plugin;
    private final Map<UUID, Long> lastCheck = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGuidance = new ConcurrentHashMap<>();

    public RouteDiscoveryListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.routes().isEnabled()) return;
        if (e.getTo() == null || e.getFrom() == null) return;
        // Ignore tiny look-only moves
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        Player player = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastCheck.get(player.getUniqueId());
        if (last != null && now - last < THROTTLE_MILLIS) return;
        lastCheck.put(player.getUniqueId(), now);

        plugin.routes().tryDiscoverAt(player);
        guide(player, now);
    }

    private void guide(Player player, long now) {
        if (!plugin.getConfig().getBoolean("routes.guidance.enabled", true)) return;
        var checkpoint = plugin.routes().activeNextCheckpoint(player.getUniqueId());
        Location target = plugin.routes().toLocation(checkpoint);
        Location here = player.getLocation();
        if (target == null || here.getWorld() == null || target.getWorld() == null
                || !here.getWorld().equals(target.getWorld())) return;
        double distance = here.distance(target);
        if (plugin.getConfig().getBoolean("routes.guidance.action_bar", true)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Next checkpoint: " + checkpoint.getName() + " (" + Math.round(distance) + " blocks)"));
        }
        if (!plugin.getConfig().getBoolean("routes.guidance.particles", true)) return;
        Long last = lastGuidance.get(player.getUniqueId());
        if (last != null && now - last < 2_000L) return;
        lastGuidance.put(player.getUniqueId(), now);
        org.bukkit.util.Vector direction = target.toVector().subtract(here.toVector()).normalize().multiply(1.5D);
        Location marker = here.clone().add(direction).add(0, 1.2D, 0);
        player.spawnParticle(Particle.END_ROD, marker, 3, 0.18D, 0.18D, 0.18D, 0.01D);
    }
}
