package com.aegisguard.util;

public final class TerritoryGeometry {

    private TerritoryGeometry() {}

    public static boolean overlaps(int firstX1, int firstZ1, int firstX2, int firstZ2,
                                   int secondX1, int secondZ1, int secondX2, int secondZ2) {
        int aMinX = Math.min(firstX1, firstX2);
        int aMaxX = Math.max(firstX1, firstX2);
        int aMinZ = Math.min(firstZ1, firstZ2);
        int aMaxZ = Math.max(firstZ1, firstZ2);
        int bMinX = Math.min(secondX1, secondX2);
        int bMaxX = Math.max(secondX1, secondX2);
        int bMinZ = Math.min(secondZ1, secondZ2);
        int bMaxZ = Math.max(secondZ1, secondZ2);
        return aMinX <= bMaxX && aMaxX >= bMinX && aMinZ <= bMaxZ && aMaxZ >= bMinZ;
    }
}
