package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemManager {

    private final AegisGuard plugin;
    
    // Security Keys (The hidden "fingerprints")
    private final NamespacedKey KEY_ADMIN_WAND;
    private final NamespacedKey KEY_PLAYER_WAND;

    public ItemManager(AegisGuard plugin) {
        this.plugin = plugin;
        // These keys allow us to identify the item even if a player renames it
        this.KEY_ADMIN_WAND = new NamespacedKey(plugin, "sentinel_scepter");
        this.KEY_PLAYER_WAND = new NamespacedKey(plugin, "claim_wand");
    }

    /**
     * Generates the Sentinel's Scepter (Admin Tool)
     */
    public ItemStack getSentinelScepter() {
        FileConfiguration config = plugin.cfg().raw(); // Adjust if you use plugin.getConfig()

        // 1. Read Config
        String matName = config.getString("admin.wand.material", "BLAZE_ROD");
        String displayName = config.getString("admin.wand.name", "&c&lSentinel's Scepter");
        List<String> lore = config.getStringList("admin.wand.lore");

        // 2. Validate Material
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            plugin.getLogger().warning("Invalid material for Admin Wand: " + matName + ". Defaulting to BLAZE_ROD.");
            mat = Material.BLAZE_ROD;
        }

        // 3. Create Item
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Apply Text
            meta.setDisplayName(format(displayName));
            List<String> formattedLore = new ArrayList<>();
            if (lore != null) {
                for (String line : lore) formattedLore.add(format(line));
            }
            meta.setLore(formattedLore);

            // Apply Glow Effect
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // 4. APPLY SECURITY TAG
            meta.getPersistentDataContainer().set(KEY_ADMIN_WAND, PersistentDataType.BYTE, (byte) 1);
            
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Generates the Aegis Wand (Player Claim Tool)
     */
    public ItemStack getPlayerWand() {
        FileConfiguration config = plugin.cfg().raw();

        // 1. Read Config
        String matName = config.getString("claims.wand.material", "GOLDEN_SHOVEL");
        String displayName = config.getString("claims.wand.name", "&eClaiming Tool");
        List<String> lore = config.getStringList("claims.wand.lore");

        // 2. Validate Material
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            plugin.getLogger().warning("Invalid material for Player Wand: " + matName + ". Defaulting to GOLDEN_SHOVEL.");
            mat = Material.GOLDEN_SHOVEL;
        }

        // 3. Create Item
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Apply Text
            meta.setDisplayName(format(displayName));
            List<String> formattedLore = new ArrayList<>();
            if (lore != null) {
                for (String line : lore) formattedLore.add(format(line));
            }
            meta.setLore(formattedLore);

            // Optional: Glow (You can make this configurable later)
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // 4. APPLY SECURITY TAG
            meta.getPersistentDataContainer().set(KEY_PLAYER_WAND, PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }

        return item;
    }

    // ==============================================================
    // VALIDATION LOGIC (Use these in your Listeners)
    // ==============================================================

    public boolean isSentinelScepter(ItemStack item) {
        if (isInvalid(item)) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_ADMIN_WAND, PersistentDataType.BYTE);
    }

    public boolean isPlayerWand(ItemStack item) {
        if (isInvalid(item)) return false;
        return item.getItemMeta().getPersistentDataContainer().has(KEY_PLAYER_WAND, PersistentDataType.BYTE);
    }

    // Helper to check for air/null
    private boolean isInvalid(ItemStack item) {
        return item == null || item.getType() == Material.AIR || !item.hasItemMeta();
    }

    // Helper for colors
    private String format(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
