package com.aegisguard;

import com.aegisguard.scheduler.AegisScheduler;
import com.aegisguard.snapshots.PlotBuildBackup;
import com.aegisguard.snapshots.SnapshotManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoliaRestoreFoundationContractTest {

    private static final Path JAVA = Path.of("src/main/java/com/aegisguard");

    @Test
    void schedulerExposesExplicitOwnershipDomainsAndRejectionResults() throws Exception {
        String scheduler = Files.readString(JAVA.resolve("scheduler/AegisScheduler.java"));
        for (String method : new String[]{"runGlobal(", "runEntity(", "runAt(", "runAsync("}) {
            assertTrue(scheduler.contains(method), "Missing scheduler ownership domain: " + method);
        }
        assertTrue(scheduler.contains("REJECTED_SHUTDOWN"));
        assertTrue(scheduler.contains("REJECTED_RETIRED_ENTITY"));
        assertTrue(scheduler.contains("cancelTasks(plugin)"));
        assertTrue(scheduler.contains("Bukkit.isOwnedByCurrentRegion(location)"));
    }

    @Test
    void pluginCompatibilityWrappersDelegateToCentralScheduler() throws Exception {
        String plugin = Files.readString(JAVA.resolve("AegisGuard.java"));
        assertTrue(plugin.contains("new AegisScheduler(this, isFolia)"));
        assertTrue(plugin.contains("platformScheduler.runGlobal(task)"));
        assertTrue(plugin.contains("platformScheduler.runEntity(player, task, null)"));
        assertTrue(plugin.contains("platformScheduler.runAt(location, task)"));
        assertTrue(plugin.contains("platformScheduler.runAsync(task)"));
        assertTrue(plugin.contains("snapshotManager.shutdownOperations()"));
        assertTrue(plugin.contains("platformScheduler.shutdown()"));
    }

    @Test
    void snapshotPackageDoesNotSelectBukkitSchedulersDirectly() throws Exception {
        try (var files = Files.walk(JAVA.resolve("snapshots"))) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = Files.readString(path);
                    assertFalse(source.contains("Bukkit.getScheduler()"), path.getFileName().toString());
                    assertFalse(source.contains("Bukkit.getRegionScheduler()"), path.getFileName().toString());
                    assertFalse(source.contains("Bukkit.getGlobalRegionScheduler()"), path.getFileName().toString());
                    assertFalse(source.contains("Bukkit.getAsyncScheduler()"), path.getFileName().toString());
                } catch (java.io.IOException error) {
                    throw new java.io.UncheckedIOException(error);
                }
            });
        }
    }

    @Test
    void schematicDiskIoIsSeparatedFromRegionOwnedWorldWork() throws Exception {
        String backup = Files.readString(JAVA.resolve("snapshots/PlotBuildBackup.java"));
        assertTrue(backup.contains("plugin.scheduler().runAsync(") && backup.contains("writeClipboard(snapshot, clipboard, dest)"),
                "Captured clipboards must be serialized asynchronously");
        int trackedRead = backup.indexOf("BuildBackupMetadata metadata = metadataStore.load(snapshot.getSnapshotId())");
        int asyncBeforeRead = backup.lastIndexOf("plugin.scheduler().runAsync(() ->", trackedRead);
        assertTrue(trackedRead > 0 && asyncBeforeRead >= 0,
                "Schematic files must be read asynchronously before a paste");
        assertTrue(backup.contains("plugin.scheduler().runAt(target,")
                        && backup.contains("() -> result.complete(restoreClipboard"),
                "Prepared clipboard pastes must return to the owning region");
    }

    @Test
    void restoreResultNeverCallsQueuedBuildWorkComplete() {
        SnapshotManager.RestoreResult queued = new SnapshotManager.RestoreResult(
                null, null, SnapshotManager.RestoreStatus.BUILD_QUEUED,
                PlotBuildBackup.RestoreQueueResult.QUEUED, "queued");
        SnapshotManager.RestoreResult dataOnly = new SnapshotManager.RestoreResult(
                null, null, SnapshotManager.RestoreStatus.DATA_RESTORED,
                PlotBuildBackup.RestoreQueueResult.NO_BACKUP, "done");

        assertTrue(queued.dataRestored());
        assertFalse(queued.complete(), "Queued schematic work must not be reported complete");
        assertTrue(dataOnly.complete());
    }

    @Test
    void dispatchResultOnlyAcceptsAcceptedState() {
        assertTrue(AegisScheduler.DispatchResult.ACCEPTED.accepted());
        assertFalse(AegisScheduler.DispatchResult.REJECTED_SHUTDOWN.accepted());
        assertFalse(AegisScheduler.DispatchResult.REJECTED_RETIRED_ENTITY.accepted());
        assertFalse(AegisScheduler.DispatchResult.FAILED.accepted());
    }
}
