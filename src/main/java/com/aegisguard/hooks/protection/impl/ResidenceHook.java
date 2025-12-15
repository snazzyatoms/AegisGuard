package com.aegisguard.hooks.protection.impl;

import com.aegisguard.AegisGuard;
import com.aegisguard.hooks.protection.ProtectionHook;
import org.bukkit.Location;

import java.lang.reflect.Method;

public class ResidenceHook implements ProtectionHook {

    private final AegisGuard plugin;

    private boolean active;
    private Object residenceApi;
    private Method getByLoc;

    public ResidenceHook(AegisGuard plugin) {
        this.plugin = plugin;
        init();
    }

    @Override
    public String id() {
        return "Residence";
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
            residenceApi = getInstance.invoke(null);
            if (residenceApi == null) {
                active = false;
                return;
            }

            Method getManager = residenceApi.getClass().getMethod("getResidenceManager");
            Object manager = getManager.invoke(residenceApi);
            if (manager == null) {
                active = false;
                return;
            }

            getByLoc = manager.getClass().getMethod("getByLoc", Location.class);
            // store manager instead of api
            residenceApi = manager;

            active = true;
        } catch (Throwable t) {
            active = false;
        }
    }

    @Override
    public boolean isProtectedElsewhere(Location location) {
        if (!active || location == null || location.getWorld() == null) return false;

        try {
            Object res = getByLoc.invoke(residenceApi, location);
            return res != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
