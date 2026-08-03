package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.notify.NotificationMode;
import com.aegisguard.notify.PlayerNotificationSettings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * SettingsGUI
 * - Personal player preferences (sounds, language, notifications)
 * - Language cycling uses CodexEngine style order (codex.yml or config).
 * - ✅ Persistence is handled by CodexEngine (config.yml), NOT MessagesUtil.
 *
 * 1.2.6 QOL:
 * - Bulletproof top-inventory gating (no bottom-inventory misfires)
 * - Reload-safe null guards (cfg/codex/notification manager may be null mid-reload)
 * - Consistent save + reopen pattern (prevents flicker/cursor snap)
 * - Keeps legacy compatibility with old notifications.<uuid> boolean/string
 *
 * IMPORTANT (Fix):
 * - Greetings/Admin Updates/Mode now use NotificationManager (notifications.yml) when available,
 *   so PlotGreetingListener respects the toggles immediately (no split-brain config vs yml).
 */
public class SettingsGUI {

    private final AegisGuard plugin;

    public SettingsGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class SettingsGUIHolder implements InventoryHolder {
        private final Plot plot;
        public SettingsGUIHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    // --------------------------------------------------
    // Codex-safe helpers (with fallbacks)
    // --------------------------------------------------

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private String t(Player p, String key, String fallback, Map<String, String> placeholders) {
        return plugin.gui().tr(p, key, fallback, placeholders);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private void reopenNextTick(Player player, Plot plot) {
        // Prevents flash + cursor snapping (especially noticeable on some clients)
        plugin.runMain(player, () -> open(player, plot));
    }

    private boolean saveConfigSafe() {
        try {
            plugin.saveConfig();
            return true;
        } catch (Throwable t) {
            try { plugin.getLogger().warning("[SettingsGUI] saveConfig failed: " + t.getMessage()); } catch (Throwable ignored) {}
            return false;
        }
    }

    private void playOpen(Player p) {
        try { if (plugin.effects() != null) plugin.effects().playMenuOpen(p); } catch (Throwable ignored) {}
    }

    private void playFlip(Player p) {
        try { if (plugin.effects() != null) plugin.effects().playMenuFlip(p); } catch (Throwable ignored) {}
    }

    private void playClose(Player p) {
        try { if (plugin.effects() != null) plugin.effects().playMenuClose(p); } catch (Throwable ignored) {}
    }

    private void playError(Player p) {
        try { if (plugin.effects() != null) plugin.effects().playError(p); } catch (Throwable ignored) {}
    }

    public void open(Player player) { open(player, null); }

    public void open(Player player, Plot plot) {
        String title = plugin.gui().title(
                player,
                "settings_menu_title",
                "&b⚙ AegisGuard Settings"
        );

        Inventory inv = Bukkit.createInventory(new SettingsGUIHolder(plot), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // --------------------------------------------------
        // 1) SOUNDS (Slot 10)
        // --------------------------------------------------
        boolean globalEnabled = true;
        try {
            if (plugin.cfg() != null) globalEnabled = plugin.cfg().globalSoundsEnabled();
        } catch (Throwable ignored) {}

        if (!globalEnabled) {
            inv.setItem(10, GUIManager.createItem(
                    Material.BARRIER,
                    t(player, "settings_sounds_global_off_name", "&cSounds Disabled Globally"),
                    tl(player, "settings_sounds_global_off_lore",
                            List.of("&7A server admin has disabled UI sounds."))
            ));
        } else {
            boolean soundsEnabled = false;
            try { soundsEnabled = plugin.isSoundEnabled(player); } catch (Throwable ignored) {}

            String soundsName = soundsEnabled
                    ? t(player, "settings_sounds_on_name", "&aSounds: ON")
                    : t(player, "settings_sounds_off_name", "&cSounds: OFF");

            inv.setItem(10, GUIManager.createItem(
                    soundsEnabled ? Material.NOTE_BLOCK : Material.JUKEBOX,
                    soundsName,
                    tl(player, "settings_sounds_lore",
                            List.of("&7Toggle AegisGuard menu sound effects."))
            ));
        }

        // --------------------------------------------------
        // 2) LANGUAGE (Slot 13)
        // --------------------------------------------------
        if (plugin.codex() == null) {
            inv.setItem(13, GUIManager.createItem(
                    Material.BARRIER,
                    t(player, "settings_language_unavailable_name", "&cLanguage Switching Unavailable"),
                    tl(player, "settings_language_unavailable_lore",
                            List.of("&7Codex language engine is not loaded."))
            ));
        } else {
            String currentStyle = "old_english";
            try { currentStyle = plugin.codex().getPlayerStyle(player); } catch (Throwable ignored) {}

            String langDisplay = formatStyle(player, currentStyle);

            inv.setItem(13, GUIManager.createItem(
                    Material.WRITABLE_BOOK,
                    t(player,
                            "settings_language_name",
                            "&eLanguage: {LANG}",
                            Map.of("LANG", langDisplay)
                    ),
                    tl(player, "settings_language_lore",
                            List.of("&7Click to cycle language styles."))
            ));
        }

        // --------------------------------------------------
        // 3) NOTIFICATIONS MODE (Slot 16) - NotificationManager aware
        // --------------------------------------------------
        String mode = "ACTION_BAR";
        try {
            if (plugin.getNotificationManager() != null) {
                PlayerNotificationSettings settings = plugin.getNotificationManager().getSettings(player.getUniqueId());
                if (settings != null && settings.getMode() != null) {
                    mode = normalizeNotif(settings.getMode().getConfigValue());
                }
            } else {
                mode = getNotifMode(player);
            }
        } catch (Throwable ignored) {}

        String modeDisplay = notifDisplay(player, mode);

        inv.setItem(16, GUIManager.createItem(
                Material.PAPER,
                t(player,
                        "settings_notifications_name",
                        "&bNotifications: {MODE}",
                        Map.of("MODE", modeDisplay)
                ),
                tl(player, "settings_notifications_lore",
                        List.of("&7Click to cycle:", "&7Chat -> Action Bar -> Title"))
        ));

        // --------------------------------------------------
        // 3B) PLOT GREETINGS TOGGLE (Slot 19)
        // --------------------------------------------------
        boolean greetingsEnabled = getGreetingsEnabled(player);

        inv.setItem(19, GUIManager.createItem(
                greetingsEnabled ? Material.BELL : Material.BARRIER,
                greetingsEnabled
                        ? t(player, "settings_greetings_on_name", "&aPlot Greetings: ON")
                        : t(player, "settings_greetings_off_name", "&cPlot Greetings: OFF"),
                tl(player, "settings_greetings_lore",
                        List.of(
                                "&7Toggle enter/leave claim messages.",
                                "&7(Does not affect approvals/denials.)"
                        ))
        ));

        // --------------------------------------------------
        // 3C) ADMIN UPDATES TOGGLE (Slot 22)
        // --------------------------------------------------
        boolean adminUpdates = getAdminUpdatesEnabled(player);

        inv.setItem(22, GUIManager.createItem(
                adminUpdates ? Material.BOOK : Material.BOOKSHELF,
                adminUpdates
                        ? t(player, "settings_admin_updates_on_name", "&aAdmin Updates: ON")
                        : t(player, "settings_admin_updates_off_name", "&cAdmin Updates: OFF"),
                tl(player, "settings_admin_updates_lore",
                        List.of(
                                "&7Toggle admin notifications:",
                                "&7approvals, denials, moderation, etc."
                        ))
        ));

        // --------------------------------------------------
        // 3D) REPEAT NOTIFICATIONS TOGGLE (Slot 25, Milestone 5)
        // --------------------------------------------------
        boolean repeatNotifications = getRepeatNotificationsEnabled(player);

        inv.setItem(25, GUIManager.createItem(
                repeatNotifications ? Material.REPEATER : Material.COMPARATOR,
                repeatNotifications
                        ? t(player, "settings_repeat_notifications_on_name", "&aRepeat Notifications: ON")
                        : t(player, "settings_repeat_notifications_off_name", "&cRepeat Notifications: OFF"),
                tl(player, "settings_repeat_notifications_lore",
                        List.of(
                                "&7When OFF, repeated blocked-action",
                                "&7messages are limited to once every",
                                "&7few seconds instead of spamming."
                        ))
        ));

        // --------------------------------------------------
        // 3E) REPLAY WALKTHROUGH (Slot 31, Milestone 5)
        // --------------------------------------------------
        inv.setItem(31, GUIManager.createItem(
                Material.KNOWLEDGE_BOOK,
                t(player, "settings_replay_walkthrough_name", "&eReplay First-Claim Walkthrough"),
                tl(player, "settings_replay_walkthrough_lore",
                        List.of("&7Revisit the optional guide covering", "&7roles, Guest Passes, and Lockdown."))
        ));

        // --------------------------------------------------
        // 3F) CATEGORY PREFERENCES (1.3.0+) — defaults ON
        // --------------------------------------------------
        placeCategoryToggle(inv, 28, player, "guest_pass",
                getCategoryEnabled(player, "guest_pass"), Material.NAME_TAG,
                "settings_guest_pass_notify_on_name", "&aGuest Pass Alerts: ON",
                "settings_guest_pass_notify_off_name", "&cGuest Pass Alerts: OFF",
                "settings_guest_pass_notify_lore", List.of("&7Issue, revoke, and expiry notices."));
        placeCategoryToggle(inv, 29, player, "alliance",
                getCategoryEnabled(player, "alliance"), Material.SHIELD,
                "settings_alliance_notify_on_name", "&aAlliance Alerts: ON",
                "settings_alliance_notify_off_name", "&cAlliance Alerts: OFF",
                "settings_alliance_notify_lore", List.of("&7Invites and alliance membership events."));
        placeCategoryToggle(inv, 30, player, "lockdown",
                getCategoryEnabled(player, "lockdown"), Material.IRON_BARS,
                "settings_lockdown_notify_on_name", "&aLockdown Alerts: ON",
                "settings_lockdown_notify_off_name", "&cLockdown Alerts: OFF",
                "settings_lockdown_notify_lore", List.of("&7Emergency Lockdown activate/deactivate."));
        placeCategoryToggle(inv, 32, player, "travel",
                getCategoryEnabled(player, "travel"), Material.ENDER_PEARL,
                "settings_travel_notify_on_name", "&aTravel Alerts: ON",
                "settings_travel_notify_off_name", "&cTravel Alerts: OFF",
                "settings_travel_notify_lore", List.of("&7Travel failures and cooldown notices."));
        placeCategoryToggle(inv, 33, player, "plot_notices",
                getCategoryEnabled(player, "plot_notices"), Material.OAK_SIGN,
                "settings_plot_notice_notify_on_name", "&aPlot Notice Alerts: ON",
                "settings_plot_notice_notify_off_name", "&cPlot Notice Alerts: OFF",
                "settings_plot_notice_notify_lore", List.of("&7Plot noticeboard updates."));
        inv.setItem(34, GUIManager.createItem(Material.GOLD_INGOT,
                t(player, "settings_settlements_name", "&6Settlements Inbox"),
                tl(player, "settings_settlements_lore", List.of(
                        "&7Review any pending payments",
                        "&7waiting for delivery."
                ))));

        // --------------------------------------------------
        // NAVIGATION (48/49)
        // --------------------------------------------------
        inv.setItem(48, GUIManager.createItem(
                Material.NETHER_STAR,
                t(player, "button_back_menu", "&fReturn to Menu"),
                tl(player, "back_menu_lore", List.of("&7Go back to the main dashboard."))
        ));

        inv.setItem(49, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        playOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        // ✅ Only handle clicks in the TOP inventory
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!(e.getInventory().getHolder() instanceof SettingsGUIHolder holder)) return;

        e.setCancelled(true);
        e.setResult(Event.Result.DENY);

        ItemStack currentItem = e.getCurrentItem();
        if (currentItem == null || currentItem.getType() == Material.AIR) return;

        // Ignore filler clicks silently
        if (currentItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        Plot plot = holder.getPlot();
        UUID uuid = player.getUniqueId();

        switch (rawSlot) {

            case 10 -> { // Sounds
                boolean globalEnabled = true;
                try { if (plugin.cfg() != null) globalEnabled = plugin.cfg().globalSoundsEnabled(); } catch (Throwable ignored) {}

                if (!globalEnabled) {
                    playError(player);
                    return;
                }

                boolean current = false;
                try { current = plugin.isSoundEnabled(player); } catch (Throwable ignored) {}

                plugin.getConfig().set("sounds.players." + uuid, !current);
                if (!saveConfigSafe()) {
                    playError(player);
                    return;
                }

                playFlip(player);
                reopenNextTick(player, plot);
            }

            case 13 -> { // Language (Codex ordered cycle + persisted by CodexEngine)
                if (plugin.codex() == null) {
                    playError(player);
                    return;
                }

                String current = plugin.codex().getPlayerStyle(player);
                String next = plugin.codex().getNextStyle(current);

                boolean applied = plugin.codex().setPlayerStyle(player, next);
                if (applied) playFlip(player);
                else playError(player);

                reopenNextTick(player, plot);
            }

            case 16 -> { // Notifications MODE (notifications.yml preferred)
                if (!canManageNotifications(player)) {
                    playError(player);
                    return;
                }

                try {
                    NotificationMode newMode = null;

                    if (plugin.getNotificationManager() != null) {
                        newMode = plugin.getNotificationManager().cycleMode(uuid);
                    } else {
                        String mode = getNotifMode(player);
                        String nextMode = switch (mode) {
                            case "CHAT" -> "ACTION_BAR";
                            case "ACTION_BAR" -> "TITLE";
                            default -> "CHAT";
                        };

                        setNotifMode(player, nextMode);

                        // Legacy compatibility
                        plugin.getConfig().set("notifications." + uuid, nextMode);
                        saveConfigSafe();
                    }

                    // Optional mirror keys (keeps fallback consistent if manager ever fails/disabled)
                    if (newMode != null) {
                        mirrorToConfig(uuid, newMode, null, null);
                    }

                } catch (Throwable error) {
                    preferenceFailure(player, error);
                    return;
                }

                playFlip(player);
                reopenNextTick(player, plot);
            }

            case 19 -> { // Plot Greetings toggle (enter/leave)
                if (!canManageGreetingNotifications(player, true)) {
                    playError(player);
                    return;
                }

                try {
                    Boolean newState = null;

                    if (plugin.getNotificationManager() != null) {
                        newState = plugin.getNotificationManager().toggleGreetings(uuid);
                    } else {
                        boolean current = getGreetingsEnabled(player);
                        setGreetingsEnabled(player, !current);

                        // Legacy collision: boolean stored under notifications.<uuid>
                        Object legacy = plugin.getConfig().get("notifications." + uuid);
                        if (legacy instanceof Boolean) {
                            String mode = getNotifMode(player);
                            plugin.getConfig().set("notifications." + uuid, mode);
                        }

                        saveConfigSafe();
                    }

                    // Optional mirror keys (keeps fallback consistent)
                    if (newState != null) {
                        // Preserve current mode/adminUpdates where possible
                        String modeStr = getNotifMode(player);
                        boolean admin = getAdminUpdatesEnabled(player);
                        mirrorToConfig(uuid, NotificationMode.fromString(modeStr), newState, admin);
                    }

                } catch (Throwable error) {
                    preferenceFailure(player, error);
                    return;
                }

                playFlip(player);
                reopenNextTick(player, plot);
            }

            case 22 -> { // Admin Updates toggle
                if (!canManageNotifications(player)) {
                    playError(player);
                    return;
                }

                try {
                    Boolean newState = null;

                    if (plugin.getNotificationManager() != null) {
                        newState = plugin.getNotificationManager().toggleAdminUpdates(uuid);
                    } else {
                        boolean current = getAdminUpdatesEnabled(player);
                        setAdminUpdatesEnabled(player, !current);
                        saveConfigSafe();
                    }

                    // Optional mirror keys (keeps fallback consistent)
                    if (newState != null) {
                        String modeStr = getNotifMode(player);
                        boolean greet = getGreetingsEnabled(player);
                        mirrorToConfig(uuid, NotificationMode.fromString(modeStr), greet, newState);
                    }

                } catch (Throwable error) {
                    preferenceFailure(player, error);
                    return;
                }

                playFlip(player);
                reopenNextTick(player, plot);
            }

            case 25 -> { // Repeat Notifications toggle (Milestone 5)
                if (!canManageNotifications(player)) {
                    playError(player);
                    return;
                }

                try {
                    if (plugin.getNotificationManager() != null) {
                        plugin.getNotificationManager().toggleRepeatNotifications(uuid);
                    }
                } catch (Throwable error) {
                    preferenceFailure(player, error);
                    return;
                }

                playFlip(player);
                reopenNextTick(player, plot);
            }

            case 31 -> { // Replay First-Claim Walkthrough (Milestone 5)
                playFlip(player);
                plugin.runMain(player, () -> plugin.gui().walkthrough().open(player, 0));
            }

            case 28 -> toggleCategory(player, plot, "guest_pass");
            case 29 -> toggleCategory(player, plot, "alliance");
            case 30 -> toggleCategory(player, plot, "lockdown");
            case 32 -> toggleCategory(player, plot, "travel");
            case 33 -> toggleCategory(player, plot, "plot_notices");
            case 34 -> {
                playFlip(player);
                plugin.gui().settlementsInbox().open(player);
            }

            case 48 -> {
                playFlip(player);
                plugin.runMain(player, () -> plugin.gui().openMain(player));
            }

            case 49 -> {
                playClose(player);
                plugin.runMain(player, player::closeInventory);
            }
        }
    }

    private boolean canManageNotifications(Player player) {
        return player != null && player.hasPermission("aegis.notify");
    }

    private void placeCategoryToggle(Inventory inv, int slot, Player player, String category,
                                     boolean enabled, Material icon,
                                     String onKey, String onFallback,
                                     String offKey, String offFallback,
                                     String loreKey, List<String> loreFallback) {
        inv.setItem(slot, GUIManager.createItem(
                enabled ? icon : Material.GRAY_DYE,
                enabled ? t(player, onKey, onFallback) : t(player, offKey, offFallback),
                tl(player, loreKey, loreFallback)
        ));
    }

    private boolean getCategoryEnabled(Player player, String category) {
        try {
            if (plugin.getNotificationManager() != null) {
                return plugin.getNotificationManager().allowsCategory(player.getUniqueId(), category);
            }
        } catch (Throwable ignored) {}
        return true;
    }

    private void toggleCategory(Player player, Plot plot, String category) {
        if (!canManageNotifications(player)) {
            playError(player);
            return;
        }
        try {
            if (plugin.getNotificationManager() == null) {
                playError(player);
                return;
            }
            UUID uuid = player.getUniqueId();
            switch (category) {
                case "guest_pass" -> plugin.getNotificationManager().toggleGuestPassNotifications(uuid);
                case "alliance" -> plugin.getNotificationManager().toggleAllianceNotifications(uuid);
                case "lockdown" -> plugin.getNotificationManager().toggleLockdownNotifications(uuid);
                case "travel" -> plugin.getNotificationManager().toggleTravelNotifications(uuid);
                case "plot_notices" -> plugin.getNotificationManager().togglePlotNoticeNotifications(uuid);
                default -> {
                    playError(player);
                    return;
                }
            }
        } catch (Throwable error) {
            preferenceFailure(player, error);
            return;
        }
        playFlip(player);
        reopenNextTick(player, plot);
    }

    private void preferenceFailure(Player player, Throwable error) {
        plugin.getLogger().warning("Could not update notification preferences for "
                + player.getName() + ": " + error.getMessage());
        player.sendMessage(plugin.gui().tr(
                player,
                "notify_preference_save_failed",
                "&cYour notification preference could not be saved. Please try again."
        ));
        playError(player);
    }

    // --------------------------------------------------
    // Mirror helper (keeps config fallback consistent without becoming the source of truth)
    // --------------------------------------------------

    private void mirrorToConfig(UUID uuid, NotificationMode mode, Boolean greetings, Boolean adminUpdates) {
        try {
            String base = "player_notifications.players." + uuid;

            if (mode != null) {
                plugin.getConfig().set(base + ".mode", mode.getConfigValue());
                // Keep legacy key as a STRING (never boolean) to avoid the old collision
                plugin.getConfig().set("notifications." + uuid, mode.getConfigValue());
            }

            if (greetings != null) {
                plugin.getConfig().set(base + ".greetings", greetings);
            }

            if (adminUpdates != null) {
                plugin.getConfig().set(base + ".admin_updates", adminUpdates);
            }

            saveConfigSafe();
        } catch (Throwable ignored) {}
    }

    // --------------------------------------------------
    // 1.2.6 Notification storage helpers (backwards compatible)
    // --------------------------------------------------

    private String baseNotifPath(Player player) {
        return "player_notifications.players." + player.getUniqueId();
    }

    private String getNotifMode(Player player) {
        // ✅ Prefer NotificationManager if available
        try {
            if (plugin.getNotificationManager() != null) {
                PlayerNotificationSettings s = plugin.getNotificationManager().getSettings(player.getUniqueId());
                if (s != null && s.getMode() != null) {
                    return normalizeNotif(s.getMode().getConfigValue());
                }
            }
        } catch (Throwable ignored) {}

        String base = baseNotifPath(player);

        // Prefer structured key
        String mode = plugin.getConfig().getString(base + ".mode", null);
        if (mode != null && !mode.trim().isEmpty()) return normalizeNotif(mode);

        // Legacy key (string mode OR boolean collision from old /aegis notify)
        Object legacy = plugin.getConfig().get("notifications." + player.getUniqueId());
        if (legacy instanceof String) {
            return normalizeNotif((String) legacy);
        }

        return "ACTION_BAR";
    }

    private void setNotifMode(Player player, String mode) {
        String base = baseNotifPath(player);
        plugin.getConfig().set(base + ".mode", normalizeNotif(mode));
    }

    private boolean getGreetingsEnabled(Player player) {
        // ✅ Prefer NotificationManager if available
        try {
            if (plugin.getNotificationManager() != null) {
                PlayerNotificationSettings s = plugin.getNotificationManager().getSettings(player.getUniqueId());
                if (s != null) return s.isGreetingsEnabled();
            }
        } catch (Throwable ignored) {}

        String base = baseNotifPath(player);

        // Structured key
        if (plugin.getConfig().contains(base + ".greetings")) {
            return plugin.getConfig().getBoolean(base + ".greetings", true);
        }

        // Legacy collision: boolean stored under notifications.<uuid>
        Object legacy = plugin.getConfig().get("notifications." + player.getUniqueId());
        if (legacy instanceof Boolean) {
            return (Boolean) legacy;
        }

        return true;
    }

    private void setGreetingsEnabled(Player player, boolean enabled) {
        String base = baseNotifPath(player);
        plugin.getConfig().set(base + ".greetings", enabled);
    }

    private boolean getAdminUpdatesEnabled(Player player) {
        // ✅ Prefer NotificationManager if available
        try {
            if (plugin.getNotificationManager() != null) {
                PlayerNotificationSettings s = plugin.getNotificationManager().getSettings(player.getUniqueId());
                if (s != null) return s.isAdminUpdatesEnabled();
            }
        } catch (Throwable ignored) {}

        String base = baseNotifPath(player);
        return plugin.getConfig().getBoolean(base + ".admin_updates", true);
    }

    private boolean getRepeatNotificationsEnabled(Player player) {
        try {
            if (plugin.getNotificationManager() != null) {
                return plugin.getNotificationManager().hasRepeatNotificationsEnabled(player.getUniqueId());
            }
        } catch (Throwable ignored) {}
        return true;
    }

    private boolean canManageGreetingNotifications(Player player, boolean togglingState) {
        if (!player.hasPermission("aegis.notify")) return false;

        String base = "titles.claim_enter_exit";
        if (!plugin.getConfig().getBoolean(base + ".enabled", true)) {
            String bypassPermission = plugin.getConfig().getString(base + ".bypass_permission", "aegis.notify.bypass");
            return bypassPermission != null && !bypassPermission.isBlank() && player.hasPermission(bypassPermission);
        }

        String requiredPermission = plugin.getConfig().getString(base + ".required_permission", "");
        if (requiredPermission != null && !requiredPermission.isBlank() && !player.hasPermission(requiredPermission)) {
            return false;
        }

        if (!togglingState) return true;

        if (!plugin.getConfig().getBoolean(base + ".allow_player_toggle", true)) {
            return false;
        }

        String mode = plugin.getConfig().getString(base + ".mode", "PER_PLAYER").toUpperCase(Locale.ROOT);
        return "PER_PLAYER".equals(mode);
    }

    private void setAdminUpdatesEnabled(Player player, boolean enabled) {
        String base = baseNotifPath(player);
        plugin.getConfig().set(base + ".admin_updates", enabled);
    }

    // --------------------------------------------------
    // Display helpers
    // --------------------------------------------------

    private String normalizeNotif(String mode) {
        if (mode == null) return "ACTION_BAR";
        String m = mode.trim().toUpperCase(Locale.ROOT);
        return switch (m) {
            case "ACTION_BAR", "CHAT", "TITLE" -> m;
            default -> "ACTION_BAR";
        };
    }

    private String notifDisplay(Player p, String mode) {
        return switch (mode) {
            case "CHAT" -> t(p, "settings_notif_mode_chat", "&aChat");
            case "TITLE" -> t(p, "settings_notif_mode_title", "&dTitle");
            default -> t(p, "settings_notif_mode_action_bar", "&bAction Bar");
        };
    }

    private String formatStyle(Player player, String style) {
        if (style == null || style.isEmpty()) return t(player, "style_old_english", "&dOld English");

        return switch (style.toLowerCase(Locale.ROOT)) {
            case "old_english" -> t(player, "style_old_english", "&dOld English");
            case "modern_english" -> t(player, "style_modern_english", "&aModern English");
            case "spanish_mx" -> t(player, "style_spanish_mx", "&bEspañol (México)");
            case "spanish_ar" -> t(player, "style_spanish_ar", "&bEspañol (Argentina)");
            case "portuguese_br" -> t(player, "style_portuguese_br", "&bPortuguês (Brasil)");
            case "french_fr" -> t(player, "style_french_fr", "&bFrançais");
            case "italian_it" -> t(player, "style_italian_it", "&bItaliano");
            case "german_de" -> t(player, "style_german_de", "&bDeutsch");
            case "polish_pl" -> t(player, "style_polish_pl", "&bPolski");
            default -> "&f" + pretty(style);
        };
    }

    private String pretty(String raw) {
        String s = raw.replace('_', ' ').trim();
        if (s.isEmpty()) return raw;

        String[] parts = s.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            out.append(Character.toUpperCase(p.charAt(0)))
               .append(p.length() > 1 ? p.substring(1).toLowerCase(Locale.ROOT) : "")
               .append(' ');
        }
        return out.toString().trim();
    }
}
