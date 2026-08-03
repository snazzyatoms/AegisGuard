package com.aegisguard.alliance;

import com.aegisguard.AegisGuard;
import com.aegisguard.audit.AuditCategory;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Milestone 7 - plot join/leave and access-toggle operations with audit logging.
 * Never touches ownership, money, rentals, or member roles.
 */
public class AllianceService {

    private final AegisGuard plugin;

    public AllianceService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public AllianceManager manager() {
        return plugin.alliances();
    }

    public boolean isEnabled() {
        return manager() != null && manager().isEnabled();
    }

    public String joinPlot(Player actor, Plot plot) {
        if (!isEnabled()) return "alliance_disabled";
        if (actor == null || plot == null) return "alliance_invalid";
        if (!plot.canManage(actor, plugin)) return "no_perm";

        Alliance alliance = manager().getByPlayer(actor.getUniqueId());
        if (alliance == null) return "alliance_not_member";

        plot.setAllianceId(alliance.getId());
        // Keep existing toggles as-is (defaults remain off for new plots).
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.ALLIANCE, actor,
                    plotLabel(plot), "Joined alliance " + alliance.getName());
        }
        return null;
    }

    public String leavePlot(Player actor, Plot plot) {
        if (!isEnabled()) return "alliance_disabled";
        if (actor == null || plot == null) return "alliance_invalid";
        if (!plot.canManage(actor, plugin)) return "no_perm";

        UUID previous = plot.getAllianceId();
        plot.clearAllianceAccess();
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        if (plugin.audit() != null) {
            String name = previous == null ? "none" : String.valueOf(previous);
            Alliance alliance = previous == null ? null : manager().get(previous);
            plugin.audit().record(AuditCategory.ALLIANCE, actor, plotLabel(plot),
                    "Left alliance " + (alliance == null ? name : alliance.getName()));
        }
        return null;
    }

    public String toggle(Player actor, Plot plot, String key) {
        if (!isEnabled()) return "alliance_disabled";
        if (actor == null || plot == null || key == null) return "alliance_invalid";
        if (!plot.canManage(actor, plugin)) return "no_perm";
        if (plot.getAllianceId() == null) return "alliance_plot_not_joined";

        boolean now = plot.getAllianceAccess().toggle(key);
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);

        if (plugin.audit() != null) {
            plugin.audit().record(AuditCategory.ALLIANCE, actor, plotLabel(plot),
                    "Alliance access '" + key + "' set to " + (now ? "ON" : "OFF"));
        }
        return null;
    }

    /**
     * When an alliance is disbanded, clear alliance access on every plot that had joined it.
     * Ownership, roles, money, and rentals are never touched.
     */
    public void clearPlotsForDisbandedAlliance(UUID allianceId, Player actor) {
        if (allianceId == null || plugin.store() == null) return;
        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null || !allianceId.equals(plot.getAllianceId())) continue;
            plot.clearAllianceAccess();
            plugin.store().savePlot(plot);
        }
        plugin.store().setDirty(true);
        if (plugin.audit() != null && actor != null) {
            plugin.audit().record(AuditCategory.ALLIANCE, actor, allianceId.toString(),
                    "Cleared plot alliance access after disband");
        }
    }

    public boolean isAllianceMember(Plot plot, UUID playerId) {
        if (plot == null || playerId == null || plot.getAllianceId() == null) return false;
        Alliance alliance = manager().get(plot.getAllianceId());
        return alliance != null && alliance.isMember(playerId);
    }

    public boolean areAlliesOnPlot(Plot plot, UUID a, UUID b) {
        return isAllianceMember(plot, a) && isAllianceMember(plot, b);
    }

    private String plotLabel(Plot plot) {
        if (plot == null) return "unknown";
        String name = plot.getPlotName();
        if (name != null && !name.isBlank()) return name;
        return plot.getOwnerName() == null ? plot.getPlotId().toString() : plot.getOwnerName() + "'s Plot";
    }
}
