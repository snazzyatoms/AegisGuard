package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fired before a player's role on a plot is changed through the live mutation
 * path (trust commands, role GUIs, stewardship tools).
 *
 * <p>{@link #getNewRole()} is {@code null} when the role is being removed.
 * Cancelling prevents the change. Internal replays (role-history undo,
 * datastore hydration) do not fire this event.</p>
 */
public class PlotRoleChangeEvent extends AbstractPlotEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final UUID target;
    private final String oldRole;
    private final String newRole;
    private boolean cancelled;

    public PlotRoleChangeEvent(Plot plot, UUID target, @Nullable String oldRole, @Nullable String newRole) {
        super(plot);
        this.target = target;
        this.oldRole = oldRole;
        this.newRole = newRole;
    }

    /** The player whose role is changing. */
    public UUID getTarget() { return target; }

    /** Previous role, or {@code null} if the player had none. */
    @Nullable
    public String getOldRole() { return oldRole; }

    /** Incoming role, or {@code null} when the role is being removed. */
    @Nullable
    public String getNewRole() { return newRole; }

    public boolean isRemoval() { return newRole == null; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
