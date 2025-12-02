package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlotLeaveEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Plot plot;
    private final Player player;

    public PlotLeaveEvent(Plot plot, Player player) {
        this.plot = plot;
        this.player = player;
    }

    public Plot getPlot() { return plot; }
    public Player getPlayer() { return player; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
