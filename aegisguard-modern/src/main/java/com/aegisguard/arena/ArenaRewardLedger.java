package com.aegisguard.arena;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Crash-safe reward ledger. PROCESSING crash → NEEDS_REVIEW (no blind auto-retry).
 */
public final class ArenaRewardLedger {

    private final Map<String, ArenaRewardEntry> entries = new ConcurrentHashMap<>();

    public ArenaRewardEntry get(String entryId) {
        return entryId == null ? null : entries.get(entryId);
    }

    public ArenaRewardEntry getOrCreate(UUID runId, UUID playerId, String rewardKey) {
        String id = ArenaRewardEntry.stableId(runId, playerId, rewardKey);
        return entries.computeIfAbsent(id, k -> new ArenaRewardEntry(runId, playerId, rewardKey));
    }

    public boolean alreadyCommitted(UUID runId, UUID playerId, String rewardKey) {
        ArenaRewardEntry e = entries.get(ArenaRewardEntry.stableId(runId, playerId, rewardKey));
        return e != null && e.getStatus() == ArenaRewardStatus.COMMITTED;
    }

    /**
     * Begin payout. Returns false if already committed/cancelled or currently processing.
     */
    public synchronized boolean beginProcessing(ArenaRewardEntry entry) {
        if (entry == null) return false;
        if (entry.getStatus() == ArenaRewardStatus.COMMITTED
                || entry.getStatus() == ArenaRewardStatus.CANCELLED) {
            return false;
        }
        if (entry.getStatus() == ArenaRewardStatus.PROCESSING) {
            entry.setStatus(ArenaRewardStatus.NEEDS_REVIEW);
            entry.setDetail("Interrupted while PROCESSING; admin review required");
            return false;
        }
        entry.setStatus(ArenaRewardStatus.PROCESSING);
        return true;
    }

    public synchronized void markCommitted(ArenaRewardEntry entry) {
        if (entry == null) return;
        entry.setStatus(ArenaRewardStatus.COMMITTED);
    }

    public synchronized void markFailed(ArenaRewardEntry entry, String detail) {
        if (entry == null) return;
        entry.setStatus(ArenaRewardStatus.FAILED);
        entry.setDetail(detail);
    }

    public synchronized void markNeedsReview(ArenaRewardEntry entry, String detail) {
        if (entry == null) return;
        entry.setStatus(ArenaRewardStatus.NEEDS_REVIEW);
        entry.setDetail(detail);
    }

    /** On load: any PROCESSING becomes NEEDS_REVIEW. */
    public void sanitizeAfterLoad() {
        for (ArenaRewardEntry e : entries.values()) {
            if (e.getStatus() == ArenaRewardStatus.PROCESSING) {
                e.setStatus(ArenaRewardStatus.NEEDS_REVIEW);
                e.setDetail("Server restarted during PROCESSING");
            }
        }
    }

    public List<ArenaRewardEntry> needsReview() {
        List<ArenaRewardEntry> list = new ArrayList<>();
        for (ArenaRewardEntry e : entries.values()) {
            if (e.getStatus() == ArenaRewardStatus.NEEDS_REVIEW || e.getStatus() == ArenaRewardStatus.FAILED) {
                list.add(e);
            }
        }
        return list;
    }

    public Collection<ArenaRewardEntry> all() {
        return List.copyOf(entries.values());
    }

    public void put(ArenaRewardEntry entry) {
        if (entry != null) entries.put(entry.getEntryId(), entry);
    }

    public Map<String, ArenaRewardEntry> asMap() {
        return Map.copyOf(entries);
    }
}
