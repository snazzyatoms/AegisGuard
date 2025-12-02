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

/**
 * SettingsGUI
 * - Handles personal player preferences (sounds, language, notifications).
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

    /* -----------------------------
     * OPEN
     * ----------------------------- */
    public void open(Player player) { open(player, null); }

    public void open(Player player, Plot plot) {
        String title = GUIManager.safeText(plugin.msg().get(player, "settings_menu_title"), "§bAegisGuard — Settings");
        Inventory inv = Bukkit.createInventory(new SettingsGUIHolder(plot), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // --- 1. SOUNDS (Slot 10) ---
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

        // --- 2. LANGUAGE (Slot 13) ---
        String currentStyle = plugin.msg().getPlayerStyle(player);
        inv.setItem(13, GUIManager.createItem(
            Material.WRITABLE_BOOK,
            "§eLanguage: " + formatStyle(currentStyle),
            List.of("§7Click to cycle language styles.")
        ));
        
        // --- 3. NOTIFICATIONS (Slot 16) ---
        // (Assuming you store this in config per player, simplified here)
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

    /* -----------------------------
     * CLICK HANDLER
     * ----------------------------- */
    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (!(e.getInventory().getHolder() instanceof SettingsGUIHolder holder)) return;
        Plot plot = holder.getPlot(); // Preserve context if passed

        switch (e.getRawSlot()) {
            case 10: // Sounds
                if (!plugin.cfg().globalSoundsEnabled()) {
                    plugin.effects().playError(player);
                } else {
                    boolean current = plugin.isSoundEnabled(player);
                    plugin.getConfig().set("sounds.players." + player.getUniqueId(), !current);
                    plugin.runGlobalAsync(() -> plugin.saveConfig());
                    plugin.effects().playMenuFlip(player);
                    open(player, plot);
                }
                break;

            case 13: // Language
                String style = plugin.msg().getPlayerStyle(player);
                String nextStyle = switch (style) {
                    case "old_english" -> "modern_english";
                    case "modern_english" -> "hybrid_english";
                    default -> "old_english";
                };
                plugin.msg().setPlayerStyle(player, nextStyle);
                plugin.effects().playMenuFlip(player);
                open(player, plot);
                break;
                
            case 16: // Notifications
                String mode = plugin.getConfig().getString("notifications." + player.getUniqueId(), "ACTION_BAR");
                String nextMode = switch (mode) {
                    case "ACTION_BAR" -> "CHAT";
                    case "CHAT" -> "TITLE";
                    default -> "ACTION_BAR";
                };
                plugin.getConfig().set("notifications." + player.getUniqueId(), nextMode);
                plugin.runGlobalAsync(() -> plugin.saveConfig());
                plugin.effects().playMenuFlip(player);
                open(player, plot);
                break;

            case 48: plugin.gui().openMain(player); break;
            case 49: player.closeInventory(); break;
        }
    }

    private String formatStyle(String style) {
        return switch (style) {
            case "modern_english" -> "§aModern";
            case "hybrid_english" -> "§eHybrid";
            default -> "§dOld English"; // Default
        };
    }
}
