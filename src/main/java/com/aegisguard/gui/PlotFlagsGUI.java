package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotStore;
import com.aegisguard.data.PlotStore.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * PlotFlagsGUI
 * - This is the NEW player-facing GUI for managing flags on a specific plot.
 * - It is opened from the main PlayerGUI.
 * - It directly modifies the plot's flags using the ProtectionManager API.
 */
public class PlotFlagsGUI {

    private final AegisGuard plugin;

    public PlotFlagsGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Reliable InventoryHolder that stores the plot being edited.
     */
    public static class PlotFlagsHolder implements InventoryHolder {
        private final Plot plot;

        public PlotFlagsHolder(Plot plot) {
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
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }

        Inventory inv = Bukkit.createInventory(new PlotFlagsHolder(plot), 54,
                GUIManager.safeText(plugin.msg().get(player, "plot_flags_title"), "§bPlot Flags")
        );

        // --- PvP Protection ---
        boolean pvp = plugin.protection().isPvPEnabled(plot);
        inv.setItem(11, GUIManager.icon(
                pvp ? Material.IRON_SWORD : Material.WOODEN_SWORD,
                pvp ? GUIManager.safeText(plugin.msg().get(player, "button_pvp_on"), "§aPvP: ON")
                    : GUIManager.safeText(plugin.msg().get(player, "button_pvp_off"), "§cPvP: OFF"),
                plugin.msg().getList(player, "pvp_toggle_lore")
        ));

        // --- Container Protection ---
        boolean containers = plugin.protection().isContainersEnabled(plot);
        inv.setItem(12, GUIManager.icon(
                containers ? Material.CHEST : Material.TRAPPED_CHEST,
                containers ? GUIManager.safeText(plugin.msg().get(player, "button_containers_on"), "§aContainers: ON")
                           : GUIManager.safeText(plugin.msg().get(player, "button_containers_off"), "§cContainers: OFF"),
                plugin.msg().getList(player, "container_toggle_lore")
        ));

        // --- Mob Protection ---
        boolean mobs = plugin.protection().isMobProtectionEnabled(plot);
        inv.setItem(13, GUIManager.icon(
                mobs ? Material.ZOMBIE_HEAD : Material.ROTTEN_FLESH,
                mobs ? GUIManager.safeText(plugin.msg().get(player, "button_mobs_on"), "§aMob Grief: ON")
                     : GUIManager.safeText(plugin.msg().get(player, "button_mobs_off"), "§cMob Grief: OFF"),
                plugin.msg().getList(player, "mob_toggle_lore")
        ));

        // --- Pet Protection ---
        boolean pets = plugin.protection().isPetProtectionEnabled(plot);
        inv.setItem(14, GUIManager.icon(
                pets ? Material.BONE : Material.LEAD,
                pets ? GUIManager.safeText(plugin.msg().get(player, "button_pets_on"), "§aPet Protection: ON")
                     : GUIManager.safeText(plugin.msg().get(player, "button_pets_off"), "§cPet Protection: OFF"),
                plugin.msg().getList(player, "pet_toggle_lore")
        ));

        // --- Entity Protection ---
        boolean entity = plugin.protection().isEntityProtectionEnabled(plot);
        inv.setItem(15, GUIManager.icon(
                entity ? Material.ARMOR_STAND : Material.ITEM_FRAME,
                entity ? GUIManager.safeText(plugin.msg().get(player, "button_entity_on"), "§aEntity Protection: ON")
                       : GUIManager.safeText(plugin.msg().get(player, "button_entity_off"), "§cEntity Protection: OFF"),
                plugin.msg().getList(player, "entity_toggle_lore")
        ));

        // --- Farm Protection ---
        boolean farm = plugin.protection().isFarmProtectionEnabled(plot);
        inv.setItem(16, GUIManager.icon(
                farm ? Material.WHEAT : Material.WHEAT_SEEDS,
                farm ? GUIManager.safeText(plugin.msg().get(player, "button_farm_on"), "§aFarm Protection: ON")
                     : GUIManager.safeText(plugin.msg().get(player, "button_farm_off"), "§cFarm Protection: OFF"),
                plugin.msg().getList(player, "farm_toggle_lore")
        ));

        // --- Safe Zone (master switch) ---
        boolean safe = plugin.protection().isSafeZoneEnabled(plot);
        inv.setItem(17, GUIManager.icon(
                safe ? Material.SHIELD : Material.IRON_NUGGET,
                safe ? GUIManager.safeText(plugin.msg().get(player, "button_safe_on"), "§aSafe Zone: ON")
                     : GUIManager.safeText(plugin.msg().get(player, "button_safe_off"), "§cSafe Zone: OFF"),
                plugin.msg().getList(player, "safe_toggle_lore")
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

    /* -----------------------------
     * Handle Clicks (slot-based)
     * This method is called by GUIListener
     * ----------------------------- */
    public void handleClick(Player player, InventoryClickEvent e, PlotFlagsHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (plot == null) {
            player.closeInventory();
            return;
        }
        
        // Ensure the player still owns this plot
        if (!plot.getOwner().equals(player.getUniqueId())) {
            plugin.msg().send(player, "no_perm");
            player.closeInventory();
            return;
        }

        int slot = e.getRawSlot();
        switch (slot) {
            // --- ALL TOGGLES use the ProtectionManager API and are ASYNC-SAFE ---
            case 11: { plugin.protection().togglePvP(plot);               plugin.effects().playMenuFlip(player); break; }
            case 12: { plugin.protection().toggleContainers(plot);        plugin.effects().playMenuFlip(player); break; }
            case 13: { plugin.protection().toggleMobProtection(plot);   plugin.effects().playMenuFlip(player); break; }
            case 14: { plugin.protection().togglePetProtection(plot);   plugin.effects().playMenuFlip(player); break; }
            case 15: { plugin.protection().toggleEntityProtection(plot);  plugin.effects().playMenuFlip(player); break; }
            case 16: { plugin.protection().toggleFarmProtection(plot);  plugin.effects().playMenuFlip(player); break; }

            case 17: { // Safe Zone master toggle
                plugin.protection().toggleSafeZone(plot, true);
                plugin.msg().send(player, plot.getFlag("safe_zone", true) ? "safe_zone_enabled" : "safe_zone_disabled");
                plugin.effects().playMenuFlip(player);
                break;
            }

            case 48: { // Back
                plugin.gui().openMain(player); // Go back to the main menu
                plugin.effects().playMenuFlip(player);
                return; // Do not refresh
            }
            case 49: { // Exit
                player.closeInventory();
                plugin.effects().playMenuClose(player);
                return; // Do not refresh
            }
            default: { /* ignore filler */ }
        }

        open(player, plot); // Refresh GUI instantly
    }
}
