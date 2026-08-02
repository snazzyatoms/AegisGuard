package com.aegisguard.snapshots;

import com.aegisguard.data.Plot;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 0 (release safety): proves that rolling a plot back to a snapshot restores every
 * tracked field exactly, so a bad expansion/merge/admin action can be safely undone.
 *
 * Exercises {@link SnapshotManager#restorePlotState(Plot, ClaimSnapshot)} directly (package-visible,
 * static, and independent of a live plugin/data-store instance) rather than the full
 * {@link SnapshotManager#rollback(UUID)} path, since that path additionally needs a running
 * {@code AegisGuard} plugin and {@code IDataStore} that this test suite does not stand up.
 */
class SnapshotManagerTest {

    @Test
    void restoringASnapshotUndoesEveryChangeMadeAfterItWasTaken() {
        UUID owner = UUID.randomUUID();
        UUID trustedMember = UUID.randomUUID();
        UUID laterBannedPlayer = UUID.randomUUID();
        UUID intruder = UUID.randomUUID();

        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.setPlotName("Original Homestead");
        plot.setDescription("A quiet farm.");
        plot.setFlag("pvp", false);
        plot.setRole(trustedMember, "trusted");
        plot.setTreasuryBalance(100.0D);

        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL, "Before risky change", owner);

        // Simulate destructive changes made after the snapshot was captured.
        plot.setPlotName("Corrupted");
        plot.setDescription("");
        plot.setFlag("pvp", true);
        plot.getPlayerRoles().clear();
        plot.setRole(intruder, "co-owner");
        plot.addBan(laterBannedPlayer);
        plot.setTreasuryBalance(0.0D);

        SnapshotManager.restorePlotState(plot, snapshot);

        assertEquals(owner, plot.getOwner());
        assertEquals("Original Homestead", plot.getPlotName());
        assertEquals("A quiet farm.", plot.getDescription());
        assertEquals(Boolean.FALSE, plot.getFlags().get("pvp"));
        assertEquals("trusted", plot.getPlayerRoles().get(trustedMember));
        assertFalse(plot.getPlayerRoles().containsKey(intruder), "Roles granted after the snapshot must not survive rollback");
        assertFalse(plot.isBanned(laterBannedPlayer), "Bans added after the snapshot must not survive rollback");
        assertEquals(100.0D, plot.getTreasuryBalance());
    }

    @Test
    void restoringASnapshotReinstatesABanThatWasLiftedAfterwards() {
        UUID owner = UUID.randomUUID();
        UUID bannedAtSnapshotTime = UUID.randomUUID();

        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.addBan(bannedAtSnapshotTime);

        ClaimSnapshot snapshot = new ClaimSnapshot(plot, ClaimSnapshot.SnapshotType.PRE_MERGE, "Before merge", owner);

        // The ban is lifted after the snapshot was taken.
        plot.removeBan(bannedAtSnapshotTime);
        assertFalse(plot.isBanned(bannedAtSnapshotTime));

        SnapshotManager.restorePlotState(plot, snapshot);

        assertTrue(plot.isBanned(bannedAtSnapshotTime), "Rollback must reinstate bans that existed at snapshot time");
    }
}
