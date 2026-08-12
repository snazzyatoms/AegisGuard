package com.aegisguard.travel;

import com.aegisguard.AegisGuard;
import com.aegisguard.util.TeleportUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared pre-teleport safety gate for Travel, Routes, checkpoints, visits,
 * markets, zones, Spawn, and staff destinations.
 *
 * Defaults preserve existing behavior: cooldown/confirmation/combat are off
 * unless configured. Safe-search radius defaults to the historical 4 blocks.
 */
public final class SafeTravelService implements Listener {

    public enum Kind {
        HOME,
        VISIT,
        ROUTE,
        MARKET,
        STALL,
        ZONE,
        SPAWN,
        STAFF,
        UNSTUCK,
        ARENA,
        OTHER
    }

    private final AegisGuard plugin;
    private volatile SafeTravelSettings settings = SafeTravelSettings.defaults();

    private final Map<UUID, Long> lastTravelAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> combatUntil = new ConcurrentHashMap<>();
    private final Map<UUID, PendingConfirm> pendingConfirm = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<UUID>> recentPlotIds = new ConcurrentHashMap<>();
    private static final int MAX_RECENT = 12;

    public SafeTravelService(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.settings = SafeTravelSettings.fromConfig(plugin.getConfig());
    }

    public SafeTravelSettings settings() {
        return settings;
    }

    /**
     * Resolve a safe standable location using the configured search radius.
     * Pure location helper — does not mutate player state.
     */
    public Location findSafeDestination(Location requested) {
        return TeleportUtil.findSafeDestination(requested, settings.getSafeSearchRadius());
    }

    /**
     * Validate + optionally teleport. Sends failure / confirmation messages.
     * On success the teleport is started and cooldown is recorded.
     */
    public SafeTravelResult travel(Player player, Location requested, Kind kind) {
        return travel(player, requested, kind, true);
    }

    /**
     * Same as {@link #travel(Player, Location, Kind)} but lets forced removals
     * (kick/ban) skip cooldown/confirmation/combat while still resolving a safe spot.
     */
    public SafeTravelResult travel(Player player, Location requested, Kind kind, boolean enforcePlayerGuards) {
        if (player == null || requested == null || requested.getWorld() == null) {
            return notify(player, SafeTravelResult.invalid());
        }
        if (!settings.isEnabled()) {
            // Feature-wide disable still allows raw teleports for compatibility.
            CompletableFuture<Boolean> future = TeleportUtil.safeTeleport(plugin, player, requested);
            return SafeTravelResult.success(requested, future);
        }

        boolean staffBypass = enforcePlayerGuards && shouldBypass(player, kind);

        Location safe = findSafeDestination(requested);
        if (safe == null) {
            return notify(player, SafeTravelResult.unsafe());
        }

        if (enforcePlayerGuards && !staffBypass) {
            SafeTravelResult combat = checkCombat(player);
            if (combat != null) return notify(player, combat);

            SafeTravelResult cooldown = checkCooldown(player);
            if (cooldown != null) return notify(player, cooldown);

            SafeTravelResult confirm = checkConfirmation(player, safe, kind);
            if (confirm != null) return notify(player, confirm);
        }

        pendingConfirm.remove(player.getUniqueId());
        CompletableFuture<Boolean> future = TeleportUtil.safeTeleport(plugin, player, safe);
        if (enforcePlayerGuards && !staffBypass && settings.getCooldownSeconds() > 0) {
            lastTravelAt.put(player.getUniqueId(), System.currentTimeMillis());
        }
        return SafeTravelResult.success(safe, future);
    }

    public void recordRecentDestination(UUID playerId, UUID plotId) {
        if (playerId == null || plotId == null) return;
        Deque<UUID> recent = recentPlotIds.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        synchronized (recent) {
            recent.remove(plotId);
            recent.addFirst(plotId);
            while (recent.size() > MAX_RECENT) recent.removeLast();
        }
    }

    public List<UUID> recentDestinations(UUID playerId) {
        if (playerId == null) return List.of();
        Deque<UUID> recent = recentPlotIds.get(playerId);
        if (recent == null || recent.isEmpty()) return List.of();
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }

    /**
     * Validate only (no teleport). Useful for GUI preview / refusal messaging.
     */
    public SafeTravelResult validate(Player player, Location requested, Kind kind) {
        if (player == null || requested == null || requested.getWorld() == null) {
            return SafeTravelResult.invalid();
        }
        if (!settings.isEnabled()) return SafeTravelResult.disabled();
        if (findSafeDestination(requested) == null) return SafeTravelResult.unsafe();
        if (shouldBypass(player, kind)) return SafeTravelResult.success(requested, null);

        SafeTravelResult combat = checkCombat(player);
        if (combat != null) return combat;
        SafeTravelResult cooldown = checkCooldown(player);
        if (cooldown != null) return cooldown;
        return SafeTravelResult.success(requested, null);
    }

