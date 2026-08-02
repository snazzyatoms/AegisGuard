package com.aegisguard.lockdown;

import com.aegisguard.AegisGuard;
import com.aegisguard.audit.AuditCategory;
import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Milestone 3 (Emergency Plot Lockdown) - plugin-integration layer around the plugin-independent
 * lockdown state stored directly on {@link Plot}.
 *
 * Handles the config-driven enable switch and confirmation requirement, and wraps
 * activate/deactivate with persistence and an audit entry. All enforcement itself lives in
 * {@link Plot#canBuild(org.bukkit.entity.Player, org.bukkit.plugin.Plugin, String)} so protection
 * checks stay in one place.
 */
public class LockdownService {

    private final AegisGuard plugin;

    public LockdownService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("lockdown.enabled", true);
    }

    public boolean requiresConfirmation() {
        return plugin.getConfig().getBoolean("lockdown.require_confirmation", true);
    }

    /**
     * Activates lockdown on {@code plot}. Never touches ownership, roles, or Guest Passes -
     * purely flips the temporary access gate and persists + audits the change.
     *
     * @return {@code null} on success, or a translation-key style failure reason otherwise.
     */
    public String activate(Player actor, Plot plot) {
        if (!isEnabled()) return "lockdown_disabled";
        if (plot == null) return "lockdown_invalid";
        if (plot.isLockdownActive()) return "lockdown_already_active";

        UUID actorId = actor == null ? null : actor.getUniqueId();
        String actorName = actor == null ? "System" : actor.getName();

        plot.setLockdown(true, actorId, actorName);
        plugin.store().savePlot(plot);

        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.LOCKDOWN, actor, plotLabel(plot),
                    "Activated Emergency Lockdown.");
        }
        return null;
    }

    /** Deactivates lockdown on {@code plot}. Idempotent - deactivating twice is a no-op success. */
    public String deactivate(Player actor, Plot plot) {
        if (plot == null) return "lockdown_invalid";
        if (!plot.isLockdownActive()) return null;

        plot.setLockdown(false, null, null);
        plugin.store().savePlot(plot);

        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.LOCKDOWN, actor, plotLabel(plot),
                    "Deactivated Emergency Lockdown.");
        }
        return null;
    }

    private String plotLabel(Plot plot) {
        if (plot == null) return "";
        String name = plot.getPlotName();
        if (name != null && !name.isBlank()) return name;
        return plot.getPlotId().toString();
    }
}
