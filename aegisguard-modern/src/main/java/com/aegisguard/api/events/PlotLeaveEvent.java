package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/**
 * Fired when a player's movement carries them out of a plot they were inside.
 * Notification only - the player has already left by the time listeners run.
 */
public class PlotLeaveEvent extends AbstractPlayerPlotEvent {

    private static final HandlerList handlers = new HandlerList();

    public PlotLeaveEvent(Plot plot, Player player) {
        super(plot, player);
    }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
