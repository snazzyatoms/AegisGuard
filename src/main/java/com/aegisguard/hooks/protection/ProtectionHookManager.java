package com.aegisguard.hooks.protection;

import com.aegisguard.AegisGuard;
import com.aegisguard.hooks.protection.impl.WorldGuardHook;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compatibility layer:
 * Ask other protection plugins first, and if any DENY, AegisGuard respects it.
 *
 * Priority rule:
 * - Any DENY => deny (first deny wins)
 * - Else allow
 *
 * This prevents "plugin fights" and keeps behavior predictable.
 */
public class ProtectionHookManager {

    private final AegisGuard plugin;
    private final List<ProtectionHook> hooks = new ArrayList<>();

    public ProtectionHookManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public void registerDefaults() {
        // Add popular hooks here (start small, expand safely)
        register(new WorldGuardHook(plugin));

        // Later:
        // register(new GriefPreventionHook(plugin));
        // register(new TownyHook(plugin));
        // register(new LandsHook(plugin));
        // register(new PlotSquaredHook(plugin));
        // register(new GriefDefenderHook(plugin));
        // register(new ResidenceHook(plugin));

        plugin.getLogger().info("[AegisGuard] Protection hooks registered: " + hooks.size());
    }

    public void register(ProtectionHook hook) {
        if (hook == null) return;
        hooks.add(hook);
        plugin.getLogger().info("[AegisGuard] Hook loaded: " + hook.getName()
                + " (available=" + hook.isAvailable() + ")");
    }

    public List<ProtectionHook> getHooks() {
        return Collections.unmodifiableList(hooks);
    }

    public HookDecision canBuild(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return HookDecision.allow();
        }

        for (ProtectionHook hook : hooks) {
            if (!safeAvailable(hook)) continue;

            ProtectionResult r = safeCanBuild(hook, player, location);
            if (r == ProtectionResult.DENY) {
                return HookDecision.deny(hook.getName(), "Denied by external protection");
            }
        }

        return HookDecision.allow();
    }

    public HookDecision canInteract(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return HookDecision.allow();
        }

        for (ProtectionHook hook : hooks) {
            if (!safeAvailable(hook)) continue;

            ProtectionResult r = safeCanInteract(hook, player, location);
            if (r == ProtectionResult.DENY) {
                return HookDecision.deny(hook.getName(), "Denied by external protection");
            }
        }

        return HookDecision.allow();
    }

    private boolean safeAvailable(ProtectionHook hook) {
        try {
            return hook.isAvailable();
        } catch (Throwable t) {
            // If a hook blows up, treat it as unavailable rather than breaking protection.
            return false;
        }
    }

    private ProtectionResult safeCanBuild(ProtectionHook hook, Player player, Location loc) {
        try {
            ProtectionResult r = hook.canBuild(player, loc);
            return (r == null) ? ProtectionResult.ABSTAIN : r;
        } catch (Throwable t) {
            return ProtectionResult.ABSTAIN;
        }
    }

    private ProtectionResult safeCanInteract(ProtectionHook hook, Player player, Location loc) {
        try {
            ProtectionResult r = hook.canInteract(player, loc);
            return (r == null) ? ProtectionResult.ABSTAIN : r;
        } catch (Throwable t) {
            return ProtectionResult.ABSTAIN;
        }
    }
}
