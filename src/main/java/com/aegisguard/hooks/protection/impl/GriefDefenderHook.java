package com.aegisguard.hooks.protection.impl;

import com.aegisguard.AegisGuard;
import com.aegisguard.hooks.protection.ProtectionHook;
import org.bukkit.Location;

import java.lang.reflect.Method;

public class GriefDefenderHook implements ProtectionHook {

    private final AegisGuard plugin;
    private final int priority;

    private boolean active;
    private Object core;
    private Method getClaimAt;

    public GriefDefenderHook(AegisGuard plugin) {
        this(plugin, 90);
    }

    public GriefDefenderHook(AegisGuard plugin, int priority) {
        this.plugin = plugin;
        this.priority = priority;
        init();
    }

    @Override
    public String id() {
        return "GriefDefender";
    }

    @Override
    public int priority() {
        return priority;
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
            if ("java.util.Optional".equals(result.getClass().getName())) {
                Method isPresent = result.getClass().getMethod("isPresent");
                return (boolean) isPresent.invoke(result);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
