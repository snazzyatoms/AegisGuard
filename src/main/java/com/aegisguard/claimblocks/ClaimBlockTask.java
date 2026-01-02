package com.aegisguard.claimblocks;

import com.aegisguard.AegisGuard;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.Map;

public class ClaimBlockTask implements Runnable {

    private final AegisGuard plugin;

    public ClaimBlockTask(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Master toggle + playtime toggle
        boolean claimBlocksEnabled = plugin.cfg().raw().getBoolean("claim_blocks.enabled", true);
        if (!claimBlocksEnabled) return;

        boolean enabled = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.enabled", true);
        if (!enabled) return;

        long amount = plugin.cfg().raw().getLong("claim_blocks.earn.playtime.blocks_per_interval", 50L);
        if (amount <= 0) return;

        boolean notify = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.notify", false);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("aegis.earn.blocks")) continue;

            plugin.getClaimBlockManager().addEarned(p.getUniqueId(), amount);

            if (notify) {
                long total = plugin.getClaimBlockManager().getTotalBlocks(p.getUniqueId());

                String msg = plugin.codex().tr(p, "claim_blocks_earned_playtime",
                        Map.of(
                                "AMOUNT", String.valueOf(amount),
                                "TOTAL", String.valueOf(total)
                        )
                );

                msg = ChatColor.translateAlternateColorCodes('&', msg);
                p.spigot().sendMessage(TextComponent.fromLegacyText(msg));
            }
        }
    }
}
