package com.aegisguard.guestpass;

import com.aegisguard.AegisGuard;
import com.aegisguard.audit.AuditCategory;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Milestone 2 (Temporary Guest Passes) - plugin-integration layer around the plugin-independent
 * {@code GuestPass}/{@code Plot} guest-pass storage.
 *
 * Handles config-driven limits, issuing/revoking passes (with audit entries), the periodic
 * expiry sweep, and active-playtime pause/resume on join/quit/shutdown. All state lives on
 * {@link Plot} itself and is persisted through the existing {@code IDataStore} plot save path,
 * so this service holds no file of its own.
 */
public class GuestPassService implements Listener {

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
     * Issues (or replaces) a Guest Pass on {@code plot} for {@code targetId} using real-time expiry
     * (the compatible default).
     *
     * @return {@code null} on success, or a translation-key style failure reason otherwise.
     */
    public String issue(Player issuer, Plot plot, UUID targetId, String targetName,
                         GuestPassPreset preset, long durationMinutes) {
        return issue(issuer, plot, targetId, targetName, preset, durationMinutes, GuestPassMode.REAL_TIME);
    }

    /**
     * Issues (or replaces) a Guest Pass on {@code plot} for {@code targetId}.
     *
     * @return {@code null} on success, or a translation-key style failure reason otherwise.
     */
    public String issue(Player issuer, Plot plot, UUID targetId, String targetName,
                         GuestPassPreset preset, long durationMinutes, GuestPassMode mode) {
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
        GuestPassMode resolvedMode = (mode == null) ? GuestPassMode.REAL_TIME : mode;

        UUID issuerId = issuer == null ? null : issuer.getUniqueId();
        String issuerName = issuer == null ? "System" : issuer.getName();

        GuestPass pass = GuestPass.issue(targetId, targetName, preset, issuerId, issuerName,
                durationMillis, resolvedMode);

        // Active-playtime starts counting immediately when the recipient is already online.
        if (pass.isActivePlaytime()) {
            Player online = Bukkit.getPlayer(targetId);
            if (online != null && online.isOnline()) {
                pass.resumeSession(System.currentTimeMillis());
            }
        }

        plot.addGuestPass(pass);
        plugin.store().savePlot(plot);

        if (plugin.audit() != null) {
            String modeLabel = resolvedMode == GuestPassMode.ACTIVE_PLAYTIME ? "active-playtime" : "real-time";
            plugin.audit().record(AuditCategory.GUEST_PASS, issuer, plotLabel(plot),
                    "Issued a " + preset.fallbackLabel() + " pass to " + pass.getPlayerName()
                            + " for " + clampedMinutes + " minute(s) (" + modeLabel + ").");
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
     * Sweeps every plot for expired passes, checkpoints online active-playtime sessions, notifies
     * the (online) player and owner, and writes an audit entry per expiry. Intended to be driven
     * by a Folia-safe repeating task from {@code AegisGuard}. Survives restarts because expiry /
     * remaining timestamps are persisted with the plot.
     */
    public void runExpirySweep() {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();

        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null) continue;

            boolean dirty = checkpointOnlineActivePlaytime(plot, now);

            List<GuestPass> expired = plot.pruneExpiredGuestPasses(now);
            if (!expired.isEmpty()) {
                dirty = true;
            }
            if (!dirty) continue;

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

    /**
     * Freezes every active-playtime session across all plots. Call before shutdown save so offline
     * / server-down time never consumes remaining playtime.
     */
    public void freezeAllActiveSessions() {
        if (plugin.store() == null) return;
        long now = System.currentTimeMillis();
        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null) continue;
            boolean dirty = false;
            for (GuestPass pass : plot.getGuestPasses().values()) {
                if (pass != null && pass.freezeSession(now)) dirty = true;
            }
            if (dirty) {
                try {
                    plugin.store().savePlot(plot);
                } catch (Throwable ignored) {}
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled() || event.getPlayer() == null || plugin.store() == null) return;
        resumeActivePlaytimeFor(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!isEnabled() || event.getPlayer() == null || plugin.store() == null) return;
        freezeActivePlaytimeFor(event.getPlayer().getUniqueId());
    }

    private void resumeActivePlaytimeFor(UUID playerId) {
        if (playerId == null) return;
        long now = System.currentTimeMillis();
        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null) continue;
            GuestPass pass = plot.getGuestPass(playerId);
            if (pass == null || !pass.isActivePlaytime() || pass.isExpired(now)) continue;
            if (pass.resumeSession(now)) {
                plugin.store().savePlot(plot);
            }
        }
    }

    private void freezeActivePlaytimeFor(UUID playerId) {
        if (playerId == null) return;
        long now = System.currentTimeMillis();
        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null) continue;
            GuestPass pass = plot.getGuestPass(playerId);
            if (pass == null || !pass.isActivePlaytime()) continue;
            if (pass.freezeSession(now)) {
                plugin.store().savePlot(plot);
            }
        }
    }

    private boolean checkpointOnlineActivePlaytime(Plot plot, long now) {
        boolean dirty = false;
        for (GuestPass pass : plot.getGuestPasses().values()) {
            if (pass == null || !pass.isActivePlaytime() || !pass.isSessionActive()) continue;
            Player online = Bukkit.getPlayer(pass.getPlayerId());
            if (online == null || !online.isOnline()) {
                // Defensive: treat as offline if Bukkit no longer lists them.
                if (pass.freezeSession(now)) dirty = true;
                continue;
            }
            if (pass.checkpointSession(now)) dirty = true;
        }
        return dirty;
    }

    private String plotLabel(Plot plot) {
        if (plot == null) return "";
        String name = plot.getPlotName();
        if (name != null && !name.isBlank()) return name;
        return plot.getPlotId().toString();
    }
}
