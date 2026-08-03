package com.aegisguard.guestpass;

import com.aegisguard.AegisGuard;
import com.aegisguard.audit.AuditCategory;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Milestone 2 (Temporary Guest Passes) - plugin-integration layer around the plugin-independent
 * {@code GuestPass}/{@code Plot} guest-pass storage.
 *
 * Handles config-driven limits, issuing/revoking passes (with audit entries), and the periodic
 * expiry sweep. All state lives on {@link Plot} itself and is persisted through the existing
 * {@code IDataStore} plot save path, so this service holds no file of its own.
 */
public class GuestPassService {

    private final AegisGuard plugin;

    public GuestPassService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("guest_passes.enabled", true);
    }

    public int maxActivePerPlot() {
        return Math.max(1, plugin.getConfig().getInt("guest_passes.max_active_per_plot", 10));
    }

    /** Duration presets offered in the GUI, in minutes, ascending order. */
    public List<Integer> durationPresetsMinutes() {
        List<Integer> configured = plugin.getConfig().getIntegerList("guest_passes.duration_presets_minutes");
        if (configured == null || configured.isEmpty()) {
            return List.of(15, 30, 60, 120, 360, 720, 1440, 4320, 10080);
        }
        return configured;
    }

    public long maxDurationMinutes() {
        return Math.max(1L, plugin.getConfig().getLong("guest_passes.max_duration_minutes", 10080L));
    }

    /**
     * Issues (or replaces) a Guest Pass on {@code plot} for {@code targetId}.
     *
     * @return {@code null} on success, or a translation-key style failure reason otherwise.
     */
    public String issue(Player issuer, Plot plot, UUID targetId, String targetName,
                         GuestPassPreset preset, long durationMinutes) {
        if (!isEnabled()) return "guest_pass_disabled";
        if (plot == null || targetId == null || preset == null) return "guest_pass_invalid";
        if (plot.isOwner(targetId) || Plot.SERVER_OWNER_UUID.equals(targetId)) return "guest_pass_target_owner";
        if (plot.isBanned(targetId)) return "guest_pass_target_banned";
        if (issuer != null && issuer.getUniqueId().equals(targetId)) return "guest_pass_target_self";

        boolean replacing = plot.getGuestPass(targetId) != null;
        if (!replacing && plot.getActiveGuestPasses().size() >= maxActivePerPlot()) {
            return "guest_pass_limit_reached";
        }

        long clampedMinutes = Math.max(1L, Math.min(durationMinutes, maxDurationMinutes()));
        long durationMillis = clampedMinutes * 60_000L;

        UUID issuerId = issuer == null ? null : issuer.getUniqueId();
        String issuerName = issuer == null ? "System" : issuer.getName();

        GuestPass pass = GuestPass.issue(targetId, targetName, preset, issuerId, issuerName, durationMillis);
        plot.addGuestPass(pass);
        plugin.store().savePlot(plot);

        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.GUEST_PASS, issuer, plotLabel(plot),
                    "Issued a " + preset.fallbackLabel() + " pass to " + pass.getPlayerName()
                            + " for " + clampedMinutes + " minute(s).");
        }
        return null;
    }

    /** Revokes an active or expired-but-not-yet-swept pass. Never touches permanent roles. */
    public boolean revoke(Player revoker, Plot plot, UUID targetId) {
        if (plot == null || targetId == null) return false;

        GuestPass existing = plot.getGuestPass(targetId);
        if (!plot.revokeGuestPass(targetId)) return false;

        plugin.store().savePlot(plot);

        if (plugin.audit() != null) {
            String presetLabel = existing == null ? "" : existing.getPreset().fallbackLabel() + " ";
            String targetLabel = existing == null ? targetId.toString() : existing.getPlayerName();
            plugin.audit().record(AuditCategory.GUEST_PASS, revoker, plotLabel(plot),
                    "Revoked " + presetLabel + "pass for " + targetLabel + ".");
        }
        return true;
    }

    /**
     * Sweeps every plot for expired passes, notifies the (online) player and owner, and writes an
     * audit entry per expiry. Intended to be driven by a Folia-safe repeating task from
     * {@code AegisGuard}. Survives restarts because expiry timestamps are persisted with the plot.
     */
    public void runExpirySweep() {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();

        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null) continue;

            List<GuestPass> expired = plot.pruneExpiredGuestPasses(now);
            if (expired.isEmpty()) continue;

            plugin.store().savePlot(plot);

            for (GuestPass pass : expired) {
                if (plugin.audit() != null) {
                    plugin.audit().record(AuditCategory.GUEST_PASS, null, "System", plotLabel(plot),
                            pass.getPreset().fallbackLabel() + " pass for " + pass.getPlayerName() + " expired.");
                }

                Player target = Bukkit.getPlayer(pass.getPlayerId());
                if (target != null && target.isOnline()) {
                    plugin.runMain(target, () -> plugin.msg().send(target, "guest_pass_expired_notice",
                            Map.of("PLOT", plotLabel(plot))));
                }

                Player owner = Bukkit.getPlayer(plot.getOwner());
                if (owner != null && owner.isOnline()) {
                    plugin.runMain(owner, () -> plugin.msg().send(owner, "guest_pass_expired_owner_notice",
                            Map.of("PLAYER", pass.getPlayerName())));
                }
            }
        }
    }

    private String plotLabel(Plot plot) {
        if (plot == null) return "";
        String name = plot.getPlotName();
        if (name != null && !name.isBlank()) return name;
        return plot.getPlotId().toString();
    }
}
