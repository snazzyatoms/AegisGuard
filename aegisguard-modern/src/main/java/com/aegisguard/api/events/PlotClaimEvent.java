package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired while a new plot is being confirmed, before it is registered with the
 * data store. Cancelling aborts the claim entirely; the player is told why via
 * the standard deny messages and any charge made by the claiming path is
 * refunded by the caller.
 */
public class PlotClaimEvent extends AbstractCancellablePlayerPlotEvent {

    private static final HandlerList handlers = new HandlerList();

    public PlotClaimEvent(Plot plot, Player player) {
        super(plot, player);
    }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
