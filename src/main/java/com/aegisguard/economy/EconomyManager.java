package com.aegisguard.economy;

import com.aegisguard.AegisGuard;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.apache.commons.lang.WordUtils; // Built-in Bukkit lib for capitalization

import java.util.HashMap;

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
                p.updateInventory(); // Safe to call in modern versions
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                return true;
            }
        }
        return false;
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
                // Drop excess on the ground if inventory full
                for (ItemStack drop : left.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
        }
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
                // Formatting: "DIAMOND_BLOCK" -> "Diamond Block"
                // Ideally pass the World to get the specific item type, 
                // but for general formatting we assume default or use a generic label.
                Material mat = Material.EMERALD; // Default fallback for generic display
                if (plugin.cfg() != null) {
                     // Note: To be perfectly accurate this needs a World context, 
                     // but usually format() is used in GUIs where context implies the player's world.
                     // For now, we return a generic string or try to grab a default.
                     mat = plugin.cfg().getWorldItemCostType(null); 
                }
                
                String name = mat.name().toLowerCase().replace("_", " ");
                try {
                    // Capitalize first letter of each word
                    char[] chars = name.toCharArray();
                    boolean found = false;
                    for (int i = 0; i < chars.length; i++) {
                        if (!found && Character.isLetter(chars[i])) {
                            chars[i] = Character.toUpperCase(chars[i]);
                            found = true;
                        } else if (Character.isWhitespace(chars[i]) || chars[i]=='.' || chars[i]=='\'') { 
                            found = false;
                        }
                    }
                    name = String.valueOf(chars);
                } catch (Exception ignored) {}
                
                // Pluralize roughly
                if (amount != 1) name += "s"; 
                
                yield (int) amount + " " + name;
            }
        };
    }

    // --- XP Calculation Utilities ---
    
    /**
     * Calculates total XP points from levels + progress.
     */
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

    /**
     * Completely resets player XP and gives them the new total amount.
     */
    private void setTotalExperience(Player player, int amount) {
        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.giveExp(amount);
    }
}
