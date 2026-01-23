package com.aegisguard.selection;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockData;
import com.aegisguard.data.Plot;
import com.aegisguard.events.PlotClaimEvent;
import com.aegisguard.hooks.protection.ProtectionHookManager;
import com.aegisguard.util.MessagesUtil;
import com.aegisguard.util.Strings;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SelectionService - Claim creation & resizing logic.
 */
public class SelectionService implements Listener {

    private final AegisGuard plugin;
    private final Map<UUID, Selection> selections = new HashMap<>();

    public SelectionService(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public Selection get(UUID uuid) {
        return selections.get(uuid);
    }

    public void set(UUID uuid, Selection sel) {
        if (sel == null) selections.remove(uuid);
        else selections.put(uuid, sel);
    }

    public void clear(UUID uuid) {
        selections.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        selections.remove(e.getPlayer().getUniqueId());
    }

    public void confirmClaim(Player p, boolean isServerClaim) {
        Selection sel = selections.get(p.getUniqueId());
        if (sel == null || sel.getL1() == null || sel.getL2() == null) {
            plugin.msg().send(p, "selection_missing");
            return;
        }

        Location l1 = sel.getL1();
        Location l2 = sel.getL2();

        if (!l1.getWorld().equals(l2.getWorld())) {
            plugin.msg().send(p, "selection_world_mismatch");
            return;
        }

        int minX = Math.min(l1.getBlockX(), l2.getBlockX());
        int maxX = Math.max(l1.getBlockX(), l2.getBlockX());
        int minZ = Math.min(l1.getBlockZ(), l2.getBlockZ());
        int maxZ = Math.max(l1.getBlockZ(), l2.getBlockZ());

        int width = (maxX - minX) + 1;
        int depth = (maxZ - minZ) + 1;

        int radius = Math.max(width, depth);

        int limitRadius = plugin.cfg().raw().getInt("claims.max_radius", 200);
        if (radius > limitRadius && !p.hasPermission("aegis.admin.bypass")) {
            plugin.msg().send(p, "resize-fail-max-area");
            return;
        }

        // Overlap checks against Aegis plots
        for (Plot other : plugin.store().getPlotsInWorld(l1.getWorld().getName())) {
            if (other == null) continue;
            if (other.isInPlot(minX, minZ) || other.isInPlot(maxX, maxZ) || other.isInPlot(minX, maxZ) || other.isInPlot(maxX, minZ)) {
                plugin.msg().send(p, "claim_overlap");
                return;
            }
        }

        // Compatibility: if other protection plugin present, yield if configured
        ProtectionHookManager hooks = plugin.protectionHooks();
        if (hooks != null && !hooks.shouldBypass(p, l1, l2)) {
            plugin.msg().send(p, "claim_external_protection_conflict");
            return;
        }

        // ClaimBlock economy checks (existing)
        boolean claimBlocksEnabled = plugin.cfg().raw().getBoolean("claim_blocks.enabled", true);
        if (!isServerClaim) {
            if (claimBlocksEnabled && !p.hasPermission("aegis.admin.bypass-limits")) {
                ClaimBlockData blocks = plugin.claimBlocks().getOrCreate(p.getUniqueId());
                int required = (width * depth);
                if (blocks.getAvailable() < required) {
                    plugin.msg().send(p, "claim_blocks_not_enough");
                    return;
                }
            }
        }

        // --- CREATION ---
        Plot plot;
        long now = System.currentTimeMillis();

        if (isServerClaim) {
            plot = new Plot(
                    UUID.randomUUID(),
                    Plot.SERVER_OWNER_UUID,
                    "Server",
                    l1.getWorld().getName(),
                    minX, minZ, maxX, maxZ, now
            );
            plot.setFlag("build", false);
            plot.setFlag("pvp", false);
            plot.setFlag("safe_zone", true);
            // 1.2.6 QoL: server plot creators should never be locked out
            plot.setRole(p.getUniqueId(), "steward");
        } else {
            plot = new Plot(
                    UUID.randomUUID(),
                    p.getUniqueId(),
                    p.getName(),
                    l1.getWorld().getName(),
                    minX, minZ, maxX, maxZ, now
            );
            plugin.worldRules().applyDefaults(plot);
        }

        PlotClaimEvent event = new PlotClaimEvent(plot, p);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        plugin.store().addPlot(plot);

        // ✅ Mark starter claim as used AFTER successful save
        if (!isServerClaim) {
            if (claimBlocksEnabled) {
                boolean starterEnabled = plugin.cfg().raw().getBoolean("claim_blocks.starter.enabled", true);
                if (starterEnabled) {
                    ClaimBlockData blocks = plugin.claimBlocks().getOrCreate(p.getUniqueId());
                    if (!blocks.hasClaimedStarter()) {
                        blocks.setClaimedStarter(true);
                        plugin.claimBlocks().save();
                    }
                }
            }
        }

        selections.remove(p.getUniqueId());
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);

        plugin.msg().send(p, isServerClaim ? "server_claim_success" : "claim_success");
    }
}
