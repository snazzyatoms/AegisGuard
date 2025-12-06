package com.aegisguard.managers;

import com.aegisguard.AegisGuard;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Central registry for all special AegisGuard items.
 *
 * - Player Claim Wand  (Aegis Scepter)
 * - Sentinel Scepter   (Admin-only server wand)
 *
 * Detection is done EXCLUSIVELY via PersistentDataContainer keys,
 * so items cannot be forged with anvils, renaming, or lore edits.
 */
public class ItemManager {

    private final AegisGuard plugin;

    // Hidden tags – these are the "true identity" of items
    public static NamespacedKey PLAYER_WAND_KEY;
    public static NamespacedKey SENTINEL_WAND_KEY;

    public ItemManager(AegisGuard plugin) {
        this.plugin = plugin;
        PLAYER_WAND_KEY = new NamespacedKey(plugin, "player_wand");
        SENTINEL_WAND_KEY = new NamespacedKey(plugin, "sentinel_scepter");
    }

    // =====================================================================
    // 🪄 PLAYER CLAIM WAND (Aegis Scepter)
    // =====================================================================

    /**
     * Standard player wand used for private estate selection.
     * This is what /ag wand (AegisCommand / EstateCommand) should give.
     */
    public ItemStack getPlayerWand() {
        ItemStack rod = new ItemStack(Material.LIGHTNING_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Aegis Scepter");
            meta.setLore(java.util.Arrays.asList(
                    ChatColor.GRAY + "Left-click: Set Corner A",
                    ChatColor.GRAY + "Right-click: Set Corner B",
                    ChatColor.DARK_AQUA + "Used to claim private Estates"
            ));

            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.addEnchant(Enchantment.LUCK, 1, true); // subtle glow

            // Hidden tag: marks this as the official player wand
            meta.getPersistentDataContainer().set(
                    PLAYER_WAND_KEY,
                    PersistentDataType.BYTE,
                    (byte) 1
            );

            rod.setItemMeta(meta);
        }
        return rod;
    }

    /**
     * Returns true only if the item is a genuine Aegis player wand.
     * A renamed lightning rod will NOT pass this check.
     */
    public boolean isPlayerWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte flag = pdc.get(PLAYER_WAND_KEY, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // =====================================================================
    // 🛡️ SENTINEL SCEPTER (Admin-only Server Wand)
    // =====================================================================

    /**
     * Admin-only Sentinel Scepter for defining server / safe-zone estates.
     * This is what /ag admin wand should give.
     */
    public ItemStack getSentinelScepter() {
        ItemStack rod = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Sentinel Scepter");
            meta.setLore(java.util.Arrays.asList(
                    ChatColor.GRAY + "Left-click: Set Server Corner A",
                    ChatColor.GRAY + "Right-click: Set Server Corner B",
                    ChatColor.YELLOW + "Used for server & safe-zone Estates"
            ));

            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.addEnchant(Enchantment.LUCK, 1, true);

            // Hidden tag: marks this as the official Sentinel Scepter
            meta.getPersistentDataContainer().set(
                    SENTINEL_WAND_KEY,
                    PersistentDataType.BYTE,
                    (byte) 1
            );

            rod.setItemMeta(meta);
        }
        return rod;
    }

    /**
     * Returns true only if the item is a genuine Sentinel Scepter.
     * A random blaze rod with the same name will NOT pass this check.
     */
    public boolean isSentinelScepter(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte flag = pdc.get(SENTINEL_WAND_KEY, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    // =====================================================================
    // 🔧 HELPER UTILITIES
    // =====================================================================

    /**
     * Returns true if this item is ANY recognized Aegis selection tool.
     * Handy if you ever need one unified check.
     */
    public boolean isAnyWand(ItemStack item) {
        return isPlayerWand(item) || isSentinelScepter(item);
    }

    /**
     * Removes every Aegis wand/scepter from a player's inventory.
     * Optional, but handy for debug/admin cleanup.
     */
    public void removeAllWands(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        boolean changed = false;

        for (int i = 0; i < contents.length; i++) {
            if (isAnyWand(contents[i])) {
                contents[i] = null;
                changed = true;
            }
        }

        if (changed) {
            player.getInventory().setContents(contents);
        }
    }
}
