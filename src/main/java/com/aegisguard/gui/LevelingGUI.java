package com.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.api.events.EstateLevelUpEvent; // Updated Event
import com.yourname.aegisguard.economy.CurrencyType;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.objects.Estate;
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
 * - Displays the Ascension (Solo) or Bastion (Guild) tree.
 * - Updated for v1.3.0 Estate System.
 */
public class LevelingGUI {

    private final AegisGuard plugin;

    public LevelingGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class LevelingHolder implements InventoryHolder {
        private final Estate estate;
        public LevelingHolder(Estate estate) { this.estate = estate; }
        public Estate getEstate() { return estate; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Estate estate) {
        LanguageManager lang = plugin.getLanguageManager();
        
        // Determine Title based on type
        String key = estate.isGuild() ? "title_bastion" : "title_ascension";
        String title = lang.getGui(key); 
        if (title.contains("Missing")) title = "§dLeveling: " + estate.getName();

        Inventory inv = Bukkit.createInventory(new LevelingHolder(estate), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        int currentLvl = estate.getLevel();
        
        // --- INFO ICON (Current State) ---
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Current Level: §f" + currentLvl);
        infoLore.add(" ");
        
        // Expansion Info
        if (plugin.getConfig().getBoolean("progression.leveling.expand_on_levelup", false)) {
            int amount = plugin.getConfig().getInt("progression.leveling.expansion_amount", 5);
            infoLore.add("§bBonus: §7+ " + amount + " block radius/lvl");
        }
        
        infoLore.add("§7Active Perks:");
        // Fetch real perks from ProgressionManager (Visual only here)
        // In a real GUI, we might list them. For now, keep it simple.
        infoLore.add("§8(Click 'Active Perks' in Main Menu to view)");

        inv.setItem(11, GUIManager.createItem(
            Material.ENCHANTING_TABLE,
            lang.getMsg(player, "level_current_status"), // "Current Status"
            infoLore
        ));

        // --- UPGRADE BUTTON ---
        int nextLvl = currentLvl + 1;
        int maxLvl = plugin.getConfig().getInt("progression.max_level", 30);
        
        if (nextLvl <= maxLvl) {
            double cost = calculateCost(nextLvl);
            CurrencyType type = CurrencyType.VAULT; // TODO: Configurable per level?
            String costStr = plugin.getEconomy().format(cost, type);
            
            List<String> upgradeLore = new ArrayList<>();
            upgradeLore.add("§7Cost: §e" + costStr);
            upgradeLore.add("");
            upgradeLore.add("§7New Buffs Unlocked:");
            
            // Fetch rewards for next level
            // (Placeholder logic - ideally fetch from ProgressionManager)
            upgradeLore.add("§a+ Increased Member Cap");
            upgradeLore.add("§a+ New Buffs");
            
            upgradeLore.add("");
            upgradeLore.add("§eClick to Purchase Upgrade");

            inv.setItem(15, GUIManager.createItem(
                Material.EXPERIENCE_BOTTLE,
                lang.getMsg(player, "level_upgrade_button").replace("%level%", String.valueOf(nextLvl)),
                upgradeLore
            ));
        } else {
            inv.setItem(15, GUIManager.createItem(
                Material.BARRIER, 
                lang.getMsg(player, "level_max_reached"), 
                List.of("§7Your estate is fully ascended.")
            ));
        }

        // Back Button
        inv.setItem(22, GUIManager.createItem(Material.ARROW, lang.getGui("button_back")));
        
        player.openInventory(inv);
        // plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, LevelingHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        Estate estate = holder.getEstate();
        
        if (e.getSlot() == 22) {
            plugin.getGuiManager().openGuardianCodex(player);
            return;
        }

        if (e.getSlot() == 15 && e.getCurrentItem().getType() == Material.EXPERIENCE_BOTTLE) {
            int nextLvl = estate.getLevel() + 1;
            double cost = calculateCost(nextLvl);
            
            // 1. Check Funds
            if (!plugin.getEconomy().withdraw(player, cost)) {
                player.sendMessage(plugin.getLanguageManager().getMsg(player, "claim_failed_money"));
                return;
            }

            // 2. Handle Expansion (If Enabled)
            if (plugin.getConfig().getBoolean("progression.leveling.expand_on_levelup", false)) {
                int expandAmount = plugin.getConfig().getInt("progression.leveling.expansion_amount", 5);
                
                // Simple expansion logic (expand all sides)
                // Note: In v1.3.0, use EstateManager.resizeEstate() for safety
                plugin.getEstateManager().resizeEstate(estate, "ALL", expandAmount);
            }

            // 3. Fire Event & Apply Level
            EstateLevelUpEvent event = new EstateLevelUpEvent(estate, player, nextLvl);
            Bukkit.getPluginManager().callEvent(event);
            
            estate.setLevel(nextLvl);
            // plugin.getEstateManager().saveEstate(estate);
            
            // 4. Feedback
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            player.sendMessage(plugin.getLanguageManager().getMsg(player, "level_up_success").replace("%level%", String.valueOf(nextLvl)));
            // plugin.effects().playConfirm(player);
            
            open(player, estate); // Refresh menu
        }
    }
    
    private double calculateCost(int level) {
        double base = plugin.getConfig().getDouble("economy.costs.level_base", 1000.0);
        double mult = plugin.getConfig().getDouble("economy.costs.level_multiplier", 1.5);
        return base * (level * mult);
    }
}
