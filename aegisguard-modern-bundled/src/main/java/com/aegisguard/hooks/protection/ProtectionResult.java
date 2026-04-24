package com.aegisguard.hooks.protection;

/**
 * Result from a 3rd-party protection plugin check.
 *
 * ALLOW  - external plugin explicitly allows the action
 * DENY   - external plugin explicitly denies the action
 * ABSTAIN- plugin not present, not applicable, or no opinion
 */
public enum ProtectionResult {
    ALLOW,
    DENY,
    ABSTAIN
}
