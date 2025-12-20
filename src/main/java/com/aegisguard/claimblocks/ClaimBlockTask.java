package com.aegisguard.claimblocks;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

public class ClaimBlockTask implements Runnable {

    private final AegisGuard plugin;

    public ClaimBlockTask(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Safety: if someone scheduled this async by accident, hop back to main thread
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this);
            return;
        }

        if (plugin.getClaimBlockManager() == null) return;

        boolean enabled = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.enabled", true);
        if (!enabled) return;

        int amount = plugin.cfg().raw().getInt("claim_blocks.earn.playtime.blocks_per_interval", 50);
        boolean notify = plugin.cfg().raw().getBoolean("claim_blocks.earn.playtime.notify", false);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("aegis.earn.blocks")) continue;

            UUID uuid = p.getUniqueId();

            // 1) Award blocks
            plugin.getClaimBlockManager().addEarned(uuid, amount);

            // 2) Notify (localized + colored)
            if (notify) {
                long total = plugin.getClaimBlockManager().getTotalBlocks(uuid);

                String msg = plugin.codex().tr(p,
                        "claim_blocks_earned_playtime",
                        Map.of(
                                "AMOUNT", String.valueOf(amount),
                                "TOTAL", String.valueOf(total)
                        )
                );

                // Fallback in case the key is missing in some language pack
                if (msg == null || msg.isBlank()) {
                    msg = "&8[&b⛨&8] &aClaim Blocks &8» &a+&e{AMOUNT} &7for playing &8| &fTotal: &e{TOTAL}&r";
                    msg = msg.replace("{AMOUNT}", String.valueOf(amount))
                             .replace("{TOTAL}", String.valueOf(total));
                }

                // ✅ This is the big fix: translate & color codes properly
                p.sendMessage(GUIManager.color(msg));
            }
        }
    }
}
