package com.aegisguard.profile;

import java.util.UUID;

/**
 * Milestone 4 (Realm Profiles and Noticeboards) - a single owner-moderated notice posted to a
 * plot's public noticeboard (rules, event details, shop info, or announcements).
 *
 * Immutable and free of any Bukkit/Plugin dependency so it can be unit tested directly, matching
 * the pattern used by {@link com.aegisguard.guestpass.GuestPass} and
 * {@link com.aegisguard.audit.AuditEntry}.
 */
public final class PlotNotice {

    private final UUID id;
    private final UUID authorId;
    private final String authorName;
    private final long createdAt;
    private final String text;

    public PlotNotice(UUID id, UUID authorId, String authorName, long createdAt, String text) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.authorId = authorId;
        this.authorName = (authorName == null || authorName.isBlank()) ? "Unknown" : authorName;
        this.createdAt = createdAt;
        this.text = text == null ? "" : text;
    }

    public static PlotNotice post(UUID authorId, String authorName, String text) {
        return new PlotNotice(UUID.randomUUID(), authorId, authorName, System.currentTimeMillis(), text);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getText() {
        return text;
    }

    public long getAgeMillis() {
        return System.currentTimeMillis() - createdAt;
    }
}
