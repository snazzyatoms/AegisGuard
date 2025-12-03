package com.aegisguard.api.events;

import com.aegisguard.objects.Estate; // Make sure this points to your new Estate object
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player (or Guild) attempts to claim a new Estate.
 * Can be cancelled to prevent the claim.
 */
public class EstateClaimEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final Estate estate;
    private final Player player;
    private boolean cancelled;

    public EstateClaimEvent(Estate estate, Player player) {
        this.estate = estate;
        this.player = player;
    }

    public Estate getEstate() { return estate; }
    public Player getPlayer() { return player; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
