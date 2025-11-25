package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
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
        String title = "§d§lPlot Level: " + plot.getLevel();
        Inventory inv = Bukkit.createInventory(new LevelingHolder(plot), 27, title);

        // Fill background
        for (int i = 0; i < 27; i++) inv.setItem(i, GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null));

        // --- INFO ICON (Slot 11) ---
        int currentLvl = plot.getLevel();
        inv.setItem(11, GUIManager.icon(
            Material.ENCHANTING_TABLE,
            "§dCurrent Status",
            List.of(
                "§7Level: §f" + currentLvl,
                "§7XP Multiplier: §f" + plugin.cfg().getLevelCostMultiplier(),
                " ",
                "§7Current Buffs:",
                getBuffsList(currentLvl)
            )
        ));

        // --- UPGRADE BUTTON (Slot 15) ---
        int nextLvl = currentLvl + 1;
        if (nextLvl <= plugin.cfg().getMaxLevel()) {
            double cost = calculateCost(nextLvl);
            CurrencyType type = plugin.cfg().getLevelCostType();
            String costStr = plugin.eco().format(cost, type);

            inv.setItem(15, GUIManager.icon(
                Material.EXPERIENCE_BOTTLE,
                "§aUpgrade to Level " + nextLvl,
                List.of(
                    "§7Cost: §e" + costStr,
                    " ",
                    "§7New Buffs Unlocked:",
                    getBuffsList(nextLvl),
                    " ",
                    "§eClick to Purchase Upgrade"
                )
            ));
        } else {
            inv.setItem(15, GUIManager.icon(Material.BARRIER, "§cMax Level Reached", List.of("§7Your plot is fully ascended.")));
        }

        inv.setItem(22, GUIManager.icon(Material.ARROW, "§fBack", null));
        
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, LevelingHolder holder) {
        e.setCancelled(true);
        Plot plot = holder.getPlot();
        
        if (e.getSlot() == 22) {
            plugin.gui().openMain(player);
            return;
        }

        if (e.getSlot() == 15 && e.getCurrentItem().getType() == Material.EXPERIENCE_BOTTLE) {
            int nextLvl = plot.getLevel() + 1;
            double cost = calculateCost(nextLvl);
            CurrencyType type = plugin.cfg().getLevelCostType();

            if (!plugin.eco().withdraw(player, cost, type)) {
                player.sendMessage("§cYou need " + plugin.eco().format(cost, type) + " to upgrade.");
                plugin.effects().playError(player);
                return;
            }

            plot.setLevel(nextLvl);
            plugin.store().setDirty(true);
            
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            player.sendMessage("§d§lLEVEL UP! §aYour plot is now Level " + nextLvl);
            
            open(player, plot); // Refresh
        }
    }
    
    private double calculateCost(int level) {
        double base = plugin.cfg().getLevelBaseCost();
        double mult = plugin.cfg().getLevelCostMultiplier();
        return base * (level * mult);
    }
    
    private String getBuffsList(int level) {
        List<String> rewards = plugin.cfg().getLevelRewards(level);
        if (rewards == null || rewards.isEmpty()) return "§7(None)";
        return "§f" + String.join(", ", rewards);
    }
}
