package com.aegisguard.guestpass;

import java.util.Set;

/**
 * Milestone 2 (Temporary Guest Passes) - the four fixed access presets an owner can hand out.
 *
 * Each preset maps onto the same permission tokens already used by {@code roles.<role>.permissions}
 * in config.yml (see {@code com.aegisguard.data.Plot#hasPermission}), so a Guest Pass is enforced
 * through the exact same protection checks as a permanent role - just additive and time-limited.
 */
public enum GuestPassPreset {

    /** Entry and ordinary doors/buttons only. No build, no break, no containers. */
    VISITOR(Set.of("INTERACT")),

    /** Entry plus safe, non-destructive interaction (pressure plates, farmland, event triggers). */
    EVENT_GUEST(Set.of("INTERACT", "FARM")),

    /** Full build/break access. Containers stay closed unless explicitly upgraded. */
    TEMPORARY_BUILDER(Set.of("INTERACT", "BUILD", "BLOCK_BREAK", "BLOCK_PLACE")),

    /** Build/break plus container access. The GUI must show a clear warning before this is issued. */
    TEMPORARY_TRUSTED_GUEST(Set.of("INTERACT", "BUILD", "BLOCK_BREAK", "BLOCK_PLACE", "CONTAINERS"));

    private final Set<String> permissions;

    GuestPassPreset(Set<String> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    /** Permission tokens granted by this preset, matching {@code Plot.hasPermission} token names. */
    public Set<String> getPermissions() {
        return permissions;
    }

    /** Whether the GUI must show an explicit "this grants container access" warning before issuing. */
    public boolean requiresContainerWarning() {
        return this == TEMPORARY_TRUSTED_GUEST;
    }

    /** Whether this preset grants any building/breaking access at all. */
    public boolean grantsBuildAccess() {
        return this == TEMPORARY_BUILDER || this == TEMPORARY_TRUSTED_GUEST;
    }

    /** Stable, human-friendly fallback label (translation keys should be preferred in GUIs). */
    public String fallbackLabel() {
        return switch (this) {
            case VISITOR -> "Visitor";
            case EVENT_GUEST -> "Event Guest";
            case TEMPORARY_BUILDER -> "Temporary Builder";
            case TEMPORARY_TRUSTED_GUEST -> "Temporary Trusted Guest";
        };
    }

    /** Ordered presets, safest first, for consistent GUI listing. */
    public static java.util.List<GuestPassPreset> ordered() {
        return java.util.List.of(VISITOR, EVENT_GUEST, TEMPORARY_BUILDER, TEMPORARY_TRUSTED_GUEST);
    }
}
