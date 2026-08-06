package com.aegisguard.arena;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArenaRewardLedgerTest {

    @Test
    void committedPayoutCannotBeginAgain() {
        ArenaRewardLedger ledger = new ArenaRewardLedger();
        UUID run = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        ArenaRewardEntry entry = ledger.getOrCreate(run, player, "clear");
        assertTrue(ledger.beginProcessing(entry));
        ledger.markCommitted(entry);
        assertFalse(ledger.beginProcessing(entry));
        assertTrue(ledger.alreadyCommitted(run, player, "clear"));
    }

    @Test
    void processingInterruptedBecomesNeedsReview() {
        ArenaRewardLedger ledger = new ArenaRewardLedger();
        ArenaRewardEntry entry = ledger.getOrCreate(UUID.randomUUID(), UUID.randomUUID(), "milestone");
        assertTrue(ledger.beginProcessing(entry));
        // Second begin while PROCESSING → NEEDS_REVIEW, no payout
        assertFalse(ledger.beginProcessing(entry));
        assertEquals(ArenaRewardStatus.NEEDS_REVIEW, entry.getStatus());
    }

    @Test
    void sanitizeAfterLoadMarksProcessingAsNeedsReview() {
        ArenaRewardLedger ledger = new ArenaRewardLedger();
        ArenaRewardEntry entry = ledger.getOrCreate(UUID.randomUUID(), UUID.randomUUID(), "clear");
        entry.setStatus(ArenaRewardStatus.PROCESSING);
        ledger.sanitizeAfterLoad();
        assertEquals(ArenaRewardStatus.NEEDS_REVIEW, entry.getStatus());
    }
}
