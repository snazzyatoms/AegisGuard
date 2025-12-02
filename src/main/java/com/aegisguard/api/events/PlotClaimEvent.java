package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlotClaimEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final Plot plot;
    private final Player player;
    private boolean cancelled;

    public PlotClaimEvent(Plot plot, Player player) {
        this.plot = plot;
        this.player = player;
    }

    public Plot getPlot() { return plot; }
    public Player getPlayer() { return player; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
