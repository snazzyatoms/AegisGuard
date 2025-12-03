package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class MigrationListener implements Listener {

    private final AegisGuard plugin;
    private final NamespacedKey migrationKey;

    public MigrationListener(AegisGuard plugin) {
        this.plugin = plugin;
        // This key acts like a "Stamp" on the player's file
        // It ensures they only see the message ONCE.
        this.migrationKey = new NamespacedKey(plugin, "migrated_v1_3_0");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Check if they already saw the message
        // If they have the stamp, stop here.
        if (player.getPersistentDataContainer().has(migrationKey, PersistentDataType.BYTE)) {
            return; 
        }

        // 2. Check if they actually own land
        // We iterate through estates to see if they are an owner.
        // (We don't want to spam new players who never had a plot to begin with)
        boolean isOwner = false;
        for (Estate estate : plugin.getEstateManager().getAllEstates()) {
            if (estate.getOwnerId().equals(player.getUniqueId())) {
                isOwner = true;
                break;
            }
        }

        if (isOwner) {
            // 3. Send the "Fancy" Notification
            LanguageManager lang = plugin.getLanguageManager();
            
            // A. Big Title on Screen (e.g., "STATUS UPGRADE")
            player.sendTitle(
                lang.getMsg(player, "migration_title"), 
                lang.getMsg(player, "migration_subtitle"), 
                20, 100, 20
            );

            // B. Chat Message (The Multi-line Explanation)
            List<String> chatLines = lang.getMsgList(player, "migration_chat");
            for (String line : chatLines) {
                player.sendMessage(line);
            }

            // C. Play a Sound (e.g., Challenge Complete)
            try {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            } catch (Exception e) {
                // Fallback for older versions
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
        }

        // 4. "Stamp" the player so they never see it again
        // Even if they didn't own land, we mark them so we don't check again next login.
        player.getPersistentDataContainer().set(migrationKey, PersistentDataType.BYTE, (byte) 1);
    }
}
