package com.aegisguard.arena;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * The arena staging hub — a configurable meeting point where players land to
 * pick an arena, form parties, and return after runs instead of scattering
 * back to the survival world.
 *
 * <p>Configured under {@code arena.staging.*}: a single world location set by
 * {@code /ag arena staging set}. When set (and {@code return_on_end} is on),
 * local participants finish a run here and get a continue/leave prompt; the
 * exit spawn remains the fallback. Remote participants still go home to their
 * origin backend — staging is a local concept.
 */
public final class ArenaStagingService {

    private final AegisGuard plugin;

    public ArenaStagingService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /** Configured staging location, or null when unset/unloadable. */
    public Location stagingLocation() {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("arena.staging.enabled", false)) return null;
        String worldName = cfg.getString("arena.staging.world", "");
        World world = worldName == null || worldName.isBlank()
                ? null : Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                cfg.getDouble("arena.staging.x", 0.5D),
                cfg.getDouble("arena.staging.y", world.getSpawnLocation().getY()),
                cfg.getDouble("arena.staging.z", 0.5D),
                (float) cfg.getDouble("arena.staging.yaw", 0.0D),
                (float) cfg.getDouble("arena.staging.pitch", 0.0D));
    }

    public boolean isConfigured() {
        return stagingLocation() != null;
    }

    /** Persist the staging point to config.yml at the player's feet. */
    public void setStaging(Player admin) {
        Location loc = admin.getLocation();
        var cfg = plugin.getConfig();
        cfg.set("arena.staging.enabled", true);
        cfg.set("arena.staging.world", loc.getWorld() == null ? null : loc.getWorld().getName());
        cfg.set("arena.staging.x", loc.getX());
        cfg.set("arena.staging.y", loc.getY());
        cfg.set("arena.staging.z", loc.getZ());
        cfg.set("arena.staging.yaw", (double) loc.getYaw());
        cfg.set("arena.staging.pitch", (double) loc.getPitch());
        plugin.saveConfig();
    }

    public void clearStaging() {
        var cfg = plugin.getConfig();
        cfg.set("arena.staging.enabled", false);
        plugin.saveConfig();
    }

    public boolean returnOnEnd() {
        return plugin.getConfig().getBoolean("arena.staging.return_on_end", true);
    }

    /** Teleport a player to the staging hub. Returns false when unconfigured. */
    public boolean sendToStaging(Player player) {
        Location staging = stagingLocation();
        if (player == null || staging == null) return false;
        plugin.arena().teleportPlayerAllowed(player, staging);
        return true;
    }

    /**
     * Post-run landing for a local participant: staging when configured and
     * return_on_end is on, otherwise the arena's exit spawn. At staging the
     * arena GUI opens so they can re-queue immediately; {@code /ag arena
     * leave} (or the GUI) sends them back to the exit spawn.
     */
    public void landAfterRun(Player player, ArenaDefinition def) {
        if (player == null) return;
        Location staging = returnOnEnd() ? stagingLocation() : null;
        if (staging == null) {
            Location exit = def == null ? null : plugin.arena().toLocation(def.getExitSpawn());
            plugin.arena().teleportPlayerAllowed(player, exit);
            return;
        }
        String arenaId = def == null ? "" : def.getId();
        plugin.arena().scheduler().runForEntity(player, () -> {
            plugin.arena().teleportPlayerAllowed(player, staging);
            plugin.msg().send(player, "arena_staging_return", Map.of("ARENA", arenaId));
            plugin.msg().send(player, "arena_staging_hint");
            try {
                plugin.gui().arena().open(player);
            } catch (Throwable ignored) {}
        });
    }
}
