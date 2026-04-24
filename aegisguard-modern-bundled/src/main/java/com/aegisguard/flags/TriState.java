package com.aegisguard.flags;

/**
 * TriState for role-based flag overrides:
 * - INHERIT: Use normal plot/world/zone flag logic.
 * - ALLOW:  Force this flag ON for this role on this plot.
 * - DENY:   Force this flag OFF for this role on this plot.
 */
public enum TriState {
    INHERIT,
    ALLOW,
    DENY
}
