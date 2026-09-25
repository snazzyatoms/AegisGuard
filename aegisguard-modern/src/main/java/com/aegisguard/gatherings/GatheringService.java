package com.aegisguard.gatherings;

import com.aegisguard.AegisGuard;
import com.aegisguard.config.Modules;
import com.aegisguard.data.Plot;
import com.aegisguard.guestpass.GuestPass;
import com.aegisguard.guestpass.GuestPassPreset;
import com.aegisguard.util.EffectUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Open House gatherings: timed Atlas listings plus optional visitor Guest Passes.
 */
public final class GatheringService {

    public enum StartResult { STARTED, EXTENDED, NEED_PLOT, NEED_MANAGE, DISABLED, BAD_DURATION }
    public enum StopResult { STOPPED, NONE, NEED_PLOT, NEED_MANAGE, DISABLED }

    private final AegisGuard plugin;
    private final GatheringStore store;

    public GatheringService(AegisGuard plugin) {
        this.plugin = plugin;
        this.store = new GatheringStore(plugin);
    }

    public GatheringStore store() { return store; }

    public boolean isEnabled() {
        try {
            return plugin.modules().on(Modules.Id.GATHERINGS)
                    && plugin.getConfig().getBoolean("gatherings.enabled", true);
        } catch (Throwable ignored) {
            return plugin.getConfig().getBoolean("gatherings.enabled", true);
        }
    }

    public void load() { store.load(); }
    public void save() { store.save(); }
    public boolean isDirty() { return store.isDirty(); }

    public int defaultMinutes() {
        return clampMinutes(plugin.getConfig().getInt("gatherings.default_minutes", 30));
    }

    public int minMinutes() {
        return Math.max(1, plugin.getConfig().getInt("gatherings.min_minutes", 10));
    }

    public int maxMinutes() {
        return Math.max(minMinutes(), plugin.getConfig().getInt("gatherings.max_minutes", 180));
    }

    public boolean defaultGrantGuestPass() {
        return plugin.getConfig().getBoolean("gatherings.grant_guest_pass", true);
    }

    public Gathering active(UUID plotId) {
        Gathering gathering = store.get(plotId);
        if (gathering == null) return null;
        if (!gathering.isLive(System.currentTimeMillis())) {
            expire(gathering);
            return null;
        }
        return gathering;
    }

    public boolean isLive(Plot plot) {
        return plot != null && active(plot.getPlotId()) != null;
    }

    public List<Plot> livePlots() {
        List<Plot> live = new ArrayList<>();
        if (!isEnabled() || plugin.store() == null) return live;
        long now = System.currentTimeMillis();
        for (Gathering gathering : store.all()) {
            if (gathering == null || !gathering.isLive(now)) continue;
            Plot plot = plugin.store().getPlotById(gathering.plotId());
            if (plot != null) live.add(plot);
        }
        return live;
    }

    public StartResult start(Player player, int requestedMinutes) {
        if (!isEnabled()) return StartResult.DISABLED;
        Plot plot = plotManagedBy(player);
        if (plot == null) return player == null ? StartResult.NEED_PLOT
                : (plugin.store().getPlotAt(player.getLocation()) == null ? StartResult.NEED_PLOT : StartResult.NEED_MANAGE);
        int minutes = clampMinutes(requestedMinutes <= 0 ? defaultMinutes() : requestedMinutes);
        if (minutes < minMinutes() || minutes > maxMinutes()) return StartResult.BAD_DURATION;

        long now = System.currentTimeMillis();
        Gathering existing = store.get(plot.getPlotId());
        boolean extended = existing != null && existing.isLive(now);
        Gathering gathering = new Gathering(
                plot.getPlotId(),
                player.getUniqueId(),
                player.getName(),
                plot.getPlotName(),
                extended ? existing.startedAt() : now,
                now + TimeUnit.MINUTES.toMillis(minutes),
                existing != null ? existing.grantGuestPass() : defaultGrantGuestPass()
        );
        if (existing != null) {
            for (UUID id : existing.issuedPasses()) gathering.markIssued(id);
        }
        store.put(gathering);
        store.save();
        announceStart(player, plot, gathering, minutes, extended);
        return extended ? StartResult.EXTENDED : StartResult.STARTED;
    }

    public StopResult stop(Player player) {
        if (!isEnabled()) return StopResult.DISABLED;
        Plot plot = plotManagedBy(player);
        if (plot == null) return player == null ? StopResult.NEED_PLOT
                : (plugin.store().getPlotAt(player.getLocation()) == null ? StopResult.NEED_PLOT : StopResult.NEED_MANAGE);
        Gathering gathering = store.get(plot.getPlotId());
        if (gathering == null) return StopResult.NONE;
        expire(gathering);
        if (player != null && plugin.msg() != null) {
            plugin.msg().send(player, "gathering_stopped",
                    java.util.Map.of("PLOT", plot.getPlotName()));
        }
        return StopResult.STOPPED;
    }

