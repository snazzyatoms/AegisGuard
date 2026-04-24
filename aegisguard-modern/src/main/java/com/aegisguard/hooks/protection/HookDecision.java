package com.aegisguard.hooks.protection;

import java.util.Objects;

public final class HookDecision {

    private final boolean allowed;
    private final String denyingPlugin; // null if allowed
    private final String reason;        // optional

    private HookDecision(boolean allowed, String denyingPlugin, String reason) {
        this.allowed = allowed;
        this.denyingPlugin = denyingPlugin;
        this.reason = reason;
    }

    public static HookDecision allow() {
        return new HookDecision(true, null, null);
    }

    public static HookDecision deny(String denyingPlugin, String reason) {
        return new HookDecision(false,
                Objects.requireNonNullElse(denyingPlugin, "Unknown"),
                reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getDenyingPlugin() {
        return denyingPlugin;
    }

    public String getReason() {
        return reason;
    }
}
