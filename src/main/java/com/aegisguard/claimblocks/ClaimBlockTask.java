package com.aegisguard.claimblocks;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * ClaimBlockTask
 * - Rewards online players with claim blocks periodically.
 * - Anti-AFK can be added here later.
 */
public class ClaimBlockTask implements Runnable {

    private final AegisGuard plugin;

    public ClaimBlockTask(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Configurable settings
        boolean enabled = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.enabled", true);
        if (!enabled) return;

        int amount = plugin.cfg().raw().getInt("claim_blocks.earn.playtime.blocks_per_interval", 50);
        
        // Loop through all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Optional: Add permission check (e.g. aegis.earn.blocks)
            if (p.hasPermission("aegis.earn.blocks")) {
                plugin.getClaimBlockManager().getOrCreate(p.getUniqueId()).addEarnedBlocks(amount);
                
                // Optional: Send a subtle action bar or message?
                // For now, let's stay silent to avoid spam, or check config.
                boolean notify = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.notify", false);
                if (notify) {
                    long total = plugin.getClaimBlockManager().getTotalBlocks(p.getUniqueId());
                    String msg = plugin.codex().tr(p, "claim_blocks_earned_playtime", 
                        Map.of("AMOUNT", String.valueOf(amount), "TOTAL", String.valueOf(total))
                    );
                    p.sendMessage(msg);
                }
            }
        }
    }
}
