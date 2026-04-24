package com.aegisguard.hooks.protection.impl;

import com.aegisguard.AegisGuard;
import com.aegisguard.hooks.protection.ProtectionHook;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;

public class WorldGuardHook implements ProtectionHook {

    private final AegisGuard plugin;
    private final int priority;
    private boolean active = false;

    public WorldGuardHook(AegisGuard plugin) {
        this(plugin, 100);
    }

    public WorldGuardHook(AegisGuard plugin, int priority) {
        this.plugin = plugin;
        this.priority = priority;

        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            active = true;
        } catch (Throwable t) {
            active = false;
        }
    }

    @Override
    public String id() {
        return "WorldGuard";
    }

    @Override
    public int priority() {
        return priority;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isProtectedElsewhere(Location location) {
        if (!active || location == null || location.getWorld() == null) return false;

        try {
            var container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            if (container == null) return false;

            var query = container.createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(location));
            if (set == null) return false;

            for (ProtectedRegion r : set) {
                if (r == null) continue;

                String id = r.getId();
                if (id != null && id.equalsIgnoreCase("__global__")) continue;

                return true; // any non-global region
            }
        } catch (Throwable t) {
            return false; // fail-open
        }

        return false;
    }
}
