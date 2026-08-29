package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotLevelUpEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.progression.AscensionFocus;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LevelingListener implements Listener {
    private final AegisGuard plugin;
    private final Map<UUID, UUID> activePlotCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<PotionEffectType>> managedEffects = new ConcurrentHashMap<>();
    private final Map<UUID, Map<PotionEffectType, PotionEffect>> displacedEffects = new ConcurrentHashMap<>();

    public LevelingListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public void refresh(Player player, Plot plot) {
        if (player == null || plot == null || !plot.contains(player.getLocation()) || !isAllowed(player, plot)) {
            return;
        }
        activePlotCache.put(player.getUniqueId(), plot.getPlotId());
        applyBuffs(player, plot);
    }

    @EventHandler
    public void onLevelUp(PlotLevelUpEvent event) {
        Plot plot = event.getPlot();
        Player player = event.getPlayer();
        int newLevel = event.getNewLevel();

        if (plugin.cfg().raw().getBoolean("claim_blocks.earn.level_up.enabled", true)) {
            int amount = Math.max(0, plugin.cfg().raw().getInt("claim_blocks.earn.level_up.per_level", 500));
            if (amount > 0 && plugin.getClaimBlockManager() != null) {
                plugin.getClaimBlockManager().getOrCreate(player.getUniqueId()).addEarnedBlocks(amount);
                send(player, "claim_blocks_earned_level", "&dAscension Bonus: &e+{AMOUNT} ClaimBlocks", Map.of(
                        "AMOUNT", String.valueOf(amount), "LEVEL", String.valueOf(newLevel)));
            }
        }

        List<String> rewards = plugin.cfg().getLevelRewards(newLevel);
        if (rewards != null) {
            for (String raw : rewards) applyPermanentReward(player, plot, raw);
        }

        plugin.store().savePlot(plot);
        if (plot.contains(player.getLocation())) applyBuffs(player, plot);
        if (plugin.getClaimBlockManager() != null) plugin.getClaimBlockManager().saveAsync();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        if (event.getFrom().getWorld().equals(to.getWorld())
                && event.getFrom().getBlockX() == to.getBlockX()
                && event.getFrom().getBlockY() == to.getBlockY()
                && event.getFrom().getBlockZ() == to.getBlockZ()) return;
        handleMovement(event.getPlayer(), to);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        handleMovement(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeBuffs(event.getPlayer());
        activePlotCache.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onFarmlandTrample(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.FARMLAND) return;
        Plot plot = plugin.store().getPlotAt(event.getClickedBlock().getLocation());
        if (plot == null || plot.getLevel() < 5
                || AscensionFocus.parse(plot.getAscensionFocus()) != AscensionFocus.VERDANT_KEEPER) return;
        if (isAllowed(event.getPlayer(), plot)) event.setCancelled(true);
    }

    private void handleMovement(Player player, Location to) {
        if (to == null || to.getWorld() == null) return;
        Plot plot = plugin.store().getPlotAt(to);
        UUID nextId = plot == null ? null : plot.getPlotId();
        UUID previousId = activePlotCache.get(player.getUniqueId());
        if (java.util.Objects.equals(nextId, previousId)) return;

        removeBuffs(player);
        if (plot != null && isAllowed(player, plot)) {
            activePlotCache.put(player.getUniqueId(), nextId);
            applyBuffs(player, plot);
        } else {
            activePlotCache.remove(player.getUniqueId());
        }
    }

    private void applyPermanentReward(Player player, Plot plot, String raw) {
        if (raw == null || raw.isBlank()) return;
        String reward = raw.trim();
        if (reward.startsWith("MEMBERS:")) {
            try {
                int amount = Math.max(0, Integer.parseInt(reward.substring("MEMBERS:".length()).trim()));
                if (amount > 0) {
                    plot.setMaxMembers(plot.getMaxMembers() + amount);
                    send(player, "ascension_member_reward", "&aYour plot can now trust {AMOUNT} additional player(s).",
                            Map.of("AMOUNT", String.valueOf(amount)));
                }
            } catch (NumberFormatException ignored) {}
            return;
        }
        if (reward.startsWith("CLAIM_BLOCKS:")) {
            try {
                int amount = Math.max(0, Integer.parseInt(reward.substring("CLAIM_BLOCKS:".length()).trim()));
                if (amount > 0 && plugin.getClaimBlockManager() != null) {
                    plugin.getClaimBlockManager().getOrCreate(player.getUniqueId()).addEarnedBlocks(amount);
                }
            } catch (NumberFormatException ignored) {}
            return;
        }
        if (reward.startsWith("FLAG:")) {
            String flag = reward.substring("FLAG:".length()).trim().toLowerCase();
            if (!flag.isBlank()) plot.setFlag(flag, true);
            return;
        }
        if (reward.startsWith("RADIUS:")) {
            plugin.getLogger().warning("Ignored unsafe RADIUS ascension reward at level " + plot.getLevel()
                    + "; use Frontier Expansion so overlap, pricing, and snapshots remain protected.");
        }
    }

    private void applyBuffs(Player player, Plot plot) {
        removeBuffs(player);
        Map<PotionEffectType, Integer> desired = collectEffects(plot);
        Map<PotionEffectType, PotionEffect> displaced = new HashMap<>();
        Set<PotionEffectType> applied = new HashSet<>();

        for (Map.Entry<PotionEffectType, Integer> entry : desired.entrySet()) {
            PotionEffect previous = player.getPotionEffect(entry.getKey());
            if (previous != null) displaced.put(entry.getKey(), previous);
            player.addPotionEffect(new PotionEffect(entry.getKey(), Integer.MAX_VALUE,
                    entry.getValue(), true, false, false), true);
            applied.add(entry.getKey());
        }
        if (!applied.isEmpty()) managedEffects.put(player.getUniqueId(), applied);
        if (!displaced.isEmpty()) displacedEffects.put(player.getUniqueId(), displaced);

        if (plugin.flightSkills() != null) plugin.flightSkills().refresh(player);
    }

    private Map<PotionEffectType, Integer> collectEffects(Plot plot) {
        Map<PotionEffectType, Integer> effects = new HashMap<>();
        for (int level = 1; level <= plot.getLevel(); level++) {
            List<String> rewards = plugin.cfg().getLevelRewards(level);
            if (rewards == null) continue;
            for (String reward : rewards) {
                if (reward == null || !reward.startsWith("EFFECT:")) continue;
                String[] parts = reward.split(":");
                if (parts.length < 3) continue;
                PotionEffectType type = PotionEffectType.getByName(parts[1].trim());
                try {
                    int amplifier = Math.max(0, Integer.parseInt(parts[2].trim()) - 1);
                    if (type != null) effects.merge(type, amplifier, Math::max);
                } catch (NumberFormatException ignored) {}
            }
        }

        AscensionFocus focus = AscensionFocus.parse(plot.getAscensionFocus());
        PotionEffectType focusType = focus.effectType();
        int focusAmplifier = focus.amplifierForLevel(plot.getLevel());
        if (focusType != null && focusAmplifier >= 0) effects.merge(focusType, focusAmplifier, Math::max);
        return effects;
    }

    public boolean hasFlightReward(Plot plot) {
        if (plot == null) return false;
        for (int level = 1; level <= plot.getLevel(); level++) {
            List<String> rewards = plugin.cfg().getLevelRewards(level);
            if (rewards == null) continue;
            if (rewards.stream().anyMatch(value -> value != null
                    && (value.equalsIgnoreCase("FLIGHT") || value.equalsIgnoreCase("FLY")
                    || value.equalsIgnoreCase("FLAG:fly")))) return true;
        }
        return false;
    }

    private void removeBuffs(Player player) {
        UUID playerId = player.getUniqueId();
        Set<PotionEffectType> applied = managedEffects.remove(playerId);
        if (applied != null) applied.forEach(player::removePotionEffect);

        Map<PotionEffectType, PotionEffect> displaced = displacedEffects.remove(playerId);
        if (displaced != null) displaced.values().forEach(effect -> player.addPotionEffect(effect, true));

        if (plugin.flightSkills() != null) plugin.flightSkills().refresh(player);
    }

    private boolean isAllowed(Player player, Plot plot) {
        return plot.isOwner(player) || plot.isTrusted(player);
    }

    private void send(Player player, String key, String fallback, Map<String, String> replacements) {
        String message = plugin.gui().tr(player, key, fallback);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        player.sendMessage(com.aegisguard.gui.GUIManager.color(message));
    }
}
