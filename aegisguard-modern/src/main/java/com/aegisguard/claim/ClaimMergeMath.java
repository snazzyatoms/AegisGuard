package com.aegisguard.claim;

/**
 * Pure geometry helpers for claim merge safety (no Bukkit dependency).
 * When {@code requireAlignment} is true, only full-edge aligned adjacent
 * rectangles may merge so the resulting bounding box never claims unowned land.
 */
public final class ClaimMergeMath {

    private ClaimMergeMath() {}

    public record Rect(int x1, int z1, int x2, int z2) {
        public Rect normalized() {
            return new Rect(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2));
        }

        public long area() {
            Rect n = normalized();
            return (long) (n.x2 - n.x1 + 1) * (long) (n.z2 - n.z1 + 1);
        }
    }

    public record MergeBounds(int x1, int z1, int x2, int z2) {}

    /** Side-adjacent with optional axis overlap (does not guarantee alignment). */
    public static boolean adjacent(Rect aIn, Rect bIn) {
        Rect a = aIn.normalized();
        Rect b = bIn.normalized();
        boolean xTouch = a.x2 + 1 == b.x1 || b.x2 + 1 == a.x1;
        boolean zTouch = a.z2 + 1 == b.z1 || b.z2 + 1 == a.z1;
        boolean zOverlap = Math.max(a.z1, b.z1) <= Math.min(a.z2, b.z2);
        boolean xOverlap = Math.max(a.x1, b.x1) <= Math.min(a.x2, b.x2);
        return (xTouch && zOverlap) || (zTouch && xOverlap);
    }

    /**
     * Full-edge alignment: east/west neighbors must share identical Z bounds;
     * north/south neighbors must share identical X bounds.
     */
    public static boolean fullyAligned(Rect aIn, Rect bIn) {
        Rect a = aIn.normalized();
        Rect b = bIn.normalized();
        boolean xTouch = a.x2 + 1 == b.x1 || b.x2 + 1 == a.x1;
        boolean zTouch = a.z2 + 1 == b.z1 || b.z2 + 1 == a.z1;
        if (xTouch && a.z1 == b.z1 && a.z2 == b.z2) return true;
        if (zTouch && a.x1 == b.x1 && a.x2 == b.x2) return true;
        return false;
    }

    public static boolean canMerge(Rect a, Rect b, boolean requireAlignment) {
        if (!adjacent(a, b)) return false;
        if (!requireAlignment) {
            // Without alignment, only allow merges that do not invent land
            // (bbox area equals sum of areas).
            return bboxClaimsNoExtraLand(a, b);
        }
        return fullyAligned(a, b);
    }

    /** True when the merged bounding box equals the union area of both plots. */
    public static boolean bboxClaimsNoExtraLand(Rect aIn, Rect bIn) {
        Rect a = aIn.normalized();
        Rect b = bIn.normalized();
        MergeBounds box = mergedBounds(a, b);
        long boxArea = (long) (box.x2 - box.x1 + 1) * (long) (box.z2 - box.z1 + 1);
        return boxArea == a.area() + b.area();
    }

    public static MergeBounds mergedBounds(Rect aIn, Rect bIn) {
        Rect a = aIn.normalized();
        Rect b = bIn.normalized();
        return new MergeBounds(
                Math.min(a.x1, b.x1),
                Math.min(a.z1, b.z1),
                Math.max(a.x2, b.x2),
                Math.max(a.z2, b.z2)
        );
    }

    /**
     * Returns true if any foreign rectangle intersects the exclusive interior of
     * {@code bounds} (used to reject merges that would swallow other claims).
     */
    public static boolean foreignLandInside(MergeBounds bounds, Iterable<Rect> foreign) {
        if (bounds == null || foreign == null) return false;
        for (Rect fIn : foreign) {
            if (fIn == null) continue;
            Rect f = fIn.normalized();
            boolean overlapX = Math.max(bounds.x1(), f.x1) <= Math.min(bounds.x2(), f.x2);
            boolean overlapZ = Math.max(bounds.z1(), f.z1) <= Math.min(bounds.z2(), f.z2);
            if (overlapX && overlapZ) return true;
        }
        return false;
    }
}
