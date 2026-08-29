package com.aegisguard.chat;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hearth: location-scoped public chat. The "closed door" is a marked room
 * (3D subplot/zone), not a scanned door block.
 *
 * When a plot's {@code hearth} flag is on:
 * <ul>
 *   <li>a zone is its own room</li>
 *   <li>the rest of that plot is one open-air room</li>
 *   <li>rooms are two-way isolated from each other and from the street</li>
 * </ul>
 * Staff with {@code aegis.admin.hearth} hear every room and are not muffled
 * when they speak. Aegis Frequency still owns opt-in plot-member chat.
 */
public final class HearthService {

    public static final String FLAG = "hearth";
    public static final String SPY_PERMISSION = "aegis.admin.hearth";

    private final AegisGuard plugin;
    private final Map<UUID, Room> lastRoom = new ConcurrentHashMap<>();

    public HearthService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled(Plot plot) {
        return plot != null && plot.getFlag(FLAG, false);
    }

    public Room roomAt(Location location) {
        if (location == null || plugin.store() == null) return null;
        Plot plot = plugin.store().getPlotAt(location);
        if (!isEnabled(plot)) return null;
        Zone zone = plot.getZoneAt(location);
        return roomOf(plot, zone == null ? null : zone.getName());
    }

    public Room roomOf(Player player) {
        return player == null ? null : roomAt(player.getLocation());
    }

    /**
     * Last room captured on the player's region thread. Safe to read from
     * {@code AsyncPlayerChatEvent} without touching Bukkit location APIs.
     */
    public Room cachedRoom(Player player) {
        return player == null ? null : lastRoom.get(player.getUniqueId());
    }

    /** Call only on the player's region / entity thread. */
    public Room updatePresence(Player player) {
        return player == null ? null : updatePresenceAt(player, player.getLocation());
    }

    /** Call only on the region thread that owns {@code location}. */
    public Room updatePresenceAt(Player player, Location location) {
        if (player == null) return null;
        Room next = roomAt(location);
        if (next == null) lastRoom.remove(player.getUniqueId());
        else lastRoom.put(player.getUniqueId(), next);
        return next;
    }

    public void forget(Player player) {
        if (player != null) lastRoom.remove(player.getUniqueId());
    }

    public boolean isSpy(Player player) {
        if (player == null) return false;
        return player.hasPermission(SPY_PERMISSION) || plugin.isAdmin(player);
    }

    /**
     * @param speakerRoom  null means the speaker is in open/public space
     * @param listenerRoom null means the listener is in open/public space
     * @param listenerIsSpy staff always hear sealed rooms
     */
    public static boolean canHear(Room speakerRoom, Room listenerRoom, boolean listenerIsSpy) {
        if (listenerIsSpy) return true;
        if (speakerRoom == null && listenerRoom == null) return true;
        if (speakerRoom == null || listenerRoom == null) return false;
        return speakerRoom.equals(listenerRoom);
    }

    public static Room roomOf(Plot plot, String zoneName) {
        if (plot == null || !plot.getFlag(FLAG, false)) return null;
        String zone = zoneName == null || zoneName.isBlank() ? "" : zoneName.trim().toLowerCase(Locale.ROOT);
        return new Room(plot.getPlotId(), zone);
    }

    public record Room(UUID plotId, String zoneName) {
        public Room {
            plotId = Objects.requireNonNull(plotId, "plotId");
            zoneName = zoneName == null ? "" : zoneName;
        }

        public boolean isZone() {
            return !zoneName.isBlank();
        }
    }
}
