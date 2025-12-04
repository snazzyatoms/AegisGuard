package com.yourname.aegisguard.managers;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.economy.CurrencyType;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.Material;
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
    
    // Liquidation Cache
    private final Map<Material, Double> liquidationValues = new HashMap<>();
    private double pricePerEnchantLevel;
    private boolean respectDurability;

    public EconomyManager(AegisGuard plugin) {
        this.plugin = plugin;
        loadLiquidationValues(); // Load v1.3.0 "Trash to Cash" values
    }

    /**
     * Checks if the player has enough of the specific currency.
     */
    public boolean has(Player p, double amount, CurrencyType type) {
        if (p.hasPermission("aegis.admin.bypass")) return true;
        if (amount <= 0) return true;

        return switch (type) {
            case VAULT -> plugin.vault().has(p, amount);
            case EXP -> getTotalExperience(p) >= (int) amount;
            case LEVEL -> p.getLevel() >= (int) amount;
            case ITEM -> {
                Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
                yield p.getInventory().containsAtLeast(new ItemStack(mat), (int) amount);
            }
        };
    }
    
    // Overload for default currency (VAULT)
    public boolean has(Player p, double amount) {
        return has(p, amount, CurrencyType.VAULT);
    }

    /**
     * Deducts the currency from the player.
     * Returns TRUE if successful, FALSE if they couldn't pay.
     */
    public boolean withdraw(Player p, double amount, CurrencyType type) {
        if (p.hasPermission("aegis.admin.bypass")) return true;
        if (amount <= 0) return true;

        if (!has(p, amount, type)) return false;

        switch (type) {
            case VAULT -> {
                return plugin.vault().charge(p, amount);
            }
            case EXP -> {
                setTotalExperience(p, getTotalExperience(p) - (int) amount);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
                return true;
            }
            case LEVEL -> {
                p.setLevel(p.getLevel() - (int) amount);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
                return true;
            }
            case ITEM -> {
                Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
                p.getInventory().removeItem(new ItemStack(mat, (int) amount));
                // p.updateInventory(); // Not strictly needed in 1.20+
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                return true;
            }
        }
        return false;
    }

    // Overload for default currency
    public boolean withdraw(Player p, double amount) {
        return withdraw(p, amount, CurrencyType.VAULT);
    }

    /**
     * Refunds/Gives currency to the player.
     */
    public void deposit(Player p, double amount, CurrencyType type) {
        if (amount <= 0) return;

        switch (type) {
            case VAULT -> plugin.vault().give(p, amount);
            case EXP -> p.giveExp((int) amount);
            case LEVEL -> p.giveExpLevels((int) amount);
            case ITEM -> {
                Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(mat, (int) amount));
                for (ItemStack drop : left.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
        }
    }
    
    // Overload for default currency
    public void deposit(Player p, double amount) {
        deposit(p, amount, CurrencyType.VAULT);
    }

    /**
     * Returns a nice string like "500 Coins" or "10 Levels".
     */
    public String format(double amount, CurrencyType type) {
        return switch (type) {
            case VAULT -> plugin.vault().format(amount);
            case EXP -> (int) amount + " XP";
            case LEVEL -> (int) amount + " Levels";
            case ITEM -> {
                Material mat = plugin.cfg().getWorldItemCostType(null); 
                String name = formatMaterialName(mat);
                if (amount != 1) name += "s"; 
                yield (int) amount + " " + name;
            }
        };
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

    // ==========================================================
    // 💸 UPKEEP & COST CALCULATOR
    // ==========================================================
    
    /**
     * Calculate daily rent for an Estate based on size.
     */
    public double calculateDailyUpkeep(Estate estate) {
        double base = plugin.getConfig().getDouble("economy.upkeep.base_cost", 100.0);
        double perBlock = plugin.getConfig().getDouble("economy.upkeep.price_per_block", 0.50);
        
        // Calculate Area (Width * Length)
        long area = estate.getRegion().getArea();
        
        double total = base + (area * perBlock);

        // TODO: Hook into Bastion Level to apply Guild Discounts here
        
        return total;
    }

    /**
     * Calculate creation cost for a new Estate selection.
     */
    public double calculateClaimCost(com.aegisguard.objects.Cuboid region) {
        if (region == null) return 0.0;
        
        double baseCost = plugin.getConfig().getDouble("economy.costs.private_estate_creation", 100.0);
        double pricePerBlock = plugin.getConfig().getDouble("expansions.price_per_block", 10.0);
        long area = region.getArea();
        
        return baseCost + (area * pricePerBlock);
    }

    // ==========================================================
    // 🛠️ UTILITIES
    // ==========================================================

    private String formatMaterialName(Material mat) {
        String name = mat.name().toLowerCase().replace("_", " ");
        char[] chars = name.toCharArray();
        boolean found = false;
        for (int i = 0; i < chars.length; i++) {
            if (!found && Character.isLetter(chars[i])) {
                chars[i] = Character.toUpperCase(chars[i]);
                found = true;
            } else if (Character.isWhitespace(chars[i])) {
                found = false;
            }
        }
        return String.valueOf(chars);
    }

    // --- XP Calculation Utilities ---
    
    private int getTotalExperience(Player player) {
        int experience = 0;
        int level = player.getLevel();
        if (level >= 0 && level <= 15) {
            experience = (int) Math.ceil(Math.pow(level, 2) + 6 * level);
        } else if (level > 15 && level <= 30) {
            experience = (int) Math.ceil((2.5 * Math.pow(level, 2) - 40.5 * level + 360));
        } else {
            experience = (int) Math.ceil(((4.5 * Math.pow(level, 2) - 162.5 * level + 2220)));
        }
        return experience + Math.round(player.getExp() * player.getExpToLevel());
    }

    private void setTotalExperience(Player player, int amount) {
        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.giveExp(amount);
    }
}
