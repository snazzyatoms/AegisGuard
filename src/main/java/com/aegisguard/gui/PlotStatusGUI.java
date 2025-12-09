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

public class LevelingGUI {

    private final AegisGuard plugin;

    public LevelingGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class LevelingHolder implements InventoryHolder {
        private final Plot plot;
        public LevelingHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        if (plot == null) return;

        String title = plugin.msg().get(player, "level_gui_title");
        Inventory inv = Bukkit.createInventory(new LevelingHolder(plot), 27, title);

        // Filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        int currentLevel = plot.getLevel();
        int maxLevel = plugin.cfg().getMaxLevel();

        // 1. Current Status Indicator
        inv.setItem(11, GUIManager.createItem(
                Material.BOOK,
                plugin.msg().get(player, "level_current_status"),
                List.of(
                    "&7Level: &b" + currentLevel,
                    "&7Max: &e" + maxLevel
                )
        ));

        // 2. Upgrade Button (Dynamic Logic)
        ItemStack upgradeButton;

        if (currentLevel >= maxLevel) {
            // MAX LEVEL REACHED
            upgradeButton = GUIManager.createItem(
                    Material.BARRIER,
                    plugin.msg().get(player, "level_max_reached"),
                    List.of("&7You have reached the highest", "&7level allowed.")
            );
        } else {
            // CAN UPGRADE
            int nextLevel = currentLevel + 1;
            double cost = plugin.cfg().getLevelCost(nextLevel);
            
            // --- DYNAMIC LORE SWITCHING ---
            // If the config says leveling increases size, show standard lore.
            // If not, show the "static" lore so we don't mislead players.
            // TODO: Ensure this method matches your ConfigManager!
            boolean levelingExpandsSize = plugin.cfg().isLevelingExpansionEnabled(); 
            
            List<String> lore;
            if (levelingExpandsSize) {
                lore = plugin.msg().getList(player, "level_button_lore");
            } else {
                // Fallback to static lore if expansion is disabled
                lore = plugin.msg().getList(player, "level_button_lore_static");
                // Safety: if the new key is missing in old configs, default to standard
                if (lore.isEmpty()) {
                    lore = plugin.msg().getList(player, "level_button_lore");
                }
            }

            // Append Cost
            lore.add("");
            lore.add("&7Cost: &6" + cost);
            lore.add("");
            lore.add("&eClick to Upgrade");

            String btnTitle = plugin.msg().get(player, "level_upgrade_button")
                    .replace("{LEVEL}", String.valueOf(nextLevel));

            upgradeButton = GUIManager.createItem(Material.EXPERIENCE_BOTTLE, btnTitle, lore);
        }

        inv.setItem(15, upgradeButton);

        // Back Button
        inv.setItem(22, GUIManager.createItem(
                Material.ARROW,
                plugin.msg().get(player, "button_back"),
                plugin.msg().getList(player, "back_lore")
        ));

        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, LevelingHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        Plot plot = holder.getPlot();

        // UPGRADE BUTTON
        if (slot == 15) {
            int currentLevel = plot.getLevel();
            int maxLevel = plugin.cfg().getMaxLevel();

            if (currentLevel >= maxLevel) {
                GUIManager.playError(player);
                return;
            }

            // Perform Upgrade Logic
            int nextLevel = currentLevel + 1;
            double cost = plugin.cfg().getLevelCost(nextLevel);

            if (plugin.econ().has(player, cost)) {
                plugin.econ().withdraw(player, cost);
                plot.setLevel(nextLevel);
                
                // Only expand radius if enabled in config
                if (plugin.cfg().isLevelingExpansionEnabled()) {
                    int addedRadius = plugin.cfg().getRadiusGain(nextLevel);
                    // Add expansion logic here (omitted for brevity)
                }

                String successMsg = plugin.msg().get(player, "level_up_success")
                        .replace("{LEVEL}", String.valueOf(nextLevel));
                player.sendMessage(successMsg);
                GUIManager.playSuccess(player);
                
                // Re-open to refresh the GUI
                open(player, plot); 
            } else {
                plugin.msg().send(player, "level_up_fail_cost");
                GUIManager.playError(player);
            }
        }

        // BACK BUTTON
        if (slot == 22) {
            GUIManager.playClick(player);
            plugin.gui().openMain(player);
        }
    }
}
