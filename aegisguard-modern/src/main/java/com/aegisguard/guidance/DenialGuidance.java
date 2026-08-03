package com.aegisguard.guidance;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.guestpass.GuestPass;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Milestone 5 (Clearer Player Guidance) - upgrades the generic "you can't do that" protection
 * denials with an actionable next step instead of a flat refusal:
 *
 * <ul>
 *   <li>A player already holding an active Guest Pass that simply does not cover the attempted
 *       action is told exactly which access is missing (e.g. "Container access is not included
 *       in your current pass.").</li>
 *   <li>A player with no role, rental, or pass at all on a private plot is told to ask the owner
 *       for a Guest Pass instead of receiving a bare "protected" message.</li>
 *   <li>An Emergency Lockdown (Milestone 3) always takes priority when active, since that
 *       denial already has its own, more specific message.</li>
 * </ul>
 *
 * Every other case (e.g. a permanent member whose role simply lacks this permission) keeps using
 * the caller-supplied fallback key, so existing wording for those situations is unchanged.
 *
 * Also honors the opt-in "repeat notifications" player preference: when a player has disabled
 * repeats, identical denials for the same player+key are throttled to at most once every few
 * seconds so mashing a blocked action does not spam their chat/action bar/title.
 */
public final class DenialGuidance {

    private static final long REPEAT_COOLDOWN_MILLIS = 4_000L;
    private static final Map<UUID, Map<String, Long>> LAST_SENT = new ConcurrentHashMap<>();

    private DenialGuidance() {}

    public static void send(AegisGuard plugin, Player player, Plot plot, String permission, String fallbackKey) {
        if (plugin == null || player == null || fallbackKey == null) return;

        String key;
        Map<String, String> vars = null;

        if (plot != null && plot.isLockdownActive() && Plot.isLockdownRestrictable(permission, plugin)) {
            key = "lockdown_action_blocked";
        } else if (plot != null && hasActivePassMissing(plot, player.getUniqueId(), permission)) {
            key = "guest_pass_missing_permission";
            vars = Map.of("PERMISSION", permissionLabel(plugin, player, permission));
        } else if (plot != null && plot.getActiveGuestPass(player.getUniqueId()) == null && isStranger(plot, player.getUniqueId())) {
            key = "plot_action_needs_pass";
        } else {
            key = fallbackKey;
        }

        if (isThrottled(plugin, player, key)) return;

        if (vars != null) plugin.msg().send(player, key, vars);
        else plugin.msg().send(player, key);
    }

    private static boolean hasActivePassMissing(Plot plot, UUID uuid, String permission) {
        GuestPass pass = plot.getActiveGuestPass(uuid);
        return pass != null && !pass.hasPermission(permission);
    }

    /** True when the player has no ownership, role, or rental on the plot - a first-time visitor. */
    private static boolean isStranger(Plot plot, UUID uuid) {
        if (plot.getOwner() != null && plot.getOwner().equals(uuid)) return false;
        if (plot.isRentedBy(uuid)) return false;
        String role = plot.getRole(uuid);
        return role == null || role.equalsIgnoreCase("visitor");
    }

    private static boolean isThrottled(AegisGuard plugin, Player player, String key) {
        boolean repeatEnabled = true;
        try {
            if (plugin.getNotificationManager() != null) {
                repeatEnabled = plugin.getNotificationManager().getSettings(player.getUniqueId()).isRepeatNotificationsEnabled();
            }
        } catch (Throwable ignored) {}
        if (repeatEnabled) return false;

        long now = System.currentTimeMillis();
        Map<String, Long> perPlayer = LAST_SENT.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        Long last = perPlayer.get(key);
        if (last != null && now - last < REPEAT_COOLDOWN_MILLIS) return true;
        perPlayer.put(key, now);
        return false;
    }

    private static String permissionLabel(AegisGuard plugin, Player player, String permission) {
        if (permission == null) return fallbackLabel(player, plugin, "permission_label_other", "That action");
        return switch (permission.toUpperCase(Locale.ROOT)) {
            case "CONTAINERS" -> fallbackLabel(player, plugin, "permission_label_containers", "Container access");
            case "BUILD", "BLOCK_PLACE" -> fallbackLabel(player, plugin, "permission_label_build", "Building");
            case "BLOCK_BREAK" -> fallbackLabel(player, plugin, "permission_label_break", "Breaking blocks");
            case "FARM" -> fallbackLabel(player, plugin, "permission_label_farm", "Farming");
            case "VEHICLES" -> fallbackLabel(player, plugin, "permission_label_vehicles", "Vehicle use");
            default -> fallbackLabel(player, plugin, "permission_label_other", "That action");
        };
    }

    private static String fallbackLabel(Player player, AegisGuard plugin, String key, String fallback) {
        try {
            if (plugin.codex() != null) {
                String tr = plugin.codex().tr(player, key);
                if (tr != null && !tr.isBlank() && !tr.equalsIgnoreCase(key)) return tr;
            }
        } catch (Throwable ignored) {}
        return fallback;
    }
}
