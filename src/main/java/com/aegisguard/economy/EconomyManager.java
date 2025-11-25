package com.aegisguard.economy;

import com.aegisguard.AegisGuard;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap; // <--- FIX: Added missing import

public class EconomyManager {

    private final AegisGuard plugin;

    public EconomyManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if the player has enough of the specific currency.
     */
    public boolean has(Player p, double amount, CurrencyType type) {
        if (p.hasPermission("aegis.admin.bypass")) return true;
        if (amount <= 0) return true;

        switch (type) {
            case VAULT:
                return plugin.vault().has(p, amount);
            
            case EXP:
                return getTotalExperience(p) >= (int) amount;
                
            case LEVEL:
                return p.getLevel() >= (int) amount;
                
            case ITEM:
                Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
                return p.getInventory().containsAtLeast(new ItemStack(mat), (int) amount);
                
            default:
                return true;
        }
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
            case VAULT:
                return plugin.vault().charge(p, amount);
                
            case EXP:
                setTotalExperience(p, getTotalExperience(p) - (int) amount);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
                return true;
                
            case LEVEL:
                p.setLevel(p.getLevel() - (int) amount);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
                return true;
                
            case ITEM:
                Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
                p.getInventory().removeItem(new ItemStack(mat, (int) amount));
                p.updateInventory();
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                return true;
        }
        return false;
    }

    /**
     * Refunds/Gives currency to the player.
     */
    public void deposit(Player p, double amount, CurrencyType type) {
        if (amount <= 0) return;

        switch (type) {
            case VAULT:
                plugin.vault().give(p, amount);
                break;
            case EXP:
                p.giveExp((int) amount);
                break;
            case LEVEL:
                p.giveExpLevels((int) amount);
                break;
            case ITEM:
                Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(mat, (int) amount));
                for (ItemStack drop : left.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
                break;
        }
    }

    /**
     * Returns a nice string like "500 Coins" or "10 Levels".
     */
    public String format(double amount, CurrencyType type) {
        switch (type) {
            case VAULT: return plugin.vault().format(amount);
            case EXP: return (int) amount + " XP";
            case LEVEL: return (int) amount + " Levels";
            case ITEM: 
                // You might want to get the material name from config for a perfect display
                return (int) amount + " Items"; 
            default: return String.valueOf(amount);
        }
    }

    // --- XP Calculation Helpers (Bukkit doesn't make this easy) ---
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
