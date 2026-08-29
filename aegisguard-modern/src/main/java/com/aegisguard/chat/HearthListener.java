package com.aegisguard.chat;

import com.aegisguard.AegisGuard;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Iterator;
import java.util.Set;

/**
 * Filters public chat recipients for Hearth rooms. Frequency already cancelled
 * the event when the speaker is tuned, so this only touches world chat.
 *
 * Room membership is cached on the player's Folia region thread (join / move /
 * teleport / world change). Chat then reads that cache so
 * {@code AsyncPlayerChatEvent} never calls {@code Player#getLocation()}.
 */
public final class HearthListener implements Listener {

    private final AegisGuard plugin;

    public HearthListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        HearthService hearth = plugin.hearth();
        if (hearth != null) hearth.updatePresence(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        HearthService hearth = plugin.hearth();
        if (hearth != null) hearth.forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || sameBlock(from, to)) return;
        HearthService hearth = plugin.hearth();
        if (hearth != null) hearth.updatePresenceAt(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        HearthService hearth = plugin.hearth();
        if (hearth != null) hearth.updatePresenceAt(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        HearthService hearth = plugin.hearth();
        if (hearth != null) hearth.updatePresence(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        HearthService hearth = plugin.hearth();
        PlotChatService frequency = plugin.plotChat();
        if (hearth == null) return;
        Player speaker = event.getPlayer();
        if (frequency != null && frequency.interceptPublicChat(speaker)) return;
        if (hearth.isSpy(speaker)) return;

        HearthService.Room speakerRoom = hearth.cachedRoom(speaker);
        Set<Player> recipients = event.getRecipients();
        if (recipients == null || recipients.isEmpty()) return;

        Iterator<Player> iterator = recipients.iterator();
        while (iterator.hasNext()) {
            Player listener = iterator.next();
            if (listener == null) continue;
            if (listener.getUniqueId().equals(speaker.getUniqueId())) continue;
            HearthService.Room listenerRoom = hearth.cachedRoom(listener);
            if (!HearthService.canHear(speakerRoom, listenerRoom, hearth.isSpy(listener))) {
                iterator.remove();
            }
        }
    }

    private static boolean sameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() != null
                && from.getWorld().equals(to.getWorld());
    }
}
