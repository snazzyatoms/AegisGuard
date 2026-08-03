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
 * {@link GuestPassPreset}, and their integration into {@link Plot}.
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
        assertEquals(source.getGuestPass(guest).getExpiresAt(), roundTripped.getExpiresAt());
    }
}
