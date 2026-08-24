package com.aegisguard.snapshots;

import java.util.EnumSet;
import java.util.Set;

/** Independently restorable portions of a claim snapshot. */
public enum RestoreScope {
    IDENTITY_AND_BOUNDS,
    FLAGS,
    MEMBERS_AND_ROLES,
    BANS,
    GUEST_PASSES,
    ALLIANCE_ACCESS,
    LOCKDOWN,
    NOTICEBOARD,
    PLOT_SETTINGS,
    ECONOMY,
    PROGRESSION,
    SOCIAL,
    ZONES_AND_STALLS,
    BUILD,
    FULL_DATA;

    private static final EnumSet<RestoreScope> DATA_SCOPES = EnumSet.of(
            IDENTITY_AND_BOUNDS, FLAGS, MEMBERS_AND_ROLES, BANS,
            GUEST_PASSES, ALLIANCE_ACCESS, LOCKDOWN, NOTICEBOARD,
            PLOT_SETTINGS, ECONOMY, PROGRESSION, SOCIAL, ZONES_AND_STALLS);

    public static EnumSet<RestoreScope> normalize(Set<RestoreScope> requested) {
        EnumSet<RestoreScope> scopes = requested == null || requested.isEmpty()
                ? EnumSet.of(FULL_DATA, BUILD)
                : EnumSet.copyOf(requested);
        if (scopes.remove(FULL_DATA)) scopes.addAll(DATA_SCOPES);
        return scopes;
    }

    public static EnumSet<RestoreScope> fullData() {
        return EnumSet.copyOf(DATA_SCOPES);
    }

    public static boolean includesData(Set<RestoreScope> scopes) {
        if (scopes == null) return false;
        for (RestoreScope scope : scopes) {
            if (scope != BUILD) return true;
        }
        return false;
    }
}
