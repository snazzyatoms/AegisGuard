package com.aegisguard.arena;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

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

    /**
     * Persist the staging point to config.yml at the player's feet. When the
     * admin holds a complete Aegis-wand selection in their world, that area is
     * also claimed as a protected server zone — the hub can be any size, and
     * its blocks can't be griefed between runs. Returns the claimed zone's
     * area in blocks, or 0 when only the point was stored.
     */
    public int setStaging(Player admin) {
        Location loc = admin.getLocation();
        var cfg = plugin.getConfig();
        int zonedArea = 0;

        if (loc.getWorld() != null && plugin.arenaWorld() != null
                && plugin.arenaWorld().hasWorldSelection(admin)) {
            var sel = plugin.selection().get(admin.getUniqueId());
            String wname = loc.getWorld().getName();
            int minX = Math.min(sel.getL1().getBlockX(), sel.getL2().getBlockX());
            int maxX = Math.max(sel.getL1().getBlockX(), sel.getL2().getBlockX());
            int minZ = Math.min(sel.getL1().getBlockZ(), sel.getL2().getBlockZ());
            int maxZ = Math.max(sel.getL1().getBlockZ(), sel.getL2().getBlockZ());
            try {
                plugin.selection().confirmClaim(admin, true); // server zone
                Plot zone = plugin.arenaWorld().findZonePlot(wname, minX, minZ, maxX, maxZ);
                if (zone != null) {
                    retireZonePlot();
                    cfg.set("arena.staging.zone_plot", zone.getPlotId().toString());
                    zonedArea = (maxX - minX + 1) * (maxZ - minZ + 1);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[Arena] Staging zone claim failed: " + t.getMessage());
            }
        }

        cfg.set("arena.staging.enabled", true);
        cfg.set("arena.staging.world", loc.getWorld() == null ? null : loc.getWorld().getName());
        cfg.set("arena.staging.x", loc.getX());
        cfg.set("arena.staging.y", loc.getY());
        cfg.set("arena.staging.z", loc.getZ());
        cfg.set("arena.staging.yaw", (double) loc.getYaw());
        cfg.set("arena.staging.pitch", (double) loc.getPitch());
        plugin.saveConfig();
        return zonedArea;
    }

    /**
     * Disable staging. The auto-claimed staging zone is removed too, unless an
     * arena is bound to it — returns true in that case so the caller can tell
     * the admin the zone was kept.
     */
    public boolean clearStaging() {
        boolean kept = retireZonePlot();
        var cfg = plugin.getConfig();
        cfg.set("arena.staging.enabled", false);
        plugin.saveConfig();
        return kept;
    }

    /**
     * Remove the stored staging zone plot when no arena uses it. Returns true
     * when a zone exists but was kept because an arena is bound to it.
     */
    private boolean retireZonePlot() {
        var cfg = plugin.getConfig();
        String stored = cfg.getString("arena.staging.zone_plot", "");
        if (stored == null || stored.isBlank()) return false;
        cfg.set("arena.staging.zone_plot", null);
        Plot zone;
        try {
            zone = plugin.store().getPlotById(UUID.fromString(stored));
        } catch (Throwable t) {
            return false;
        }
        if (zone == null) return false;
        if (arenaUsesPlot(zone.getPlotId())
                || !Plot.SERVER_OWNER_UUID.equals(zone.getOwner())) {
            return true;
        }
        try {
            plugin.store().removePlot(zone.getOwner(), zone.getPlotId());
        } catch (Throwable t) {
            plugin.getLogger().warning("[Arena] Staging zone removal failed: " + t.getMessage());
        }
        return false;
    }

    /** True when any arena definition binds the plot as lobby/floor/spectator. */
    private boolean arenaUsesPlot(UUID plotId) {
        if (plotId == null || plugin.arena() == null) return false;
        for (ArenaDefinition d : plugin.arena().allArenas()) {
            if (d == null) continue;
            if (plotId.equals(d.getLobbyPlotId()) || plotId.equals(d.getArenaPlotId())
                    || plotId.equals(d.getSpectatorPlotId()) || d.isFloorPlot(plotId)) {
                return true;
            }
        }
        return false;
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