    public void markCombat(Player player) {
        if (player == null || !settings.isBlockWhileInCombat()) return;
        long until = System.currentTimeMillis() + (settings.getCombatSeconds() * 1000L);
        combatUntil.put(player.getUniqueId(), until);
    }

    public boolean isInCombat(UUID playerId) {
        if (playerId == null || !settings.isBlockWhileInCombat()) return false;
        Long until = combatUntil.get(playerId);
        if (until == null) return false;
        if (until <= System.currentTimeMillis()) {
            combatUntil.remove(playerId);
            return false;
        }
        return true;
    }

    public long combatRemainingMillis(UUID playerId) {
        Long until = combatUntil.get(playerId);
        if (until == null) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public long cooldownRemainingMillis(UUID playerId) {
        if (playerId == null || settings.getCooldownSeconds() <= 0) return 0L;
        Long last = lastTravelAt.get(playerId);
        if (last == null) return 0L;
        long readyAt = last + (settings.getCooldownSeconds() * 1000L);
        return Math.max(0L, readyAt - System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!settings.isBlockWhileInCombat()) return;
        Player victim = asPlayer(event.getEntity());
        Player attacker = asPlayer(event.getDamager());
        if (attacker == null && event.getDamager() instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player shooter) attacker = shooter;
        }
        if (victim != null) markCombat(victim);
        if (attacker != null) markCombat(attacker);
    }

    private SafeTravelResult checkCombat(Player player) {
        if (!settings.isBlockWhileInCombat()) return null;
        long remaining = combatRemainingMillis(player.getUniqueId());
        if (remaining <= 0L) return null;
        return SafeTravelResult.inCombat(remaining);
    }

    private SafeTravelResult checkCooldown(Player player) {
        long remaining = cooldownRemainingMillis(player.getUniqueId());
        if (remaining <= 0L) return null;
        return SafeTravelResult.cooldown(remaining);
    }

    private SafeTravelResult checkConfirmation(Player player, Location safe, Kind kind) {
        if (!settings.isRequireConfirmation()) return null;
        UUID id = player.getUniqueId();
        PendingConfirm pending = pendingConfirm.get(id);
        long now = System.currentTimeMillis();
        String fingerprint = fingerprint(safe, kind);
        if (pending != null
                && Objects.equals(pending.fingerprint, fingerprint)
                && pending.expiresAt > now) {
            pendingConfirm.remove(id);
            return null;
        }
        pendingConfirm.put(id, new PendingConfirm(fingerprint,
                now + (settings.getConfirmationSeconds() * 1000L)));
        SafeTravelResult result = SafeTravelResult.confirmationRequired();
        return result;
    }

    private boolean shouldBypass(Player player, Kind kind) {
        if (kind == Kind.STAFF && !settings.isApplyToStaff()) return true;
        if (!settings.isBypassPermissionHonored()) return false;
        return player.hasPermission("aegis.admin.bypass") || player.isOp();
    }

    private SafeTravelResult notify(Player player, SafeTravelResult result) {
        if (player == null || result == null || result.isSuccess()) return result;
        if (result.messageKey() == null) return result;
        Map<String, String> vars = Map.of(
                "SECONDS", String.valueOf(Math.max(1L,
                        result.remainingSeconds() > 0
                                ? result.remainingSeconds()
                                : settings.getConfirmationSeconds()))
        );
        try {
            if (plugin.msg() != null) {
                plugin.msg().send(player, result.messageKey(), vars);
            } else {
                String msg = result.fallbackMessage() == null ? "" : result.fallbackMessage();
                for (Map.Entry<String, String> e : vars.entrySet()) {
                    msg = msg.replace("{" + e.getKey() + "}", e.getValue());
                }
                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', msg));
            }
        } catch (Throwable ignored) {}
        try {
            if (plugin.effects() != null) plugin.effects().playError(player);
        } catch (Throwable ignored) {}

        // Respect travel-failure notification preference when present.
        try {
            if (plugin.getNotificationManager() != null
                    && !plugin.getNotificationManager().getSettings(player).isTravelNotificationsEnabled()) {
                // Message already sent above for clear failure feedback on the travel action itself.
                // Preference is honored for non-action spam paths elsewhere.
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private static Player asPlayer(Entity entity) {
        return entity instanceof Player player ? player : null;
    }

    private static String fingerprint(Location loc, Kind kind) {
        return kind.name().toLowerCase(Locale.ROOT) + "|"
                + loc.getWorld().getName() + "|"
                + loc.getBlockX() + "|" + loc.getBlockY() + "|" + loc.getBlockZ();
    }

    private record PendingConfirm(String fingerprint, long expiresAt) {}
}
