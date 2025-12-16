package com.aegisguard.claimblocks;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public class ClaimBlockTask implements Runnable {

    private final AegisGuard plugin;

    public ClaimBlockTask(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        boolean enabled = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.enabled", true);
        if (!enabled) return;

        int amount = plugin.cfg().raw().getInt("claim_blocks.earn.playtime.blocks_per_interval", 50);
        boolean notify = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.notify", false);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("aegis.earn.blocks")) continue;

            plugin.getClaimBlockManager().addEarned(p.getUniqueId(), amount);

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
