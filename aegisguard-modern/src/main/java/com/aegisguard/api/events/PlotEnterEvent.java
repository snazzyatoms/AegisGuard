package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player's movement would carry them across a plot boundary into
 * the plot. Cancelling cancels the underlying {@code PlayerMoveEvent}, so the
 * player is held at the boundary rather than merely being denied entry effects.
 */
public class PlotEnterEvent extends AbstractCancellablePlayerPlotEvent {

    private static final HandlerList handlers = new HandlerList();

    public PlotEnterEvent(Plot plot, Player player) {
        super(plot, player);
    }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
