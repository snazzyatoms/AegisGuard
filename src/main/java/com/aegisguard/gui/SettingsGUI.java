package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class SettingsGUI {

    private final AegisGuard plugin;

    public SettingsGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class SettingsHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        
        // Title: "Personal Settings"
        String title = lang.getGui("title_settings");
        if (title.contains("Missing")) title = "§8Personal Settings";

        Inventory inv = Bukkit.createInventory(new SettingsHolder(), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // --- 1. SOUNDS (Slot 10) ---
        boolean globalEnabled = plugin.getConfig().getBoolean("sounds.global_enabled", true);
        if (!globalEnabled) {
            inv.setItem(10, GUIManager.createItem(Material.BARRIER, 
                "§cSounds Disabled", 
                List.of("§7Server admin has disabled sounds.")
            ));
        } else {
            boolean soundsEnabled = plugin.isSoundEnabled(player);
            inv.setItem(10, GUIManager.createItem(
                soundsEnabled ? Material.NOTE_BLOCK : Material.JUKEBOX,
                soundsEnabled ? "§aSounds: ON" : "§cSounds: OFF",
                List.of("§7Toggle UI sound effects.", "§eClick to Toggle")
            ));
        }

        // --- 2. LANGUAGE (Slot 13) ---
        // We detect the current file name (e.g., "en_old") to display the style
        String currentFile = plugin.getConfig().getString("players." + player.getUniqueId() + ".lang", 
                             plugin.getConfig().getString("settings.default_language_file", "en_old.yml"));
        
        inv.setItem(13, GUIManager.createItem(
            Material.WRITABLE_BOOK,
            "§eLanguage: " + formatStyle(currentFile),
            List.of("§7Click to cycle styles:", "§fModern ➡ Hybrid ➡ Old")
        ));

        // --- 3. NOTIFICATIONS (Slot 16) ---
        String notifMode = plugin.getConfig().getString("players." + player.getUniqueId() + ".notifications", "ACTION_BAR");
        inv.setItem(16, GUIManager.createItem(
            Material.PAPER,
            "§bNotifications: " + notifMode,
            List.of("§7Where do messages appear?", "§eClick to Cycle")
        ));

        // --- Navigation ---
        inv.setItem(22, GUIManager.createItem(Material.ARROW, lang.getGui("button_back")));

        player.openInventory(inv);
        // plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        switch (e.getSlot()) {
            // Sounds
            case 10:
                if (!plugin.getConfig().getBoolean("sounds.global_enabled", true)) {
                    // Error sound
                } else {
                    boolean current = plugin.isSoundEnabled(player);
                    plugin.getConfig().set("sounds.players." + player.getUniqueId(), !current);
                    plugin.saveConfig(); // In production, use async save
                    open(player);
                }
                break;

            // Language
            case 13:
                cycleLanguage(player);
                open(player);
                break;

            // Notifications
            case 16:
                cycleNotifications(player);
                open(player);
                break;

            // Back
            case 22:
                plugin.getGuiManager().openGuardianCodex(player);
                break;
        }
        
        GUIManager.playClick(player);
    }

    private void cycleLanguage(Player p) {
        String current = plugin.getConfig().getString("players." + p.getUniqueId() + ".lang", "en_old.yml");
        String next;
        
        if (current.contains("modern")) next = "en_hybrid.yml";
        else if (current.contains("hybrid")) next = "en_old.yml";
        else next = "en_modern.yml";
        
        plugin.getLanguageManager().setPlayerLang(p, next);
        // Save to config so it persists
        plugin.getConfig().set("players." + p.getUniqueId() + ".lang", next);
        plugin.saveConfig();
    }
    
    private void cycleNotifications(Player p) {
        String current = plugin.getConfig().getString("players." + p.getUniqueId() + ".notifications", "ACTION_BAR");
        String next;
        
        if (current.equals("ACTION_BAR")) next = "CHAT";
        else if (current.equals("CHAT")) next = "TITLE";
        else next = "ACTION_BAR";
        
        plugin.getConfig().set("players." + p.getUniqueId() + ".notifications", next);
        plugin.saveConfig();
    }

    private String formatStyle(String filename) {
        if (filename.contains("modern")) return "§aModern";
        if (filename.contains("hybrid")) return "§eHybrid";
        return "§dOld English";
    }
}
