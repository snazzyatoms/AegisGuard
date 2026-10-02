package com.aegisguard.network;

import java.util.UUID;

/**
 * Immutable data carriers for the BungeeCord/proxy network layer.
 * All state lives in the shared SQL database; these are read views.
 */
public final class NetworkModels {

    private NetworkModels() { }

    /** A backend server registered in aegis_network_servers. */
    public record NetworkServer(
            String serverName,
            String displayName,
            String pluginVersion,
            int onlinePlayers,
            long lastSeen) {

        public String label() {
            return displayName != null && !displayName.isBlank() ? displayName : serverName;
        }

        public boolean isOnline(long offlineAfterMs) {
            return System.currentTimeMillis() - lastSeen <= Math.max(1L, offlineAfterMs);
        }
    }

    /** A pending cross-server arrival written before a proxy hop. */
    public record NetworkArrival(
            UUID playerUuid,
            String targetServer,
            ArrivalKind kind,
            UUID plotId,
            UUID beaconId,
            String world,
            double x, double y, double z,
            float yaw, float pitch,
            long issuedAt,
            long expiresAt) {

        public enum ArrivalKind {
            PLOT_SPAWN,
            BEACON_PAD,
            SERVER_WARP
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /** One row on the aegis_network_events bus (chat relay etc.). */
    public record NetworkEvent(
            long id,
            String originServer,
            String kind,
            String payload,
            long createdAt) { }
}
