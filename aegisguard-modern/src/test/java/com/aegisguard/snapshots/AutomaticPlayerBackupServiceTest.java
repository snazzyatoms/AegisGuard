package com.aegisguard.snapshots;

import com.aegisguard.data.Plot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomaticPlayerBackupServiceTest {

    @Test
    void batchingWrapsFairlyAndNeverExceedsTheConfiguredLimit() {
        List<Integer> plots = List.of(1, 2, 3, 4, 5);
        assertEquals(List.of(4, 5, 1),
                AutomaticPlayerBackupService.selectRoundRobin(plots, 3, 3));
        assertEquals(plots,
                AutomaticPlayerBackupService.selectRoundRobin(plots, 0, 99));
        assertTrue(AutomaticPlayerBackupService.selectRoundRobin(plots, 0, 0).isEmpty());
    }

    @Test
    void dataFingerprintIgnoresSnapshotIdentityButDetectsPlotChanges() {
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "Owner", "world", 0, 0, 31, 31);
        ClaimSnapshot first = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.AUTOMATIC_PLAYER,
                "first", null);
        ClaimSnapshot second = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL,
                "different metadata", UUID.randomUUID());
        assertEquals(AutomaticPlayerBackupService.snapshotFingerprint(first),
                AutomaticPlayerBackupService.snapshotFingerprint(second));

        plot.setFlag("build", false);
        ClaimSnapshot changed = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.AUTOMATIC_PLAYER,
                "changed", null);
        assertNotEquals(AutomaticPlayerBackupService.snapshotFingerprint(first),
                AutomaticPlayerBackupService.snapshotFingerprint(changed));
    }

    @Test
    void serverZoneSnapshotDataUsesTheSameDeterministicFingerprint() {
        Plot zone = new Plot(UUID.randomUUID(), Plot.SERVER_OWNER_UUID,
                "Server", "world", 0, 0, 31, 31);
        ClaimSnapshot first = new ClaimSnapshot(zone,
                ClaimSnapshot.SnapshotType.AUTOMATIC_SERVER_ZONE, "first", null);
        ClaimSnapshot second = new ClaimSnapshot(zone,
                ClaimSnapshot.SnapshotType.AUTOMATIC_SERVER_ZONE, "second", null);
        assertEquals(AutomaticPlayerBackupService.snapshotFingerprint(first),
                AutomaticPlayerBackupService.snapshotFingerprint(second));
    }

    @Test
    void buildCaptureRequiresUnchangedWorldAndBounds() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "Owner", "world", 0, 0, 31, 31);
        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.AUTOMATIC_PLAYER, "", null);
        assertTrue(AutomaticPlayerBackupService.sameGeometry(plot, snapshot));
        plot.setBounds(0, 0, 63, 63);
        assertFalse(AutomaticPlayerBackupService.sameGeometry(plot, snapshot));
    }

    @Test
    void loadProbePausesOnlyWhenARealLowTpsSampleExists() {
        assertEquals(20.0D, AutomaticPlayerBackupService.currentTps(new Object()));
        assertEquals(17.25D, AutomaticPlayerBackupService.currentTps(new FakeServer()), 0.001D);
    }

    @Test
    void stateRoundTripPreservesFingerprintAndTiming(@TempDir Path temp) throws Exception {
        UUID plotId = UUID.randomUUID();
        AutomaticBackupState original = new AutomaticBackupState(plotId, "abc", 100L, 120L, "saved");
        AutomaticBackupStateStore store = new AutomaticBackupStateStore(
                temp.resolve("automatic-player-backups.yml").toFile());
        store.save(List.of(original));
        Map<UUID, AutomaticBackupState> loaded = store.load();
        assertEquals("abc", loaded.get(plotId).fingerprint());
        assertEquals(100L, loaded.get(plotId).lastBackupAt());
        assertEquals(120L, loaded.get(plotId).lastCheckedAt());
        assertEquals("saved", loaded.get(plotId).outcome());
    }

    public static final class FakeServer {
        public double[] getTPS() {
            return new double[] {17.25D, 18.0D, 19.0D};
        }
    }
}
