package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class PlotLeaveEvent extends AbstractPlayerPlotEvent {

    private static final HandlerList handlers = new HandlerList();

    public PlotLeaveEvent(Plot plot, Player player) {
        super(plot, player);
    }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
