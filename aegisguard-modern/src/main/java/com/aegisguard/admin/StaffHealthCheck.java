package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.guestpass.GuestPass;
import com.aegisguard.snapshots.ClaimSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight staff health check for destinations, Guest Passes, snapshots, and config.
 * Exposed via {@code /agadmin health}.
 */
public final class StaffHealthCheck {

    private StaffHealthCheck() {}

    public record Finding(String code, String message) {}

    public static List<Finding> scan(AegisGuard plugin) {
        List<Finding> findings = new ArrayList<>();
        if (plugin == null || plugin.store() == null) {
            findings.add(new Finding("STORE_MISSING", "Plot data store is not available."));
            return findings;
        }

        int missingWorlds = 0;
        int invalidDestinations = 0;
        int staleGuestPasses = 0;
        long now = System.currentTimeMillis();

        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null) continue;

            World world = plot.getWorld() == null ? null : Bukkit.getWorld(plot.getWorld());
            if (world == null) {
                missingWorlds++;
            }

            if (plot.isServerWarp() || plot.getSpawnLocation() != null) {
                var spawn = plot.getSpawnLocation();
                if (spawn == null || spawn.getWorld() == null) {
                    invalidDestinations++;
                } else if (plugin.safeTravel() != null
                        && plugin.safeTravel().findSafeDestination(spawn) == null) {
                    invalidDestinations++;
                }
            }

            for (GuestPass pass : plot.getGuestPasses().values()) {
                if (pass == null) continue;
                if (pass.isExpired(now)) staleGuestPasses++;
            }
        }

        if (missingWorlds > 0) {
            findings.add(new Finding("MISSING_WORLDS",
                    missingWorlds + " plot(s) reference worlds that are not loaded."));
        }
        if (invalidDestinations > 0) {
            findings.add(new Finding("INVALID_DESTINATIONS",
                    invalidDestinations + " warp/spawn destination(s) are missing or unsafe."));
        }
        if (staleGuestPasses > 0) {
            findings.add(new Finding("STALE_GUEST_PASSES",
                    staleGuestPasses + " expired Guest Pass(es) still stored on plots."));
        }

        if (plugin.getSnapshotManager() == null
                || !plugin.getConfig().getBoolean("snapshots.enabled", true)) {
            findings.add(new Finding("SNAPSHOTS_OFF", "Snapshot manager is disabled or missing."));
        } else {
            List<ClaimSnapshot> snaps = plugin.getSnapshotManager().getAllSnapshots();
            findings.add(new Finding("SNAPSHOT_STATUS",
                    "Snapshots stored: " + snaps.size()
                            + " (auto lockdown="
                            + plugin.getConfig().getBoolean("snapshots.auto_snapshot.before_lockdown", true)
                            + ", alliance="
                            + plugin.getConfig().getBoolean("snapshots.auto_snapshot.before_alliance_access", true)
                            + ")."));
        }

        if (!plugin.getConfig().getBoolean("travel.enabled", true)) {
            findings.add(new Finding("TRAVEL_DISABLED", "Safe Travel gate is disabled in config."));
        }
        if (plugin.getConfig().getInt("config_schema", 0)
                < com.aegisguard.config.ConfigMigrationService.CURRENT_SCHEMA) {
            findings.add(new Finding("CONFIG_SCHEMA",
                    "config_schema is behind CURRENT_SCHEMA "
                            + com.aegisguard.config.ConfigMigrationService.CURRENT_SCHEMA + "."));
        }

        if (plugin.getConfig().getBoolean("mob_barrier.staff_diagnostics", true)
                && plugin.protection() != null) {
            findings.add(new Finding("MOB_BARRIER",
                    "Mob barrier enabled="
                            + plugin.getConfig().getBoolean("mob_barrier.enabled", true)
                            + ", hostile="
                            + plugin.getConfig().getBoolean("mob_barrier.protect_hostile", true)
                            + ", passive="
                            + plugin.getConfig().getBoolean("mob_barrier.protect_passive", false)
                            + ", boss="
                            + plugin.getConfig().getBoolean("mob_barrier.protect_boss", false)
                            + "."));
        }

        if (findings.isEmpty()) {
            findings.add(new Finding("OK", "No staff health issues detected."));
        }
        return findings;
    }

    public static void report(AegisGuard plugin, CommandSender sender) {
        List<Finding> findings = scan(plugin);
        sender.sendMessage("§6§lAegisGuard Staff Health Check");
        for (Finding finding : findings) {
            String color = "OK".equals(finding.code()) || finding.code().endsWith("STATUS")
                    || "MOB_BARRIER".equals(finding.code()) ? "§a" : "§e";
            sender.sendMessage(color + "[" + finding.code() + "] §7" + finding.message());
        }
    }
}
