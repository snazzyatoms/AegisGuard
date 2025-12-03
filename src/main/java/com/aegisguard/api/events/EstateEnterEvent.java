package com.aegisguard.api.events;

import com.aegisguard.objects.Estate; // Updated Import
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class EstateEnterEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final Estate estate;
    private final Player player;
    private boolean cancelled;

    public EstateEnterEvent(Estate estate, Player player) {
        this.estate = estate;
        this.player = player;
    }

    /**
     * Get the Estate being entered.
     */
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
