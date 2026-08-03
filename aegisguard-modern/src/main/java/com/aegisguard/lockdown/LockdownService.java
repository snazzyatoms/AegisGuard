package com.aegisguard.lockdown;

import com.aegisguard.AegisGuard;
import com.aegisguard.audit.AuditCategory;
import com.aegisguard.data.Plot;
import com.aegisguard.snapshots.ClaimSnapshot.SnapshotType;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        return activate(actor, plot, 0L, "FULL");
    }

    /**
     * @param durationMinutes 0 = until manually lifted
     * @param mode FULL (configured restricted list) or SOFT (containers + build/break only)
     */
    public String activate(Player actor, Plot plot, long durationMinutes, String mode) {
        if (!isEnabled()) return "lockdown_disabled";
        if (plot == null) return "lockdown_invalid";
        if (plot.isLockdownActive()) return "lockdown_already_active";

        UUID actorId = actor == null ? null : actor.getUniqueId();
        String actorName = actor == null ? "System" : actor.getName();
        String resolvedMode = (mode == null || mode.isBlank()) ? "FULL" : mode.trim().toUpperCase();
        long expiresAt = durationMinutes > 0
                ? System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(durationMinutes)
                : 0L;

        if (plugin.getSnapshotManager() != null
                && plugin.getConfig().getBoolean("snapshots.auto_snapshot.before_lockdown", true)) {
            try {
                plugin.getSnapshotManager().createSnapshot(plot, SnapshotType.PRE_LOCKDOWN,
                        "Before Emergency Lockdown by " + actorName, actorId);
            } catch (Throwable ignored) {}
        }

        plot.setLockdown(true, actorId, actorName, expiresAt, resolvedMode);
        plugin.store().savePlot(plot);
        if (plugin.getDiscord() != null) {
            plugin.getDiscord().sendEvent("lockdown", "Emergency lockdown activated",
                    actorName + " activated lockdown for " + plotLabel(plot) + ".", 0xE53935);
        }

        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.LOCKDOWN, actor, plotLabel(plot),
                    "Activated Emergency Lockdown (" + resolvedMode
                            + (durationMinutes > 0 ? ", " + durationMinutes + "m" : ", manual") + ").");
        }
        try {
            if (plugin.getNotificationManager() != null) {
                java.util.LinkedHashSet<java.util.UUID> targets = new java.util.LinkedHashSet<>();
                if (plot.getOwner() != null) targets.add(plot.getOwner());
                if (plot.getPlayerRoles() != null) targets.addAll(plot.getPlayerRoles().keySet());
                for (java.util.UUID id : targets) {
                    if (actorId != null && actorId.equals(id)) continue;
                    plugin.getNotificationManager().notifyCategory(id, "lockdown",
                            "lockdown_activated_notify",
                            "&cEmergency Lockdown activated on &f{PLOT}&c.",
                            java.util.Map.of("PLOT", plotLabel(plot)));
                }
            }
        } catch (Throwable ignored) {}
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

    /** Sweep expired timed lockdowns across loaded plots (called from a light scheduler). */
    public int sweepExpired() {
        if (plugin.store() == null) return 0;
        int lifted = 0;
        long now = System.currentTimeMillis();
        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null) continue;
            long expires = plot.getLockdownExpiresAt();
            if (expires <= 0L || now < expires) continue;
            if (!plot.refreshLockdownExpiry()) {
                plugin.store().savePlot(plot);
                lifted++;
            }
        }
        return lifted;
    }

    private String plotLabel(Plot plot) {
        if (plot == null) return "";
        String name = plot.getPlotName();
        if (name != null && !name.isBlank()) return name;
        return plot.getPlotId().toString();
    }
}
