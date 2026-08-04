package com.aegisguard.data;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 3 (Emergency Plot Lockdown) - plugin-independent unit tests for the lockdown state
 * stored directly on {@link Plot}. The permission-enforcement side of {@code canBuild} requires a
 * live {@code AegisGuard}/{@code Plugin} instance and is covered by {@code LockdownContractTest}'s
 * source-level checks instead.
 */
class PlotLockdownTest {

    @Test
    void newPlotsAreNotLockedDownByDefault() {
        Plot plot = new Plot(UUID.randomUUID(), UUID.randomUUID(), "OwnerName", "world", 0, 0, 20, 20);
        assertFalse(plot.isLockdownActive());
        assertEquals(0L, plot.getLockdownActivatedAt());
        assertNull(plot.getLockdownActivatedBy());
    }

    @Test
    void activatingLockdownRecordsTheActorAndTimestamp() {
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);

        long before = System.currentTimeMillis();
        plot.setLockdown(true, owner, "OwnerName");

        assertTrue(plot.isLockdownActive());
        assertEquals(owner, plot.getLockdownActivatedBy());
        assertEquals("OwnerName", plot.getLockdownActivatedByName());
        assertTrue(plot.getLockdownActivatedAt() >= before);
    }

    @Test
    void deactivatingLockdownClearsAllLockdownStateButNeverTouchesOwnershipOrRoles() {
        UUID owner = UUID.randomUUID();
        UUID trustedMember = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.setRole(trustedMember, "trusted");

        plot.setLockdown(true, owner, "OwnerName");
        plot.setLockdown(false, null, null);

        assertFalse(plot.isLockdownActive());
        assertEquals(0L, plot.getLockdownActivatedAt());
        assertNull(plot.getLockdownActivatedBy());
        assertEquals(owner, plot.getOwner());
        assertEquals("trusted", plot.getPlayerRoles().get(trustedMember));
    }

    @Test
    void restoreLockdownPreservesTheOriginalActivationTimestampAcrossReload() {
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);

        long originalTimestamp = System.currentTimeMillis() - 3_600_000L;
        plot.restoreLockdown(true, owner, "OwnerName", originalTimestamp);

        assertTrue(plot.isLockdownActive());
        assertEquals(originalTimestamp, plot.getLockdownActivatedAt(),
                "Restoring from storage must not reset the activation time to \"now\"");
    }

    @Test
    void interactIsNeverRestrictableSoLeavingIsAlwaysPossible() {
        assertFalse(Plot.isLockdownRestrictable("INTERACT", null));
        assertFalse(Plot.isLockdownRestrictable("interact", null));
        assertFalse(Plot.isLockdownRestrictable(null, null));
        assertFalse(Plot.isLockdownRestrictable("", null));
    }

    @Test
    void buildBreakPlaceAndContainersAreRestrictableByDefault() {
        assertTrue(Plot.isLockdownRestrictable("BUILD", null));
        assertTrue(Plot.isLockdownRestrictable("BLOCK_BREAK", null));
        assertTrue(Plot.isLockdownRestrictable("BLOCK_PLACE", null));
        assertTrue(Plot.isLockdownRestrictable("CONTAINERS", null));
    }

    @Test
    void softLockdownBlocksContainersButNeverInteractOnPermissionProbe() {
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot(UUID.randomUUID(), owner, "OwnerName", "world", 0, 0, 20, 20);
        plot.setLockdown(true, owner, "OwnerName", 0L, "SOFT");

        assertTrue(plot.isPermissionRestrictedByLockdown("CONTAINERS", null));
        assertTrue(plot.isPermissionRestrictedByLockdown("BUILD", null));
        assertFalse(plot.isPermissionRestrictedByLockdown("INTERACT", null),
                "Doors/interact must remain usable during lockdown");
    }
}
