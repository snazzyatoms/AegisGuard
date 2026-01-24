package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
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

    private void saveConfigSafe() {
        try {
            plugin.saveConfig();
        } catch (Throwable t) {
            try { plugin.getLogger().warning("[SettingsGUI] saveConfig failed: " + t.getMessage()); } catch (Throwable ignored) {}
        }
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
        // 3) NOTIFICATIONS MODE (Slot 16) - v1.2.6: NotificationManager aware
        // --------------------------------------------------
        String mode = "ACTION_BAR";
        try {
            if (plugin.getNotificationManager() != null) {
                var settings = plugin.getNotificationManager().getSettings(player.getUniqueId());
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
        // 3B) PLOT GREETINGS TOGGLE (Slot 19) - Enter/Leave messages
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
        // 3C) ADMIN UPDATES TOGGLE (Slot 22) - approvals/denials, admin decisions
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
        plugin.effects().playMenuOpen(player);
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
                    plugin.effects().playError(player);
                    return;
                }

                boolean current = false;
                try { current = plugin.isSoundEnabled(player); } catch (Throwable ignored) {}

                plugin.getConfig().set("sounds.players." + uuid, !current);
                saveConfigSafe();

                plugin.effects().playMenuFlip(player);
                reopenNextTick(player, plot);
            }

            case 13 -> { // Language (Codex ordered cycle + persisted by CodexEngine)
                if (plugin.codex() == null) {
                    plugin.effects().playError(player);
                    return;
                }

                String current = plugin.codex().getPlayerStyle(player);
                String next = plugin.codex().getNextStyle(current);

                boolean applied = plugin.codex().setPlayerStyle(player, next);
                if (applied) plugin.effects().playMenuFlip(player);
                else plugin.effects().playError(player);

                reopenNextTick(player, plot);
            }

            case 16 -> { // Notifications MODE
                try {
                    if (plugin.getNotificationManager() != null) {
                        plugin.getNotificationManager().cycleMode(uuid);
                    } else {
                        String mode = getNotifMode(player);
                        String nextMode = switch (mode) {
                            case "ACTION_BAR" -> "CHAT";
                            case "CHAT" -> "TITLE";
                            default -> "ACTION_BAR";
                        };

                        setNotifMode(player, nextMode);

                        // Legacy compatibility
                        plugin.getConfig().set("notifications." + uuid, nextMode);
                        saveConfigSafe();
                    }
                } catch (Throwable ignored) {}

                plugin.effects().playMenuFlip(player);
                reopenNextTick(player, plot);
            }

            case 19 -> { // Plot Greetings toggle (enter/leave)
                boolean current = getGreetingsEnabled(player);
                setGreetingsEnabled(player, !current);

                // Legacy collision: boolean stored under notifications.<uuid>
                Object legacy = plugin.getConfig().get("notifications." + uuid);
                if (legacy instanceof Boolean) {
                    String mode = getNotifMode(player);
                    plugin.getConfig().set("notifications." + uuid, mode);
                }

                saveConfigSafe();
                plugin.effects().playMenuFlip(player);
                reopenNextTick(player, plot);
            }

            case 22 -> { // Admin Updates toggle
                boolean current = getAdminUpdatesEnabled(player);
                setAdminUpdatesEnabled(player, !current);

                saveConfigSafe();
                plugin.effects().playMenuFlip(player);
                reopenNextTick(player, plot);
            }

            case 48 -> {
                plugin.effects().playMenuFlip(player);
                plugin.runMain(player, () -> plugin.gui().openMain(player));
            }

            case 49 -> {
                plugin.effects().playMenuClose(player);
                plugin.runMain(player, player::closeInventory);
            }
        }
    }

    // --------------------------------------------------
    // 1.2.6 Notification storage helpers (backwards compatible)
    // --------------------------------------------------

    private String baseNotifPath(Player player) {
        return "player_notifications.players." + player.getUniqueId();
    }

    private String getNotifMode(Player player) {
        String base = baseNotifPath(player);

        // Prefer new structured key
        String mode = plugin.getConfig().getString(base + ".mode", null);
        if (mode != null && !mode.trim().isEmpty()) return normalizeNotif(mode);

        // Legacy key (string mode OR boolean toggle from old /aegis notify)
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
        String base = baseNotifPath(player);

        // New structured key
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
        String base = baseNotifPath(player);
        return plugin.getConfig().getBoolean(base + ".admin_updates", true);
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
            case "modern_english" -> t(player, "style_modern_english", "&aModern");
            case "hybrid_english" -> t(player, "style_hybrid_english", "&eHybrid");
            case "spanish_mx" -> t(player, "style_spanish_mx", "&bEspañol (LatAm)");
            case "spanish_ar" -> t(player, "style_spanish_ar", "&bEspañol (AR)");
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
