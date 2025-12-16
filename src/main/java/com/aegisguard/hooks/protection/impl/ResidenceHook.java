package com.aegisguard.hooks.protection.impl;

import com.aegisguard.AegisGuard;
import com.aegisguard.hooks.protection.ProtectionHook;
import org.bukkit.Location;

import java.lang.reflect.Method;

public class ResidenceHook implements ProtectionHook {

    private final AegisGuard plugin;
    private final int priority;

    private boolean active;
    private Object residenceManager; // store manager (not api instance)
    private Method getByLoc;

    public ResidenceHook(AegisGuard plugin) {
        this(plugin, 70);
    }

    public ResidenceHook(AegisGuard plugin, int priority) {
        this.plugin = plugin;
        this.priority = priority;
        init();
    }

    @Override
    public String id() {
        return "Residence";
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
            // Residence.getInstance().getResidenceManager().getByLoc(Location)
            Class<?> resClass = Class.forName("com.bekvon.bukkit.residence.Residence");
            Method getInstance = resClass.getMethod("getInstance");
            Object residence = getInstance.invoke(null);
            if (residence == null) {
                active = false;
                return;
            }

            Method getManager = residence.getClass().getMethod("getResidenceManager");
            Object manager = getManager.invoke(residence);
            if (manager == null) {
                active = false;
                return;
            }

            getByLoc = manager.getClass().getMethod("getByLoc", Location.class);
            residenceManager = manager;

            active = true;
        } catch (Throwable t) {
            active = false;
        }
    }

    @Override
    public boolean isProtectedElsewhere(Location location) {
        if (!active || location == null || location.getWorld() == null) return false;

        try {
            Object res = getByLoc.invoke(residenceManager, location);
            return res != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
