package com.aegisguard.chat;

import com.aegisguard.AegisGuard;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Iterator;
import java.util.Set;

/**
 * Filters public chat recipients for Hearth rooms. Frequency already cancelled
 * the event when the speaker is tuned, so this only touches world chat.
 */
public final class HearthListener implements Listener {

    private final AegisGuard plugin;

    public HearthListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        HearthService hearth = plugin.hearth();
        PlotChatService frequency = plugin.plotChat();
        if (hearth == null) return;
        Player speaker = event.getPlayer();
        if (frequency != null && frequency.interceptPublicChat(speaker)) return;
        if (hearth.isSpy(speaker)) return;

        HearthService.Room speakerRoom = hearth.roomOf(speaker);
        Set<Player> recipients = event.getRecipients();
        if (recipients == null || recipients.isEmpty()) return;

        Iterator<Player> iterator = recipients.iterator();
        while (iterator.hasNext()) {
            Player listener = iterator.next();
            if (listener == null) continue;
            if (listener.getUniqueId().equals(speaker.getUniqueId())) continue;
            HearthService.Room listenerRoom = hearth.roomOf(listener);
            if (!HearthService.canHear(speakerRoom, listenerRoom, hearth.isSpy(listener))) {
                iterator.remove();
            }
        }
    }
}
