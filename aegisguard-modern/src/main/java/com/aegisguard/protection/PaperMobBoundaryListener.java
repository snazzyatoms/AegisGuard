package com.aegisguard.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

/**
 * Paper-family movement barrier. This listener is loaded reflectively so the
 * plugin remains loadable on servers that only expose the Spigot API.
 */
public final class PaperMobBoundaryListener implements Listener {
    private static final double RETREAT_DISTANCE = 0.75D;
    private static final double PUSH_STRENGTH = 0.35D;
    private static final double MINIMUM_LIFT = 0.08D;

    private final AegisGuard plugin;

    public PaperMobBoundaryListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHostileMove(EntityMoveEvent event) {
        if (!event.hasChangedBlock()
                || !plugin.cfg().raw().getBoolean("mob_barrier.enabled", false)
                || !plugin.cfg().raw().getBoolean("mob_barrier.block_boundary_entry", true)) {
            return;
        }

        Entity entity = event.getEntity();
        if (!plugin.protection().isProtectedMobCategory(entity)) {
            return;
        }

        Plot destination = plugin.store().getPlotAt(event.getTo());
        if (!plugin.protection().isMobProtectionEnabled(destination)) {
            return;
        }

        Plot origin = plugin.store().getPlotAt(event.getFrom());
        if (!plugin.protection().isSamePlot(origin, destination)) {
            if (entity instanceof Mob mob) {
                mob.setTarget(null);
                mob.getPathfinder().stopPathfinding();
            }

            Vector outward = calculateOutwardDirection(
                    destination,
                    event.getFrom().getX(),
                    event.getFrom().getZ()
            );
            Location retreat = event.getFrom().clone().add(outward.clone().multiply(RETREAT_DISTANCE));
            if (hasClearance(entity, retreat)) {
                event.setTo(retreat);
            } else {
                event.setCancelled(true);
            }

            Vector velocity = outward.multiply(PUSH_STRENGTH);
            velocity.setY(Math.max(entity.getVelocity().getY(), MINIMUM_LIFT));
            entity.setVelocity(velocity);
            return;
        }

        // Mobs loaded or placed inside protected land are harmless during the
        // grace window, then removed if they have not left the plot.
        plugin.protection().queueProtectedHostileRemoval(entity);
    }

    static Vector calculateOutwardDirection(Plot plot, double x, double z) {
        double minX = plot.getX1();
        double maxX = plot.getX2() + 1.0D;
        double minZ = plot.getZ1();
        double maxZ = plot.getZ2() + 1.0D;

        double directionX = x < minX ? -1.0D : x >= maxX ? 1.0D : 0.0D;
        double directionZ = z < minZ ? -1.0D : z >= maxZ ? 1.0D : 0.0D;
        if (directionX == 0.0D && directionZ == 0.0D) {
            directionX = x - ((minX + maxX) / 2.0D);
            directionZ = z - ((minZ + maxZ) / 2.0D);
        }

        Vector outward = new Vector(directionX, 0.0D, directionZ);
        return outward.lengthSquared() == 0.0D
                ? new Vector(1.0D, 0.0D, 0.0D)
                : outward.normalize();
    }

    private static boolean hasClearance(Entity entity, Location location) {
        int blocksHigh = Math.max(1, (int) Math.ceil(entity.getHeight()));
        for (int offset = 0; offset < blocksHigh; offset++) {
            if (!location.clone().add(0.0D, offset, 0.0D).getBlock().isPassable()) {
                return false;
            }
        }
        return true;
    }
}
