package com.aegisguard.hooks.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.hooks.protection.impl.GriefDefenderHook;
import com.aegisguard.hooks.protection.impl.GriefPreventionHook;
import com.aegisguard.hooks.protection.impl.ResidenceHook;
import com.aegisguard.hooks.protection.impl.TownyHook;
import com.aegisguard.hooks.protection.impl.WorldGuardHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central compatibility layer.
 *
 * Goal:
 * - If another protection plugin already owns/protects an area, AegisGuard should yield
 *   so plugins don't "fight" over the same turf.
 *
 * This manager does NOT enforce permissions. It only answers:
 * - "Is this protected elsewhere?"
 */
public class ProtectionHookManager {

    private final AegisGuard plugin;
    private final List<ProtectionHook> hooks = new ArrayList<>();

    public ProtectionHookManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public void registerDefaults() {
        hooks.clear();

        // WorldGuard (you already depend on it in pom) – safe to use actual API.
        tryRegister(new WorldGuardHook(plugin));

        // Reflection-based hooks (no compile-time dependency needed)
        tryRegister(new GriefPreventionHook(plugin));
        tryRegister(new TownyHook(plugin));
        tryRegister(new GriefDefenderHook(plugin));
        tryRegister(new ResidenceHook(plugin));

        plugin.getLogger().info("[AegisGuard] Protection compatibility hooks loaded: " + getActiveHookIds());
    }

    private void tryRegister(ProtectionHook hook) {
        if (hook == null) return;

        // Only register if plugin is installed/enabled and hook initialized.
        boolean enabled = Bukkit.getPluginManager().isPluginEnabled(hook.id());
        if (!enabled) return;

        if (hook.isActive()) {
            hooks.add(hook);
            plugin.getLogger().info("[AegisGuard] Hooked into " + hook.id() + " (protection compatibility).");
        } else {
            plugin.getLogger().warning("[AegisGuard] " + hook.id() + " detected, but hook failed to initialize. (No conflict protection for it.)");
        }
    }

    public List<ProtectionHook> getHooks() {
        return Collections.unmodifiableList(hooks);
    }

    public List<String> getActiveHookIds() {
        List<String> ids = new ArrayList<>();
        for (ProtectionHook h : hooks) {
            if (h != null && h.isActive()) ids.add(h.id());
        }
        return ids;
    }

    /**
     * True if ANY hooked protection plugin says this location is inside its region/claim.
     */
    public boolean isProtectedElsewhere(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;

        for (ProtectionHook hook : hooks) {
            try {
                if (hook != null && hook.isActive() && hook.isProtectedElsewhere(loc)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Hooks must never crash the server.
            }
        }
        return false;
    }

    /**
     * Conservative area scan for claim creation.
     * You can call this from /claim logic before allowing an AegisGuard claim.
     */
    public boolean isAreaProtectedElsewhere(String world, int x1, int z1, int x2, int z2) {
        if (world == null || Bukkit.getWorld(world) == null) return false;

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        // Sample every 16 blocks (chunk grid) but cap work to stay safe.
        int step = 16;
        long samples = 0;

        for (int x = minX; x <= maxX; x += step) {
            for (int z = minZ; z <= maxZ; z += step) {
                samples++;
                if (samples > 4000) {
                    // Too large: fail-safe (treat as protected) to avoid big lag scans.
                    return true;
                }
                Location loc = new Location(Bukkit.getWorld(world), x + 0.5, 64, z + 0.5);
                if (isProtectedElsewhere(loc)) return true;
            }
        }

        // Also check far edges/corners
        Location c1 = new Location(Bukkit.getWorld(world), minX + 0.5, 64, minZ + 0.5);
        Location c2 = new Location(Bukkit.getWorld(world), maxX + 0.5, 64, maxZ + 0.5);
        Location c3 = new Location(Bukkit.getWorld(world), minX + 0.5, 64, maxZ + 0.5);
        Location c4 = new Location(Bukkit.getWorld(world), maxX + 0.5, 64, minZ + 0.5);

        return isProtectedElsewhere(c1) || isProtectedElsewhere(c2) || isProtectedElsewhere(c3) || isProtectedElsewhere(c4);
    }
}
