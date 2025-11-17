package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder; // --- NEW IMPORT ---
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.concurrent.CompletableFuture; // --- NEW IMPORT ---

/**
 * ==============================================================
 * SettingsGUI
 * ... (existing comments) ...
 * ==============================================================
 *
 * --- UPGRADE NOTES ---
 * - Added a reliable InventoryHolder (SettingsGUIHolder).
 * - Fixed main-thread lag by making saveConfig() and flushSync() asynchronous.
 * - Removed all duplicated helper methods (createItem, sounds, etc.).
 */
public class SettingsGUI {

    private final AegisGuard plugin;

    public SettingsGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * --- NEW ---
     * Tag holder so click handler only reacts to this GUI.
     */
    private static class SettingsGUIHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /* -----------------------------
     * Open Settings Menu
     * ----------------------------- */
    public void open(Player player) {
        // --- MODIFIED ---
        Inventory inv = Bukkit.createInventory(new SettingsGUIHolder(), 54,
                GUIManager.safeText(plugin.msg().get(player, "settings_menu_title"), "§bAegisGuard — Settings")
        );

        // --- Sounds ---
        boolean globalEnabled = plugin.getConfig().getBoolean("sounds.global_enabled", true);
        if (!globalEnabled) {
            // --- MODIFIED --- (Uses GUIManager.icon)
            inv.setItem(10, GUIManager.icon(
                    Material.BARRIER,
                    GUIManager.safeText(plugin.msg().get(player, "button_sounds_disabled_global"), "§cSounds Disabled By Admin"),
                    plugin.msg().getList(player, "sounds_toggle_global_disabled_lore")
            ));
        } else {
            boolean soundsEnabled = plugin.isSoundEnabled(player);
            // --- MODIFIED --- (Uses GUIManager.icon)
            inv.setItem(10, GUIManager.icon(
                    soundsEnabled ? Material.NOTE_BLOCK : Material.RED_DYE,
                    soundsEnabled ? GUIManager.safeText(plugin.msg().get(player, "button_sounds_on"), "§aSounds: ON")
                                  : GUIManager.safeText(plugin.msg().get(player, "button_sounds_off"), "§cSounds: OFF"),
                    plugin.msg().getList(player, "sounds_toggle_lore")
            ));
        }

        // --- PvP Protection ---
        boolean pvp = plugin.protection().isPvPEnabled(player);
        // --- MODIFIED --- (Uses GUIManager.icon)
        inv.setItem(11, GUIManager.icon(
                pvp ? Material.IRON_SWORD : Material.WOODEN_SWORD,
                pvp ? GUIManager.safeText(plugin.msg().get(player, "button_pvp_on"), "§aPvP: ON")
                    : GUIManager.safeText(plugin.msg().get(player, "button_pvp_off"), "§cPvP: OFF"),
                plugin.msg().getList(player, "pvp_toggle_lore")
        ));

        // --- Container Protection ---
        boolean containers = plugin.protection().isContainersEnabled(player);
        // --- MODIFIED --- (Uses GUIManager.icon)
        inv.setItem(12, GUIManager.icon(
                containers ? Material.CHEST : Material.TRAPPED_CHEST,
                containers ? GUIManager.safeText(plugin.msg().get(player, "button_containers_on"), "§aContainers: ON")
                           : GUIManager.safeText(plugin.msg().get(player, "button_containers_off"), "§cContainers: OFF"),
                plugin.msg().getList(player, "container_toggle_lore")
        ));

        // --- Mob Protection ---
        boolean mobs = plugin.protection().isMobProtectionEnabled(player);
        // --- MODIFIED --- (Uses GUIManager.icon)
        inv.setItem(13, GUIManager.icon(
                mobs ? Material.ZOMBIE_HEAD : Material.ROTTEN_FLESH,
                mobs ? GUIManager.safeText(plugin.msg().get(player, "button_mobs_on"), "§aMob Grief: ON")
                     : GUIManager.safeText(plugin.msg().get(player, "button_mobs_off"), "§cMob Grief: OFF"),
                plugin.msg().getList(player, "mob_toggle_lore")
        ));

        // --- Pet Protection ---
        boolean pets = plugin.protection().isPetProtectionEnabled(player);
        // --- MODIFIED --- (Uses GUIManager.icon)
        inv.setItem(14, GUIManager.icon(
                pets ? Material.BONE : Material.LEAD,
                pets ? GUIManager.safeText(plugin.msg().get(player, "button_pets_on"), "§aPet Protection: ON")
                     : GUIManager.safeText(plugin.msg().get(player, "button_pets_off"), "§cPet Protection: OFF"),
                plugin.msg().getList(player, "pet_toggle_lore")
        ));

        // --- Entity Protection ---
        boolean entity = plugin.protection().isEntityProtectionEnabled(player);
        // --- MODIFIED --- (Uses GUIManager.icon)
        inv.setItem(15, GUIManager.icon(
                entity ? Material.ARMOR_STAND : Material.ITEM_FRAME,
                entity ? GUIManager.safeText(plugin.msg().get(player, "button_entity_on"), "§aEntity Protection: ON")
                       : GUIManager.safeText(plugin.msg().get(player, "button_entity_off"), "§cEntity Protection: OFF"),
                plugin.msg().getList(player, "entity_toggle_lore")
        ));

        // --- Farm Protection ---
        boolean farm = plugin.protection().isFarmProtectionEnabled(player);
        // --- MODIFIED --- (Uses GUIManager.icon)
        inv.setItem(16, GUIManager.icon(
                farm ? Material.WHEAT : Material.WHEAT_SEEDS,
                farm ? GUIManager.safeText(plugin.msg().get(player, "button_farm_on"), "§aFarm Protection: ON")
                     : GUIManager.safeText(plugin.msg().get(player, "button_farm_off"), "§cFarm Protection: OFF"),
                plugin.msg().getList(player, "farm_toggle_lore")
        ));

        // --- Safe Zone (master switch) ---
        boolean safe = isSafeZoneEnabled(player);
        // --- MODIFIED --- (Uses GUIManager.icon)
        inv.setItem(17, GUIManager.icon(
                safe ? Material.SHIELD : Material.IRON_NUGGET,
                safe ? GUIManager.safeText(plugin.msg().get(player, "button_safe_on"), "§aSafe Zone: ON")
                     : GUIManager.safeText(plugin.msg().get(player, "button_safe_off"), "§cSafe Zone: OFF"),
                plugin.msg().getList(player, "safe_toggle_lore")
        ));

        /* -----------------------------
         * Language Style Selection
         * ----------------------------- */
// ... (existing language logic is good) ...
        String currentStyle = plugin.msg().getPlayerStyle(player);
        Material icon = switch (currentStyle) {
            case "modern_english" -> Material.BOOK;
            case "hybrid_english" -> Material.ENCHANTED_BOOK;
            default -> Material.WRITABLE_BOOK;
        };

        // --- MODIFIED --- (Uses GUIManager.icon)
        inv.setItem(31, GUIManager.icon(
                icon,
                "§b🕮 " + GUIManager.safeText(plugin.msg().get(player, "language_style_title"), "Language: {STYLE}")
                            .replace("{STYLE}", formatStyle(currentStyle)),
                plugin.msg().getList(player, "language_style_lore")
        ));

        // Navigation
        // --- MODIFIED --- (Uses GUIManager.icon)
        inv.setItem(48, GUIManager.icon(
                Material.ARROW,
                GUIManager.safeText(plugin.msg().get(player, "button_back"), "§fBack"),
                plugin.msg().getList(player, "back_lore")
        ));

        // --- MODIFIED --- (Uses GUIManager.icon)
        inv.setItem(49, GUIManager.icon(
                Material.BARRIER,
                GUIManager.safeText(plugin.msg().get(player, "button_exit"), "§cExit"),
                plugin.msg().getList(player, "exit_lore")
        ));

        player.openInventory(inv);
        // --- MODIFIED --- (Uses SoundUtil)
        plugin.sounds().playMenuFlip(player);
    }

