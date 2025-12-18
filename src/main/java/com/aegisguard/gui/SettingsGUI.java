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

import java.util.List;
import java.util.Locale;

/**
 * SettingsGUI
 * - Personal player preferences (sounds, language, notifications)
 * - Language cycling uses CodexEngine style order (codex.yml).
 * - Language persistence is handled via MessagesUtil/playerdata.yml (autosave).
 * - ✅ NO CodexEngine.savePlayerData() calls (prevents compile errors).
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

        // --- 1) SOUNDS (Slot 10) ---
        boolean globalEnabled = plugin.cfg().globalSoundsEnabled();
        if (!globalEnabled) {
            inv.setItem(10, GUIManager.createItem(
                    Material.BARRIER,
                    "§cSounds Disabled Globally",
                    List.of("§7Server admin has disabled sounds.")
            ));
        } else {
            boolean soundsEnabled = plugin.isSoundEnabled(player);
            inv.setItem(10, GUIManager.createItem(
                    soundsEnabled ? Material.NOTE_BLOCK : Material.JUKEBOX,
                    soundsEnabled ? "§aSounds: ON" : "§cSounds: OFF",
                    List.of("§7Toggle UI sound effects.")
            ));
        }

        // --- 2) LANGUAGE (Slot 13) ---
        String currentStyle = getCurrentStyle(player);

        inv.setItem(13, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                "§eLanguage: " + formatStyle(currentStyle),
                List.of("§7Click to cycle language styles.")
        ));

        // --- 3) NOTIFICATIONS (Slot 16) ---
        String notifMode = plugin.getConfig().getString("notifications." + player.getUniqueId(), "ACTION_BAR");
        inv.setItem(16, GUIManager.createItem(
                Material.PAPER,
                "§bNotifications: " + notifMode,
                List.of("§7Click to cycle:", "§7Action Bar -> Chat -> Title")
        ));

        // --- NAVIGATION ---
        inv.setItem(48, GUIManager.createItem(Material.ARROW, "§fBack to Menu", null));
        inv.setItem(49, GUIManager.createItem(Material.BARRIER, "§cClose", null));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (!(e.getInventory().getHolder() instanceof SettingsGUIHolder holder)) return;
        Plot plot = holder.getPlot();

        switch (e.getRawSlot()) {

            case 10: { // Sounds
                if (!plugin.cfg().globalSoundsEnabled()) {
                    plugin.effects().playError(player);
                    return;
                }
                boolean current = plugin.isSoundEnabled(player);
                plugin.getConfig().set("sounds.players." + player.getUniqueId(), !current);
                plugin.runGlobalAsync(plugin::saveConfig);
                plugin.effects().playMenuFlip(player);
                open(player, plot);
                break;
            }

            case 13: { // Language (Codex ordered cycle)
                if (plugin.codex() == null) {
                    plugin.effects().playError(player);
                    return;
                }

                String current = getCurrentStyle(player);
                String next = plugin.codex().getNextStyle(current);

                // ✅ Persist to playerdata.yml via MessagesUtil (no direct save call here)
                if (plugin.msg() != null) {
                    plugin.msg().setPlayerStyle(player, next);
                }

                // ✅ Also update Codex runtime style (instant UI translation refresh)
                try { plugin.codex().setPlayerStyle(player, next); } catch (Throwable ignored) {}

                plugin.effects().playMenuFlip(player);
                open(player, plot);
                break;
            }

            case 16: { // Notifications
                String mode = plugin.getConfig().getString("notifications." + player.getUniqueId(), "ACTION_BAR");
                String nextMode = switch (mode) {
                    case "ACTION_BAR" -> "CHAT";
                    case "CHAT" -> "TITLE";
                    default -> "ACTION_BAR";
                };
                plugin.getConfig().set("notifications." + player.getUniqueId(), nextMode);
                plugin.runGlobalAsync(plugin::saveConfig);
                plugin.effects().playMenuFlip(player);
                open(player, plot);
                break;
            }

            case 48:
                plugin.gui().openMain(player);
                break;

            case 49:
                player.closeInventory();
                break;
        }
    }

    private String getCurrentStyle(Player player) {
        // Prefer persisted prefs (playerdata.yml) if MessagesUtil exists
        try {
            if (plugin.msg() != null) {
                String s = plugin.msg().getPlayerStyle(player);
                if (s != null && !s.isBlank()) return s;
            }
        } catch (Throwable ignored) {}

        // Fallback to Codex runtime cache if present
        try {
            if (plugin.codex() != null) {
                String s = plugin.codex().getPlayerStyle(player);
                if (s != null && !s.isBlank()) return s;
            }
        } catch (Throwable ignored) {}

        return "old_english";
    }

    private String formatStyle(String style) {
        if (style == null || style.isEmpty()) return "§dOld English";

        return switch (style.toLowerCase(Locale.ROOT)) {
            case "old_english" -> "§dOld English";
            case "modern_english" -> "§aModern";
            case "hybrid_english" -> "§eHybrid";
            case "spanish_mx" -> "§bEspañol (LatAm)";
            case "spanish_ar" -> "§bEspañol (AR)";
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
}
