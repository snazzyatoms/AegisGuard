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
    private boolean active = false;

    public WorldGuardHook(AegisGuard plugin) {
        this.plugin = plugin;
        try {
            // Ensure classes exist
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
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean isProtectedElsewhere(Location location) {
        if (!active || location == null || location.getWorld() == null) return false;

        try {
            var container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            var query = container.createQuery();
            ApplicableRegionSet set = query.getApplicableRegions(BukkitAdapter.adapt(location));

            if (set == null) return false;

            for (ProtectedRegion r : set) {
                if (r == null) continue;

                // Ignore the global region (WorldGuard uses __global__).
                String id = r.getId();
                if (id != null && id.equalsIgnoreCase("__global__")) continue;

                // Any non-global region counts as "protected elsewhere".
                return true;
            }
        } catch (Throwable t) {
            // If WG API hiccups, fail open (don’t block claims) to avoid weird false positives.
            return false;
        }

        return false;
    }
}
