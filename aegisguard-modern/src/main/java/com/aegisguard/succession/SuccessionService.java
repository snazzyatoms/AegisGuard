package com.aegisguard.succession;

import com.aegisguard.AegisGuard;
import com.aegisguard.audit.AuditCategory;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guardian Succession: co-owner locks, heir inactivity, transfer cooldown + rollback window.
 */
public final class SuccessionService {

    public record PendingRollback(UUID plotId, UUID previousOwner, String previousOwnerName,
                                  UUID newOwner, long completedAtMs) {}

    private final AegisGuard plugin;
    private final Map<UUID, Long> lastTransferAt = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRollback> pending = new ConcurrentHashMap<>();

    public SuccessionService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.modules() != null && plugin.modules().on(com.aegisguard.config.Modules.Id.SUCCESSION)
                && plugin.getConfig().getBoolean("succession.enabled", true);
    }

    public long inactivityDays() {
        return Math.max(1L, plugin.getConfig().getLong("succession.inactivity_days", 30L));
    }

    public long transferCooldownMs() {
        return Math.max(0L, plugin.getConfig().getLong("succession.transfer_cooldown_seconds", 3600L)) * 1000L;
    }

    public long rollbackWindowMs() {
        return Math.max(0L, plugin.getConfig().getLong("succession.rollback_window_seconds", 300L)) * 1000L;
    }

    public static boolean isInactive(long lastSeenMs, long nowMs, long inactivityDays) {
        if (lastSeenMs <= 0L) return false;
        return nowMs - lastSeenMs >= inactivityDays * 86_400_000L;
    }

    public static long remainingCooldownMs(long lastTransferMs, long nowMs, long cooldownMs) {
        if (lastTransferMs <= 0L || cooldownMs <= 0L) return 0L;
        return Math.max(0L, (lastTransferMs + cooldownMs) - nowMs);
    }

    public long remainingTransferCooldown(UUID plotId) {
        return remainingCooldownMs(lastTransferAt.getOrDefault(plotId, 0L),
                System.currentTimeMillis(), transferCooldownMs());
    }

    public boolean canTransferNow(Plot plot) {
        if (plot == null) return false;
        if (!enabled()) return true;
        return remainingTransferCooldown(plot.getPlotId()) <= 0L;
    }

    public void recordTransfer(Plot plot, UUID previousOwner, String previousName, UUID newOwner, Player actor) {
        if (plot == null || newOwner == null) return;
        long now = System.currentTimeMillis();
        lastTransferAt.put(plot.getPlotId(), now);
        pending.put(plot.getPlotId(), new PendingRollback(plot.getPlotId(), previousOwner,
                previousName == null ? "Unknown" : previousName, newOwner, now));
        if (plugin.audit() != null && actor != null) {
            plugin.audit().record(AuditCategory.OWNERSHIP_TRANSFER, actor, plot.getPlotName(),
                    "Transferred to " + newOwner);
        }
    }

    public PendingRollback pendingRollback(UUID plotId) {
        PendingRollback row = pending.get(plotId);
        if (row == null) return null;
        if (System.currentTimeMillis() - row.completedAtMs() > rollbackWindowMs()) {
            pending.remove(plotId);
            return null;
        }
        return row;
    }

    public boolean rollback(Player actor, Plot plot) {
        if (!enabled() || actor == null || plot == null) return false;
        PendingRollback row = pendingRollback(plot.getPlotId());
        if (row == null) return false;
        if (!actor.getUniqueId().equals(row.previousOwner()) && !plugin.isAdmin(actor)) return false;
        plugin.store().changePlotOwner(plot, row.previousOwner(), row.previousOwnerName());
        plugin.store().savePlot(plot);
        pending.remove(plot.getPlotId());
        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.OWNERSHIP_TRANSFER, actor, plot.getPlotName(),
                    "Rolled back ownership transfer");
        }
        return true;
    }

    public boolean ownerInactive(Plot plot) {
        if (plot == null || plot.getOwner() == null) return false;
        OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());
        long last = owner.getLastPlayed();
        if (last <= 0L) last = plot.getLastUpkeepPayment();
        return isInactive(last, System.currentTimeMillis(), inactivityDays());
    }

    public boolean canAssume(Player player, Plot plot) {
        if (!enabled() || player == null || plot == null) return false;
        UUID heir = plot.getHeir();
        if (heir == null || !heir.equals(player.getUniqueId())) return false;
        if (plot.isOwner(player.getUniqueId())) return false;
        return ownerInactive(plot);
    }

    public boolean assume(Player player, Plot plot) {
        if (!canAssume(player, plot)) return false;
        UUID previous = plot.getOwner();
        String previousName = plot.getOwnerName();
        plugin.store().changePlotOwner(plot, player.getUniqueId(), player.getName());
        if (previous != null) {
            plot.setRole(previous, "co_owner", true);
            plot.lockMember(previous);
        }
        plot.setHeir(null);
        plugin.store().savePlot(plot);
        recordTransfer(plot, previous, previousName, player.getUniqueId(), player);
        return true;
    }
}
