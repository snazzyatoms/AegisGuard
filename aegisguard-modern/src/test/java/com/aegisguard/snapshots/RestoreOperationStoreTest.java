package com.aegisguard.snapshots;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreOperationStoreTest {

    @Test
    void roundTripKeepsIdentityScopesRescueAndLifecycle(@TempDir Path temp) throws Exception {
        UUID snapshotId = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID rescueId = UUID.randomUUID();
        RestoreOperation operation = RestoreOperation.start(snapshotId, plotId, actorId,
                EnumSet.of(RestoreScope.FLAGS, RestoreScope.BUILD), 100L);
        operation.setRescueSnapshotId(rescueId, 150L);
        operation.markDataRestored("data ok", 175L);
        operation.initializeBuildTiles(java.util.List.of("a.schem", "b.schem"), 180L);
        operation.checkpointBuildTile("a.schem", true, 185L);
        operation.checkpointBuildTile("b.schem", false, 190L);
        operation.markBuildResult("PARTIALLY_COMPLETED", 195L);
        operation.transition(RestoreOperation.Status.BUILD_RUNNING, "pasting tiles", 200L);

        RestoreOperationStore store = new RestoreOperationStore(temp.resolve("restore-operations.yml").toFile());
        store.save(java.util.List.of(operation));
        Map<UUID, RestoreOperation> loaded = store.load();

        RestoreOperation restored = loaded.get(operation.operationId());
        assertEquals(snapshotId, restored.snapshotId());
        assertEquals(plotId, restored.plotId());
        assertEquals(actorId, restored.actorId());
        assertEquals(rescueId, restored.rescueSnapshotId());
        assertEquals(RestoreOperation.Status.BUILD_RUNNING, restored.status());
        assertEquals(EnumSet.of(RestoreScope.FLAGS, RestoreScope.BUILD), restored.scopes());
        assertEquals("pasting tiles", restored.detail());
        assertTrue(restored.dataRestored());
        assertEquals("data ok", restored.dataResult());
        assertEquals("PARTIALLY_COMPLETED", restored.buildResult());
        assertEquals(java.util.Set.of("a.schem"), restored.completedBuildTiles());
        assertEquals(java.util.Set.of("b.schem"), restored.failedBuildTiles());
    }

    @Test
    void retryKeepsCompletedTilesAndRequeuesOnlyFailedWork() {
        RestoreOperation operation = RestoreOperation.start(UUID.randomUUID(), UUID.randomUUID(), null,
                EnumSet.of(RestoreScope.FULL_DATA, RestoreScope.BUILD), 1L);
        operation.markDataRestored("ok", 2L);
        operation.initializeBuildTiles(java.util.List.of("done.schem", "failed.schem"), 3L);
        operation.checkpointBuildTile("done.schem", true, 4L);
        operation.checkpointBuildTile("failed.schem", false, 5L);
        operation.transition(RestoreOperation.Status.PARTIAL, "review", 6L);

        operation.resumeForRetry("retry", 7L);
        assertEquals(RestoreOperation.Status.BUILD_RUNNING, operation.status());
        assertEquals(java.util.Set.of("done.schem"), operation.completedBuildTiles());
        assertEquals(java.util.Set.of("failed.schem"), operation.pendingBuildTiles());
        assertTrue(operation.failedBuildTiles().isEmpty());
    }

    @Test
    void partialAndPausedStatesRemainReviewable() {
        RestoreOperation operation = RestoreOperation.start(UUID.randomUUID(), UUID.randomUUID(), null,
                EnumSet.of(RestoreScope.FULL_DATA), 1L);
        assertFalse(operation.status().terminal());
        operation.transition(RestoreOperation.Status.PARTIAL, "one tile failed", 2L);
        assertTrue(operation.status().terminal());
        assertEquals("one tile failed", operation.detail());
    }

    @Test
    void restartPauseAndStaffReleaseAreExplicitTerminalTransitions() {
        RestoreOperation operation = RestoreOperation.start(UUID.randomUUID(), UUID.randomUUID(), null,
                EnumSet.of(RestoreScope.BUILD), 1L);
        assertTrue(operation.pauseForReviewIfActive("restart", 2L));
        assertEquals(RestoreOperation.Status.PAUSED_REVIEW, operation.status());
        assertFalse(operation.pauseForReviewIfActive("again", 3L));

        operation.markReleased("reviewed", 4L);
        assertEquals(RestoreOperation.Status.RELEASED, operation.status());
        assertEquals("reviewed", operation.detail());

        operation.reopenForReview("disk failed", 5L);
        assertEquals(RestoreOperation.Status.PAUSED_REVIEW, operation.status());
    }
}
