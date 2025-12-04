package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import com.aegisguard.objects.Estate;
import com.aegisguard.objects.Guild;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EconomyManager {

    private final AegisGuard plugin;
    private Economy vaultEconomy;
    private final Map<Material, Double> liquidationValues = new HashMap<>();
    private double pricePerEnchantLevel;
    private boolean respectDurability;

    public EconomyManager(AegisGuard plugin) {
        this.plugin = plugin;
        setupVault();
        loadLiquidationValues();
    }

    // ==========================================================
    // 🔌 VAULT INTEGRATION
    // ==========================================================
    private boolean setupVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        vaultEconomy = rsp.getProvider();
        return vaultEconomy != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (vaultEconomy == null) return false;
        return vaultEconomy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (vaultEconomy == null) return false;
        return vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public void deposit(OfflinePlayer player, double amount) {
        if (vaultEconomy == null) return;
        vaultEconomy.depositPlayer(player, amount);
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
                if (mat != null) {
                    liquidationValues.put(mat, values.getDouble(key));
                }
            }
        }
    }

    public double calculateValue(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0.0;
        
        // 1. Base Value
        Double basePrice = liquidationValues.get(item.getType());
        if (basePrice == null) return 0.0;
        
        double finalValue = basePrice * item.getAmount();

        // 2. Durability Calculation
        if (respectDurability && item.getType().getMaxDurability() > 0) {
            double damagePercent = (double) (item.getType().getMaxDurability() - item.getDurability()) / item.getType().getMaxDurability();
            finalValue *= damagePercent;
        }

        // 3. Enchantment Bonus
        if (pricePerEnchantLevel > 0) {
            int totalEnchants = item.getEnchantments().values().stream().mapToInt(Integer::intValue).sum();
            finalValue += (totalEnchants * pricePerEnchantLevel);
        }

        return Math.max(0, finalValue); // Never return negative
    }

    // ==========================================================
    // 💸 UPKEEP CALCULATOR
    // ==========================================================
    public double calculateDailyUpkeep(Estate estate) {
        // Formula: Base Cost + (Blocks * PricePerBlock)
        double base = plugin.getConfig().getDouble("economy.upkeep.base_cost", 100.0);
        double perBlock = plugin.getConfig().getDouble("economy.upkeep.price_per_block", 0.50);
        
        // Calculate Area (Width * Length)
        long area = estate.getRegion().getArea();
        
        double total = base + (area * perBlock);

        // Apply Guild Discount (from Bastion Level) if applicable
        if (estate.isGuild()) {
            // Example: Guild level 3 gives 0.95 multiplier (5% off)
            // double discount = plugin.getAllianceManager().getTaxMultiplier(estate.getOwnerId());
            // total *= discount;
        }
        
        return total;
    }
}
