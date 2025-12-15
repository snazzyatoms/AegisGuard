package com.aegisguard.hooks.protection.impl;

import com.aegisguard.AegisGuard;
import com.aegisguard.hooks.protection.ProtectionHook;
import org.bukkit.Location;

import java.lang.reflect.Method;

public class TownyHook implements ProtectionHook {

    private final AegisGuard plugin;

    private boolean active;
    private Object api;
    private Method isWilderness;

    public TownyHook(AegisGuard plugin) {
        this.plugin = plugin;
        init();
    }

    @Override
    public String id() {
        return "Towny";
    }

    @Override
    public boolean isActive() {
        return active;
    }

    private void init() {
        try {
            Class<?> apiClass = Class.forName("com.palmergames.bukkit.towny.TownyAPI");
            Method getInstance = apiClass.getMethod("getInstance");
            api = getInstance.invoke(null);
            if (api == null) {
                active = false;
                return;
            }

            isWilderness = apiClass.getMethod("isWilderness", Location.class);
            active = true;
        } catch (Throwable t) {
            active = false;
        }
    }

    @Override
    public boolean isProtectedElsewhere(Location location) {
        if (!active || location == null || location.getWorld() == null) return false;

        try {
            boolean wilderness = (boolean) isWilderness.invoke(api, location);
            // If NOT wilderness, Towny considers it managed by Town/Zone rules.
            return !wilderness;
        } catch (Throwable t) {
            return false;
        }
    }
}
