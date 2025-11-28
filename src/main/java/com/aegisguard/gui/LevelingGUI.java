package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotLevelUpEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * LevelingGUI
 * - Allows players to upgrade their plot level.
 * - Displays costs and rewards dynamically.
 */
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
        String title = GUIManager.safeText(null, "§d§lPlot Level: " + plot.getLevel());
        Inventory inv = Bukkit.createInventory(new LevelingHolder(plot), 27, title);

        // Fill background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // --- INFO ICON (Slot 11) ---
        int currentLvl = plot.getLevel();
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Level: §f" + currentLvl);
        infoLore.add("§7XP Multiplier: §f" + plugin.cfg().getLevelCostMultiplier());
        infoLore.add("");
        infoLore.add("§7Current Buffs:");
        infoLore.addAll(formatBuffs(currentLvl));

        inv.setItem(11, GUIManager.createItem(
            Material.ENCHANTING_TABLE,
            "§dCurrent Status",
            infoLore
        ));

        // --- UPGRADE BUTTON (Slot 15) ---
        int nextLvl = currentLvl + 1;
        int maxLvl = plugin.cfg().getMaxLevel();
        
        if (nextLvl <= maxLvl) {
            double cost = calculateCost(nextLvl);
            CurrencyType type = plugin.cfg().getLevelCostType();
            String costStr = plugin.eco().format(cost, type);
            
            List<String> upgradeLore = new ArrayList<>();
            upgradeLore.add("§7Cost: §e" + costStr);
            upgradeLore.add("");
            upgradeLore.add("§7New Buffs Unlocked:");
            upgradeLore.addAll(formatBuffs(nextLvl));
            upgradeLore.add("");
            upgradeLore.add("§eClick to Purchase Upgrade");

            inv.setItem(15, GUIManager.createItem(
                Material.EXPERIENCE_BOTTLE,
                "§aUpgrade to Level " + nextLvl,
                upgradeLore
            ));
        } else {
            inv.setItem(15, GUIManager.createItem(
                Material.BARRIER, 
                "§cMax Level Reached", 
                List.of("§7Your plot is fully ascended.")
            ));
        }

        // Back Button (Slot 22)
        inv.setItem(22, GUIManager.createItem(Material.ARROW, "§fBack", List.of("§7Return to dashboard.")));
        
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, LevelingHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        Plot plot = holder.getPlot();
        
        if (e.getSlot() == 22) {
            plugin.gui().openMain(player);
            return;
        }

        if (e.getSlot() == 15 && e.getCurrentItem().getType() == Material.EXPERIENCE_BOTTLE) {
            int nextLvl = plot.getLevel() + 1;
            double cost = calculateCost(nextLvl);
            CurrencyType type = plugin.cfg().getLevelCostType();

            // 1. Check Funds
            if (!plugin.eco().withdraw(player, cost, type)) {
                player.sendMessage("§cYou need " + plugin.eco().format(cost, type) + " to upgrade.");
                plugin.effects().playError(player);
                return;
            }

            // 2. Fire Event
            PlotLevelUpEvent event = new PlotLevelUpEvent(plot, player, nextLvl);
            Bukkit.getPluginManager().callEvent(event);
            
            // 3. Apply Upgrade
            plot.setLevel(nextLvl);
            plugin.store().setDirty(true);
            
            // 4. Feedback
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            player.sendMessage("§d§lLEVEL UP! §aYour plot is now Level " + nextLvl);
            plugin.effects().playConfirm(player);
            
            open(player, plot); // Refresh menu
        } else if (e.getSlot() == 15) {
             GUIManager.playClick(player); // Play click even if max level (barrier)
        }
    }
    
    private double calculateCost(int level) {
        double base = plugin.cfg().getLevelBaseCost();
        double mult = plugin.cfg().getLevelCostMultiplier();
        // Formula: Base * (Level * Multiplier) -> Linear Scaling
        // For exponential: base * Math.pow(mult, level)
        return base * (level * mult);
    }
    
    private List<String> formatBuffs(int level) {
        List<String> rewards = plugin.cfg().getLevelRewards(level);
        List<String> formatted = new ArrayList<>();
        if (rewards == null || rewards.isEmpty()) {
            formatted.add("§8- (None)");
        } else {
            for (String s : rewards) {
                // Prettify strings like "EFFECT:SPEED:2" -> "Speed II"
                if (s.startsWith("EFFECT:")) {
                    try {
                        String[] parts = s.split(":");
                        String type = parts[1].toLowerCase().replace("_", " ");
                        // Capitalize
                        type = type.substring(0, 1).toUpperCase() + type.substring(1);
                        formatted.add("§b✦ " + type + " " + toRoman(Integer.parseInt(parts[2])));
                    } catch (Exception e) {
                        formatted.add("§b✦ " + s);
                    }
                } else {
                    formatted.add("§a✦ " + s);
                }
            }
        }
        return formatted;
    }
    
    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
