package com.aegisguard;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end source contracts for the 1.3.5 recovery-safety release. */
class RecoverySafetyContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");

    @Test
    void everyRestoreEntryUsesTheDurableTransaction() throws Exception {
        String manager = Files.readString(JAVA.resolve("snapshots/SnapshotManager.java"));
        assertTrue(manager.contains("return restoreAsync(snapshotId, null"));
        assertTrue(manager.contains("CompletableFuture<RestoreResult> future = restoreAsync(snapshotId)"));
        assertFalse(manager.contains("restoreOnOwnedRegion"));
        assertFalse(manager.contains("plotBuildBackup.queueRestore"));
        String backup = Files.readString(JAVA.resolve("snapshots/PlotBuildBackup.java"));
        assertFalse(backup.contains("public CompletableFuture<TrackedRestoreResult> restoreTrackedAsync"));
        assertTrue(backup.contains("protectedBuildSnapshotIds().contains(snapshotId)"));
        assertTrue(manager.contains("buildDestinationSafe(snapshot, current, scopes)"));
        assertTrue(manager.contains("Snapshot build world is not loaded"));
        assertTrue(manager.contains("operation.scopes().contains(RestoreScope.BUILD)"));
        assertTrue(manager.contains("captureAsync(live, rescue, true)"));
        assertTrue(manager.contains("resumeBeforeDataRestore"));
        assertTrue(manager.contains("persisted build rescue is missing, corrupt, or incompatible"));
    }

    @Test
    void fullBuildCaptureIsTiledBoundedAndManifestValidated() throws Exception {
        String backup = Files.readString(JAVA.resolve("snapshots/PlotBuildBackup.java"));
        assertTrue(backup.contains("world.getMinHeight()"));
        assertTrue(backup.contains("world.getMaxHeight() - 1"));
        assertTrue(backup.contains("max_chunks_per_region_job"));
        assertTrue(backup.contains("Math.floorDiv(tileMinX, 16) + chunksX"));
        assertTrue(backup.contains("Math.floorDiv(tileMinZ, 16) + chunksZ"));
        assertTrue(backup.contains("FOLIA_REGION_BLOCKS"));
        assertTrue(backup.contains("persistMetadataAsync"));
        assertTrue(backup.contains("coverageMismatch(metadata)"));
        assertTrue(backup.contains("discardIncompleteCaptureAsync"));
    }

    @Test
    void durableCrossRegionProgressIsCheckpointedBeforeContinuing() throws Exception {
        String manager = Files.readString(JAVA.resolve("snapshots/SnapshotManager.java"));
        String backup = Files.readString(JAVA.resolve("snapshots/PlotBuildBackup.java"));
        String store = Files.readString(JAVA.resolve("snapshots/RestoreOperationStore.java"));
        assertTrue(manager.contains("checkpointBuildTile(tileId, success"));
        assertTrue(manager.contains("return persistOperationsAsync()"));
        assertTrue(backup.contains("chain = chain.thenCompose"));
        assertTrue(backup.contains("checkpoint.apply(entry.fileName(), success)"));
        assertTrue(store.contains("completed_build_tiles"));
        assertTrue(store.contains("pending_build_tiles"));
        assertTrue(store.contains("failed_build_tiles"));
    }

    @Test
    void maintenanceLockCoversPlayersAndNaturalWorldChanges() throws Exception {
        String plot = Files.readString(JAVA.resolve("data/Plot.java"));
        String listener = Files.readString(JAVA.resolve("snapshots/RestoreMaintenanceListener.java"));
        assertTrue(plot.contains("isRestoreMaintenanceLocked(pl)"));
        for (String event : new String[]{"BlockFromToEvent", "BlockPistonExtendEvent",
                "EntityExplodeEvent", "BlockGrowEvent", "BlockFadeEvent", "StructureGrowEvent"}) {
            assertTrue(listener.contains(event), "Maintenance lock does not cover " + event);
        }
    }

    @Test
    void browserHealthAndAuditExposeRecoveryState() throws Exception {
        String gui = Files.readString(JAVA.resolve("snapshots/SnapshotAdminGUI.java"));
        String health = Files.readString(JAVA.resolve("admin/StaffHealthCheck.java"));
        String manager = Files.readString(JAVA.resolve("snapshots/SnapshotManager.java"));
        assertTrue(gui.contains("INTEGRITY_ISSUES"));
        assertTrue(gui.contains("openOperations(player)"));
        assertTrue(gui.contains("maintainStorageAsync(true)"));
        assertTrue(health.contains("scanAsync(AegisGuard plugin)"));
        assertTrue(health.contains("maintenanceLockCount()"));
        assertTrue(health.contains("maintainStorageAsync(true)"));
        assertTrue(manager.contains("AuditCategory.SNAPSHOT_RESTORE"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void riskyIntegrationAndWebhookFeaturesShipFailClosed() throws Exception {
        Map<String, Object> root;
        try (var input = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            root = new Yaml().load(input);
        }
        Map<String, Object> snapshots = (Map<String, Object>) root.get("snapshots");
        Map<String, Object> build = (Map<String, Object>) snapshots.get("build_backup");
        Map<String, Object> folia = (Map<String, Object>) build.get("folia");
        Map<String, Object> hooks = (Map<String, Object>) root.get("hooks");
        Map<String, Object> discord = (Map<String, Object>) hooks.get("discord");
        Map<String, Object> events = (Map<String, Object>) discord.get("events");
        assertEquals(Boolean.FALSE, build.get("enabled"));
        assertEquals(Boolean.TRUE, folia.get("require_fawe"));
        assertEquals(Boolean.FALSE, events.get("restore_failure"));
        assertEquals(Boolean.FALSE, events.get("backup_warning"));
    }

    @Test
    void persistenceFailuresAndReloadableWebhooksFailClosed() throws Exception {
        String snapshots = Files.readString(JAVA.resolve("snapshots/SnapshotManager.java"));
        String yml = Files.readString(JAVA.resolve("data/YMLDataStore.java"));
        String sql = Files.readString(JAVA.resolve("data/SQLDataStore.java"));
        String webhook = Files.readString(JAVA.resolve("hooks/DiscordWebhook.java"));
        String territory = Files.readString(JAVA.resolve("territory/TerritoryLifeService.java"));
        assertTrue(snapshots.contains("Failed to durably save claim-snapshots.yml"));
        assertTrue(yml.contains("Failed to durably save plot data"));
        assertTrue(sql.contains("SQL datastore is unavailable while saving plot"));
        assertTrue(webhook.contains("private String configuredUrl()"));
        assertFalse(webhook.contains("private final String url;"));
        assertTrue(webhook.contains("connection.disconnect();"));
        assertTrue(territory.contains("synchronizeRestoredPlot"));
        assertTrue(territory.contains("Failed to durably save territory-life.yml"));
    }

    @Test
    void translatedRestoreHelpKeepsExecutableArgumentsLiteral() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/resources/lang"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().equals("system.yml")).toList()) {
                String text = Files.readString(path);
                for (String line : text.lines().filter(line -> line.startsWith("admin_help_restore:")
                        || line.startsWith("admin_migrate_help_restore:")
                        || line.startsWith("admin_restore_confirm_hint:")).toList()) {
                    assertTrue(line.contains("/agadmin restore here confirm"), path + ": " + line);
                }
            }
        }
    }
}
