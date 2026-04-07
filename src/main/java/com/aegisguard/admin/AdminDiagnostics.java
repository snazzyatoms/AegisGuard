package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.migration.MigrationManager;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;

public final class AdminDiagnostics {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private AdminDiagnostics() {}

    public static Path writeReport(AegisGuard plugin) throws IOException {
        Path reportsDir = plugin.getDataFolder().toPath().resolve("reports");
        Files.createDirectories(reportsDir);

        String fileName = "doctor-" + LocalDateTime.now().format(FILE_TS) + ".txt";
        Path report = reportsDir.resolve(fileName);
        Files.writeString(report, buildReport(plugin), StandardCharsets.UTF_8);
        return report;
    }

    public static String buildReport(AegisGuard plugin) {
        Collection<Plot> allPlots = safePlots(plugin);
        long serverZones = allPlots.stream().filter(Plot::isServerZone).count();
        long marketPlots = allPlots.stream().filter(p -> p.isForSale() || p.isForRent() || p.isForAuction()).count();

        List<MigrationManager.SourcePlugin> migrationSources = List.of();
        try {
            if (plugin.migration() != null) {
                migrationSources = plugin.migration().getAvailableSources();
            }
        } catch (Throwable ignored) {}

        StringBuilder out = new StringBuilder();
        out.append("AegisGuard Doctor Report\n");
        out.append("========================\n");
        out.append("Generated: ").append(LocalDateTime.now()).append('\n');
        out.append("Plugin Version: ").append(plugin.getDescription().getVersion()).append('\n');
        out.append("Server: ").append(Bukkit.getName()).append(' ').append(Bukkit.getVersion()).append('\n');
        out.append("Bukkit: ").append(Bukkit.getBukkitVersion()).append('\n');
        out.append("Java: ").append(System.getProperty("java.version", "unknown")).append('\n');
        out.append("OS: ").append(System.getProperty("os.name", "unknown")).append(' ')
                .append(System.getProperty("os.version", "")).append('\n');
        out.append('\n');

        out.append("Core State\n");
        out.append("----------\n");
        out.append("Data Store: ").append(plugin.store() == null ? "missing" : plugin.store().getClass().getSimpleName()).append('\n');
        out.append("Economy Hook: ").append(plugin.eco() == null ? "missing" : "present").append('\n');
        out.append("Claim Blocks: ").append(plugin.claimBlocks() == null ? "missing" : "present").append('\n');
        out.append("Exchange Service: ").append(plugin.exchange() == null ? "missing" : "present").append('\n');
        out.append("Snapshot Manager: ").append(plugin.getSnapshotManager() == null ? "missing" : "present").append('\n');
        out.append("Migration Manager: ").append(plugin.migration() == null ? "missing" : "present").append('\n');
        out.append("Protection Hooks: ").append(plugin.protectionHooks() == null ? "missing" : plugin.protectionHooks().getActiveHookIds()).append('\n');
        out.append("Migration Sources Detected: ").append(migrationSources).append('\n');
        out.append('\n');

        out.append("Plot Summary\n");
        out.append("------------\n");
        out.append("Total Plots: ").append(allPlots.size()).append('\n');
        out.append("Server Zones: ").append(serverZones).append('\n');
        out.append("Market/Rent/Auction Plots: ").append(marketPlots).append('\n');
        out.append('\n');

        out.append("Config Summary\n");
        out.append("--------------\n");
        out.append("Low Overhead Mode: ").append(bool(plugin, "performance.low_overhead_mode", false)).append('\n');
        out.append("Claim Blocks Enabled: ").append(bool(plugin, "claim_blocks.enabled", true)).append('\n');
        out.append("Exchange Enabled: ").append(bool(plugin, "claim_blocks.exchange.enabled", false)).append('\n');
        out.append("Snapshots Enabled: ").append(bool(plugin, "snapshots.enabled", true)).append('\n');
        out.append("Zoning Enabled: ").append(bool(plugin, "zoning.enabled", true)).append('\n');
        out.append("Leveling Enabled: ").append(bool(plugin, "leveling.enabled", true)).append('\n');
        out.append("Biomes Enabled: ").append(bool(plugin, "biomes.enabled", true)).append('\n');
        out.append('\n');

        out.append("Staff Access\n");
        out.append("------------\n");
        out.append("Global Manage Permissions: ").append(list(plugin, "staff_access.global_manage_permissions", List.of("aegis.admin.manage"))).append('\n');
        out.append("Server Zone Manage Permissions: ").append(list(plugin, "staff_access.server_zone_manage_permissions", List.of("aegis.serverzone.manage", "aegis.staff.co_owner"))).append('\n');
        out.append("Market Plot Manage Permissions: ").append(list(plugin, "staff_access.market_plot_manage_permissions", List.of("aegis.market.manage", "aegis.staff.market_steward"))).append('\n');
        out.append('\n');

        out.append("Notes\n");
        out.append("-----\n");
        out.append("- This report is static and configuration-focused.\n");
        out.append("- It does not include live stack traces unless the server logs them separately.\n");
        out.append("- Share this file with plugin support when reporting issues.\n");
        return out.toString();
    }

    private static Collection<Plot> safePlots(AegisGuard plugin) {
        try {
            return plugin.store() == null ? List.of() : plugin.store().getAllPlots();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static boolean bool(AegisGuard plugin, String path, boolean fallback) {
        try {
            return plugin.getConfig().getBoolean(path, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static List<String> list(AegisGuard plugin, String path, List<String> fallback) {
        try {
            List<String> values = plugin.getConfig().getStringList(path);
            if (values != null && !values.isEmpty()) return values;
        } catch (Throwable ignored) {}
        return fallback;
    }
}
