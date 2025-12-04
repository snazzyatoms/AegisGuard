package com.yourname.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.gui.GUIManager;
import com.yourname.aegisguard.managers.AllianceManager;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.objects.Guild;
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
        
        // 1. Get Player's Guild
        Guild guild = allianceManager.getPlayerGuild(player.getUniqueId());
        
        if (guild == null) {
            openNoGuildMenu(player);
            return;
        }

        // 2. Prepare Dashboard
        String title = lang.getGui("title_guild_dashboard").replace("%guild%", guild.getName());
        Inventory inv = Bukkit.createInventory(null, 45, title);

        // Background Filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        // =========================================================
        // 🏰 CENTERPIECE: GUILD STATUS (Slot 13)
        // =========================================================
        ItemStack statusItem = new ItemStack(Material.BEACON);
        ItemMeta meta = statusItem.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + guild.getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Level: §f" + guild.getLevel());
        lore.add("§7Members: §f" + guild.getMemberCount());
        lore.add(" ");
        lore.add("§7Treasury: §e$" + String.format("%.2f", guild.getBalance()));
        
        // Status check (Frozen?)
        if (allianceManager.isTreasuryFrozen(guild)) {
             lore.add(" ");
             lore.add(lang.getMsg(player, "treasury_frozen")
                 .replace("%current%", String.valueOf(guild.getMemberCount()))
                 .replace("%min%", "2")); // Retrieve config min later
        }
        
        meta.setLore(colorize(lore));
        statusItem.setItemMeta(meta);
        inv.setItem(13, statusItem);

        // =========================================================
        // 💰 TREASURY & BANKING (Slot 29)
        // =========================================================
        inv.setItem(29, createButton(Material.GOLD_BLOCK, "&eAlliance Treasury", "guild_bank",
            "&7Deposit funds to support", "&7your guild's growth.", " ", "&eClick to Deposit/View"));

        // =========================================================
        // 👥 ROSTER & ROLES (Slot 31)
        // =========================================================
        inv.setItem(31, createButton(Material.PLAYER_HEAD, "&bMember Roster", "guild_members",
            "&7View all members and", "&7manage their roles.", " ", "&eClick to Manage"));

        // =========================================================
        // 📈 LEVEL UP / BASTION (Slot 33)
        // =========================================================
        inv.setItem(33, createButton(Material.EXPERIENCE_BOTTLE, "&dUpgrade Bastion", "guild_upgrade",
            "&7Unlock new perks and", "&7expand your territory.", " ", "&eClick to View Tree"));

        // =========================================================
        // ⚙️ ADMIN / SETTINGS (Slot 40) - Leader Only
        // =========================================================
        if (guild.getLeader().equals(player.getUniqueId())) {
             inv.setItem(40, createButton(Material.COMPARATOR, "&cAdmin Settings", "guild_settings",
                "&7Rename Guild", "&7Disband Guild", " ", "&c&lLEADER ONLY"));
        } else {
             inv.setItem(40, createButton(Material.RED_BED, "&cLeave Guild", "guild_leave",
                "&7Abandon your allegiance.", " ", "&cClick to Leave"));
        }

        // Close Button
        inv.setItem(44, GUIManager.createItem(Material.BARRIER, lang.getGui("button_close")));

        player.openInventory(inv);
    }

    private void openNoGuildMenu(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        Inventory inv = Bukkit.createInventory(null, 27, "§8No Alliance Found");
        
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(11, createButton(Material.GRASS_BLOCK, "&aCreate a Guild", "guild_create",
            "&7Start your own empire.", "&7Cost: &a$5,000"));
            
        inv.setItem(15, createButton(Material.PAPER, "&bAccept Invite", "guild_join",
            "&7View pending invitations."));
            
        inv.setItem(26, GUIManager.createItem(Material.BARRIER, lang.getGui("button_close")));
            
        player.openInventory(inv);
    }

    // =========================================================
    // 🛠️ UTILITIES
    // =========================================================
    
    private ItemStack createButton(Material mat, String name, String actionId, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            List<String> lore = new ArrayList<>();
            if (loreLines != null) {
                for (String line : loreLines) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            }
            meta.setLore(lore);
            
            if (actionId != null) {
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, actionId);
            }
            
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private List<String> colorize(List<String> list) {
        List<String> colored = new ArrayList<>();
        for (String s : list) colored.add(ChatColor.translateAlternateColorCodes('&', s));
        return colored;
    }
}
