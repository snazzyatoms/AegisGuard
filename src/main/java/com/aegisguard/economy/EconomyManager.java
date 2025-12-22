package com.aegisguard.economy;

import com.aegisguard.AegisGuard;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public class EconomyManager {

    private final AegisGuard plugin;

    public EconomyManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean has(Player p, double amount, CurrencyType type) {
        if (p == null) return false;
        if (p.hasPermission("aegis.admin.bypass")) return true;
        if (amount <= 0) return true;
        if (type == null) return false;

        return switch (type) {
            case VAULT -> plugin.vault() != null && plugin.vault().has(p, amount);
            case EXP -> getTotalExperience(p) >= (int) amount;
            case LEVEL -> p.getLevel() >= (int) amount;
            case ITEM -> {
                Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
                yield p.getInventory().containsAtLeast(new ItemStack(mat), (int) amount);
            }
            case CLAIM_BLOCKS -> {
                if (plugin.getClaimBlockManager() == null) yield false;
                yield plugin.getClaimBlockManager().getAvailableBlocks(p.getUniqueId()) >= (long) amount;
            }
            default -> false;
        };
    }

    public boolean withdraw(Player p, double amount, CurrencyType type) {
        if (p == null) return false;
        if (p.hasPermission("aegis.admin.bypass")) return true;
        if (amount <= 0) return true;
        if (type == null) return false;

        if (!has(p, amount, type)) return false;

        // ✅ Switch EXPRESSION: guaranteed return for all paths
        return switch (type) {
            case VAULT -> plugin.vault() != null && plugin.vault().charge(p, amount);

            case EXP -> {
                setTotalExperience(p, getTotalExperience(p) - (int) amount);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
                yield true;
            }

            case LEVEL -> {
                p.setLevel(p.getLevel() - (int) amount);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
                yield true;
            }

            case ITEM -> {
                Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
                p.getInventory().removeItem(new ItemStack(mat, (int) amount));
                p.updateInventory();
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                yield true;
            }

            case CLAIM_BLOCKS -> {
                if (plugin.getClaimBlockManager() == null) yield false;
                boolean ok = plugin.getClaimBlockManager().spend(p.getUniqueId(), (long) amount);
                if (ok) p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.15f);
                yield ok;
            }

            default -> false;
        };
    }

    public void deposit(Player p, double amount, CurrencyType type) {
        if (p == null) return;
        if (amount <= 0) return;
        if (type == null) return;

        switch (type) {
            case VAULT -> {
                if (plugin.vault() != null) plugin.vault().give(p, amount);
            }
            case EXP -> p.giveExp((int) amount);
            case LEVEL -> p.giveExpLevels((int) amount);
            case ITEM -> {
                Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(mat, (int) amount));
                for (ItemStack drop : left.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
            case CLAIM_BLOCKS -> {
                if (plugin.getClaimBlockManager() != null) {
                    plugin.getClaimBlockManager().refund(p.getUniqueId(), (long) amount);
                }
            }
            default -> {
                // no-op
            }
        }
    }

    public String format(double amount, CurrencyType type) {
        if (type == null) return String.valueOf(amount);

        return switch (type) {
            case VAULT -> plugin.vault() != null ? plugin.vault().format(amount) : String.valueOf(amount);
            case EXP -> (int) amount + " XP";
            case LEVEL -> (int) amount + " Levels";
            case ITEM -> {
                Material mat = (plugin.cfg() != null)
                        ? plugin.cfg().getWorldItemCostType(null)
                        : Material.DIAMOND;

                String name = mat.name().toLowerCase().replace("_", " ");
                name = capitalizeWords(name);
                if (amount != 1) name += "s";
                yield (int) amount + " " + name;
            }
            case CLAIM_BLOCKS -> (long) amount + " Claim Blocks";
            default -> String.valueOf(amount);
        };
    }

    private String capitalizeWords(String s) {
        if (s == null || s.isBlank()) return s;
        char[] chars = s.toCharArray();
        boolean found = false;

        for (int i = 0; i < chars.length; i++) {
            if (!found && Character.isLetter(chars[i])) {
                chars[i] = Character.toUpperCase(chars[i]);
                found = true;
            } else if (Character.isWhitespace(chars[i]) || chars[i] == '.' || chars[i] == '\'') {
                found = false;
            }
        }
        return String.valueOf(chars);
    }

    private int getTotalExperience(Player player) {
        int level = player.getLevel();
        int experience;

        if (level >= 0 && level <= 15) {
            experience = (int) Math.ceil(Math.pow(level, 2) + 6 * level);
        } else if (level <= 30) {
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
