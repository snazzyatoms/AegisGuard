package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlotLevelUpEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Plot plot;
    private final Player player;
    private final int newLevel;

    public PlotLevelUpEvent(Plot plot, Player player, int newLevel) {
        this.plot = plot;
        this.player = player;
        this.newLevel = newLevel;
    }

    public Plot getPlot() { return plot; }
    public Player getPlayer() { return player; }
    public int getNewLevel() { return newLevel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
