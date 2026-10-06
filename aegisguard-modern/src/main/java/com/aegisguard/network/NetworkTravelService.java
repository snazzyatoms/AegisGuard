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

        // SQL write is async — the entity thread must not block on database
        // latency. The Connect goes out after the arrival row is committed,
        // ordered through the scheduler (proxy hops take far longer than this).
        plugin.scheduler().runAsync(() -> {
            net().store().writeArrival(arrival);
            plugin.scheduler().runEntity(player, () -> {
                if (!net().sendToServer(player, targetServer)) {
                    send(player, "visit_fail_server_offline", "&cCould not reach that server.");
                    return;
                }
                String label = serverLabel(targetServer);
                send(player, "network_sending_to_server",
                        "&7Sending you to &b" + label + "&7...");
                player.closeInventory();
            }, null);
        });
        return true;
    }

    /**
     * Plain server hop — hub command, servers picker, join redirect. Writes a
     * WORLD_SPAWN arrival so the destination lands the player at world spawn
     * deterministically instead of wherever they last stood there.
     */
    public boolean sendToServer(Player player, String targetServer) {
        if (player == null) return false;
        return sendToServer(player, targetServer, ArrivalKind.WORLD_SPAWN, null, null,
                0, 0, 0, player.getLocation().getYaw(), player.getLocation().getPitch());
    }

    /**
     * Full form — callers may pin a landing world/coords inside the arrival
     * (e.g. a configured hub point). Blank world lands at the destination's
     * default spawn.
     */
    private boolean sendToServer(Player player, String targetServer, ArrivalKind kind,
                                 UUID plotId, String world, double x, double y, double z,
                                 float yaw, float pitch) {
        if (!ready() || player == null || targetServer == null || targetServer.isBlank()) return false;
        if (!net().isServerOnline(targetServer)) {
            send(player, "visit_fail_server_offline", "&cThat server is currently offline.");
            return false;
        }
        long now = System.currentTimeMillis();
        long ttl = Math.max(15L, plugin.getConfig().getLong("network.arrival_ttl_seconds", 90L)) * 1000L;
        NetworkArrival arrival = new NetworkArrival(
                player.getUniqueId(), targetServer,
                kind != null ? kind : ArrivalKind.WORLD_SPAWN,
                plotId, null, world, x, y, z, yaw, pitch,
                now, now + ttl);
        plugin.scheduler().runAsync(() -> {
            net().store().writeArrival(arrival);
            plugin.scheduler().runEntity(player, () -> {
                if (!net().sendToServer(player, targetServer)) {
                    send(player, "visit_fail_server_offline", "&cCould not reach that server.");
                    return;
                }
                send(player, "network_sending_to_server",
                        "&7Sending you to &b" + serverLabel(targetServer) + "&7...");
                player.closeInventory();
            }, null);
        });
        return true;
    }

    /**
     * Hub hop: send the player to the configured {@code network.hub.server},
     * landing at the configured hub world/coords or that server's world spawn.
     * Returns false when networking is off, no hub is configured, or the player
     * is already on the hub — callers then fall back to local world spawn.
     */
    public boolean sendToHub(Player player) {
        if (!ready() || player == null) return false;
        String hub = plugin.getConfig().getString("network.hub.server", "");
        if (hub == null || hub.isBlank()) return false;
        if (hub.equalsIgnoreCase(net().serverName())) return false;
        String world = plugin.getConfig().getString("network.hub.world", "");
        if (world == null || world.isBlank()) world = null;
        return sendToServer(player, hub, ArrivalKind.WORLD_SPAWN, null, world,
                plugin.getConfig().getDouble("network.hub.x", 0.0),
                plugin.getConfig().getDouble("network.hub.y", 0.0),
                plugin.getConfig().getDouble("network.hub.z", 0.0), 0f, 0f);
    }

    /**
     * Remote pad hop: a pad on this backend linked to a pad owned by another
     * server. The shared row carries coords + owning plot so the destination
     * can land the player and re-check plot entry rules.
     */
    public boolean sendToPad(Player player, NetworkStore.RemoteBeacon pad) {
        if (!ready() || player == null || pad == null
                || pad.server() == null || pad.server().isBlank()) return false;
        if (!net().isServerOnline(pad.server())) {
            send(player, "visit_fail_server_offline", "&cThat server is currently offline.");
            return false;
        }
        long now = System.currentTimeMillis();
        long ttl = Math.max(15L, plugin.getConfig().getLong("network.arrival_ttl_seconds", 90L)) * 1000L;
        NetworkArrival arrival = new NetworkArrival(
                player.getUniqueId(), pad.server(), ArrivalKind.BEACON_PAD,
                pad.plotId(), pad.beaconId(), pad.world(),
                pad.x(), pad.y() + 1.0, pad.z(), pad.yaw(), pad.pitch(),
                now, now + ttl);
        plugin.scheduler().runAsync(() -> {
            net().store().writeArrival(arrival);
            plugin.scheduler().runEntity(player, () -> {
                if (!net().sendToServer(player, pad.server())) {
                    send(player, "visit_fail_server_offline", "&cCould not reach that server.");
                    return;
                }
                send(player, "network_sending_to_server",
                        "&7Sending you to &b" + serverLabel(pad.server()) + "&7...");
                player.closeInventory();
            }, null);
        });
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
            NetworkArrival arrival = net().store().consumeArrival(id, net().serverName());
            if (arrival == null || arrival.isExpiredAt(net().networkNow())) return;
            // A foreign-target arrival is left in the table for the correct
            // backend — nothing to do here.
            if (net().serverName() != null
                    && !net().serverName().equalsIgnoreCase(arrival.targetServer())) {
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

        if (arrival.kind() == ArrivalKind.WORLD_SPAWN) {
            landAtWorldSpawn(player, arrival);
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

    /**
     * WORLD_SPAWN arrival: land at this backend's designated hub point —
     * {@code network.hub.world/x/y/z} when configured, else the arrival's
     * world (or the server's main world) spawn. No plot needed.
     */
    private void landAtWorldSpawn(Player player, NetworkArrival arrival) {
        Location target = null;

        String hubWorld = plugin.getConfig().getString("network.hub.world", "");
        String hubServer = plugin.getConfig().getString("network.hub.server", "");
        boolean isHub = hubServer != null && net().serverName() != null
                && hubServer.equalsIgnoreCase(net().serverName());
        // y==0 means the coords were never configured — a hub point at bedrock
        // level is a config mistake, so treat unset/0,0,0 as "use world spawn".
        if (isHub && hubWorld != null && !hubWorld.isBlank()
                && plugin.getConfig().getDouble("network.hub.y", 0.0) != 0.0) {
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(hubWorld);
            if (w != null) {
                target = new Location(w,
                        plugin.getConfig().getDouble("network.hub.x"),
                        plugin.getConfig().getDouble("network.hub.y"),
                        plugin.getConfig().getDouble("network.hub.z"));
            }
        }

        if (target == null) {
            org.bukkit.World w = arrival.world() != null && !arrival.world().isBlank()
                    ? org.bukkit.Bukkit.getWorld(arrival.world())
                    : null;
            if (w == null && !org.bukkit.Bukkit.getWorlds().isEmpty()) {
                w = org.bukkit.Bukkit.getWorlds().get(0);
            }
            if (w != null) target = w.getSpawnLocation();
        }

        if (target == null) return; // no worlds loaded — vanilla spawn stands
        SafeTravelResult result = plugin.safeTravel().travel(player, target,
                com.aegisguard.travel.SafeTravelService.Kind.SPAWN);
        if (result.isSuccess() && plugin.effects() != null) {
            plugin.effects().playTeleport(player);
        }
    }

    private void landOnPad(Player player, NetworkArrival arrival) {
        BeaconService beacons = plugin.beacons();
        TeleportBeacon pad = beacons != null && arrival.beaconId() != null
                ? beacons.store().get(arrival.beaconId()) : null;
        if (pad == null || !pad.isEnabled()) {
            // Pad gone between link and landing — fall back to the pad coords
            // recorded in the arrival so the player still lands near it.
            if (arrival.world() != null) {
                org.bukkit.World w = org.bukkit.Bukkit.getWorld(arrival.world());
                if (w != null) {
                    plugin.safeTravel().travel(player,
                            new Location(w, arrival.x(), arrival.y(), arrival.z(),
                                    arrival.yaw(), arrival.pitch()),
                            com.aegisguard.travel.SafeTravelService.Kind.VISIT);
                    return;
                }
            }
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
