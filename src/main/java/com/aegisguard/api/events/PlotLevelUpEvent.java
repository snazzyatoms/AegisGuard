package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class PlotLevelUpEvent extends AbstractPlayerPlotEvent {

    private static final HandlerList handlers = new HandlerList();
    private final int newLevel;

    public PlotLevelUpEvent(Plot plot, Player player, int newLevel) {
        super(plot, player);
        this.newLevel = newLevel;
    }

    public int getNewLevel() { return newLevel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
