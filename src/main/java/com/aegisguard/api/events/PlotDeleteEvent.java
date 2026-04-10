package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.event.HandlerList;

public class PlotDeleteEvent extends AbstractPlotEvent {

    private static final HandlerList handlers = new HandlerList();

    public PlotDeleteEvent(Plot plot) {
        super(plot);
    }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
