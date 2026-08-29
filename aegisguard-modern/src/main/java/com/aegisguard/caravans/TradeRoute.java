package com.aegisguard.caravans;

import java.util.UUID;

/**
 * A one-hop trade corridor derived from a public linked beacon pair.
 */
public record TradeRoute(
        UUID originId,
        UUID destId,
        String originName,
        String destName,
        String world,
        int originX,
        int originZ,
        int destX,
        int destZ,
        int distance,
        long travelMs,
        CaravanRules.Risk risk,
        UUID destPlotOwner
) {
    public String label() {
        String from = originName == null || originName.isBlank() ? "Origin" : originName;
        String to = destName == null || destName.isBlank() ? "Destination" : destName;
        return from + " → " + to;
    }
}
