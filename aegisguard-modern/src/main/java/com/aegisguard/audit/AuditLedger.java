package com.aegisguard.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * Pure, plugin-independent in-memory store backing {@link AuditService}.
 *
 * Deliberately has no dependency on Bukkit or a live plugin instance so retention, pruning,
 * and category filtering can be unit tested directly. Entries are kept oldest-first internally;
 * {@link #recent(AuditCategory, int)} returns them newest-first.
 */
public final class AuditLedger {

    private final ConcurrentLinkedDeque<AuditEntry> entries = new ConcurrentLinkedDeque<>();

    public void add(AuditEntry entry) {
        if (entry == null) return;
        entries.addLast(entry);
    }

    public int size() {
        return entries.size();
    }

    /**
     * Entries matching {@code category} (or every entry when {@code category} is {@code null}),
     * newest first. {@code limit <= 0} returns every matching entry.
     */
    public List<AuditEntry> recent(AuditCategory category, int limit) {
        List<AuditEntry> filtered = entries.stream()
                .filter(entry -> category == null || entry.getCategory() == category)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(filtered);
        if (limit > 0 && filtered.size() > limit) {
            return new ArrayList<>(filtered.subList(0, limit));
        }
        return filtered;
    }

    /** All entries, oldest first (used for persistence). */
    public List<AuditEntry> all() {
        return new ArrayList<>(entries);
    }

    /** Replaces the entire contents, e.g. after loading from disk. Input order is preserved. */
    public void replaceAll(List<AuditEntry> newEntries) {
        entries.clear();
        if (newEntries != null) entries.addAll(newEntries);
    }

    /**
     * Enforces both a maximum entry count and a maximum age (evaluated relative to {@code now}).
     * {@code maxAgeMillis <= 0} disables age-based pruning; {@code maxEntries} is always enforced.
     */
    public void prune(int maxEntries, long maxAgeMillis, long now) {
        while (entries.size() > Math.max(0, maxEntries)) {
            entries.pollFirst();
        }

        if (maxAgeMillis <= 0) return;
        long cutoff = now - maxAgeMillis;
        while (true) {
            AuditEntry first = entries.peekFirst();
            if (first == null || first.getTimestamp() >= cutoff) break;
            entries.pollFirst();
        }
    }
}
