package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.EstateLevelUpEvent; // New Event
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LevelingListener implements Listener {

    private final AegisGuard plugin;

    public LevelingListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLevelUp(EstateLevelUpEvent event) {
        Player player = event.getPlayer();
        Estate estate = event.getEstate();
        int newLevel = event.getNewLevel();

        // 1. Broadcast (if enabled)
        if (plugin.getConfig().getBoolean("progression.notifications.broadcast_max_level", true)) {
            int max = plugin.getConfig().getInt("progression.max_level", 30);
            if (newLevel == max) {
                Bukkit.broadcastMessage("§6§l[Aegis] §e" + estate.getName() + " §7has reached MAX LEVEL!");
            }
        }

        // 2. Play Effects
        if (plugin.getConfig().getBoolean("progression.notifications.play_effects", true)) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            // plugin.getEffects().playLevelUp(player); // If you added this method
        }
        
        // 3. Apply Rewards (Handled by ProgressionManager actively, but we can log here)
        plugin.getLogger().info("Estate " + estate.getName() + " leveled up to " + newLevel);
    }
}
