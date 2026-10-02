package com.aegisguard.network;

import com.aegisguard.AegisGuard;
import com.aegisguard.beacon.BeaconService;
import com.aegisguard.beacon.TeleportBeacon;
import com.aegisguard.data.Plot;
import com.aegisguard.network.NetworkModels.NetworkArrival;
import com.aegisguard.network.NetworkModels.NetworkArrival.ArrivalKind;
import com.aegisguard.travel.SafeTravelResult;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Cross-server travel. The sender writes a pending arrival row and asks the
 * proxy to Connect the player to the target backend; that backend consumes the
 * row on join and lands the player honoring the destination's arrival rules
 * (classic → safe travel to spawn; beacon-arrival → public pad).
 *
 * <p>Everything is DB-backed so it works with zero players online on the
 * target; plugin messages only carry the player.</p>
 */
public final class NetworkTravelService {

    private final AegisGuard plugin;

    public NetworkTravelService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    private NetworkService net() {
        return plugin.network();
    }

    private boolean ready() {
        NetworkService n = net();
        return n != null && n.isNetworked()
                && plugin.getConfig().getBoolean("network.travel.cross_server_travel", true);
    }

    // ------------------------------------------------------------------
    // Send side
    // ------------------------------------------------------------------

    /**
     * Hop the player to {@code targetServer} to land at {@code plot}'s arrival
     * point. Returns true when the proxy hop was requested.
     */
    public boolean sendToPlot(Player player, Plot plot, String targetServer) {
        if (!ready() || player == null || plot == null || targetServer == null) return false;
        if (!net().isServerOnline(targetServer)) {
            send(player, "visit_fail_server_offline", "&cThat server is currently offline.");
            return false;
        }

        long now = System.currentTimeMillis();
        long ttl = Math.max(15L, plugin.getConfig().getLong("network.arrival_ttl_seconds", 90L)) * 1000L;
        Location dest = plot.getSpawnLocation() != null
                ? plot.getSpawnLocation()
                : safeCenter(plot);
        NetworkArrival arrival = new NetworkArrival(
                player.getUniqueId(),
                targetServer,
                plot.isServerWarp() ? ArrivalKind.SERVER_WARP : ArrivalKind.PLOT_SPAWN,
                plot.getPlotId(),
                null,
                dest != null && dest.getWorld() != null ? dest.getWorld().getName() : plot.getWorld(),
                dest != null ? dest.getX() : 0,
                dest != null ? dest.getY() : 0,
                dest != null ? dest.getZ() : 0,
                player.getLocation().getYaw(),
                player.getLocation().getPitch(),
                now, now + ttl);

        net().store().writeArrival(arrival);
        if (!net().sendToServer(player, targetServer)) {
            send(player, "visit_fail_server_offline", "&cCould not reach that server.");
            return false;
        }
        String label = serverLabel(targetServer);
        send(player, "network_sending_to_server",
                "&7Sending you to &b" + label + "&7...");
        player.closeInventory();
        return true;
    }

    /** Hop the player to another backend's arrival pad directly. */
    public boolean sendToBeacon(Player player, TeleportBeacon beacon, Plot plot, String targetServer) {
        if (!ready() || player == null || beacon == null || targetServer == null) return false;
        if (!net().isServerOnline(targetServer)) {
            send(player, "visit_fail_server_offline", "&cThat server is currently offline.");
            return false;
        }
        long now = System.currentTimeMillis();
        long ttl = Math.max(15L, plugin.getConfig().getLong("network.arrival_ttl_seconds", 90L)) * 1000L;
        NetworkArrival arrival = new NetworkArrival(
                player.getUniqueId(), targetServer, ArrivalKind.BEACON_PAD,
                plot != null ? plot.getPlotId() : null,
                beacon.getId(), beacon.getWorldName(),
                beacon.getX() + 0.5, beacon.getY() + 1.0, beacon.getZ() + 0.5,
                (float) beacon.getYaw(), (float) beacon.getPitch(),
                now, now + ttl);
        net().store().writeArrival(arrival);
        if (!net().sendToServer(player, targetServer)) {
            send(player, "visit_fail_server_offline", "&cCould not reach that server.");
            return false;
        }
        send(player, "network_sending_to_server",
                "&7Sending you to &b" + serverLabel(targetServer) + "&7...");
        player.closeInventory();
        return true;
    }

