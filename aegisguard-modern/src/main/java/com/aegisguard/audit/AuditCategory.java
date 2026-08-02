package com.aegisguard.audit;

/**
 * High-impact staff/admin action categories recorded by {@link AuditService}.
 *
 * Milestone 1 scope rule: only these categories are recorded. Ordinary player actions
 * (claiming, trusting, chatting, etc.) are deliberately NOT logged here.
 */
public enum AuditCategory {
    SNAPSHOT_RESTORE,
    DOCTOR_REPAIR,
    MIGRATION,
    ADMIN_BYPASS,
    CLAIM_BLOCK_ADJUST,

    /** Reserved for Milestone 2 (Temporary Guest Passes): creation, revocation, and expiry. */
    GUEST_PASS
}
