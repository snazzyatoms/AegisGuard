package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import com.aegisguard.objects.Guild;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlayerGUI {

    private final AegisGuard plugin;
    private final NamespacedKey actionKey;

    public PlayerGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "ag_action");
    }

    // Critical: This specific class is checked by GUIListener to prevent moving items
    public static class PlayerMenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        
        // FIXED: Fallback title if lang file is missing keys
        String title = lang.getGui("menu_title");
        if (title == null || title.equals("menu_title") || title.contains("Missing")) {
            title = "§8The Guardian Codex";
        }
        
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 54, title);

        // --- 1. BORDERS (The v1.2.0 Glass Frame) ---
        ItemStack filler = GUIManager.getFiller();
        int[] borderSlots = {0,1,2,3,4,5,6,7,8, 9,17, 18,26, 27,35, 36,44, 45,46,47,51,52,53};
        for (int i : borderSlots) inv.setItem(i, filler);

        // --- 2. HEADER / STATUS (Center) ---
        ItemStack statusItem;
        if (estate != null) {
            // Standing in Estate -> Show "Manage Current"
            statusItem = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = statusItem.getItemMeta();
            
            String ownerName = Bukkit.getOfflinePlayer(estate.getOwnerId()).getName();
            String estateName = estate.getName();
            
            // Use a hardcoded fallback if lang is missing
            String displayName = lang.getMsg(player, "title_entering", Map.of("PLOT_NAME", estateName));
            if (displayName.contains("Missing")) displayName = "§b" + estateName;
            meta.setDisplayName(displayName);
            
            List<String> lore = new ArrayList<>();
            lore.add("§8----------------");
            lore.add("§7Owner: §f" + ownerName);
            lore.add("§7Level: §e" + estate.getLevel());
            lore.add(" ");
            
            if (estate.getOwnerId().equals(player.getUniqueId()) || plugin.isAdmin(player)) {
                lore.add("§eClick to Manage Estate");
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "manage_current_estate");
            } else {
                lore.add("§7(You are a guest here)");
            }
            
            meta.setLore(colorize(lore));
            statusItem.setItemMeta(meta);
            
            // ACTIVE PERKS (Side Window) - Slot 17
            inv.setItem(17, plugin.getGuiManager().createActionItem(Material.ENCHANTED_BOOK, 
                "§d🔮 Active Perks", "view_perks", 
                "§7View active effects & buffs.", " ", "§eClick to View ➡"));

        } else {
            // Wilderness -> Show "Claim Here"
            statusItem = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta meta = statusItem.getItemMeta();
            
            String displayName = lang.getMsg(player, "title_entering_wilderness");
            if (displayName.contains("Missing")) displayName = "§aThe Wilderness";
            meta.setDisplayName(displayName);
            
            List<String> lore = new ArrayList<>();
            lore.add("§7You are in the wild.");
            lore.add(" ");
            lore.add("§eClick to Claim Land");
            
            meta.setLore(colorize(lore));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "start_claim");
            statusItem.setItemMeta(meta);
        }
        inv.setItem(13, statusItem); // Center Slot

        // --- 3. MAIN CHAPTERS (The v1.2.0 Layout) ---

        // [Slot 4] Info / Help
        inv.setItem(4, plugin.getGuiManager().createActionItem(Material.WRITABLE_BOOK, 
            "§6📜 Guidebook", "open_info",
            "§7Read the manual."));

        // [Slot 11] Claiming Tools
        inv.setItem(11, plugin.getGuiManager().createActionItem(Material.GOLDEN_HOE, 
            "§e§lI. Land Claiming", "start_claim",
            "§7Get the Wand to claim land.", " ", "§eClick to Equip"));

        // [Slot 15] THE KING'S LEDGER (NEW Guild System)
        Guild guild = plugin.getAllianceManager().getPlayerGuild(player.getUniqueId());
        String guildStatus = (guild != null) ? "&a" + guild.getName() : "&7None";
        inv.setItem(15, plugin.getGuiManager().createActionItem(Material.GOLDEN_HELMET, 
            "§6§lII. The King's Ledger", "open_guild",
            "§7Alliance & Guild Management.", "§7Current: " + guildStatus, " ", "§eClick to Open Ledger"));

        // [Slot 20] My Private Estates (Replaces old "My Plots")
        inv.setItem(20, plugin.getGuiManager().createActionItem(Material.OAK_DOOR, 
            "§3§lIII. My Estates", "open_estates",
            "§7List all lands you own.", " ", "§eClick to View List"));

        // [Slot 22] Flags (Wards)
        inv.setItem(22, plugin.getGuiManager().createActionItem(Material.OAK_SIGN, 
            "§9§lIV. Land Wards", "manage_flags",
            "§7Edit PvP, Mobs, and Rules.", " ", "§eClick to Manage"));

        // [Slot 24] Roles (Trust)
        inv.setItem(24, plugin.getGuiManager().createActionItem(Material.PLAYER_HEAD, 
            "§e§lV. Roles & Trust", "manage_roles",
            "§7Trust friends on your land.", " ", "§eClick to Manage"));

        // [Slot 29] Leveling (Ascension)
        if (plugin.cfg().isProgressionEnabled()) {
            inv.setItem(29, plugin.getGuiManager().createActionItem(Material.EXPERIENCE_BOTTLE, 
                "§d§lVI. Ascension", "open_leveling",
                "§7Upgrade estate limits."));
        }
        
        // [Slot 31] Zoning (Sub-Claims)
        if (plugin.cfg().isZoningEnabled()) {
             inv.setItem(31, plugin.getGuiManager().createActionItem(Material.IRON_BARS, 
                "§3§lVII. Zoning", "open_zoning",
                "§7Create rental sub-claims."));
        }
        
        // [Slot 33] Biomes
        if (plugin.cfg().isBiomesEnabled()) {
             inv.setItem(33, plugin.getGuiManager().createActionItem(Material.SPORE_BLOSSOM, 
                "§2§lVIII. Biomes", "open_biomes",
                "§7Change estate biome/colors."));
        }

        // --- 6. ECONOMY & EXPANSION ---
        
        // Market (Slot 38)
        inv.setItem(38, plugin.getGuiManager().createActionItem(Material.GOLD_INGOT, 
            "§aMarketplace", "open_market",
            "§7Buy and Sell Land Deeds."));

        // Auctions (Slot 42)
        if (plugin.cfg().isUpkeepEnabled()) {
             inv.setItem(42, plugin.getGuiManager().createActionItem(Material.LAVA_BUCKET, 
                "§cAuctions", "open_auction",
                "§7Bid on expired lands."));
        }

        // --- 7. FOOTER ---
        
        // Settings (Slot 48)
        inv.setItem(48, plugin.getGuiManager().createActionItem(Material.COMPARATOR, 
            "§7Settings", "open_settings",
            "§7Language, Sounds, Notifications."));

        // Admin (Slot 49)
        if (plugin.isAdmin(player)) {
            inv.setItem(49, plugin.getGuiManager().createActionItem(Material.REDSTONE_BLOCK, 
                "§cAdmin Panel", "open_admin",
                "§cOperator Control Panel"));
        }

        // Exit (Slot 50)
        inv.setItem(50, plugin.getGuiManager().createActionItem(Material.BARRIER, 
            "§cClose", "close",
            "§7Close Menu"));

        player.openInventory(inv);
        GUIManager.playClick(player);
    }
    
    public void openPerksMenu(Player player, Estate estate) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8Active Perks");
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Fetch real perks from ProgressionManager
        List<ItemStack> perks = plugin.getProgressionManager().getActivePerks(estate);
        int slot = 10;
        for (ItemStack perk : perks) {
            if (slot > 16) break;
            inv.setItem(slot++, perk);
        }

        inv.setItem(22, plugin.getGuiManager().createActionItem(Material.ARROW, "§c⬅ Back", "back_to_codex"));
        player.openInventory(inv);
    }

    private List<String> colorize(List<String> list) {
        List<String> colored = new ArrayList<>();
        for (String s : list) colored.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', s));
        return colored;
    }
}
