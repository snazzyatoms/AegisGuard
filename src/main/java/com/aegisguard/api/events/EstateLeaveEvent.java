package com.aegisguard.api.events;

import com.aegisguard.objects.Estate; // Updated Import
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player exits an Estate boundary.
 * This event is not cancellable because movement has usually already happened.
 */
public class EstateLeaveEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Estate estate;
    private final Player player;

    public EstateLeaveEvent(Estate estate, Player player) {
        this.estate = estate;
        this.player = player;
    }

    /**
     * Get the Estate being left.
     */
    public Estate getEstate() { return estate; }
    
    public Player getPlayer() { return player; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