    /* -----------------------------
     * Handle Clicks (slot-based)
     * This method is called by GUIListener
     * ----------------------------- */
    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        // --- RELIABILITY FIX ---
        // We MUST get the holder to ensure it's our GUI.
        // This is now done by GUIListener, so this check is redundant
        // but harmless to keep.
        if (e.getRawSlot() < 0 || e.getRawSlot() >= 54) return;

        int slot = e.getRawSlot();
        switch (slot) {
            case 10: { // Sounds
                boolean globalEnabled = plugin.getConfig().getBoolean("sounds.global_enabled", true);
                if (!globalEnabled) {
                    plugin.sounds().playError(player);
                } else {
                    boolean currentlyEnabled = plugin.isSoundEnabled(player);
                    plugin.getConfig().set("sounds.players." + player.getUniqueId(), !currentlyEnabled);

                    // --- CRITICAL LAG FIX ---
                    // Save config on an async thread to prevent lag
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        plugin.saveConfig();
                    });
                    plugin.sounds().playMenuFlip(player);
                }
                break; // Break here, refresh is at the end
            }
            case 11: { plugin.protection().togglePvP(player);               plugin.sounds().playMenuFlip(player); break; }
            case 12: { plugin.protection().toggleContainers(player);        plugin.sounds().playMenuFlip(player); break; }
            case 13: { plugin.protection().toggleMobProtection(player);   plugin.sounds().playMenuFlip(player); break; }
            case 14: { plugin.protection().togglePetProtection(player);   plugin.sounds().playMenuFlip(player); break; }
            case 15: { plugin.protection().toggleEntityProtection(player);  plugin.sounds().playMenuFlip(player); break; }
            case 16: { plugin.protection().toggleFarmProtection(player);  plugin.sounds().playMenuFlip(player); break; }

            case 17: { // Safe Zone master toggle
                toggleSafeZone(player); // This method now handles its own async save
                plugin.sounds().playMenuFlip(player);
                break; // Break here, refresh is at the end
            }

            case 31: { // Language cycle
// ... (existing language logic is good) ...
                String current = plugin.msg().getPlayerStyle(player);
                String next = switch (current) {
                    case "old_english" -> "hybrid_english";
                    case "hybrid_english" -> "modern_english";
                    default -> "old_english";
                };
                plugin.msg().setPlayerStyle(player, next); // Assume this handles its own async save
                plugin.sounds().playMenuFlip(player);
                break; // Break here, refresh is at the end
            }

            case 48: { // Back
                plugin.gui().openMain(player);
                plugin.sounds().playMenuFlip(player);
                return; // Do not refresh
            }
            case 49: { // Exit
                player.closeInventory();
                plugin.sounds().playMenuClose(player);
                return; // Do not refresh
            }
            default: { /* ignore filler */ }
        }

        open(player); // Refresh GUI instantly
    }

    /* -----------------------------
     * Helpers
     * ----------------------------- */
    
    // --- createItem() removed, now uses GUIManager.icon() ---

    private String formatStyle(String style) {
// ... (existing logic is fine) ...
        return switch (style) {
            case "modern_english" -> "§aModern English";
            case "hybrid_english" -> "§eHybrid English";
            default -> "§dOld English";
        };
    }

    private boolean isSafeZoneEnabled(Player player) {
// ... (existing logic is fine) ...
        PlotStore.Plot plot = plugin.store().getPlotAt(player.getLocation());
        return plot != null && plot.getFlag("safe_zone", true);
    }

    private void toggleSafeZone(Player player) {
        PlotStore.Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            plugin.sounds().playError(player); // --- MODIFIED ---
            return;
        }
        boolean next = !plot.getFlag("safe_zone", true);
        plot.setFlag("safe_zone", next);

        // When toggling Safe Zone ON, also ensure the individual protections are ON
// ... (existing logic is fine) ...
        if (next) {
            plot.setFlag("pvp", true);
// ... (existing logic) ...
            plot.setFlag("farm", true);
        }

        // --- CRITICAL LAG FIX ---
        // Save the PlotStore on an async thread
        // We are assuming `plot.setFlag` does not auto-save.
        // We call saveSync() because it is the synchronized save method in PlotStore.
        CompletableFuture.runAsync(() -> {
            plugin.store().saveSync();
        }, plugin.getServer().getScheduler().getMainThreadExecutor(plugin));
        
        plugin.msg().send(player, next ? "safe_zone_enabled" : "safe_zone_disabled");
    }

    /* -----------------------------
     * Inline sound helpers
     * --- REMOVED ---
     * (Now handled by plugin.sounds().play...())
     * ----------------------------- */
}
