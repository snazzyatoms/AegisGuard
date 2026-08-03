package com.aegisguard.guestpass;

import com.aegisguard.data.Plot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 2 (Temporary Guest Passes) - plugin-independent unit tests for {@link GuestPass},
 * {@link GuestPassPreset}, {@link GuestPassMode}, and their integration into {@link Plot}.
 */
class GuestPassTest {

    @Test
    void issuedPassSnapshotsThePresetPermissionsAndComputesExpiry() {
        UUID player = UUID.randomUUID();
        UUID issuer = UUID.randomUUID();

        GuestPass pass = GuestPass.issue(player, "Guest", GuestPassPreset.TEMPORARY_BUILDER, issuer, "Owner", 60_000L);

        assertEquals(player, pass.getPlayerId());
        assertEquals(GuestPassPreset.TEMPORARY_BUILDER, pass.getPreset());
        assertEquals(GuestPassPreset.TEMPORARY_BUILDER.getPermissions(), pass.getPermissions());
        assertEquals(GuestPassMode.REAL_TIME, pass.getMode());
        assertTrue(pass.getExpiresAt() > pass.getIssuedAt());
        assertFalse(pass.isExpired(pass.getIssuedAt()));
        assertTrue(pass.isExpired(pass.getExpiresAt() + 1));
    }

    @Test
    void zeroDurationMeansNeverExpires() {
        GuestPass pass = GuestPass.issue(UUID.randomUUID(), "Guest", GuestPassPreset.VISITOR,
                UUID.randomUUID(), "Owner", 0L);

        assertEquals(0L, pass.getExpiresAt());
        assertFalse(pass.isExpired(System.currentTimeMillis() + 1_000_000_000L));
        assertEquals(Long.MAX_VALUE, pass.getRemainingMillis(System.currentTimeMillis()));
    }

    @Test
    void presetPermissionTokensMatchTheDocumentedGrantsExactly() {
        assertEquals(java.util.Set.of("INTERACT"), GuestPassPreset.VISITOR.getPermissions());
        assertEquals(java.util.Set.of("INTERACT", "FARM"), GuestPassPreset.EVENT_GUEST.getPermissions());
        assertEquals(java.util.Set.of("INTERACT", "BUILD", "BLOCK_BREAK", "BLOCK_PLACE"),
                GuestPassPreset.TEMPORARY_BUILDER.getPermissions());
        assertEquals(java.util.Set.of("INTERACT", "BUILD", "BLOCK_BREAK", "BLOCK_PLACE", "CONTAINERS"),
                GuestPassPreset.TEMPORARY_TRUSTED_GUEST.getPermissions());

        assertFalse(GuestPassPreset.VISITOR.requiresContainerWarning());
        assertFalse(GuestPassPreset.TEMPORARY_BUILDER.requiresContainerWarning());
        assertTrue(GuestPassPreset.TEMPORARY_TRUSTED_GUEST.requiresContainerWarning());

        assertFalse(GuestPassPreset.VISITOR.grantsBuildAccess());
        assertTrue(GuestPassPreset.TEMPORARY_BUILDER.grantsBuildAccess());
        assertTrue(GuestPassPreset.TEMPORARY_TRUSTED_GUEST.grantsBuildAccess());
    }

