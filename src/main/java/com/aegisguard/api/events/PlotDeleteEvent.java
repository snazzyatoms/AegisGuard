package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlotDeleteEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Plot plot;

    public PlotDeleteEvent(Plot plot) {
        this.plot = plot;
    }

    public Plot getPlot() { return plot; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
