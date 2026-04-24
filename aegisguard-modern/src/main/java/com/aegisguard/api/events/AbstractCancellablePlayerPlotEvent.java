package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

public abstract class AbstractCancellablePlayerPlotEvent extends AbstractPlayerPlotEvent implements Cancellable {

    private boolean cancelled;

    protected AbstractCancellablePlayerPlotEvent(Plot plot, Player player) {
        super(plot, player);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