    @Test
    void plotGrantsGuestPassPermissionsWithoutTouchingPermanentRoles() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);

        assertNull(plot.getActiveGuestPass(guest));

        GuestPass pass = GuestPass.issue(guest, "Guest", GuestPassPreset.TEMPORARY_BUILDER,
                owner, "OwnerName", 60_000L);
        plot.addGuestPass(pass);

        GuestPass active = plot.getActiveGuestPass(guest);
        assertTrue(active != null, "An active Guest Pass should be retrievable by player UUID");
        assertTrue(active.hasPermission("BUILD"));
        assertTrue(active.hasPermission("BLOCK_BREAK"));
        assertFalse(active.hasPermission("CONTAINERS"), "Builder preset must not include container access");
        assertTrue(plot.getPlayerRoles().isEmpty(), "Issuing a pass must never write a permanent role");
    }

    @Test
    void revokingOrExpiringAGuestPassNeverRemovesAPermanentRole() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.setRole(member, "trusted");

        GuestPass pass = GuestPass.issue(member, "Member", GuestPassPreset.TEMPORARY_TRUSTED_GUEST,
                owner, "OwnerName", 60_000L);
        plot.addGuestPass(pass);

        assertTrue(plot.revokeGuestPass(member));
        assertEquals("trusted", plot.getPlayerRoles().get(member), "Revoking a pass must not touch the permanent role");
        assertNull(plot.getGuestPass(member));
    }

    @Test
    void pruneExpiredGuestPassesRemovesOnlyExpiredEntriesAndReturnsThem() {
        UUID owner = UUID.randomUUID();
        UUID stillActive = UUID.randomUUID();
        UUID nowExpired = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);

        long now = System.currentTimeMillis();
        plot.addGuestPass(new GuestPass(stillActive, "Active", GuestPassPreset.VISITOR,
                GuestPassPreset.VISITOR.getPermissions(), owner, "OwnerName", now, now + 60_000L));
        plot.addGuestPass(new GuestPass(nowExpired, "Expired", GuestPassPreset.VISITOR,
                GuestPassPreset.VISITOR.getPermissions(), owner, "OwnerName", now - 10_000L, now - 1_000L));

        List<GuestPass> pruned = plot.pruneExpiredGuestPasses(now);

        assertEquals(1, pruned.size());
        assertEquals(nowExpired, pruned.get(0).getPlayerId());
        assertNull(plot.getGuestPass(nowExpired));
        assertTrue(plot.getGuestPass(stillActive) != null, "Active pass must survive the sweep");
    }

    @Test
    void banningAPlayerImmediatelyRevokesAnyGuestPass() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.addGuestPass(GuestPass.issue(target, "Target", GuestPassPreset.EVENT_GUEST, owner, "OwnerName", 60_000L));

        plot.addBan(target);

        assertNull(plot.getGuestPass(target), "Banning a player must revoke any Guest Pass they hold");
        assertTrue(plot.isBanned(target));
    }

    @Test
    void serializingAndDeserializingGuestPassesRoundTripsExactly() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        Plot source = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        source.addGuestPass(GuestPass.issue(guest, "Guest", GuestPassPreset.TEMPORARY_TRUSTED_GUEST,
                owner, "OwnerName", 3_600_000L));

        String blob = source.serializeGuestPasses();
        assertFalse(blob.isEmpty());

        Plot target = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        target.deserializeGuestPasses(blob);

        GuestPass roundTripped = target.getGuestPass(guest);
        assertTrue(roundTripped != null);
        assertEquals("Guest", roundTripped.getPlayerName());
        assertEquals(GuestPassPreset.TEMPORARY_TRUSTED_GUEST, roundTripped.getPreset());
        assertEquals(GuestPassMode.REAL_TIME, roundTripped.getMode());
        assertEquals(source.getGuestPass(guest).getExpiresAt(), roundTripped.getExpiresAt());
    }

    @Test
    void realTimePassContinuesDuringDowntime() {
        long issuedAt = 1_000_000L;
        long expiresAt = issuedAt + 60_000L;
        GuestPass pass = new GuestPass(UUID.randomUUID(), "Guest", GuestPassPreset.VISITOR,
                GuestPassPreset.VISITOR.getPermissions(), UUID.randomUUID(), "Owner",
                issuedAt, expiresAt);

        // Simulate server downtime spanning most of the pass lifetime.
        long afterDowntime = issuedAt + 45_000L;
        assertFalse(pass.isExpired(afterDowntime));
        assertEquals(15_000L, pass.getRemainingMillis(afterDowntime));
        assertTrue(pass.isExpired(expiresAt + 1));
    }

    @Test
    void activePlaytimePassPausesWhileOffline() {
        GuestPass pass = GuestPass.issue(UUID.randomUUID(), "Guest", GuestPassPreset.VISITOR,
                UUID.randomUUID(), "Owner", 60_000L, GuestPassMode.ACTIVE_PLAYTIME);

        long t0 = 5_000_000L;
        assertTrue(pass.resumeSession(t0));
        assertEquals(50_000L, pass.getRemainingMillis(t0 + 10_000L));

        assertTrue(pass.freezeSession(t0 + 10_000L));
        assertFalse(pass.isSessionActive());
        assertEquals(50_000L, pass.getStoredRemainingMillis());

        // Offline / downtime gap must not consume remaining playtime.
        long muchLater = t0 + 10_000L + 3_600_000L;
        assertEquals(50_000L, pass.getRemainingMillis(muchLater));
        assertFalse(pass.isExpired(muchLater));
    }

    @Test
    void activePlaytimePassPersistsAcrossRestartAndResumesCleanly() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        Plot source = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);

        GuestPass pass = GuestPass.issue(guest, "Guest", GuestPassPreset.EVENT_GUEST,
                owner, "OwnerName", 120_000L, GuestPassMode.ACTIVE_PLAYTIME);
        long t0 = 9_000_000L;
        pass.resumeSession(t0);
        pass.checkpointSession(t0 + 20_000L); // 100s remaining, session still active mid-run
        assertTrue(pass.isSessionActive());
        source.addGuestPass(pass);

        // Persist + reload as if the server restarted mid-session.
        Plot reloaded = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        reloaded.deserializeGuestPasses(source.serializeGuestPasses());

        GuestPass restored = reloaded.getGuestPass(guest);
        assertTrue(restored != null);
        assertEquals(GuestPassMode.ACTIVE_PLAYTIME, restored.getMode());
        assertFalse(restored.isSessionActive(), "Load must pause so downtime is not consumed");
        assertEquals(100_000L, restored.getStoredRemainingMillis());

        long afterRestart = t0 + 20_000L + 86_400_000L;
        assertEquals(100_000L, restored.getRemainingMillis(afterRestart));
        assertTrue(restored.resumeSession(afterRestart));
        assertEquals(90_000L, restored.getRemainingMillis(afterRestart + 10_000L));
    }

    @Test
    void activePlaytimePassExpiresOnlyAfterConsumedOnlineTime() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);

        GuestPass pass = GuestPass.issue(guest, "Guest", GuestPassPreset.VISITOR,
                owner, "OwnerName", 30_000L, GuestPassMode.ACTIVE_PLAYTIME);
        long t0 = 2_000_000L;
        pass.resumeSession(t0);
        plot.addGuestPass(pass);

        assertTrue(plot.pruneExpiredGuestPasses(t0 + 10_000L).isEmpty());
        assertTrue(plot.getGuestPass(guest) != null);

        List<GuestPass> expired = plot.pruneExpiredGuestPasses(t0 + 30_000L);
        assertEquals(1, expired.size());
        assertNull(plot.getGuestPass(guest));
    }

    @Test
    void offlineRevocationWorksForBothModesWithoutTouchingPermanentRoles() {
        UUID owner = UUID.randomUUID();
        UUID realTimeGuest = UUID.randomUUID();
        UUID playtimeGuest = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.setRole(realTimeGuest, "trusted");
        plot.setRole(playtimeGuest, "builder");

        plot.addGuestPass(GuestPass.issue(realTimeGuest, "RT", GuestPassPreset.VISITOR,
                owner, "OwnerName", 60_000L, GuestPassMode.REAL_TIME));
        GuestPass playtime = GuestPass.issue(playtimeGuest, "PT", GuestPassPreset.VISITOR,
                owner, "OwnerName", 60_000L, GuestPassMode.ACTIVE_PLAYTIME);
        // Recipient is offline: session stays paused.
        assertFalse(playtime.isSessionActive());
        plot.addGuestPass(playtime);

        assertTrue(plot.revokeGuestPass(realTimeGuest));
        assertTrue(plot.revokeGuestPass(playtimeGuest));
        assertNull(plot.getGuestPass(realTimeGuest));
        assertNull(plot.getGuestPass(playtimeGuest));
        assertEquals("trusted", plot.getPlayerRoles().get(realTimeGuest));
        assertEquals("builder", plot.getPlayerRoles().get(playtimeGuest));
    }

    @Test
    void legacyEightFieldGuestPassBlobDefaultsToRealTime() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        long now = System.currentTimeMillis();
        String legacy = guest + "|LegacyGuest|VISITOR|INTERACT|" + owner + "|OwnerName|"
                + now + "|" + (now + 60_000L);

        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.deserializeGuestPasses(legacy);

        GuestPass restored = plot.getGuestPass(guest);
        assertTrue(restored != null);
        assertEquals(GuestPassMode.REAL_TIME, restored.getMode());
        assertEquals(now + 60_000L, restored.getExpiresAt());
        assertEquals("LegacyGuest", restored.getPlayerName());
    }
}
