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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** Full health scan with block checks on owning regions and file/storage checks asynchronously. */
    public static CompletableFuture<List<Finding>> scanAsync(AegisGuard plugin) {
        CompletableFuture<List<Finding>> result = new CompletableFuture<>();
        if (plugin == null || plugin.scheduler() == null) {
            result.complete(scan(plugin));
            return result;
        }
        var global = plugin.scheduler().runGlobal(() -> {
            try {
                List<Finding> base = new ArrayList<>(scan(plugin));
                List<CompletableFuture<Void>> destinationJobs = new ArrayList<>();
                AtomicInteger unsafeDestinations = new AtomicInteger();
                if (plugin.store() != null && plugin.safeTravel() != null) {
                    for (Plot plot : plugin.store().getAllPlots()) {
                        if (plot == null || (!plot.isServerWarp() && plot.getSpawnLocation() == null)) continue;
                        var spawn = plot.getSpawnLocation();
                        if (spawn == null || spawn.getWorld() == null) continue;
                        CompletableFuture<Void> checked = new CompletableFuture<>();
                        var dispatch = plugin.scheduler().runAt(spawn, () -> {
                            try {
                                if (plugin.safeTravel().findSafeDestination(spawn) == null) {
                                    unsafeDestinations.incrementAndGet();
                                }
                            } catch (Throwable checkError) {
                                unsafeDestinations.incrementAndGet();
                            } finally {
                                checked.complete(null);
                            }
                        });
                        if (!dispatch.accepted()) {
                            unsafeDestinations.incrementAndGet();
                            checked.complete(null);
                        }
                        destinationJobs.add(checked);
                    }
                }
                CompletableFuture.allOf(destinationJobs.toArray(CompletableFuture[]::new))
                        .thenCompose(ignored -> plugin.getSnapshotManager() == null
                                ? CompletableFuture.completedFuture(null)
                                : plugin.getSnapshotManager().buildBackup().maintainStorageAsync(true))
                        .whenComplete((storage, error) -> {
                            if (unsafeDestinations.get() > 0) {
                                base.add(new Finding("UNSAFE_DESTINATIONS", "staff_health_unsafe_destinations",
                                        "{COUNT} destination(s) failed a Folia-region-safe block check.",
                                        Map.of("COUNT", String.valueOf(unsafeDestinations.get()))));
                            }
                            appendRecoveryFindings(plugin, base, storage, error);
                            result.complete(List.copyOf(base));
                        });
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        if (!global.accepted()) result.complete(List.of(new Finding("SCHEDULER_REJECTED",
                "staff_health_scheduler_rejected", "Scheduler rejected the health scan.")));
        return result;
    }

    private static void appendRecoveryFindings(AegisGuard plugin, List<Finding> findings,
                                                com.aegisguard.snapshots.PlotBuildBackup.StorageReport storage,
                                                Throwable storageError) {
        if (plugin.getSnapshotManager() == null) return;
        var integration = plugin.getSnapshotManager().buildBackup().integrationInfo();
        boolean buildRequired = plugin.getConfig().getBoolean("snapshots.build_backup.enabled", false)
                || plugin.getConfig().getBoolean("snapshots.automatic_player.build_backup.enabled", false);
        String integrationCode = integration.compatible() || !buildRequired
                ? "BUILD_INTEGRATION_STATUS" : "BUILD_INTEGRATION_WARNING";
        findings.add(new Finding(integrationCode,
                "staff_health_build_integration",
                "Build integration: {NAME} {VERSION}; compatible={COMPATIBLE}; {DETAIL}",
                Map.of("NAME", integration.name(), "VERSION", integration.version(),
                        "COMPATIBLE", String.valueOf(integration.compatible()), "DETAIL", integration.detail())));
        if (storageError != null || storage == null) {
            findings.add(new Finding("SNAPSHOT_STORAGE_ERROR", "staff_health_snapshot_storage_error",
                    "Snapshot storage inspection failed: {ERROR}",
                    Map.of("ERROR", storageError == null ? "unknown error" : String.valueOf(storageError.getMessage()))));
        } else {
            findings.add(new Finding("SNAPSHOT_STORAGE_STATUS", "staff_health_snapshot_storage_status",
                    "Build storage {USED}/{LIMIT} bytes; manifests={MANIFESTS}, missing={MISSING}, corrupt={CORRUPT}, incompatible={INCOMPATIBLE}, orphans={ORPHANS}, protected={PROTECTED}.",
                    Map.of("USED", String.valueOf(storage.totalBytes()),
                            "LIMIT", String.valueOf(storage.configuredLimitBytes()),
                            "MANIFESTS", String.valueOf(storage.manifests()),
                            "MISSING", String.valueOf(storage.missingBackups()),
                            "CORRUPT", String.valueOf(storage.corruptBackups()),
                            "INCOMPATIBLE", String.valueOf(storage.incompatibleBackups()),
                            "ORPHANS", String.valueOf(storage.orphanFiles()),
                            "PROTECTED", String.valueOf(storage.protectedBackups()))));
        }
        long pending = 0, paused = 0, partial = 0, failed = 0;
        for (var operation : plugin.getSnapshotManager().getRestoreOperations()) {
            switch (operation.status()) {
                case PREFLIGHT, RESCUE_CREATING, DATA_RESTORING, BUILD_QUEUED, BUILD_RUNNING -> pending++;
                case PAUSED_REVIEW -> paused++;
                case PARTIAL -> partial++;
                case FAILED -> failed++;
                default -> { }
            }
        }
        findings.add(new Finding("RESTORE_OPERATIONS_STATUS", "staff_health_restore_operations",
                "Restores pending={PENDING}, paused={PAUSED}, partial={PARTIAL}, failed={FAILED}, maintenance locks={LOCKS}.",
                Map.of("PENDING", String.valueOf(pending), "PAUSED", String.valueOf(paused),
                        "PARTIAL", String.valueOf(partial), "FAILED", String.valueOf(failed),
                        "LOCKS", String.valueOf(plugin.getSnapshotManager().maintenanceLockCount()))));
        findings.add(new Finding("SCHEDULER_STATUS", "staff_health_scheduler_status",
                "Scheduler platform={PLATFORM}; in-flight restores={RESTORES}; automatic backups={BACKUPS}.",
                Map.of("PLATFORM", plugin.isFolia() ? "Folia" : "Paper/Purpur/Spigot",
                        "RESTORES", String.valueOf(plugin.getSnapshotManager().inFlightRestoreCount()),
                        "BACKUPS", String.valueOf(plugin.getSnapshotManager().automaticPlayerBackups().inFlightCount()))));
        var modules = plugin.getConfig().getConfigurationSection("modules");
        if (modules != null) {
            List<String> disabled = modules.getKeys(false).stream()
                    .filter(key -> !modules.getBoolean(key, true)).sorted().toList();
            findings.add(new Finding("DISABLED_MODULES_STATUS", "staff_health_disabled_modules",
                    "Disabled modules: {MODULES}",
                    Map.of("MODULES", disabled.isEmpty() ? "none" : String.join(", ", disabled))));
        }
    }

    public static void report(AegisGuard plugin, CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Running region-safe AegisGuard health checks...");
        scanAsync(plugin).whenComplete((findings, error) -> {
            Runnable deliver = () -> {
                List<Finding> safe = error == null && findings != null ? findings
                        : List.of(new Finding("HEALTH_SCAN_FAILED", "staff_health_scan_failed",
                        "Health scan failed: {ERROR}", Map.of("ERROR",
                        error == null ? "unknown error" : String.valueOf(error.getMessage()))));
                send(plugin, sender, "staff_health_title", "&6&lAegisGuard Staff Health Check", Map.of());
                for (Finding finding : safe) {
                    String color = "OK".equals(finding.code()) || finding.code().endsWith("STATUS")
                            || "MOB_BARRIER".equals(finding.code()) ? "&a" : "&e";
                    String body = localize(plugin, sender, finding.messageKey(), finding.fallback(), finding.placeholders());
                    send(plugin, sender, "staff_health_line", "{COLOR}[{CODE}] &7{MESSAGE}",
                            Map.of("COLOR", color, "CODE", finding.code(), "MESSAGE", ChatColor.stripColor(
                                    ChatColor.translateAlternateColorCodes('&', body))));
                }
            };
            if (sender instanceof Player player) plugin.runMain(player, deliver);
            else {
                var dispatch = plugin.scheduler().runGlobal(deliver);
                if (!dispatch.accepted()) deliver.run();
            }
        });
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
