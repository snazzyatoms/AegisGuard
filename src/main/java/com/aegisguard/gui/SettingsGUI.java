package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SettingsGUI
 * - Personal player preferences (sounds, language, notifications)
 * - Language cycling uses CodexEngine style order (codex.yml).
 * - ✅ Persistence is handled by CodexEngine (config.yml), NOT MessagesUtil.
 * - ✅ No savePlayerData/savePlayerPreferences calls here.
 *
 * + Admin tools:
 * - ✅ Refresh Language Packs (Codex reload)
 * - ✅ Reload All Settings (reload config + refresh managers + Codex reload)
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

    private String t(Player p, String key, Map<String, String> vars, String fallback) {
        // Prefer Codex map-aware translate if available; fall back to gui().tr
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(p, key, vars);
        } catch (Throwable ignored) {}

        String out = (raw == null || raw.isBlank() || raw.equalsIgnoreCase(key))
                ? plugin.gui().tr(p, key, fallback)
                : raw;

        // Apply vars to fallback-shaped strings too
        if (vars != null && !vars.isEmpty()) {
            for (var en : vars.entrySet()) {
                String k = en.getKey();
                String v = en.getValue() == null ? "" : en.getValue();
                out = out.replace("{" + k + "}", v).replace("{" + k.toLowerCase(Locale.ROOT) + "}", v);
            }
        }
        return out;
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

            String name = soundsEnabled
                    ? t(player, "settings_sounds_on_name", "&aSounds: ON")
                    : t(player, "settings_sounds_off_name", "&cSounds: OFF");

            inv.setItem(10, GUIManager.createItem(
                    soundsEnabled ? Material.NOTE_BLOCK : Material.JUKEBOX,
                    name,
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
                t(player, "settings_language_name",
                        Map.of("LANG", langDisplay),
                        "&eLanguage: {LANG}"
                ),
                tl(player, "settings_language_lore",
                        List.of("&7Click to cycle language styles."))
        ));

        // --------------------------------------------------
        // 3) NOTIFICATIONS (Slot 16)
        // --------------------------------------------------
        String mode = normalizeNotif(plugin.getConfig().getString("notifications." + player.getUniqueId(), "ACTION_BAR"));
        String modeDisplay = notifDisplay(player, mode);

        inv.setItem(16, GUIManager.createItem(
                Material.PAPER,
                t(player, "settings_notifications_name",
                        Map.of("MODE", modeDisplay),
                        "&bNotifications: {MODE}"
                ),
                tl(player, "settings_notifications_lore",
                        List.of("&7Click to cycle:", "&7Action Bar -> Chat -> Title"))
        ));

        // --------------------------------------------------
        // 4) ADMIN TOOLS (Refresh + Reload)
        // --------------------------------------------------
        if (plugin.isAdmin(player)) {

            // Slot 40: Refresh Language Packs (Codex only)
            inv.setItem(40, GUIManager.createItem(
                    Material.RECOVERY_COMPASS,
                    t(player, "settings_refresh_lang_name", "&a🔄 Refresh Language Packs"),
                    tl(player, "settings_refresh_lang_lore",
                            List.of("&7Reloads the Codex language bundles.", "&7Use after editing lang files.", " ", "&eClick to refresh"))
            ));

            // Slot 41: Reload All Settings (config + managers + codex)
            inv.setItem(41, GUIManager.createItem(
                    Material.REDSTONE,
                    t(player, "settings_reload_all_name", "&6⚙ Reload All Settings"),
                    tl(player, "settings_reload_all_lore",
                            List.of("&7Reloads config.yml and all systems.", "&7Also reloads language bundles.", " ", "&eClick to reload"))
            ));

        } else {
            // Optional: leave filler, or show a subtle locked item. Keeping filler avoids clutter.
        }

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
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (!(e.getInventory().getHolder() instanceof SettingsGUIHolder holder)) return;

        // Ignore clicks in the player's bottom inventory
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        Plot plot = holder.getPlot();

        switch (rawSlot) {

            case 10 -> { // Sounds
                if (!plugin.cfg().globalSoundsEnabled()) {
                    plugin.effects().playError(player);
                    return;
                }

                boolean current = plugin.isSoundEnabled(player);
                plugin.getConfig().set("sounds.players." + player.getUniqueId(), !current);
                plugin.runGlobalAsync(plugin::saveConfig);

                plugin.effects().playMenuFlip(player);
                open(player, plot);
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

                open(player, plot);
            }

            case 16 -> { // Notifications
                String mode = normalizeNotif(plugin.getConfig().getString("notifications." + player.getUniqueId(), "ACTION_BAR"));
                String nextMode = switch (mode) {
                    case "ACTION_BAR" -> "CHAT";
                    case "CHAT" -> "TITLE";
                    default -> "ACTION_BAR";
                };

                plugin.getConfig().set("notifications." + player.getUniqueId(), nextMode);
                plugin.runGlobalAsync(plugin::saveConfig);

                plugin.effects().playMenuFlip(player);
                open(player, plot);
            }

            // --------------------------------------------
            // Admin tools
            // --------------------------------------------

            case 40 -> { // Refresh Language Packs (Codex reload)
                if (!plugin.isAdmin(player)) {
                    plugin.effects().playError(player);
                    return;
                }
                plugin.effects().playMenuFlip(player);

                plugin.runMainGlobal(() -> {
                    try {
                        if (plugin.codex() != null) plugin.codex().reload();
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[SettingsGUI] Codex refresh failed: " + t.getMessage());
                    }
                });

                // Re-open so text updates immediately in the new bundles
                plugin.runMain(player, () -> open(player, plot));
            }

            case 41 -> { // Reload All Settings (config + managers + codex)
                if (!plugin.isAdmin(player)) {
                    plugin.effects().playError(player);
                    return;
                }
                plugin.effects().playMenuFlip(player);

                plugin.runMainGlobal(() -> {
                    try {
                        // Reload config.yml from disk
                        plugin.reloadConfig();
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[SettingsGUI] reloadConfig() failed: " + t.getMessage());
                    }

                    // Try to reload AGConfig manager if it exposes a reload-style method
                    tryInvokeNoArg(plugin.cfg(), "reload", "load", "refresh", "reloadAll", "reloadConfig");

                    // Reload Codex language engine
                    try {
                        if (plugin.codex() != null) plugin.codex().reload();
                    } catch (Throwable t) {
                        plugin.getLogger().warning("[SettingsGUI] Codex reload failed: " + t.getMessage());
                    }
                });

                plugin.runMain(player, () -> open(player, plot));
            }

            case 48 -> {
                plugin.effects().playMenuFlip(player);
                plugin.gui().openMain(player);
            }

            case 49 -> {
                plugin.effects().playMenuClose(player);
                player.closeInventory();
            }
        }
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
        if (style == null || style.isEmpty()) return t(player, "style_old_english", "§dOld English");

        return switch (style.toLowerCase(Locale.ROOT)) {
            case "old_english" -> t(player, "style_old_english", "§dOld English");
            case "modern_english" -> t(player, "style_modern_english", "§aModern");
            case "hybrid_english" -> t(player, "style_hybrid_english", "§eHybrid");
            case "spanish_mx" -> t(player, "style_spanish_mx", "§bEspañol (LatAm)");
            case "spanish_ar" -> t(player, "style_spanish_ar", "§bEspañol (AR)");
            default -> "§f" + pretty(style);
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

    // --------------------------------------------------
    // Reflection helper (safe no-arg invoke)
    // --------------------------------------------------

    private static void tryInvokeNoArg(Object target, String... methodNames) {
        if (target == null || methodNames == null) return;
        for (String name : methodNames) {
            if (name == null || name.isBlank()) continue;
            try {
                Method m = target.getClass().getMethod(name);
                m.setAccessible(true);
                m.invoke(target);
                return; // success, stop
            } catch (Throwable ignored) {
                // try next
            }
        }
    }
}
