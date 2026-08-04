package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.guestpass.GuestPass;
import com.aegisguard.snapshots.ClaimSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lightweight staff health check for destinations, Guest Passes, snapshots, and config.
 * Exposed via {@code /agadmin health}.
 */
public final class StaffHealthCheck {

    private StaffHealthCheck() {}

    public record Finding(String code, String messageKey, String fallback, Map<String, String> placeholders) {
        public Finding(String code, String messageKey, String fallback) {
            this(code, messageKey, fallback, Map.of());
        }
    }

    public static List<Finding> scan(AegisGuard plugin) {
        List<Finding> findings = new ArrayList<>();
        if (plugin == null || plugin.store() == null) {
            findings.add(new Finding("STORE_MISSING", "staff_health_store_missing",
                    "Plot data store is not available."));
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
            findings.add(new Finding("MISSING_WORLDS", "staff_health_missing_worlds",
                    "{COUNT} plot(s) reference worlds that are not loaded.",
                    Map.of("COUNT", String.valueOf(missingWorlds))));
        }
        if (invalidDestinations > 0) {
            findings.add(new Finding("INVALID_DESTINATIONS", "staff_health_invalid_destinations",
                    "{COUNT} warp/spawn destination(s) are missing or unsafe.",
                    Map.of("COUNT", String.valueOf(invalidDestinations))));
        }
        if (staleGuestPasses > 0) {
            findings.add(new Finding("STALE_GUEST_PASSES", "staff_health_stale_guest_passes",
                    "{COUNT} expired Guest Pass(es) still stored on plots.",
                    Map.of("COUNT", String.valueOf(staleGuestPasses))));
        }

        if (plugin.getSnapshotManager() == null
                || !plugin.getConfig().getBoolean("snapshots.enabled", true)) {
            findings.add(new Finding("SNAPSHOTS_OFF", "staff_health_snapshots_off",
                    "Snapshot manager is disabled or missing."));
        } else {
            List<ClaimSnapshot> snaps = plugin.getSnapshotManager().getAllSnapshots();
            findings.add(new Finding("SNAPSHOT_STATUS", "staff_health_snapshot_status",
                    "Snapshots stored: {COUNT} (auto lockdown={LOCKDOWN}, alliance={ALLIANCE}).",
                    Map.of(
                            "COUNT", String.valueOf(snaps.size()),
                            "LOCKDOWN", String.valueOf(plugin.getConfig().getBoolean(
                                    "snapshots.auto_snapshot.before_lockdown", true)),
                            "ALLIANCE", String.valueOf(plugin.getConfig().getBoolean(
                                    "snapshots.auto_snapshot.before_alliance_access", true))
                    )));
        }

        if (!plugin.getConfig().getBoolean("travel.enabled", true)) {
            findings.add(new Finding("TRAVEL_DISABLED", "staff_health_travel_disabled",
                    "Safe Travel gate is disabled in config."));
        }
        if (plugin.getConfig().getInt("config_schema", 0)
                < com.aegisguard.config.ConfigMigrationService.CURRENT_SCHEMA) {
            findings.add(new Finding("CONFIG_SCHEMA", "staff_health_config_schema",
                    "config_schema is behind CURRENT_SCHEMA {SCHEMA}.",
                    Map.of("SCHEMA", String.valueOf(
                            com.aegisguard.config.ConfigMigrationService.CURRENT_SCHEMA))));
        }

        if (plugin.getConfig().getBoolean("mob_barrier.staff_diagnostics", true)
                && plugin.protection() != null) {
            findings.add(new Finding("MOB_BARRIER", "staff_health_mob_barrier",
                    "Mob barrier enabled={ENABLED}, hostile={HOSTILE}, passive={PASSIVE}, boss={BOSS}.",
                    Map.of(
                            "ENABLED", String.valueOf(plugin.getConfig().getBoolean("mob_barrier.enabled", true)),
                            "HOSTILE", String.valueOf(plugin.getConfig().getBoolean("mob_barrier.protect_hostile", true)),
                            "PASSIVE", String.valueOf(plugin.getConfig().getBoolean("mob_barrier.protect_passive", false)),
                            "BOSS", String.valueOf(plugin.getConfig().getBoolean("mob_barrier.protect_boss", false))
                    )));
        }

        if (findings.isEmpty()) {
            findings.add(new Finding("OK", "staff_health_ok", "No staff health issues detected."));
        }
        return findings;
    }

    public static void report(AegisGuard plugin, CommandSender sender) {
        List<Finding> findings = scan(plugin);
        send(plugin, sender, "staff_health_title", "&6&lAegisGuard Staff Health Check", Map.of());
        for (Finding finding : findings) {
            String color = "OK".equals(finding.code()) || finding.code().endsWith("STATUS")
                    || "MOB_BARRIER".equals(finding.code()) ? "&a" : "&e";
            String body = localize(plugin, sender, finding.messageKey(), finding.fallback(), finding.placeholders());
            send(plugin, sender, "staff_health_line", "{COLOR}[{CODE}] &7{MESSAGE}",
                    Map.of("COLOR", color, "CODE", finding.code(), "MESSAGE", ChatColor.stripColor(
                            ChatColor.translateAlternateColorCodes('&', body))));
        }
    }

    private static void send(AegisGuard plugin, CommandSender sender, String key, String fallback,
                             Map<String, String> placeholders) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                localize(plugin, sender, key, fallback, placeholders)));
    }

    private static String localize(AegisGuard plugin, CommandSender sender, String key, String fallback,
                                   Map<String, String> placeholders) {
        Map<String, String> ph = placeholders == null ? Map.of() : placeholders;
        if (sender instanceof Player player && plugin != null && plugin.gui() != null) {
            return plugin.gui().tr(player, key, fallback, ph);
        }
        if (plugin != null && plugin.codex() != null) {
            try {
                String value = plugin.codex().tr(key, ph);
                if (value != null && !value.isBlank() && !value.equals(key)) return value;
            } catch (Throwable ignored) {}
        }
        String out = fallback == null ? "" : fallback;
        for (Map.Entry<String, String> entry : ph.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return out;
    }
}
