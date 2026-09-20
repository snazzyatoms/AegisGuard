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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Open House gatherings: timed Atlas listings plus optional visitor Guest Passes.
 */
public final class GatheringService {

    public enum StartResult { STARTED, EXTENDED, NEED_PLOT, NEED_MANAGE, DISABLED, BAD_DURATION, SAVE_FAILED }
    public enum StopResult { STOPPED, NONE, NEED_PLOT, NEED_MANAGE, DISABLED, SAVE_FAILED }

    private final AegisGuard plugin;
    private final GatheringStore store;
    private volatile boolean loaded;

    public GatheringService(AegisGuard plugin) {
        this.plugin = plugin;
        this.store = new GatheringStore(plugin);
    }

    public GatheringStore store() { return store; }

    public boolean isEnabled() {
        if (!loaded) return false;
        try {
            return plugin.modules().on(Modules.Id.GATHERINGS)
                    && plugin.getConfig().getBoolean("gatherings.enabled", true);
        } catch (Throwable ignored) {
            return plugin.getConfig().getBoolean("gatherings.enabled", true);
        }
    }

    public synchronized void load() {
        store.load();
        loaded = true;
    }
    public synchronized void save() { if (loaded) store.save(); }
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
        if (!loaded || !isEnabled()) return null;
        Gathering gathering = store.get(plotId);
        Plot plot = gathering == null || plugin.store() == null ? null : plugin.store().getPlotById(plotId);
        return gathering != null && plot != null && hostHasRole(plot, gathering.hostId())
                && gathering.isLive(System.currentTimeMillis()) ? gathering : null;
    }

    public boolean isLive(Plot plot) {
        return plot != null && active(plot.getPlotId()) != null;
    }

    public List<Plot> livePlots() {
        List<Plot> live = new ArrayList<>();
        if (!loaded || !isEnabled() || plugin.store() == null) return live;
        long now = System.currentTimeMillis();
        for (Gathering gathering : store.all()) {
            if (gathering == null || !gathering.isLive(now)) continue;
            Plot plot = plugin.store().getPlotById(gathering.plotId());
            if (plot != null && hostHasRole(plot, gathering.hostId())) live.add(plot);
        }
        return live;
    }

    public synchronized StartResult start(Player player, int requestedMinutes) {
        if (!loaded || !isEnabled()) return StartResult.DISABLED;
        Plot plot = plotManagedBy(player);
        if (plot == null) return player == null ? StartResult.NEED_PLOT
                : (plugin.store().getPlotAt(player.getLocation()) == null ? StartResult.NEED_PLOT : StartResult.NEED_MANAGE);
        if (plot.isServerZone()) return StartResult.NEED_MANAGE;
        int minutes = requestedMinutes;
        if (minutes < minMinutes() || minutes > maxMinutes()) return StartResult.BAD_DURATION;

        long now = System.currentTimeMillis();
        Gathering existing = store.get(plot.getPlotId());
        boolean extended = existing != null && existing.isLive(now)
                && hostHasRole(plot, existing.hostId());
        if (existing != null && !extended && !expire(existing)) return StartResult.SAVE_FAILED;
        Gathering gathering = new Gathering(
                plot.getPlotId(),
                player.getUniqueId(),
                player.getName(),
                plot.getPlotName(),
                extended ? existing.startedAt() : now,
                now + TimeUnit.MINUTES.toMillis(minutes),
                extended ? existing.grantGuestPass() : defaultGrantGuestPass()
        );
        if (extended) {
            existing.issuedPasses().forEach(gathering::recordIssued);
        }
        Map<UUID, GuestPass> replacedPasses = new HashMap<>();
        if (extended && plugin.guestPasses() != null) {
            long passEnd = Math.min(gathering.endsAt(),
                    now + TimeUnit.MINUTES.toMillis(plugin.guestPasses().maxDurationMinutes()));
            for (UUID id : existing.issuedPasses().keySet()) {
                GuestPass oldPass = plot.getGuestPass(id);
                if (!existing.issuedByThisGathering(oldPass) || oldPass.getExpiresAt() == passEnd) continue;
                GuestPass renewed = gathering.renewedPass(oldPass, now, passEnd);
                if (renewed == null) continue;
                replacedPasses.put(id, oldPass);
                plot.addGuestPass(renewed);
                gathering.recordIssued(id, renewed.getIssuedAt());
            }
            if (!replacedPasses.isEmpty()) {
                try {
                    plugin.store().savePlotSync(plot);
                } catch (RuntimeException error) {
                    replacedPasses.values().forEach(plot::addGuestPass);
                    plugin.getLogger().warning("Could not renew Open House passes: " + error.getMessage());
                    return StartResult.SAVE_FAILED;
                }
            }
        }
        store.put(gathering);
        if (!store.save()) {
            if (extended) store.put(existing);
            else store.remove(plot.getPlotId());
            if (!replacedPasses.isEmpty()) {
                replacedPasses.values().forEach(plot::addGuestPass);
                plugin.store().savePlotSync(plot);
            }
            return StartResult.SAVE_FAILED;
        }
        announceStart(player, plot, gathering, minutes, extended);
        return extended ? StartResult.EXTENDED : StartResult.STARTED;
    }

    public synchronized StopResult stop(Player player) {
        if (!loaded || !isEnabled()) return StopResult.DISABLED;
        Plot plot = plotManagedBy(player);
        if (plot == null) return player == null ? StopResult.NEED_PLOT
                : (plugin.store().getPlotAt(player.getLocation()) == null ? StopResult.NEED_PLOT : StopResult.NEED_MANAGE);
        Gathering gathering = store.get(plot.getPlotId());
        if (gathering == null) return StopResult.NONE;
        if (!expire(gathering)) return StopResult.SAVE_FAILED;
        if (player != null && plugin.msg() != null) {
            plugin.msg().send(player, "gathering_stopped",
                    java.util.Map.of("PLOT", plot.getPlotName()));
        }
        return StopResult.STOPPED;
    }

    public synchronized Boolean toggleGuestPass(Player player) {
        if (!loaded || !isEnabled()) return null;
        Plot plot = plotManagedBy(player);
        if (plot == null) return null;
        Gathering gathering = active(plot.getPlotId());
        if (gathering == null) return null;
        gathering.setGrantGuestPass(!gathering.grantGuestPass());
        store.markDirty();
        if (!store.save()) {
            gathering.setGrantGuestPass(!gathering.grantGuestPass());
            return null;
        }
        return gathering.grantGuestPass();
    }

    public synchronized void welcomeVisitor(Player player, Plot plot) {
        if (player == null || plot == null || !loaded || !isEnabled()) return;
        if (plot.isOwner(player.getUniqueId()) || plot.isBanned(player.getUniqueId())) return;
        String role = plot.getPlayerRoles().get(player.getUniqueId());
        if (role != null && !role.isBlank() && !role.equalsIgnoreCase("visitor")) return;
        Gathering gathering = active(plot.getPlotId());
        if (gathering == null || !gathering.grantGuestPass()) return;
        if (plugin.modules() != null && !plugin.modules().on(Modules.Id.GUEST_PASSES)) return;
        if (!plugin.getConfig().getBoolean("guest_passes.enabled", true)) return;
        // A manually issued pass belongs to its issuer and must never be replaced by Open House.
        if (plot.getActiveGuestPass(player.getUniqueId()) != null) return;
        if (plot.getActiveGuestPasses().size() >= plugin.guestPasses().maxActivePerPlot()) return;
        long issuedAt = System.currentTimeMillis();
        long remaining = gathering.endsAt() - issuedAt;
        if (remaining <= 0L) return;
        remaining = Math.min(remaining, TimeUnit.MINUTES.toMillis(plugin.guestPasses().maxDurationMinutes()));
        GuestPass pass = new GuestPass(
                player.getUniqueId(),
                player.getName(),
                GuestPassPreset.VISITOR,
                GuestPassPreset.VISITOR.getPermissions(),
                gathering.hostId(),
                gathering.hostName(),
                issuedAt,
                issuedAt + remaining
        );
        GuestPass previous = plot.getGuestPass(player.getUniqueId());
        plot.addGuestPass(pass);
        try {
            plugin.store().savePlotSync(plot);
            plugin.store().setDirty(true);
        } catch (RuntimeException error) {
            if (previous == null) plot.revokeGuestPass(player.getUniqueId());
            else plot.addGuestPass(previous);
            plugin.getLogger().warning("Could not save Open House visitor pass: " + error.getMessage());
            return;
        }
        gathering.recordIssued(player.getUniqueId(), pass.getIssuedAt());
        store.markDirty();
        if (!store.save()) {
            gathering.forgetIssued(player.getUniqueId());
            if (previous == null) plot.revokeGuestPass(player.getUniqueId());
            else plot.addGuestPass(previous);
            plugin.store().savePlotSync(plot);
            return;
        }
        if (plugin.msg() != null) {
            plugin.msg().send(player, "gathering_guest_pass",
                    java.util.Map.of("PLOT", plot.getPlotName()));
        }
    }

    public void tick() {
        if (!loaded) return;
        long now = System.currentTimeMillis();
        for (Gathering gathering : store.all()) {
            if (gathering == null) continue;
            Plot plot = plugin.store() == null ? null : plugin.store().getPlotById(gathering.plotId());
            if (plot != null && gathering.isLive(now) && hostHasRole(plot, gathering.hostId())) continue;
            if (plot == null) {
                expire(gathering);
                continue;
            }
            org.bukkit.World world = Bukkit.getWorld(plot.getWorldName());
            if (world == null) continue;
            org.bukkit.Location location = new org.bukkit.Location(world, plot.getX1(), 64, plot.getZ1());
            plugin.runAt(location, () -> {
                if (store.get(gathering.plotId()) == gathering
                        && (!gathering.isLive(System.currentTimeMillis()) || !hostHasRole(plot, gathering.hostId()))) {
                    expire(gathering);
                }
            });
        }
    }

    private synchronized boolean expire(Gathering gathering) {
        if (gathering == null) return true;
        if (store.get(gathering.plotId()) != gathering) return true;
        try {
            revokeIssuedPasses(gathering);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Could not revoke Open House passes: " + error.getMessage());
            return false;
        }
        store.remove(gathering.plotId());
        if (store.save()) return true;
        store.put(gathering);
        return false;
    }

    private void revokeIssuedPasses(Gathering gathering) {
        if (plugin.store() == null) return;
        Plot plot = plugin.store().getPlotById(gathering.plotId());
        if (plot == null) return;
        Map<UUID, GuestPass> removed = new HashMap<>();
        for (UUID id : gathering.issuedPasses().keySet()) {
            GuestPass pass = plot.getGuestPass(id);
            if (gathering.issuedByThisGathering(pass)) {
                plot.revokeGuestPass(id);
                removed.put(id, pass);
            }
        }
        if (!removed.isEmpty()) {
            try {
                plugin.store().savePlotSync(plot);
                plugin.store().setDirty(true);
            } catch (RuntimeException error) {
                removed.values().forEach(plot::addGuestPass);
                throw error;
            }
        }
    }

    private Plot plotManagedBy(Player player) {
        if (player == null || plugin.store() == null) return null;
        if (!player.hasPermission("aegis.gathering")) return null;
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !hostHasRole(plot, player.getUniqueId()) || !plot.canManage(player, plugin)) return null;
        return plot;
    }

    static boolean hostHasRole(Plot plot, UUID hostId) {
        if (plot == null || hostId == null || plot.isServerZone()) return false;
        if (plot.isOwner(hostId)) return true;
        String role = plot.getPlayerRoles().get(hostId);
        return "co_owner".equalsIgnoreCase(role) || "steward".equalsIgnoreCase(role);
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
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (host != null && online.getUniqueId().equals(host.getUniqueId())) continue;
            plugin.runMain(online, () -> {
                if (plugin.msg() != null) plugin.msg().send(online, "gathering_broadcast",
                        java.util.Map.of("PLOT", gathering.plotName(), "HOST", gathering.hostName(),
                                "MINUTES", String.valueOf(minutes)));
            });
        }
    }
}
