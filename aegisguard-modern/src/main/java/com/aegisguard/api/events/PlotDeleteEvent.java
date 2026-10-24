package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.event.HandlerList;

/**
 * Fired after a plot has been permanently removed from the data store (unclaim,
 * admin delete, upkeep expiry, banned-player purge, etc.). This is a
 * notification event - the plot is already gone and cannot be restored from
 * this event.
 */
public class PlotDeleteEvent extends AbstractPlotEvent {

    private static final HandlerList handlers = new HandlerList();

    public PlotDeleteEvent(Plot plot) {
        super(plot);
    }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
