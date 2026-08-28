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
        long overlap = Math.min(spentIncludingDuplicatedLand, expandedUsed);
        long spentAfterReconcile = spentIncludingDuplicatedLand - overlap;
        long available = Math.max(0L, total - expandedUsed - spentAfterReconcile);
        assertEquals(1_000L, available);
    }

    @Test
    void nonLandSpendStillReducesTheWalletAfterReconcile() {
        long total = 5_000L;
        long used = 1_000L;
        long spent = 1_050L;
        long overlap = Math.min(spent, used);
        long extra = spent - overlap;
        long available = Math.max(0L, total - used - extra);
        assertEquals(3_950L, available);
    }
}
