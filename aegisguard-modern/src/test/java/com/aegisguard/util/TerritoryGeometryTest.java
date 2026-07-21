package com.aegisguard.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryGeometryTest {

    @Test
    void detectsOverlapRegardlessOfCoordinateOrder() {
        assertTrue(TerritoryGeometry.overlaps(10, 10, 0, 0, 5, 5, 20, 20));
    }

    @Test
    void treatsSharedBoundaryAsOverlap() {
        assertTrue(TerritoryGeometry.overlaps(0, 0, 10, 10, 10, 2, 20, 8));
    }

    @Test
    void permitsTrulyAdjacentTerritories() {
        assertFalse(TerritoryGeometry.overlaps(0, 0, 10, 10, 11, 0, 20, 10));
    }
}
