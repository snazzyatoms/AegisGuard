package com.aegisguard.claimblocks;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
        // IMPORTANT:
        // In non-Folia mode, your repeating scheduler currently runs ASYNC.
        // We must not touch Bukkit API (players, perms, sendMessage) off-thread.
        plugin.runMainGlobal(() -> {
            // Master switch
            if (!plugin.cfg().raw().getBoolean("claim_blocks.enabled", true)) return;

            // Configurable settings
            boolean enabled = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.enabled", true);
            if (!enabled) return;

            int amount = plugin.cfg().raw().getInt("claim_blocks.earn.playtime.blocks_per_interval", 50);
            if (amount <= 0) return;

            boolean notify = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.notify", false);

            boolean anyChanged = false;

            // Loop through all online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                // Optional: Add permission check (e.g. aegis.earn.blocks)
                if (!p.hasPermission("aegis.earn.blocks")) continue;

                plugin.getClaimBlockManager()
                        .getOrCreate(p.getUniqueId())
                        .addEarnedBlocks(amount);

                anyChanged = true;

                // Optional notification
                if (notify) {
                    long total = plugin.getClaimBlockManager().getTotalBlocks(p.getUniqueId());

                    String msg = tr(
                            p,
                            "claim_blocks_earned_playtime",
                            "&a+{AMOUNT} claim blocks &7(Total: &e{TOTAL}&7)",
                            Map.of(
                                    "AMOUNT", String.valueOf(amount),
                                    "TOTAL", String.valueOf(total)
                            )
                    );

                    p.sendMessage(color(msg));
                }
            }

            // Persist changes once per run (no disk spam)
            if (anyChanged) {
                plugin.getClaimBlockManager().saveAsync();
            }
        });
    }

    // --------------------------------------------------
    // Helpers
    // --------------------------------------------------

    private String tr(Player player, String key, String fallback, Map<String, String> placeholders) {
        String msg = fallback;
        try {
            if (plugin.codex() != null) {
                String value = plugin.codex().tr(player, key, placeholders);
                if (value != null && !value.trim().isEmpty()) {
                    msg = value;
                }
            }
        } catch (Throwable ignored) {
        }

        // Very small placeholder fallback (if Codex isn't used)
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                msg = msg.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return msg;
    }

    private String color(String msg) {
        if (msg == null) return "";
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
