package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import com.aegisguard.guestpass.GuestPass;
import org.bukkit.event.HandlerList;

/**
 * Fired after a {@link GuestPass} has been revoked from a plot. Not
 * cancellable — the pass is already removed; use {@link GuestPassGrantEvent}
 * to gate issuance.
 */
public class GuestPassRevokeEvent extends AbstractPlotEvent {

    private static final HandlerList handlers = new HandlerList();

    private final GuestPass pass;

    public GuestPassRevokeEvent(Plot plot, GuestPass pass) {
        super(plot);
        this.pass = pass;
    }

    public GuestPass getPass() { return pass; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
