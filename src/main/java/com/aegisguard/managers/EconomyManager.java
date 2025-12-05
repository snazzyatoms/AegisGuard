package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.objects.Cuboid;
import com.aegisguard.objects.Estate;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class EconomyManager {

    private final AegisGuard plugin;
    private final Map<Material, Double> liquidationValues = new HashMap<>();
    private double pricePerEnchantLevel;
    private boolean respectDurability;

    public EconomyManager(AegisGuard plugin) {
        this.plugin = plugin;
        loadLiquidationValues();
    }

    // ==========================================================
    // 💰 VAULT / CURRENCY METHODS
    // ==========================================================

    public boolean has(OfflinePlayer p, double amount) {
        return plugin.getVault().has(p, amount);
    }

    /**
     * Withdraw money from a player.
     * Returns true if successful, false if insufficient funds.
     */
    public boolean withdraw(OfflinePlayer p, double amount) {
        return plugin.getVault().charge(p, amount);
    }
    
    /**
     * Alias for legacy code that calls withdrawPlayer.
     * Redirects to the main withdraw method.
     */
    public boolean withdrawPlayer(Player p, double amount) {
        return withdraw(p, amount);
    }

    public void deposit(OfflinePlayer p, double amount) {
        plugin.getVault().give(p, amount);
    }

    public String format(double amount, CurrencyType type) {
        // Simple formatter using Vault
        return plugin.getVault().format(amount);
    }

    // ==========================================================
    // 🏗️ COST CALCULATIONS
    // ==========================================================
    
    /**
     * Calculates the cost to claim a specific region.
     * Used by /ag claim and SelectionService.
     */
    public double calculateClaimCost(Cuboid region) {
        if (region == null) return 0.0;
        
        double baseCost = plugin.getConfig().getDouble("economy.costs.private_estate_creation", 100.0);
        double pricePerBlock = plugin.getConfig().getDouble("expansions.price_per_block", 10.0);
        
        return baseCost + (region.getArea() * pricePerBlock);
    }

    /**
     * Calculates daily rent for an Estate.
     * Used by UpkeepTask.
     */
    public double calculateDailyUpkeep(Estate estate) {
        double base = plugin.getConfig().getDouble("economy.upkeep.base_cost", 100.0);
        double perBlock = plugin.getConfig().getDouble("economy.upkeep.price_per_block", 0.50);
        
        return base + (estate.getRegion().getArea() * perBlock);
    }

    // ==========================================================
    // ♻️ ASSET LIQUIDATION (The Chute)
    // ==========================================================

    public void loadLiquidationValues() {
        File file = new File(plugin.getDataFolder(), "liquidation.yml");
        if (!file.exists()) plugin.saveResource("liquidation.yml", false);
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.respectDurability = config.getBoolean("settings.respect_durability", true);
        this.pricePerEnchantLevel = config.getDouble("settings.price_per_enchant_level", 10.0);
        
        liquidationValues.clear();
        ConfigurationSection values = config.getConfigurationSection("values");
        if (values != null) {
            for (String key : values.getKeys(false)) {
                Material mat = Material.getMaterial(key);
                if (mat != null) liquidationValues.put(mat, values.getDouble(key));
            }
        }
    }

    public double calculateValue(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0.0;
        Double basePrice = liquidationValues.get(item.getType());
        if (basePrice == null) return 0.0;
        
        double finalValue = basePrice * item.getAmount();
        
        // Durability Logic
        if (respectDurability && item.getType().getMaxDurability() > 0) {
            double damagePercent = (double) (item.getType().getMaxDurability() - item.getDurability()) / item.getType().getMaxDurability();
            finalValue *= damagePercent;
        }
        
        // Enchantment Bonus
        if (pricePerEnchantLevel > 0) {
            int totalEnchants = item.getEnchantments().values().stream().mapToInt(Integer::intValue).sum();
            finalValue += (totalEnchants * pricePerEnchantLevel);
        }
        
        return Math.max(0, finalValue);
    }
}
