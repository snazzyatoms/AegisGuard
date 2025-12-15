package com.aegisguard.hooks.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.hooks.protection.impl.GriefDefenderHook;
import com.aegisguard.hooks.protection.impl.GriefPreventionHook;
import com.aegisguard.hooks.protection.impl.ResidenceHook;
import com.aegisguard.hooks.protection.impl.TownyHook;
import com.aegisguard.hooks.protection.impl.WorldGuardHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Central compatibility layer.
 *
 * Goal:
 * - If another protection plugin already owns/protects an area, AegisGuard can yield
 *   so plugins don't fight over the same turf.
 *
 * This manager does NOT enforce permissions. It only answers:
 * - "Is this protected elsewhere?"
 */
public class ProtectionHookManager {

    public enum OverlapPolicy {
        EXTERNAL_WINS,
        AEGIS_WINS,
        DENY_IF_CONFLICT
    }

    private final AegisGuard plugin;
    private final List<ProtectionHook> hooks = new ArrayList<>();

    public ProtectionHookManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("hooks.protection_compat.enabled", true);
    }

    public boolean isDebug() {
        return plugin.getConfig().getBoolean("hooks.protection_compat.debug_logging", false);
    }

    public OverlapPolicy getOverlapPolicy() {
        String raw = plugin.getConfig().getString("hooks.protection_compat.overlap_policy", "EXTERNAL_WINS");
        try {
            return OverlapPolicy.valueOf(raw.trim().toUpperCase());
        } catch (Exception ignored) {
            return OverlapPolicy.EXTERNAL_WINS;
        }
    }

    public boolean isPluginHookEnabled(String id) {
        String path = "hooks.protection_compat.plugins." + id.toLowerCase() + ".enabled";
        return plugin.getConfig().getBoolean(path, true);
    }

    public int getPluginPriority(String id, int def) {
        String path = "hooks.protection_compat.plugins." + id.toLowerCase() + ".priority";
        return plugin.getConfig().getInt(path, def);
    }

    public void registerDefaults() {
        hooks.clear();

        if (!isEnabled()) {
            plugin.getLogger().info("[AegisGuard] Protection compatibility is disabled in config.");
            return;
        }

        // WorldGuard (compile-time API)
        if (isPluginHookEnabled("worldguard")) {
            tryRegister(new WorldGuardHook(plugin, getPluginPriority("worldguard", 100)));
        }

        // Reflection hooks (no compile-time dependency)
        if (isPluginHookEnabled("griefprevention")) {
            tryRegister(new GriefPreventionHook(plugin, getPluginPriority("griefprevention", 90)));
        }
        if (isPluginHookEnabled("towny")) {
            tryRegister(new TownyHook(plugin, getPluginPriority("towny", 80)));
        }
        if (isPluginHookEnabled("griefdefender")) {
            tryRegister(new GriefDefenderHook(plugin, getPluginPriority("griefdefender", 90)));
        }
        if (isPluginHookEnabled("residence")) {
            tryRegister(new ResidenceHook(plugin, getPluginPriority("residence", 70)));
        }

        hooks.sort(Comparator.comparingInt(ProtectionHook::priority).reversed());

        plugin.getLogger().info("[AegisGuard] Protection compatibility hooks loaded: " + getActiveHookIds());
    }

    private void tryRegister(ProtectionHook hook) {
        if (hook == null) return;

        boolean enabled = Bukkit.getPluginManager().isPluginEnabled(hook.id());
        if (!enabled) return;

        if (hook.isActive()) {
            hooks.add(hook);
            plugin.getLogger().info("[AegisGuard] Hooked into " + hook.id() + " (protection compatibility).");
        } else {
            plugin.getLogger().warning("[AegisGuard] " + hook.id() + " detected, but hook failed to initialize.");
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
     * Returns the FIRST hook id that says "protected here", respecting priority order.
     * Returns null if none.
     */
    public String getBlockingHookId(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        if (!isEnabled()) return null;

        for (ProtectionHook hook : hooks) {
            try {
                if (hook != null && hook.isActive() && hook.isProtectedElsewhere(loc)) {
                    if (isDebug()) {
                        plugin.getLogger().info("[AegisGuard] External protection hit by " + hook.id()
                                + " at " + loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
                    }
                    return hook.id();
                }
            } catch (Throwable t) {
                // Hooks must never crash the server.
            }
        }
        return null;
    }

    /**
     * True if ANY hooked protection plugin says this location is inside its region/claim.
     */
    public boolean isProtectedElsewhere(Location loc) {
        return getBlockingHookId(loc) != null;
    }

    /**
     * Conservative area scan for claim creation.
     * Call this before allowing an AegisGuard claim.
     */
    public boolean isAreaProtectedElsewhere(String worldName, int x1, int z1, int x2, int z2) {
        if (worldName == null) return false;
        World w = Bukkit.getWorld(worldName);
        if (w == null) return false;
        if (!isEnabled()) return false;

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        int step = 16; // chunk grid sampling
        long samples = 0;

        int y = clampY(w, 64);

        for (int x = minX; x <= maxX; x += step) {
            for (int z = minZ; z <= maxZ; z += step) {
                samples++;
                if (samples > 4000) {
                    // Too large: fail-safe (treat as protected) to avoid big lag scans.
                    return true;
                }
                Location loc = new Location(w, x + 0.5, y, z + 0.5);
                if (isProtectedElsewhere(loc)) return true;
            }
        }

        // Also check corners
        Location c1 = new Location(w, minX + 0.5, y, minZ + 0.5);
        Location c2 = new Location(w, maxX + 0.5, y, maxZ + 0.5);
        Location c3 = new Location(w, minX + 0.5, y, maxZ + 0.5);
        Location c4 = new Location(w, maxX + 0.5, y, minZ + 0.5);

        return isProtectedElsewhere(c1) || isProtectedElsewhere(c2) || isProtectedElsewhere(c3) || isProtectedElsewhere(c4);
    }

    private int clampY(World w, int preferred) {
        int min = w.getMinHeight() + 1;
        int max = w.getMaxHeight() - 1;
        int y = preferred;
        if (y < min) y = min;
        if (y > max) y = max;
        return y;
    }
}
