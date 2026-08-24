package com.aegisguard.snapshots;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildBackupMetadataStoreTest {

    @Test
    void manifestRoundTripPreservesIdentityIntegrityAndOperationLinks(@TempDir Path temp) throws Exception {
        UUID snapshot = UUID.randomUUID();
        UUID plot = UUID.randomUUID();
        UUID capture = UUID.randomUUID();
        UUID restore = UUID.randomUUID();
        BuildBackupMetadata original = metadata(snapshot, plot, 100L, 123L)
                .withCaptureOperation(capture).withLastRestoreOperation(restore);
        BuildBackupMetadataStore store = new BuildBackupMetadataStore(temp.toFile());
        store.save(original);

        BuildBackupMetadata loaded = store.load(snapshot);
        assertEquals(snapshot, loaded.snapshotId());
        assertEquals(plot, loaded.plotId());
        assertEquals(capture, loaded.captureOperationId());
        assertEquals(restore, loaded.lastRestoreOperationId());
        assertEquals(original.aggregateChecksum(), loaded.aggregateChecksum());
        assertEquals(123L, loaded.totalBytes());
        assertFalse(Files.exists(temp.resolve(snapshot + ".meta.yml.tmp")));
    }

    @Test
    void checksumChangesWhenAFileDigestChanges() {
        UUID snapshot = UUID.randomUUID();
        UUID plot = UUID.randomUUID();
        BuildBackupMetadata first = metadata(snapshot, plot, 1L, 10L);
        BuildBackupMetadata changed = new BuildBackupMetadata(
                BuildBackupMetadata.CURRENT_SCHEMA, snapshot, plot, "world",
                0, 0, 31, 31, -64, 319, 1L, "MANUAL", "SPONGE_SCHEMATIC",
                "WorldEdit", "1", List.of(new BuildBackupMetadata.BuildFile(
                snapshot + ".schem", 0, 0, 31, 31, 10L, "different")), null, null);
        assertNotEquals(first.aggregateChecksum(), changed.aggregateChecksum());
    }

    @Test
    void retentionAndGlobalLimitNeverPruneProtectedBackups() {
        UUID plot = UUID.randomUUID();
        BuildBackupMetadata oldest = metadata(UUID.randomUUID(), plot, 1L, 100L);
        BuildBackupMetadata middle = metadata(UUID.randomUUID(), plot, 2L, 100L);
        BuildBackupMetadata newest = metadata(UUID.randomUUID(), plot, 3L, 100L);

        Set<UUID> countPrune = PlotBuildBackup.selectBuildBackupsToPrune(
                List.of(oldest, middle, newest), 2, 10_000L, Set.of());
        assertEquals(Set.of(oldest.snapshotId()), countPrune);

        Set<UUID> protectedPrune = PlotBuildBackup.selectBuildBackupsToPrune(
                List.of(oldest, middle, newest), 2, 150L, Set.of(oldest.snapshotId()));
        assertFalse(protectedPrune.contains(oldest.snapshotId()));
        assertTrue(protectedPrune.contains(middle.snapshotId()));
        assertTrue(protectedPrune.contains(newest.snapshotId()));
    }

    @Test
    void sha256DetectsFileMutation(@TempDir Path temp) throws Exception {
        Path file = temp.resolve("backup.schem");
        Files.writeString(file, "first");
        String first = PlotBuildBackup.sha256(file.toFile());
        Files.writeString(file, "second");
        assertNotEquals(first, PlotBuildBackup.sha256(file.toFile()));
    }

    @Test
    void malformedManifestIsNotAcceptedOrDeleted(@TempDir Path temp) throws Exception {
        UUID snapshot = UUID.randomUUID();
        Path manifest = temp.resolve(snapshot + ".meta.yml");
        Files.writeString(manifest, "snapshot_id: definitely-not-a-uuid\n");
        BuildBackupMetadataStore store = new BuildBackupMetadataStore(temp.toFile());

        assertNull(store.load(snapshot));
        assertTrue(Files.exists(manifest), "Invalid evidence must remain available for quarantine/review");
    }

    @Test
    void manifestCoverageRejectsHolesOverlapsAndEscapedTiles() {
        UUID snapshot = UUID.randomUUID();
        UUID plot = UUID.randomUUID();
        BuildBackupMetadata valid = new BuildBackupMetadata(BuildBackupMetadata.CURRENT_SCHEMA,
                snapshot, plot, "world", 0, 0, 31, 15, -64, 319, 1L, "MANUAL",
                "SPONGE_SCHEMATIC", "FAWE", "1", List.of(
                file("a.schem", 0, 0, 15, 15), file("b.schem", 16, 0, 31, 15)), null, null);
        assertNull(PlotBuildBackup.coverageMismatch(valid));

        BuildBackupMetadata hole = new BuildBackupMetadata(BuildBackupMetadata.CURRENT_SCHEMA,
                snapshot, plot, "world", 0, 0, 31, 15, -64, 319, 1L, "MANUAL",
                "SPONGE_SCHEMATIC", "FAWE", "1",
                List.of(file("a.schem", 0, 0, 15, 15)), null, null);
        assertEquals("Build tile coverage has holes", PlotBuildBackup.coverageMismatch(hole));

        BuildBackupMetadata overlap = new BuildBackupMetadata(BuildBackupMetadata.CURRENT_SCHEMA,
                snapshot, plot, "world", 0, 0, 31, 15, -64, 319, 1L, "MANUAL",
                "SPONGE_SCHEMATIC", "FAWE", "1", List.of(
                file("a.schem", 0, 0, 20, 15), file("b.schem", 16, 0, 31, 15)), null, null);
        assertEquals("Build tile bounds overlap", PlotBuildBackup.coverageMismatch(overlap));

        BuildBackupMetadata escaped = new BuildBackupMetadata(BuildBackupMetadata.CURRENT_SCHEMA,
                snapshot, plot, "world", 0, 0, 31, 15, -64, 319, 1L, "MANUAL",
                "SPONGE_SCHEMATIC", "FAWE", "1",
                List.of(file("a.schem", -1, 0, 31, 15)), null, null);
        assertEquals("Build tile bounds escape the plot or are inverted",
                PlotBuildBackup.coverageMismatch(escaped));
    }

    private static BuildBackupMetadata.BuildFile file(String name, int minX, int minZ,
                                                       int maxX, int maxZ) {
        return new BuildBackupMetadata.BuildFile(name, minX, minZ, maxX, maxZ, 1L, "digest");
    }

    private static BuildBackupMetadata metadata(UUID snapshot, UUID plot, long createdAt, long bytes) {
        return new BuildBackupMetadata(BuildBackupMetadata.CURRENT_SCHEMA, snapshot, plot, "world",
                0, 0, 31, 31, -64, 319, createdAt, "MANUAL", "SPONGE_SCHEMATIC",
                "WorldEdit", "1", List.of(new BuildBackupMetadata.BuildFile(
                snapshot + ".schem", 0, 0, 31, 31, bytes, "digest-" + createdAt)), null, null);
    }
}
