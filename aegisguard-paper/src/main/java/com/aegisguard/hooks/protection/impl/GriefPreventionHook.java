package com.aegisguard.hooks.protection.impl;

import com.aegisguard.AegisGuard;
import com.aegisguard.hooks.protection.ProtectionHook;
import org.bukkit.Location;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class GriefPreventionHook implements ProtectionHook {

    private final AegisGuard plugin;
    private final int priority;

    private boolean active;
    private Object dataStore;
    private Method getClaimAt;

    public GriefPreventionHook(AegisGuard plugin) {
        this(plugin, 90);
    }

    public GriefPreventionHook(AegisGuard plugin, int priority) {
        this.plugin = plugin;
        this.priority = priority;
        init();
    }

    @Override
    public String id() {
        return "GriefPrevention";
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
            Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Field instanceField = gpClass.getField("instance");
            Object gp = instanceField.get(null);
            if (gp == null) {
                active = false;
                return;
            }

            Field dsField = gpClass.getField("dataStore");
            dataStore = dsField.get(gp);
            if (dataStore == null) {
                active = false;
                return;
            }

            // getClaimAt(Location, boolean, PlayerData) – 3rd param can be null safely
            getClaimAt = dataStore.getClass().getMethod("getClaimAt", Location.class, boolean.class, Object.class);

            active = true;
        } catch (Throwable t) {
            active = false;
        }
    }

    @Override
    public boolean isProtectedElsewhere(Location location) {
        if (!active || location == null || location.getWorld() == null) return false;

        try {
            Object claim = getClaimAt.invoke(dataStore, location, false, null);
            return claim != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
