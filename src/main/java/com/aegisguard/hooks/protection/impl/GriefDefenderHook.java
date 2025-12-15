package com.aegisguard.hooks.protection.impl;

import com.aegisguard.AegisGuard;
import com.aegisguard.hooks.protection.ProtectionHook;
import org.bukkit.Location;

import java.lang.reflect.Method;

public class GriefDefenderHook implements ProtectionHook {

    private final AegisGuard plugin;

    private boolean active;
    private Object core;
    private Method getClaimAt;

    public GriefDefenderHook(AegisGuard plugin) {
        this.plugin = plugin;
        init();
    }

    @Override
    public String id() {
        return "GriefDefender";
    }

    @Override
    public boolean isActive() {
        return active;
    }

    private void init() {
        try {
            // GriefDefender API entrypoint
            Class<?> gdClass = Class.forName("com.griefdefender.api.GriefDefender");
            Method getCoreM = gdClass.getMethod("getCore");
            core = getCoreM.invoke(null);
            if (core == null) {
                active = false;
                return;
            }

            // core.getClaimAt(Location) -> Optional/Claim depending on platform/build.
            // We'll attempt common signature: getClaimAt(Location)
            getClaimAt = core.getClass().getMethod("getClaimAt", Location.class);

            active = true;
        } catch (Throwable t) {
            active = false;
        }
    }

    @Override
    public boolean isProtectedElsewhere(Location location) {
        if (!active || location == null || location.getWorld() == null) return false;

        try {
            Object result = getClaimAt.invoke(core, location);
            if (result == null) return false;

            // If Optional, check present. Otherwise, non-null claim object is "protected".
            if (result.getClass().getName().equals("java.util.Optional")) {
                Method isPresent = result.getClass().getMethod("isPresent");
                return (boolean) isPresent.invoke(result);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
