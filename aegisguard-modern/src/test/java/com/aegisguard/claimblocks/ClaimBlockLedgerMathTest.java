package com.aegisguard.claimblocks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClaimBlockLedgerMathTest {

    @Test
    void expansionDoesNotReportNegativeAvailableAfterDuplicatedLandSpend() {
        long total = 5_000L;
        long originalClaim = 2_000L;
        long expandedUsed = 4_000L;
        long spentIncludingDuplicatedLand = originalClaim;
        long overlap = (spentIncludingDuplicatedLand + expandedUsed > total)
                ? Math.min(spentIncludingDuplicatedLand, expandedUsed)
                : 0L;
        long spentAfterReconcile = spentIncludingDuplicatedLand - overlap;
        long available = Math.max(0L, total - expandedUsed - spentAfterReconcile);
        assertEquals(1_000L, available);
    }

    @Test
    void overcommittedLedgerPeelsDuplicatedLandAndKeepsExtraSpend() {
        long total = 2_000L;
        long used = 1_000L;
        long spent = 1_050L;
        long overlap = (spent + used > total) ? Math.min(spent, used) : 0L;
        long available = Math.max(0L, total - used - (spent - overlap));
        assertEquals(950L, available);
    }

    @Test
    void healthyBeaconSpendIsNotTreatedAsDuplicatedLand() {
        long total = 5_000L;
        long used = 1_000L;
        long spent = 50L;
        long overlap = (spent + used > total) ? Math.min(spent, used) : 0L;
        long available = Math.max(0L, total - used - (spent - overlap));
        assertEquals(3_950L, available);
    }
}
