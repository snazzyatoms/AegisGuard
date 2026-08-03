package com.aegisguard.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import com.aegisguard.AegisGuard;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * TeleportUtil
 *
 * Centralized, Folia/region-thread safe teleport helpers.
 *
 * Use these instead of calling player.teleport(...) directly
 * in commands, GUI click handlers, or async tasks.
 */
public final class TeleportUtil {

    /**
     * Detects whether the current server environment has Entity#teleportAsync(Location).
     */
    private static final boolean HAS_TELEPORT_ASYNC;

    static {
        boolean hasAsync;
        try {
            Entity.class.getMethod("teleportAsync", Location.class);
            hasAsync = true;
        } catch (NoSuchMethodException ex) {
            hasAsync = false;
        }
        HAS_TELEPORT_ASYNC = hasAsync;
    }

    private TeleportUtil() {
        // Utility class
    }

    /**
     * Safely teleports an entity to a location in a region-thread / Folia friendly way.
     *
     * - On servers with teleportAsync: uses teleportAsync directly.
     * - On legacy servers: schedules a sync teleport on the main thread.
     *
     * You can ignore the returned future if you don't need it.
     */
    public static CompletableFuture<Boolean> safeTeleport(Plugin plugin, Entity entity, Location target) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();

        if (plugin == null || entity == null || target == null) {
            result.complete(false);
            return result;
        }

        // Normalize location (clone so nobody mutates the original)
        Location loc = target.clone();

        // If API supports teleportAsync, use it first
        if (HAS_TELEPORT_ASYNC) {
            try {
                entity.teleportAsync(loc).whenComplete((success, throwable) -> {
                    if (throwable != null) {
                        plugin.getLogger().log(Level.WARNING,
                                "[AegisGuard] teleportAsync failed for " + entity.getName() +
                                        " to " + formatLoc(loc) + ": " + throwable.getMessage(), throwable);
                        result.completeExceptionally(throwable);
                    } else {
                        result.complete(Boolean.TRUE.equals(success));
                    }
                });
                return result;
            } catch (UnsupportedOperationException | NoSuchMethodError ex) {
                // Some edge cases may still throw; fall back below.
                plugin.getLogger().log(Level.FINE,
                        "[AegisGuard] teleportAsync unsupported at runtime, falling back to sync teleport.", ex);
            }
        }

        // Fallback: schedule a classic sync teleport on the main thread
        Runnable fallback = () -> {
            try {
                boolean success = entity.teleport(loc);
                result.complete(success);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING,
                        "[AegisGuard] Fallback teleport failed for " + entity.getName() +
                                " to " + formatLoc(loc) + ": " + ex.getMessage(), ex);
                result.completeExceptionally(ex);
            }
        };
        if (plugin instanceof AegisGuard aegis) {
            aegis.runEntity(entity, fallback);
        } else {
            Bukkit.getScheduler().runTask(plugin, fallback);
        }

        return result;
    }

    /**
     * Teleports a player to the highest safe block at the given base location
     * (used by your admin plot TP, for example).
     *
     * If world is null, nothing happens.
     */
    public static CompletableFuture<Boolean> safeTeleportToHighest(Plugin plugin, Player player, Location base) {
        if (plugin == null || player == null || base == null || base.getWorld() == null) {
            return CompletableFuture.completedFuture(false);
        }

        Location loc = base.clone();
        World world = loc.getWorld();

        int y = world.getHighestBlockYAt(loc);
        loc.setY(y + 1);

        return safeTeleport(plugin, player, loc);
    }

    /**
     * Teleports a player to the spawn of the given world in a safe way.
     *
     * Ideal for commands like /ag spawn:
     *
     *     TeleportUtil.safeTeleportToWorldSpawn(plugin, player, plugin.getServer().getWorld("world"));
     */
    public static CompletableFuture<Boolean> safeTeleportToWorldSpawn(Plugin plugin, Player player, World world) {
        if (plugin == null || player == null || world == null) {
            return CompletableFuture.completedFuture(false);
        }

        Location spawn = world.getSpawnLocation();
        return safeTeleport(plugin, player, spawn);
    }

    /**
     * Convenience: teleport the player to their current world's spawn.
     *
     * Good default if your /ag spawn just uses the current world.
     */
    public static CompletableFuture<Boolean> safeTeleportToCurrentWorldSpawn(Plugin plugin, Player player) {
        if (plugin == null || player == null) {
            return CompletableFuture.completedFuture(false);
        }
        World world = player.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(false);
        }
        return safeTeleportToWorldSpawn(plugin, player, world);
    }

    /**
     * Finds a standable location near a requested destination.  A configured plot
     * spawn may be moved by a build, removed by world generation, or accidentally
     * set inside a block; travel callers should resolve it before teleporting.
     */
    public static Location findSafeDestination(Location requested) {
        if (requested == null || requested.getWorld() == null) return null;

        World world = requested.getWorld();
        int baseX = requested.getBlockX();
        int baseZ = requested.getBlockZ();
        int baseY = requested.getBlockY();

        Location exact = standableLocation(world, baseX, baseY, baseZ, requested.getYaw(), requested.getPitch());
        if (exact != null) return exact;

        // Prefer a nearby safe surface over an unsafe configured point.  The
        // bounded search prevents a teleport from unexpectedly moving far away.
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = baseX + dx;
                    int z = baseZ + dz;
                    int surfaceY = world.getHighestBlockYAt(x, z) + 1;
                    Location candidate = standableLocation(world, x, surfaceY, z, requested.getYaw(), requested.getPitch());
                    if (candidate != null) return candidate;
                }
            }
        }
        return null;
    }

    private static Location standableLocation(World world, int x, int feetY, int z, float yaw, float pitch) {
        if (feetY <= world.getMinHeight() || feetY + 1 >= world.getMaxHeight()) return null;
        Block ground = world.getBlockAt(x, feetY - 1, z);
        Block feet = world.getBlockAt(x, feetY, z);
        Block head = world.getBlockAt(x, feetY + 1, z);

        if (!ground.getType().isSolid() || isHazardous(ground)
                || !feet.isPassable() || !head.isPassable() || feet.isLiquid() || head.isLiquid()) {
            return null;
        }
        return new Location(world, x + 0.5D, feetY, z + 0.5D, yaw, pitch);
    }

    private static boolean isHazardous(Block block) {
        return switch (block.getType()) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE,
                    CACTUS, SWEET_BERRY_BUSH, POWDER_SNOW -> true;
            default -> false;
        };
    }

    private static String formatLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return loc.getWorld().getName() + "@" +
                loc.getBlockX() + "," +
                loc.getBlockY() + "," +
                loc.getBlockZ();
    }
}
