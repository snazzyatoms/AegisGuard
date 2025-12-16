package com.aegisguard.hooks.protection;

/**
 * HookAction
 * Result returned by external protection hooks.
 *
 * PASS = hook has no opinion / not applicable
 * ALLOW = explicitly allow AegisGuard to handle the action
 * DENY = explicitly deny (another plugin claims authority)
 */
public enum HookAction {
    PASS,
    ALLOW,
    DENY;

    public boolean isPass()  { return this == PASS; }
    public boolean isAllow() { return this == ALLOW; }
    public boolean isDeny()  { return this == DENY; }
}
