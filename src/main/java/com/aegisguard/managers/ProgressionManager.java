package com.yourname.aegisguard.managers;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.objects.Estate;
import com.yourname.aegisguard.objects.Guild;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class ProgressionManager {

    private final AegisGuard plugin;
    
    // Level Data Cache: Level -> List of Reward Strings
    private final Map<Integer, List<String>> ascensionRewards = new HashMap<>();
    private final Map<Integer, List<String>> bastionRewards = new HashMap<>();

    public ProgressionManager(AegisGuard plugin) {
        this.plugin = plugin;
        loadProgressionFiles();
    }

    public void loadProgressionFiles() {
        loadData("ascension.yml", ascensionRewards);
        loadData("bastion.yml", bastionRewards);
    }

    private void loadData(String filename, Map<Integer, List<String>> targetMap) {
        targetMap.clear();
        File file = new File(plugin.getDataFolder(), filename);
        if (!file.exists()) plugin.saveResource(filename, false);
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection rewards = config.getConfigurationSection("rewards");
        
        if (rewards != null) {
            for (String key : rewards.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    List<String> lines = rewards.getStringList(key + ".rewards");
                    targetMap.put(level, lines);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    /**
     * Get all active perks for a specific Estate based on its level.
     */
    public List<ItemStack> getActivePerks(Estate estate) {
        List<ItemStack> perks = new ArrayList<>();
        int level = getEstateLevel(estate);
        Map<Integer, List<String>> rewardMap = estate.isGuild() ? bastionRewards : ascensionRewards;
        String sourceName = estate.isGuild() ? "Bastion" : "Ascension";

        // Loop through all levels up to current to gather cumulative perks
        // (Or just check active buffs logic depending on your design)
        for (int i = 1; i <= level; i++) {
            List<String> rewards = rewardMap.get(i);
            if (rewards == null) continue;

            for (String rewardStr : rewards) {
                ItemStack icon = parseRewardIcon(rewardStr, sourceName, i);
                if (icon != null) perks.add(icon);
            }
        }
        
        if (perks.isEmpty()) {
            perks.add(plugin.getGuiManager().createItem(Material.GLASS_BOTTLE, "&7No Active Perks", 
                List.of("&7Upgrade your estate to unlock buffs!")));
        }
        
        return perks;
    }

    /**
     * Helper to retrieve the level (Placeholder logic until stored in DB)
     */
    public int getEstateLevel(Estate estate) {
        if (estate.isGuild()) {
            Guild guild = plugin.getAllianceManager().getGuild(estate.getOwnerId());
            return (guild != null) ? guild.getLevel() : 1;
        } else {
            // For private estates, level is stored on the Estate object
            // You need to add 'private int level' to Estate.java if not added yet
            return 1; // Default
        }
    }

    /**
     * Converts a config string like "EFFECT:SPEED:1" into a GUI Item.
     */
    private ItemStack parseRewardIcon(String rewardString, String source, int level) {
        String[] parts = rewardString.split(":");
        String type = parts[0].toUpperCase();

        Material mat = Material.BOOK;
        String name = "&fUnknown Perk";
        String desc = "&7A mysterious power.";

        switch (type) {
            case "EFFECT":
                mat = Material.POTION;
                name = "&bPotion Effect: &f" + formatEnum(parts[1]) + " " + parts[2];
                desc = "&7Permanent effect inside borders.";
                break;
            case "FLIGHT":
            case "FLAG":
                if (parts.length > 1 && parts[1].equalsIgnoreCase("fly")) {
                    mat = Material.FEATHER;
                    name = "&f🕊 Flight";
                    desc = "&7Ability to fly inside borders.";
                } else {
                    mat = Material.PAPER;
                    name = "&fFlag: " + (parts.length > 1 ? parts[1] : "Unknown");
                    desc = "&7Special region flag enabled.";
                }
                break;
            case "MEMBERS":
                mat = Material.PLAYER_HEAD;
                name = "&aRoster Upgrade";
                desc = "&7Increases max members by " + parts[1];
                break;
            case "RADIUS":
                mat = Material.GRASS_BLOCK;
                name = "&eLand Expansion";
                desc = "&7Increases claim size by " + parts[1] + " blocks.";
                break;
            case "TAX_REDUCTION":
                mat = Material.GOLD_INGOT;
                name = "&6Tax Break";
                desc = "&7Reduces daily upkeep by " + (Double.parseDouble(parts[1]) * 100) + "%.";
                break;
            default:
                return null; // Skip unknown internal rewards
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(Arrays.asList(
            ChatColor.translateAlternateColorCodes('&', desc),
            " ",
            ChatColor.translateAlternateColorCodes('&', "&8» Source: &7" + source + " Level " + level)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private String formatEnum(String input) {
        return input.charAt(0) + input.substring(1).toLowerCase().replace("_", " ");
    }
}
