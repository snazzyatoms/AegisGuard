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

/**
 * SettingsGUI
 * - Handles *personal* player preferences (sounds, language).
 */
public class SettingsGUI {

    private final AegisGuard plugin;

    public SettingsGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Tag holder that stores the plot being edited (or null).
     * Must be PUBLIC for GUIListener.
     */
    public static class SettingsGUIHolder implements InventoryHolder {
        private final Plot plot;

        public SettingsGUIHolder(Plot plot) {
            this.plot = plot;
        }

        public Plot getPlot() {
            return plot;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /* -----------------------------
     * Open Settings Menu
     * ----------------------------- */
    public void open(Player player, Plot plot) {
        Inventory inv = Bukkit.createInventory(new SettingsGUIHolder(plot), 54,
                GUIManager.safeText(plugin.msg().get(player, "settings_menu_title"), "§bAegisGuard — Settings")
        );

        // --- Sounds ---
        boolean globalEnabled = plugin.cfg().globalSoundsEnabled();
        if (!globalEnabled) {
            inv.setItem(10, GUIManager.icon(
                    Material.BARRIER,
                    GUIManager.safeText(plugin.msg().get(player, "button_sounds_disabled_global"), "§cSounds Disabled By Admin"),
                    plugin.msg().getList(player, "sounds_toggle_global_disabled_lore")
            ));
        } else {
            boolean soundsEnabled = plugin.isSoundEnabled(player);
            inv.setItem(10, GUIManager.icon(
                    soundsEnabled ? Material.NOTE_BLOCK : Material.RED_DYE,
                    soundsEnabled ? GUIManager.safeText(plugin.msg().get(player, "button_sounds_on"), "§aSounds: ON")
                                  : GUIManager.safeText(plugin.msg().get(player, "button_sounds_off"), "§cSounds: OFF"),
                    plugin.msg().getList(player, "sounds_toggle_lore")
            ));
        }

        // --- Protection Dummies (Show global defaults, but don't toggle) ---
        inv.setItem(11, GUIManager.icon(Material.IRON_SWORD, "§7PvP Protection (Global Default)", plugin.msg().getList(player, "pvp_toggle_lore")));
        inv.setItem(12, GUIManager.icon(Material.CHEST, "§7Container Protection (Global Default)", plugin.msg().getList(player, "container_toggle_lore")));
        inv.setItem(13, GUIManager.icon(Material.ZOMBIE_HEAD, "§7Mob Protection (Global Default)", plugin.msg().getList(player, "mob_toggle_lore")));
        inv.setItem(14, GUIManager.icon(Material.BONE, "§7Pet Protection (Global Default)", plugin.msg().getList(player, "pet_toggle_lore")));
        inv.setItem(15, GUIManager.icon(Material.ARMOR_STAND, "§7Entity Protection (Global Default)", plugin.msg().getList(player, "entity_toggle_lore")));
        inv.setItem(16, GUIManager.icon(Material.WHEAT, "§7Farm Protection (Global Default)", plugin.msg().getList(player, "farm_toggle_lore")));
        inv.setItem(17, GUIManager.icon(Material.SHIELD, "§7Safe Zone (Global Default)", plugin.msg().getList(player, "safe_toggle_lore")));


        /* -----------------------------
         * Language Style Selection
         * ----------------------------- */
        String currentStyle = plugin.msg().getPlayerStyle(player);
        Material icon;
        switch (currentStyle) {
            case "modern_english": icon = Material.BOOK; break;
            case "hybrid_english": icon = Material.ENCHANTED_BOOK; break;
            default: icon = Material.WRITABLE_BOOK; break;
        }

        inv.setItem(31, GUIManager.icon(
                icon,
                "§b🕮 " + GUIManager.safeText(plugin.msg().get(player, "language_style_title"), "Language: {STYLE}")
                            .replace("{STYLE}", formatStyle(currentStyle)),
                plugin.msg().getList(player, "language_style_lore")
        ));

        // Navigation
        inv.setItem(48, GUIManager.icon(
                Material.ARROW,
                GUIManager.safeText(plugin.msg().get(player, "button_back"), "§fBack"),
                plugin.msg().getList(player, "back_lore")
        ));

        inv.setItem(49, GUIManager.icon(
                Material.BARRIER,
                GUIManager.safeText(plugin.msg().get(player, "button_exit"), "§cExit"),
                plugin.msg().getList(player, "exit_lore")
        ));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }
    
    // Overload for convenience
    public void open(Player player) {
        open(player, null);
    }

    /* -----------------------------
     * Handle Clicks (slot-based)
     * This method is called by GUIListener
     * ----------------------------- */
    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (!(e.getInventory().getHolder() instanceof SettingsGUIHolder holder)) {
            return;
        }
        Plot plot = holder.getPlot();

        int slot = e.getRawSlot();
        switch (slot) {
            case 10: // Sounds Toggle
                boolean globalEnabled = plugin.cfg().globalSoundsEnabled();
                if (!globalEnabled) {
                    plugin.effects().playError(player);
                } else {
                    boolean currentlyEnabled = plugin.isSoundEnabled(player);
                    plugin.getConfig().set("sounds.players." + player.getUniqueId(), !currentlyEnabled);

                    // Save Async
                    plugin.runGlobalAsync(() -> {
                        plugin.saveConfig();
                    });

                    plugin.effects().playMenuFlip(player);
                }
                break;
            
            // Slots 11-17 are dummy buttons
            case 11: case 12: case 13: case 14: case 15: case 16: case 17:
                plugin.effects().playError(player);
                plugin.msg().send(player, "settings_gui_not_toggle");
                break;
            
            case 31: // Language cycle
                if (!plugin.cfg().raw().getBoolean("messages.allow_runtime_switch", true)) {
                    plugin.effects().playError(player);
                    plugin.msg().send(player, "language_switch_disabled");
                    return;
                }
                
                String current = plugin.msg().getPlayerStyle(player);
                String next;
                if (current.equals("old_english")) next = "hybrid_english";
                else if (current.equals("hybrid_english")) next = "modern_english";
                else next = "old_english";
                
                plugin.msg().setPlayerStyle(player, next);
                plugin.effects().playMenuFlip(player);
                
                open(player, plot); 
                return; 

            case 48: // Back
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
                return; 
            
            case 49: // Exit
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                return;
            
            default: 
                /* ignore filler */
                break;
        }

        open(player, plot); // Refresh GUI instantly
    }

    private String formatStyle(String style) {
        switch (style) {
            case "modern_english": return "§aModern English";
            case "hybrid_english": return "§eHybrid English";
            default: return "§dOld English";
        }
    }
}
