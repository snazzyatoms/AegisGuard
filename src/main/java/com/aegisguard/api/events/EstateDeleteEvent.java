package com.aegisguard.api.events;

import com.aegisguard.objects.Estate;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when an Estate is deleted (unclaimed).
 * This event is not cancellable because it usually happens *after* the deletion logic starts.
 */
public class EstateDeleteEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Estate estate;

    public EstateDeleteEvent(Estate estate) {
        this.estate = estate;
    }

    public Estate getEstate() { return estate; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