    /** Plain server hop with no plot destination (Atlas "Servers" scope). */
    public boolean sendToServer(Player player, String targetServer) {
        if (!ready() || player == null || targetServer == null) return false;
        if (!net().isServerOnline(targetServer)) {
            send(player, "visit_fail_server_offline", "&cThat server is currently offline.");
            return false;
        }
        if (!net().sendToServer(player, targetServer)) return false;
        send(player, "network_sending_to_server",
                "&7Sending you to &b" + serverLabel(targetServer) + "&7...");
        return true;
    }

    // ------------------------------------------------------------------
    // Land side — consume pending arrival on join
    // ------------------------------------------------------------------

    /**
     * Called from the join listener (already on an entity-safe task). Reads and
     * deletes the player's pending arrival and routes them to the destination.
     */
    public void handleJoin(Player player) {
        if (!ready() || player == null) return;
        UUID id = player.getUniqueId();
        plugin.scheduler().runAsync(() -> {
            NetworkArrival arrival = net().store().consumeArrival(id);
            if (arrival == null || arrival.isExpired()) return;
            // Landed on the wrong backend? Don't consume someone else's arrival —
            // requeue it for the intended server.
            if (net().serverName() != null
                    && !net().serverName().equalsIgnoreCase(arrival.targetServer())) {
                net().store().writeArrival(arrival);
                return;
            }
            plugin.scheduler().runEntity(player, () -> land(player, arrival), null);
        });
    }

    private void land(Player player, NetworkArrival arrival) {
        if (player == null || !player.isOnline() || arrival == null) return;

        if (arrival.kind() == ArrivalKind.BEACON_PAD) {
            landOnPad(player, arrival);
            return;
        }

        Plot plot = arrival.plotId() != null ? plugin.store().getPlotById(arrival.plotId()) : null;
        if (plot == null) {
            send(player, "network_arrival_gone", "&cThat destination no longer exists.");
            return;
        }

        // Same rules as local travel: lockdown + entry checks, then the plot's
        // chosen arrival mode (beacon pad vs classic spawn).
        if (plot.isLockdownActive() && !plugin.isAdmin(player) && !plot.canManage(player, plugin)) {
            send(player, "beacon_lockdown", "&cThat plot is in lockdown.");
            return;
        }
        if (plugin.protection() != null && !plugin.protection().canEnterPlot(player, plot)) {
            send(player, "beacon_cannot_enter", "&cYou cannot enter that plot.");
            return;
        }

        BeaconService beacons = plugin.beacons();
        if (beacons != null && beacons.isEnabled()
                && beacons.requiresBeaconArrival(player, plot)) {
            if (beacons.handlePublicListingTravel(player, plot, TeleportBeacon.Purpose.SPAWN)) {
                return;
            }
        }

        Location target = plot.getSpawnLocation() != null ? plot.getSpawnLocation() : safeCenter(plot);
        if (target == null || target.getWorld() == null) {
            send(player, "visit_fail_no_spawn", "&cThis destination has no valid spawn set.");
            return;
        }
        SafeTravelResult result = plugin.safeTravel().travel(player, target,
                com.aegisguard.travel.SafeTravelService.Kind.VISIT);
        if (result.isSuccess()) {
            send(player, "visit_teleport_success", "&aTeleported.");
            if (plugin.effects() != null) plugin.effects().playTeleport(player);
        }
    }

    private void landOnPad(Player player, NetworkArrival arrival) {
        BeaconService beacons = plugin.beacons();
        TeleportBeacon pad = beacons != null && arrival.beaconId() != null
                ? beacons.store().get(arrival.beaconId()) : null;
        if (pad == null || !pad.isEnabled()) {
            send(player, "beacon_pad_gone", "&cThe destination pad is missing or broken.");
            return;
        }
        Plot plot = arrival.plotId() != null ? plugin.store().getPlotById(arrival.plotId()) : null;
        if (plot != null && plugin.protection() != null && !plugin.protection().canEnterPlot(player, plot)) {
            send(player, "beacon_cannot_enter", "&cYou cannot enter that plot.");
            return;
        }
        beacons.executeTrip(player, null, pad, true);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Location safeCenter(Plot plot) {
        try {
            return plot.getCenter(plugin);
        } catch (Throwable t) {
            return null;
        }
    }

    private String serverLabel(String targetServer) {
        var srv = net().findServer(targetServer);
        return srv != null ? srv.label() : targetServer;
    }

    private void send(Player player, String key, String fallback) {
        try {
            String msg = plugin.gui() != null
                    ? plugin.gui().tr(player, key, fallback)
                    : fallback;
            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
        } catch (Throwable t) {
            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', fallback));
        }
    }
}
