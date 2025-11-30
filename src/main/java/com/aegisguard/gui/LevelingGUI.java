package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotLevelUpEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LevelingGUI (Ultimate Edition)
 * - Supports Infinite Levels (Config defined).
 * - "Cool Name" generator for effects.
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
        String title = GUIManager.safeText(plugin.msg().get(player, "level_gui_title"), "§8⚡ Dominion Ascension");
        Inventory inv = Bukkit.createInventory(new LevelingHolder(plot), 45, title);

        ItemStack filler = GUIManager.createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        int currentLvl = plot.getLevel();
        int nextLvl = currentLvl + 1;
        int maxLvl = plugin.cfg().getMaxLevel();

        // --- PROGRESS BAR ---
        renderProgressBar(inv, currentLvl, maxLvl);

        // --- CURRENT STATS (Slot 11) ---
        List<String> currentLore = new ArrayList<>();
        currentLore.add("§7Current Rank: §f" + currentLvl);
        currentLore.add("");
        currentLore.add("§7Active Bonuses:");
        currentLore.addAll(formatBuffs(currentLvl));
        
        inv.setItem(11, GUIManager.createItem(Material.BOOK, "§e§nYour Current Power", currentLore));

        // --- NEXT LEVEL PREVIEW (Slot 15) ---
        List<String> nextLore = new ArrayList<>();
        if (nextLvl <= maxLvl) {
            nextLore.add("§7Next Rank: §b" + nextLvl);
            nextLore.add("");
            nextLore.add("§7Upcoming Bonuses:");
            nextLore.addAll(formatBuffs(nextLvl));
        } else {
            nextLore.add("§7You have reached the");
            nextLore.add("§7pinnacle of power.");
        }
        inv.setItem(15, GUIManager.createItem(Material.KNOWLEDGE_BOOK, "§b§nNext Tier Preview", nextLore));

        // --- UPGRADE BUTTON (Slot 13) ---
        if (nextLvl <= maxLvl) {
            double cost = calculateCost(nextLvl);
            CurrencyType type = plugin.cfg().getLevelCostType();
            String costStr = plugin.eco().format(cost, type);

            List<String> upgradeLore = new ArrayList<>();
            upgradeLore.add("§7Upgrade to §dLevel " + nextLvl);
            upgradeLore.add("");
            upgradeLore.add("§7Cost: §6" + costStr);
            upgradeLore.add("");
            upgradeLore.add("§7§oClick to pay tribute and");
            upgradeLore.add("§7§oascend your dominion.");

            ItemStack upgradeBtn = GUIManager.createItem(Material.NETHER_STAR,"§6§l⬆ ASCEND DOMINION ⬆", upgradeLore);
            ItemMeta meta = upgradeBtn.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                upgradeBtn.setItemMeta(meta);
            }
            inv.setItem(13, upgradeBtn);
        } else {
            inv.setItem(13, GUIManager.createItem(Material.BEACON,"§b§lMAXIMUM LEVEL", List.of("§7Your dominion is fully ascended.")));
        }

        // --- BACK BUTTON (Slot 40) ---
        inv.setItem(40, GUIManager.createItem(Material.ARROW, plugin.msg().get(player, "button_back"), plugin.msg().getList(player, "back_lore")));
        
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    private void renderProgressBar(Inventory inv, int current, int max) {
        int[] slots = {28, 29, 30, 31, 32, 33, 34};
        double progress = Math.min(1.0, (double) current / max);
        int filledSlots = (int) (progress * slots.length);

        for (int i = 0; i < slots.length; i++) {
            if (i < filledSlots) inv.setItem(slots[i], GUIManager.createItem(Material.LIME_STAINED_GLASS_PANE, "§a§lUNLOCKED"));
            else if (i == filledSlots && current < max) inv.setItem(slots[i], GUIManager.createItem(Material.ORANGE_STAINED_GLASS_PANE, "§6§lNEXT GOAL"));
            else inv.setItem(slots[i], GUIManager.createItem(Material.GRAY_STAINED_GLASS_PANE, "§7LOCKED"));
        }
    }

    public void handleClick(Player player, InventoryClickEvent e, LevelingHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        Plot plot = holder.getPlot();
        
        if (e.getSlot() == 40) { plugin.gui().openMain(player); return; }

        if (e.getSlot() == 13 && e.getCurrentItem().getType() == Material.NETHER_STAR) {
            int nextLvl = plot.getLevel() + 1;
            double cost = calculateCost(nextLvl);
            CurrencyType type = plugin.cfg().getLevelCostType();

            if (!plugin.eco().withdraw(player, cost, type)) {
                plugin.msg().send(player, "level_up_fail_cost");
                plugin.effects().playError(player);
                return;
            }

            PlotLevelUpEvent event = new PlotLevelUpEvent(plot, player, nextLvl);
            Bukkit.getPluginManager().callEvent(event);
            
            plot.setLevel(nextLvl);
            plugin.store().setDirty(true);
            
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.5f);
            plugin.msg().send(player, "level_up_success", Map.of("LEVEL", String.valueOf(nextLvl)));
            plugin.effects().playConfirm(player);
            open(player, plot);
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
            formatted.add("§8- (No buffs for this level)");
            return formatted;
        }

        for (String s : rewards) {
            // STRICTLY FILTER OUT REGEN/HEALTH
            if (s.contains("REGENERATION") || s.contains("SATURATION") || s.contains("HEAL")) continue;

            if (s.equalsIgnoreCase("FLIGHT") || s.equalsIgnoreCase("FLY")) {
                formatted.add("§b☁ §lABILITY: FLIGHT");
                formatted.add("   §7(Fly within your borders)");
            }
            else if (s.startsWith("PARTICLE:")) {
                 String type = s.split(":")[1];
                 formatted.add("§d✨ Aura: " + beautifyName(type));
            }
            else if (s.startsWith("EFFECT:")) {
                try {
                    String[] parts = s.split(":");
                    String rawType = parts[1];
                    String lvl = toRoman(Integer.parseInt(parts[2]));
                    formatted.add("§a✦ " + beautifyEffect(rawType) + " " + lvl);
                } catch (Exception e) { formatted.add("§a✦ " + s); }
            }
            else if (s.startsWith("RADIUS:")) {
                formatted.add("§e⬈ Expansion: §f+" + s.split(":")[1] + " Blocks");
            }
            else if (s.startsWith("MEMBERS:")) {
                formatted.add("§e👥 Roster: §f+" + s.split(":")[1] + " Slots");
            }
            else {
                formatted.add("§a✦ " + s);
            }
        }
        return formatted;
    }
    
    // Makes effect names look RPG-style
    private String beautifyEffect(String type) {
        type = type.toUpperCase();
        if (type.contains("FAST_DIGGING")) return "Mining Haste";
        if (type.contains("DAMAGE_RESISTANCE")) return "Iron Skin"; // Better Armor Protection
        if (type.contains("INCREASE_DAMAGE")) return "Strength";
        if (type.contains("SPEED")) return "Agility";
        if (type.contains("JUMP")) return "High Jump";
        if (type.contains("NIGHT_VISION")) return "True Sight";
        if (type.contains("WATER_BREATHING")) return "Aquatic Lungs";
        if (type.contains("FIRE_RESISTANCE")) return "Fireborn";
        if (type.contains("LUCK")) return "Fortune's Favor";
        if (type.contains("DOLPHINS_GRACE")) return "Ocean's Speed";
        if (type.contains("CONDUIT_POWER")) return "Atlantis Power";
        if (type.contains("SLOW_FALLING")) return "Feather Weight";
        return beautifyName(type);
    }
    
    private String beautifyName(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] words = str.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            sb.append(w.substring(0, 1).toUpperCase()).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
    
    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
