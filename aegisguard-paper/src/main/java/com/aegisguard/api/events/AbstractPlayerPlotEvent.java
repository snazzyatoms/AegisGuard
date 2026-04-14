package com.aegisguard.api.events;

import com.aegisguard.data.Plot;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Base event for plot events that also involve a player actor.
 */
public abstract class AbstractPlayerPlotEvent extends AbstractPlotEvent {

    private final Player player;

    protected AbstractPlayerPlotEvent(Plot plot, Player player) {
        super(plot);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public UUID getPlayerId() {
        return player.getUniqueId();
    }
}
