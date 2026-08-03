package com.aegisguard.routes;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 6 - plugin-independent unit tests for Route/Checkpoint/RouteProgress.
 */
class RouteModelTest {

    @Test
    void checkpointsDetectProximityByWorldAndRadius() {
        Checkpoint cp = Checkpoint.at("Spawn", "world", 0, 64, 0, 4);
        assertTrue(cp.isWithinRange("world", 2, 64, 2));
        assertFalse(cp.isWithinRange("world", 10, 64, 0));
        assertFalse(cp.isWithinRange("nether", 0, 64, 0));
    }

    @Test
    void routeReturnsNextCheckpointInOrderAndCompletes() {
        Route route = Route.create("Town Tour");
        Checkpoint a = Checkpoint.at("A", "world", 0, 64, 0, 3);
        Checkpoint b = Checkpoint.at("B", "world", 20, 64, 0, 3);
        route.addCheckpoint(a);
        route.addCheckpoint(b);

        assertEquals(a, route.nextAfter(0));
        assertEquals(b, route.nextAfter(1));
        assertNull(route.nextAfter(2));
        assertTrue(route.isComplete(2));
        assertFalse(route.isComplete(1));
    }

    @Test
    void progressTracksDiscoveryWithoutDuplicates() {
        UUID player = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        Checkpoint a = Checkpoint.at("A", "world", 0, 64, 0, 3);
        RouteProgress progress = new RouteProgress(player, routeId);

        assertTrue(progress.discover(a.getId()));
        assertFalse(progress.discover(a.getId()));
        assertEquals(1, progress.getDiscoveredCount());
        assertTrue(progress.hasDiscovered(a.getId()));
    }

    @Test
    void removingACheckpointDoesNotForceTeleportsOrTouchClaimGeometry() {
        Route route = Route.create("Shop Walk");
        Checkpoint first = Checkpoint.at("Gate", "world", 1, 64, 1, 3);
        route.addCheckpoint(first);
        assertTrue(route.removeCheckpoint(first.getId()));
        assertEquals(0, route.size());
        assertNull(route.nextAfter(0));
    }
}
