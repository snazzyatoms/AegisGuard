package com.aegisguard.claim;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimMergeMathTest {

    @Test
    void fullyAlignedEastWestNeighborsCanMerge() {
        ClaimMergeMath.Rect a = new ClaimMergeMath.Rect(0, 0, 10, 10);
        ClaimMergeMath.Rect b = new ClaimMergeMath.Rect(11, 0, 20, 10);
        assertTrue(ClaimMergeMath.adjacent(a, b));
        assertTrue(ClaimMergeMath.fullyAligned(a, b));
        assertTrue(ClaimMergeMath.canMerge(a, b, true));
        assertTrue(ClaimMergeMath.bboxClaimsNoExtraLand(a, b));
    }

    @Test
    void lShapePartialOverlapIsRejectedWhenAlignmentRequired() {
        ClaimMergeMath.Rect a = new ClaimMergeMath.Rect(0, 0, 10, 10);
        ClaimMergeMath.Rect b = new ClaimMergeMath.Rect(11, 0, 15, 5);
        assertTrue(ClaimMergeMath.adjacent(a, b));
        assertFalse(ClaimMergeMath.fullyAligned(a, b));
        assertFalse(ClaimMergeMath.canMerge(a, b, true));
        assertFalse(ClaimMergeMath.bboxClaimsNoExtraLand(a, b));
    }

    @Test
    void foreignPlotInsideMergedBoundsIsDetected() {
        ClaimMergeMath.Rect a = new ClaimMergeMath.Rect(0, 0, 10, 10);
        ClaimMergeMath.Rect b = new ClaimMergeMath.Rect(11, 0, 20, 10);
        ClaimMergeMath.MergeBounds box = ClaimMergeMath.mergedBounds(a, b);
        ClaimMergeMath.Rect foreign = new ClaimMergeMath.Rect(12, 2, 14, 4);
        assertTrue(ClaimMergeMath.foreignLandInside(box, List.of(foreign)));
        assertFalse(ClaimMergeMath.foreignLandInside(box, List.of(new ClaimMergeMath.Rect(30, 30, 40, 40))));
    }

    @Test
    void nonAdjacentPlotsCannotMerge() {
        ClaimMergeMath.Rect a = new ClaimMergeMath.Rect(0, 0, 5, 5);
        ClaimMergeMath.Rect b = new ClaimMergeMath.Rect(10, 10, 15, 15);
        assertFalse(ClaimMergeMath.adjacent(a, b));
        assertFalse(ClaimMergeMath.canMerge(a, b, true));
    }
}
