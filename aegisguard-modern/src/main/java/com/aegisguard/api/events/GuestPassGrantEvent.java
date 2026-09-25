package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import com.aegisguard.guestpass.GuestPass;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired before a {@link GuestPass} is granted on a plot. Cancelling prevents
 * the pass from being added.
 */
public class GuestPassGrantEvent extends AbstractPlotEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final GuestPass pass;
    private boolean cancelled;

    public GuestPassGrantEvent(Plot plot, GuestPass pass) {
        super(plot);
        this.pass = pass;
    }

    public GuestPass getPass() { return pass; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
