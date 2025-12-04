package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import com.gmail.nossr50.api.ExperienceAPI;
import com.gmail.nossr50.datatypes.skills.PrimarySkillType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class McMMOHook {

    private final AegisGuard plugin;
    private boolean enabled = false;

    public McMMOHook(AegisGuard plugin) {
        this.plugin = plugin;
        if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")) {
            this.enabled = true;
            plugin.getLogger().info("Hooked into mcMMO!");
        }
    }

    public void giveClaimExp(Player player) {
        if (!enabled) return;
        
        // Give some "Woodcutting" or "Taming" XP for claiming land
        // You can make the skill type configurable later
        try {
            ExperienceAPI.addXP(player, "WOODCUTTING", 500); 
            // player.sendMessage("§e+500 Woodcutting XP for claiming land!");
        } catch (Exception e) {
            // mcMMO not ready or player offline
        }
    }
}
