package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * Base class for AegisGuard events that involve a player acting on (or entering)
 * a plot and may be vetoed by listeners.
 * <p>
 * Cancelling the event prevents the underlying action: for
 * {@link PlotClaimEvent} the claim is aborted, for {@link PlotEnterEvent} the
 * movement that would cross the boundary is cancelled so the player never
 * enters the plot.
 */
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
