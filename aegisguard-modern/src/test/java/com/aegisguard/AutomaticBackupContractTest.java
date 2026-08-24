package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticBackupContractTest {
    private static final Path ROOT = Path.of("src/main");

    @Test
    void defaultsAreBoundedStorageSafeAndDisabled() throws Exception {
        Map<?, ?> config = new Yaml().load(Files.readString(ROOT.resolve("resources/config.yml")));
        Map<?, ?> snapshots = (Map<?, ?>) config.get("snapshots");
        Map<?, ?> automatic = (Map<?, ?>) snapshots.get("automatic_player");
        assertFalse((Boolean) automatic.get("enabled"));
        assertTrue((Boolean) automatic.get("include_server_zones"));
        assertEquals(5, ((Number) automatic.get("batch_size")).intValue());
        assertEquals(5, ((Number) automatic.get("retention_per_plot")).intValue());
        assertTrue((Boolean) automatic.get("skip_unchanged"));
        Map<?, ?> builds = (Map<?, ?>) automatic.get("build_backup");
        assertFalse((Boolean) builds.get("enabled"));
    }

    @Test
    void schedulingKeepsBukkitCaptureOnGlobalAndRegionDomains() throws Exception {
        String service = Files.readString(ROOT.resolve(
                "java/com/aegisguard/snapshots/AutomaticPlayerBackupService.java"));
        String plugin = Files.readString(ROOT.resolve("java/com/aegisguard/AegisGuard.java"));
        assertTrue(plugin.contains("startAutomaticPlayerBackupTask()"));
        assertTrue(plugin.contains("cancelTaskReflectively(automaticPlayerBackupTask)"));
        assertTrue(service.contains("plugin.scheduler().runAt(target"));
        assertTrue(service.contains("plugin.scheduler().runGlobal("),
                "A batch invoked from any thread must first enter the global ownership domain");
        assertTrue(service.contains("plugin.scheduler().runAsync("));
        assertTrue(service.contains("plugin.scheduler().owns(target)"));
        assertTrue(service.contains("getBoolean(\"snapshots.enabled\", true)"));
        assertFalse(service.contains("Bukkit.getScheduler()"));
        assertFalse(service.contains("Bukkit.getRegionScheduler()"));
    }

    @Test
    void automaticAndRestoreWorkRefuseToOverlap() throws Exception {
        String manager = Files.readString(ROOT.resolve(
                "java/com/aegisguard/snapshots/SnapshotManager.java"));
        String service = Files.readString(ROOT.resolve(
                "java/com/aegisguard/snapshots/AutomaticPlayerBackupService.java"));
        assertTrue(manager.contains("automaticPlayerBackups.isPlotInFlight"));
        assertTrue(manager.contains("RestoreStatus.BACKUP_IN_PROGRESS"));
        assertTrue(service.contains("snapshots.isRestoreLocked(plotId)"));
        assertTrue(service.contains("!inFlightPlots.add(plotId)"));
    }

    @Test
    void serverZonesUseTheBoundedCoordinatorWithoutLegacyTimerOverlap() throws Exception {
        String manager = Files.readString(ROOT.resolve(
                "java/com/aegisguard/snapshots/SnapshotManager.java"));
        String service = Files.readString(ROOT.resolve(
                "java/com/aegisguard/snapshots/AutomaticPlayerBackupService.java"));
        assertTrue(service.contains("AUTOMATIC_SERVER_ZONE"));
        assertTrue(service.contains("plot.isServerZone()"));
        assertTrue(service.contains("includesServerZones()"));
        assertTrue(manager.contains("usesUnifiedAutomaticServerZoneBackups()"));
        assertTrue(manager.contains("!isScheduledEnabled() || usesUnifiedAutomaticServerZoneBackups()"));
        assertTrue(manager.contains("snapshot.getType() == automaticType"));
    }

    @Test
    void buildEnabledPassesDoNotClaimReliableUnchangedDetection() throws Exception {
        String service = Files.readString(ROOT.resolve(
                "java/com/aegisguard/snapshots/AutomaticPlayerBackupService.java"));
        assertTrue(service.contains("skipUnchanged && !captureBuild"));
        assertTrue(service.contains("captureBuild && !sameGeometry"));
    }
}
