package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * Fired before a plot flag is changed through the live mutation path.
 *
 * <p>Cancelling prevents the change; {@link #setNewValue(boolean)} lets
 * listeners override the value that will be applied. Initial flag seeding
 * (claim creation defaults, datastore hydration, snapshot deserialization)
 * does not fire this event.</p>
 */
public class PlotFlagChangeEvent extends AbstractPlotEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final String flag;
    private final boolean oldValue;
    private boolean newValue;
    private boolean cancelled;

    public PlotFlagChangeEvent(Plot plot, String flag, boolean oldValue, boolean newValue) {
        super(plot);
        this.flag = flag;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    /** Lower-cased flag key, e.g. {@code "pvp"}. */
    public String getFlag() { return flag; }

    public boolean getOldValue() { return oldValue; }

    public boolean getNewValue() { return newValue; }

    public void setNewValue(boolean newValue) { this.newValue = newValue; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
