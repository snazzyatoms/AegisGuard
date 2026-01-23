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
 * IMPORTANT:
 * - ❌ No admin tools in this menu anymore.
 *   Admin-only actions belong in AdminGUI.
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
        boolean globalEnabled = plugin.cfg().globalSoundsEnabled();
        if (!globalEnabled) {
            inv.setItem(10, GUIManager.createItem(
                    Material.BARRIER,
                    t(player, "settings_sounds_global_off_name", "&cSounds Disabled Globally"),
                    tl(player, "settings_sounds_global_off_lore",
                            List.of("&7A server admin has disabled UI sounds."))
            ));
        } else {
            boolean soundsEnabled = plugin.isSoundEnabled(player);

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
        String currentStyle = "old_english";
        try {
            if (plugin.codex() != null) currentStyle = plugin.codex().getPlayerStyle(player);
        } catch (Throwable ignored) {}

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

        // --------------------------------------------------
        // 3) NOTIFICATIONS MODE (Slot 16) - v1.2.6: Using NotificationManager
        // --------------------------------------------------
        String modeDisplay;
        if (plugin.getNotificationManager() != null) {
            com.aegisguard.notify.PlayerNotificationSettings settings =
                plugin.getNotificationManager().getSettings(player.getUniqueId());
            modeDisplay = notifDisplay(player, settings.getMode().getConfigValue());
        } else {
            // Fallback to local method if NotificationManager unavailable
            String mode = getNotifMode(player);
            modeDisplay = notifDisplay(player, mode);
        }

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
        // ✅ Only handle clicks in the TOP inventory (prevents weird cursor focus + misfires)
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        if (!(e.getInventory().getHolder() instanceof SettingsGUIHolder holder)) return;

        e.setCancelled(true);
        e.setResult(Event.Result.DENY);

        ItemStack currentItem = e.getCurrentItem();
        if (currentItem == null || currentItem.getType() == Material.AIR) return;

        // Ignore filler clicks silently
        if (currentItem.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        // Raw slot is safe now because we already confirmed TOP inventory
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        Plot plot = holder.getPlot();
        UUID uuid = player.getUniqueId();

        switch (rawSlot) {

            case 10 -> { // Sounds
                if (!plugin.cfg().globalSoundsEnabled()) {
                    plugin.effects().playError(player);
                    return;
                }

                boolean current = plugin.isSoundEnabled(player);
                plugin.getConfig().set("sounds.players." + uuid, !current);
                plugin.runGlobalAsync(plugin::saveConfig);

                plugin.effects().playMenuFlip(player);

                // ✅ Reopen NEXT tick (prevents flash + cursor snapping)
                plugin.runMain(player, () -> open(player, plot));
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

                // ✅ Reopen NEXT tick (prevents flash + cursor snapping)
                plugin.runMain(player, () -> open(player, plot));
            }

            case 16 -> { // Notifications MODE - v1.2.6: Using NotificationManager
                if (plugin.getNotificationManager() != null) {
                    // Use NotificationManager to cycle modes
                    plugin.getNotificationManager().cycleMode(player.getUniqueId());
                } else {
                    // Fallback to local method
                    String mode = getNotifMode(player);
                    String nextMode = switch (mode) {
                        case "ACTION_BAR" -> "CHAT";
                        case "CHAT" -> "TITLE";
                        default -> "ACTION_BAR";
                    };
                    setNotifMode(player, nextMode);

                    // Keep legacy key as MODE string for compatibility
                    plugin.getConfig().set("notifications." + uuid, nextMode);
                    plugin.runGlobalAsync(plugin::saveConfig);
                }

                plugin.effects().playMenuFlip(player);

                // ✅ Reopen NEXT tick
                plugin.runMain(player, () -> open(player, plot));
            }

            case 19 -> { // Plot Greetings toggle (enter/leave)
                boolean current = getGreetingsEnabled(player);
                setGreetingsEnabled(player, !current);

                // If legacy key was a boolean, convert it into a MODE string to prevent future collisions.
                Object legacy = plugin.getConfig().get("notifications." + uuid);
                if (legacy instanceof Boolean) {
                    // Preserve current mode if possible, otherwise default.
                    String mode = getNotifMode(player);
                    plugin.getConfig().set("notifications." + uuid, mode);
                }

                plugin.runGlobalAsync(plugin::saveConfig);
                plugin.effects().playMenuFlip(player);
                plugin.runMain(player, () -> open(player, plot));
            }

            case 22 -> { // Admin Updates toggle (approvals/denials/mod actions)
                boolean current = getAdminUpdatesEnabled(player);
                setAdminUpdatesEnabled(player, !current);

                plugin.runGlobalAsync(plugin::saveConfig);
                plugin.effects().playMenuFlip(player);
                plugin.runMain(player, () -> open(player, plot));
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
