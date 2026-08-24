package com.aegisguard.snapshots;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotBuildBackupTest {

    @Test
    void volumeUsesInclusiveBlockBounds() {
        assertEquals(8L, PlotBuildBackup.volume(0, 0, 1, 1, 0, 1));
        assertEquals(21L * 384L * 21L, PlotBuildBackup.volume(0, 0, 20, 20, -64, 319));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shippedBuildBackupDefaultsOffWithVolumeCap() throws Exception {
        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (var in = Files.newInputStream(Path.of("src/main/resources/config.yml"))) {
            config = yaml.load(in);
        }
        Map<String, Object> snapshots = (Map<String, Object>) config.get("snapshots");
        Map<String, Object> backup = (Map<String, Object>) snapshots.get("build_backup");
        assertEquals(Boolean.FALSE, backup.get("enabled"));
        assertEquals(2_000_000, ((Number) backup.get("max_volume")).intValue());
        assertEquals(Boolean.FALSE, backup.get("copy_entities"));
        String pluginYml = Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertTrue(pluginYml.contains("WorldEdit"));
        assertTrue(pluginYml.contains("FastAsyncWorldEdit"));
        String manager = Files.readString(Path.of("src/main/java/com/aegisguard/snapshots/SnapshotManager.java"));
        assertTrue(manager.contains("queueCapture"));
        assertTrue(manager.contains("restoreTrackedAsync"));
        assertFalse(manager.contains("public RestoreQueueResult queueRestore"));
        assertFalse(Files.readString(Path.of("src/main/resources/config.yml"))
                .contains("planned for a later version"));
    }

    @Test
    void pasteAlignsClipboardMinimumToDestinationEvenIfOriginIsZero() {
        var destMin = com.sk89q.worldedit.math.BlockVector3.at(1000, -64, 2000);
        var clipMin = com.sk89q.worldedit.math.BlockVector3.at(1000, -64, 2000);
        var originZero = com.sk89q.worldedit.math.BlockVector3.ZERO;
        var to = PlotBuildBackup.alignedPasteTo(destMin, clipMin, originZero);
        var placedMin = to.add(clipMin.subtract(originZero));
        assertEquals(destMin, placedMin);
    }

    @Test
    void largePlotSpansMultipleFoliaRegions() {
        UUID owner = UUID.randomUUID();
        ClaimSnapshot small = new ClaimSnapshot(
                new com.aegisguard.data.Plot(UUID.randomUUID(), owner, "n", "world", 0, 0, 20, 20),
                ClaimSnapshot.SnapshotType.MANUAL, "t", owner);
        assertFalse(PlotBuildBackup.spansMultipleFoliaRegions(small));

        ClaimSnapshot wide = new ClaimSnapshot(
                new com.aegisguard.data.Plot(UUID.randomUUID(), owner, "n", "world", 0, 0, 600, 10),
                ClaimSnapshot.SnapshotType.MANUAL, "t", owner);
        assertTrue(PlotBuildBackup.spansMultipleFoliaRegions(wide));
    }

    @Test
    void tileFileNameRoundTripsCoordinates() {
        UUID id = UUID.randomUUID();
        int[] parsed = PlotBuildBackup.parseTileCoords(id + "__-128_512.schem", id);
        assertEquals(-128, parsed[0]);
        assertEquals(512, parsed[1]);
    }

    @Test
    void foliaTilesNeverCrossAChunkOwnershipBoundary() {
        var tiles = PlotBuildBackup.computeTileBounds(-17, 40, -2, 33, 16, true);
        assertTrue(tiles.size() > 1);
        for (int[] tile : tiles) {
            assertEquals(Math.floorDiv(tile[0], 16), Math.floorDiv(tile[1], 16),
                    "Folia tile crossed an X chunk boundary");
            assertEquals(Math.floorDiv(tile[2], 16), Math.floorDiv(tile[3], 16),
                    "Folia tile crossed a Z chunk boundary");
        }
    }
}
