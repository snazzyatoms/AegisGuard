package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.event.Event;

import java.util.UUID;

/**
 * Base event for plot-related API events.
 */
public abstract class AbstractPlotEvent extends Event {

    private final Plot plot;

    protected AbstractPlotEvent(Plot plot) {
        this.plot = plot;
    }

    public Plot getPlot() {
        return plot;
    }

    public UUID getPlotId() {
        return plot.getPlotId();
    }

    public UUID getOwnerId() {
        return plot.getOwner();
    }

    public String getWorldName() {
        return plot.getWorldName();
    }
}
