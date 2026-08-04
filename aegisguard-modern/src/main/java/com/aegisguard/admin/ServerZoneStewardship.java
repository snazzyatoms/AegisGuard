package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Shared post-create / post-convert stewardship for server zones.
 * Create (wand) and convert both end here so staff get a consistent Steward grant,
 * localized feedback, and optional Claim Settings open.
 */
public final class ServerZoneStewardship {

    private final AegisGuard plugin;

    public ServerZoneStewardship(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Assigns Steward to {@code actor} on a server zone, persists, notifies, and optionally opens Flags.
     *
     * @param openSettings when true and config allows, open Claim Settings after grant
     * @return true when steward role was applied
     */
    public boolean grantSteward(Player actor, Plot plot, boolean openSettings) {
        if (actor == null || plot == null || !plot.isServerZone()) return false;

        UUID actorId = actor.getUniqueId();
        plot.setRole(actorId, "steward");
        try {
            plugin.store().addPlayerRole(plot, actorId, "steward");
        } catch (Throwable ignored) {
            plugin.store().savePlotSync(plot);
        }

        send(actor, "server_zone_steward_granted",
                "&aSteward access granted. &7You can manage this server zone's settings.");

        boolean shouldOpen = openSettings
                && plugin.cfg().raw().getBoolean("admin.wand.open_settings_after_claim", true);
        if (shouldOpen) {
            plugin.runMain(actor, () -> {
                if (!actor.isOnline()) return;
                if (!plot.canManage(actor, plugin)) {
                    send(actor, "server_zone_manage_denied",
                            "&cYou need server-zone manage permission or the Steward role to change these settings.");
                    return;
                }
                if (plugin.gui() != null && plugin.gui().flags() != null) {
                    plugin.gui().flags().open(actor, plot);
                    send(actor, "server_zone_settings_opened",
                            "&7Opening Claim Settings for this server zone.");
                }
            });
        }
        return true;
    }

    private void send(Player player, String key, String fallback) {
        if (player == null || plugin.gui() == null) return;
        player.sendMessage(GUIManager.color(plugin.gui().tr(player, key, fallback)));
    }

    /** Whether this player may manage server-zone settings (perm list, OP trust, bypass, or steward role). */
    public static boolean canManageServerZoneSettings(Player player, Plot plot, AegisGuard plugin) {
        if (player == null || plot == null || plugin == null || !plot.isServerZone()) return false;
        return plot.canManage(player, plugin);
    }
}
