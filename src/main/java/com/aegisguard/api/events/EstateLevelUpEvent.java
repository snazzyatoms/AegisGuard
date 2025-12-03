package com.aegisguard.api.events;

import com.aegisguard.objects.Estate; // Updated Import
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when an Estate (Private or Guild) increases its level.
 * Useful for other plugins to trigger effects or announcements.
 */
public class EstateLevelUpEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final Estate estate;
    private final Player player; // The player who triggered the upgrade
    private final int newLevel;

    public EstateLevelUpEvent(Estate estate, Player player, int newLevel) {
        this.estate = estate;
        this.player = player;
        this.newLevel = newLevel;
    }

    /**
     * Get the Estate that leveled up.
     */
    public Estate getEstate() { return estate; }
    
    /**
     * Get the player who purchased/triggered the level up.
     */
    public Player getPlayer() { return player; }
    
    /**
     * Get the level reached.
     */
    public int getNewLevel() { return newLevel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
