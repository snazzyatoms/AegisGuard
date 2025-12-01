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
import java.util.Map;

/**
 * LevelingGUI
 * - Allows players to upgrade their plot level.
 * - Configurable: Can optionally expand plot size on level up.
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
        String title = GUIManager.safeText(plugin.msg().get(player, "level_gui_title"), "§dPlot Leveling");
        Inventory inv = Bukkit.createInventory(new LevelingHolder(plot), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        int currentLvl = plot.getLevel();
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Level: §f" + currentLvl);
        infoLore.add("§7XP Multiplier: §f" + plugin.cfg().getLevelCostMultiplier());
        infoLore.add("");
        
        // Show expansion info if enabled
        if (plugin.cfg().isLevelingExpansionEnabled()) {
            int amount = plugin.cfg().getLevelingExpansionAmount();
            infoLore.add("§bBonus: §7+ " + amount + " block radius/lvl");
        }
        
        infoLore.add("§7Current Buffs:");
        infoLore.addAll(formatBuffs(currentLvl));

        inv.setItem(11, GUIManager.createItem(
            Material.ENCHANTING_TABLE,
            plugin.msg().get(player, "level_current_status"),
            infoLore
        ));

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
            
            if (plugin.cfg().isLevelingExpansionEnabled()) {
                upgradeLore.add("");
                upgradeLore.add("§b+ " + plugin.cfg().getLevelingExpansionAmount() + " Block Radius");
            }
            
            upgradeLore.add("");
            upgradeLore.add("§eClick to Purchase Upgrade");

            inv.setItem(15, GUIManager.createItem(
                Material.EXPERIENCE_BOTTLE,
                plugin.msg().get(player, "level_upgrade_button", Map.of("LEVEL", String.valueOf(nextLvl))),
                upgradeLore
            ));
        } else {
            inv.setItem(15, GUIManager.createItem(
                Material.BARRIER, 
                plugin.msg().get(player, "level_max_reached"), 
                List.of("§7Your plot is fully ascended.")
            ));
        }

        inv.setItem(22, GUIManager.createItem(Material.ARROW, 
            plugin.msg().get(player, "button_back"), 
            plugin.msg().getList(player, "back_lore")));
        
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
                plugin.msg().send(player, "level_up_fail_cost");
                plugin.effects().playError(player);
                return;
            }

            // 2. Handle Expansion (If Enabled)
            if (plugin.cfg().isLevelingExpansionEnabled()) {
                int expandAmount = plugin.cfg().getLevelingExpansionAmount();
                int newX1 = plot.getX1() - expandAmount;
                int newZ1 = plot.getZ1() - expandAmount;
                int newX2 = plot.getX2() + expandAmount;
                int newZ2 = plot.getZ2() + expandAmount;

                // Overlap Check
                if (plugin.store().isAreaOverlapping(plot, plot.getWorld(), newX1, newZ1, newX2, newZ2)) {
                    plugin.eco().deposit(player, cost, type); // Refund
                    player.sendMessage("§cCannot level up: Plot expansion would overlap a neighbor.");
                    plugin.effects().playError(player);
                    return;
                }

                // Limit Check (Admin Bypass)
                int newRadius = (newX2 - newX1) / 2;
                int maxRadius = plugin.cfg().getWorldMaxRadius(player.getWorld());
                if (newRadius > maxRadius && !player.hasPermission("aegis.admin.bypass")) {
                     plugin.eco().deposit(player, cost, type); // Refund
                     player.sendMessage("§cCannot level up: Expansion exceeds world limit (" + maxRadius + ").");
                     plugin.effects().playError(player);
                     return;
                }

                // Apply Resize
                plugin.store().removePlot(plot.getOwner(), plot.getPlotId()); // Remove old index
                plot.setX1(newX1); plot.setX2(newX2);
                plot.setZ1(newZ1); plot.setZ2(newZ2);
                plugin.store().addPlot(plot); // Add new index
            }

            // 3. Fire Event & Apply Level
            PlotLevelUpEvent event = new PlotLevelUpEvent(plot, player, nextLvl);
            Bukkit.getPluginManager().callEvent(event);
            
            plot.setLevel(nextLvl);
            plugin.store().setDirty(true);
            
            // 4. Feedback
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            plugin.msg().send(player, "level_up_success", Map.of("LEVEL", String.valueOf(nextLvl)));
            plugin.effects().playConfirm(player);
            
            open(player, plot); // Refresh menu
        } else if (e.getSlot() == 15) {
             GUIManager.playClick(player);
        }
    }
    
    private double calculateCost(int level) {
        double base = plugin.cfg().getLevelBaseCost();
        double mult = plugin.cfg().getLevelCostMultiplier();
        return base * (level * mult);
    }
    
    private List<String> formatBuffs(int level) {
        List<String> rewards = plugin.cfg().getLevelRewards(level);
        List<String> formatted = new ArrayList<>();
        if (rewards == null || rewards.isEmpty()) {
            formatted.add("§8- (None)");
        } else {
            for (String s : rewards) {
                if (s.startsWith("EFFECT:")) {
                    try {
                        String[] parts = s.split(":");
                        String type = parts[1].toLowerCase().replace("_", " ");
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
