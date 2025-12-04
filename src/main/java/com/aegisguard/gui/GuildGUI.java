package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.AllianceManager;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Guild;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class GuildGUI {

    private final AegisGuard plugin;
    private final NamespacedKey actionKey;

    public GuildGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "guild_action");
    }

    public void openDashboard(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        AllianceManager allianceManager = plugin.getAllianceManager();
        
        Guild guild = allianceManager.getPlayerGuild(player.getUniqueId());
        
        if (guild == null) {
            openNoGuildMenu(player);
            return;
        }

        String title = lang.getGui("title_guild_dashboard").replace("%guild%", guild.getName());
        Inventory inv = Bukkit.createInventory(null, 45, title);
        
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        // Status Item
        ItemStack statusItem = new ItemStack(Material.BEACON);
        ItemMeta meta = statusItem.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + guild.getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Level: §f" + guild.getLevel());
        lore.add("§7Members: §f" + guild.getMemberCount());
        
        meta.setLore(lore);
        statusItem.setItemMeta(meta);
        inv.setItem(13, statusItem);

        // Buttons
        inv.setItem(31, createButton(Material.PLAYER_HEAD, "&bMember Roster", "guild_members", "&7Manage roles."));
        inv.setItem(40, createButton(Material.RED_BED, "&cLeave Guild", "guild_leave", "&7Abandon allegiance."));

        // Close
        inv.setItem(44, GUIManager.createItem(Material.BARRIER, lang.getGui("button_close")));

        player.openInventory(inv);
    }

    private void openNoGuildMenu(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        Inventory inv = Bukkit.createInventory(null, 27, "§8No Alliance Found");
        
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(11, createButton(Material.GRASS_BLOCK, "&aCreate a Guild", "guild_create", "&7Cost: &a$5,000"));
        inv.setItem(26, GUIManager.createItem(Material.BARRIER, lang.getGui("button_close")));
            
        player.openInventory(inv);
    }
    
    private ItemStack createButton(Material mat, String name, String actionId, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> lore = new ArrayList<>();
            for (String line : loreLines) lore.add(ChatColor.translateAlternateColorCodes('&', line));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, actionId);
            item.setItemMeta(meta);
        }
        return item;
    }
}
