package com.aegisguard.api.service;

import com.aegisguard.data.Plot;

public interface ProtectionAccess {

    boolean isFlagEnabled(Plot plot, String flagKey);

    boolean isMobProtectionEnabled(Plot plot);

    boolean isSafeZoneEnabled(Plot plot);

    void toggleSafeZone(Plot plot, boolean enabled);
}
