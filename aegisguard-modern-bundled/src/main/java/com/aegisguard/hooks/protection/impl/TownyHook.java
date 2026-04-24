package com.aegisguard.hooks.protection.impl;

import com.aegisguard.AegisGuard;
import com.aegisguard.hooks.protection.ProtectionHook;
import org.bukkit.Location;

import java.lang.reflect.Method;

public class TownyHook implements ProtectionHook {

    private final AegisGuard plugin;
    private final int priority;

    private boolean active;
    private Object api;
    private Method isWilderness;

    // Backwards compatible constructor (if anything still calls new TownyHook(plugin))
    public TownyHook(AegisGuard plugin) {
        this(plugin, 80);
    }

    // New constructor used by ProtectionHookManager (new TownyHook(plugin, priority))
    public TownyHook(AegisGuard plugin, int priority) {
        this.plugin = plugin;
        this.priority = priority;
        init();
    }

    @Override
    public String id() {
        return "Towny";
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
            // If NOT wilderness, Towny considers it managed by Town/Plot rules.
            return !wilderness;
        } catch (Throwable t) {
            return false;
        }
    }
}