    public boolean toggleGuestPass(Player player) {
        Plot plot = plotManagedBy(player);
        if (plot == null) return false;
        Gathering gathering = active(plot.getPlotId());
        if (gathering == null) return false;
        gathering.setGrantGuestPass(!gathering.grantGuestPass());
        store.markDirty();
        store.save();
        return gathering.grantGuestPass();
    }

    public void welcomeVisitor(Player player, Plot plot) {
        if (player == null || plot == null || !isEnabled()) return;
        if (plot.isOwner(player.getUniqueId())) return;
        String role = plot.getPlayerRoles().get(player.getUniqueId());
        if (role != null && !role.isBlank() && !role.equalsIgnoreCase("visitor")) return;
        Gathering gathering = active(plot.getPlotId());
        if (gathering == null || !gathering.grantGuestPass()) return;
        if (!gathering.markIssued(player.getUniqueId())) return;
        if (plugin.modules() != null && !plugin.modules().on(Modules.Id.GUEST_PASSES)) return;
        long remaining = Math.max(60_000L, gathering.endsAt() - System.currentTimeMillis());
        GuestPass pass = GuestPass.issue(
                player.getUniqueId(),
                player.getName(),
                GuestPassPreset.VISITOR,
                gathering.hostId(),
                gathering.hostName(),
                remaining
        );
        plot.addGuestPass(pass);
        if (plugin.store() != null) {
            plugin.store().savePlot(plot);
            plugin.store().setDirty(true);
        }
        store.markDirty();
        if (plugin.msg() != null) {
            plugin.msg().send(player, "gathering_guest_pass",
                    java.util.Map.of("PLOT", plot.getPlotName()));
        }
    }

    public void tick() {
        if (!isEnabled()) return;
        long now = System.currentTimeMillis();
        for (Gathering gathering : store.all()) {
            if (gathering != null && !gathering.isLive(now)) expire(gathering);
        }
    }

    private void expire(Gathering gathering) {
        if (gathering == null) return;
        store.remove(gathering.plotId());
        store.save();
        revokeIssuedPasses(gathering);
    }

    private void revokeIssuedPasses(Gathering gathering) {
        if (plugin.store() == null) return;
        Plot plot = plugin.store().getPlotById(gathering.plotId());
        if (plot == null) return;
        boolean changed = false;
        for (UUID id : gathering.issuedPasses()) {
            GuestPass pass = plot.getActiveGuestPass(id);
            if (pass != null && pass.getPreset() == GuestPassPreset.VISITOR) {
                plot.revokeGuestPass(id);
                changed = true;
            }
        }
        if (changed) {
            plugin.store().savePlot(plot);
            plugin.store().setDirty(true);
        }
    }

    private Plot plotManagedBy(Player player) {
        if (player == null || plugin.store() == null) return null;
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.canManage(player, plugin)) return null;
        return plot;
    }

    private int clampMinutes(int minutes) {
        return Math.max(minMinutes(), Math.min(maxMinutes(), minutes));
    }

    private void announceStart(Player host, Plot plot, Gathering gathering, int minutes, boolean extended) {
        if (host != null) {
            if (plugin.msg() != null) {
                plugin.msg().send(host, extended ? "gathering_extended" : "gathering_started",
                        java.util.Map.of("PLOT", plot.getPlotName(), "MINUTES", String.valueOf(minutes)));
            }
            EffectUtil effects = plugin.effects();
            if (effects != null) effects.playConfirm(host);
            try {
                host.sendTitle(
                        plugin.gui() == null ? "Open House" : plugin.gui().tr(host, "gathering_title", "&6Open House"),
                        plugin.gui() == null ? plot.getPlotName() : plugin.gui().tr(host, "gathering_subtitle",
                                "&7{PLOT} · {MINUTES}m",
                                java.util.Map.of("PLOT", plot.getPlotName(), "MINUTES", String.valueOf(minutes))),
                        10, 40, 10);
            } catch (Throwable ignored) {
            }
        }
        String broadcast = "&6" + gathering.plotName() + " &7is holding an Open House.";
        try {
            if (plugin.msg() != null) {
                String key = plugin.msg().get(host, "gathering_broadcast");
                if (key != null && !key.isBlank() && !key.equalsIgnoreCase("gathering_broadcast")) {
                    broadcast = key.replace("{PLOT}", gathering.plotName())
                            .replace("{HOST}", gathering.hostName())
                            .replace("{MINUTES}", String.valueOf(minutes));
                }
            }
        } catch (Throwable ignored) {
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (host != null && online.getUniqueId().equals(host.getUniqueId())) continue;
            online.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                    "&8[&bAegisGuard&8]&r " + broadcast));
        }
    }
}
