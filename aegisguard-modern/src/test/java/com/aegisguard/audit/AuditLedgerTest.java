package com.aegisguard.audit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 1 (Staff Audit Ledger): proves that recording, filtering, and pruning behave
 * correctly. Exercises {@link AuditLedger} directly since it is deliberately independent of a
 * live plugin instance.
 */
class AuditLedgerTest {

    private static AuditEntry entry(long timestampMillis, AuditCategory category, String summary) {
        return new AuditEntry(UUID.randomUUID(), timestampMillis, category, UUID.randomUUID(), "Steward", "target", summary);
    }

    @Test
    void recentReturnsEveryEntryNewestFirst() {
        AuditLedger ledger = new AuditLedger();
        ledger.add(entry(1_000L, AuditCategory.DOCTOR_REPAIR, "first"));
        ledger.add(entry(2_000L, AuditCategory.SNAPSHOT_RESTORE, "second"));
        ledger.add(entry(3_000L, AuditCategory.ADMIN_BYPASS, "third"));

        List<AuditEntry> recent = ledger.recent(null, 0);

        assertEquals(3, recent.size());
        assertEquals("third", recent.get(0).getSummary());
        assertEquals("second", recent.get(1).getSummary());
        assertEquals("first", recent.get(2).getSummary());
    }

    @Test
    void recentFiltersByCategory() {
        AuditLedger ledger = new AuditLedger();
        ledger.add(entry(1_000L, AuditCategory.DOCTOR_REPAIR, "repair-1"));
        ledger.add(entry(2_000L, AuditCategory.SNAPSHOT_RESTORE, "restore-1"));
        ledger.add(entry(3_000L, AuditCategory.DOCTOR_REPAIR, "repair-2"));

        List<AuditEntry> repairsOnly = ledger.recent(AuditCategory.DOCTOR_REPAIR, 0);

        assertEquals(2, repairsOnly.size());
        assertEquals("repair-2", repairsOnly.get(0).getSummary());
        assertEquals("repair-1", repairsOnly.get(1).getSummary());
    }

    @Test
    void recentHonorsALimit() {
        AuditLedger ledger = new AuditLedger();
        for (int i = 0; i < 5; i++) {
            ledger.add(entry(1_000L + i, AuditCategory.ADMIN_BYPASS, "entry-" + i));
        }

        List<AuditEntry> limited = ledger.recent(null, 2);

        assertEquals(2, limited.size());
        assertEquals("entry-4", limited.get(0).getSummary());
        assertEquals("entry-3", limited.get(1).getSummary());
    }

    @Test
    void pruneEnforcesTheMaximumEntryCountByDroppingTheOldestFirst() {
        AuditLedger ledger = new AuditLedger();
        for (int i = 0; i < 10; i++) {
            ledger.add(entry(1_000L + i, AuditCategory.CLAIM_BLOCK_ADJUST, "entry-" + i));
        }

        ledger.prune(3, 0L, 2_000_000L);

        assertEquals(3, ledger.size());
        List<AuditEntry> remaining = ledger.recent(null, 0);
        assertEquals("entry-9", remaining.get(0).getSummary());
        assertEquals("entry-8", remaining.get(1).getSummary());
        assertEquals("entry-7", remaining.get(2).getSummary());
    }

    @Test
    void pruneEnforcesRetentionByAgeRegardlessOfCount() {
        AuditLedger ledger = new AuditLedger();
        long now = 100 * 86_400_000L; // an arbitrary "now" 100 days into epoch time
        long oneDay = 86_400_000L;

        ledger.add(entry(now - (95 * oneDay), AuditCategory.MIGRATION, "too-old"));
        ledger.add(entry(now - (10 * oneDay), AuditCategory.MIGRATION, "within-retention"));
        ledger.add(entry(now - oneDay, AuditCategory.MIGRATION, "recent"));

        // retention_days=90, expressed in millis, evaluated at "now"
        ledger.prune(500, 90L * oneDay, now);

        List<AuditEntry> remaining = ledger.recent(null, 0);
        assertEquals(2, remaining.size(), "The entry older than the retention window must be pruned");
        assertTrue(remaining.stream().anyMatch(e -> e.getSummary().equals("within-retention")));
        assertTrue(remaining.stream().anyMatch(e -> e.getSummary().equals("recent")));
        assertTrue(remaining.stream().noneMatch(e -> e.getSummary().equals("too-old")));
    }

    @Test
    void pruneWithNoAgeLimitOnlyEnforcesCount() {
        AuditLedger ledger = new AuditLedger();
        ledger.add(entry(1L, AuditCategory.GUEST_PASS, "ancient"));
        ledger.add(entry(2L, AuditCategory.GUEST_PASS, "also-ancient"));

        ledger.prune(10, 0L, System.currentTimeMillis());

        assertEquals(2, ledger.size(), "retention_days=0 must disable age-based pruning");
    }
}
