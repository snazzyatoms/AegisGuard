package com.aegisguard.routes;

import com.aegisguard.AegisGuard;
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
    }
}
