package com.aegisguard.audit;

import java.util.Objects;
import java.util.UUID;

/**
 * A single, immutable record in the Staff Audit Ledger.
 */
public final class AuditEntry {

    private final UUID id;
    private final long timestamp;
    private final AuditCategory category;
    private final UUID actorId;
    private final String actorName;
    private final String target;
    private final String summary;

    public AuditEntry(UUID id, long timestamp, AuditCategory category, UUID actorId,
                       String actorName, String target, String summary) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.timestamp = timestamp;
        this.category = Objects.requireNonNull(category, "category");
        this.actorId = actorId;
        this.actorName = actorName == null || actorName.isBlank() ? "System" : actorName;
        this.target = target == null ? "" : target;
        this.summary = summary == null ? "" : summary;
    }

    public UUID getId() { return id; }
    public long getTimestamp() { return timestamp; }
    public AuditCategory getCategory() { return category; }
    public UUID getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getTarget() { return target; }
    public String getSummary() { return summary; }

    public long getAgeMillis() {
        return System.currentTimeMillis() - timestamp;
    }
}
