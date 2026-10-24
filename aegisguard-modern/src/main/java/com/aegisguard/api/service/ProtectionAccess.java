package com.aegisguard.api.service;

import com.aegisguard.data.Plot;

/**
 * Query and toggle the protection state of a plot: per-flag checks, the
 * hostile-mob barrier, and safe-zone (pvp-free) status.
 */
public interface ProtectionAccess {

    boolean isFlagEnabled(Plot plot, String flagKey);

    boolean isMobProtectionEnabled(Plot plot);

    boolean isSafeZoneEnabled(Plot plot);

    void toggleSafeZone(Plot plot, boolean enabled);
}
