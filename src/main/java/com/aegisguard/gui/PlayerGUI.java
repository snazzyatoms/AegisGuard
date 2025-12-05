package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import com.aegisguard.objects.Guild;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

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

    public static class PlayerMenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        
        // Title from config or default
        String title = lang.getGui("menu_title");
        if (title.contains("Missing")) title = "§8AegisGuard Dashboard";
        
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 54, title);

        // --- 1. BORDERS (Classic Style) ---
        ItemStack filler = GUIManager.getFiller();
        int[] borderSlots = {0,1,2,3,4,5,6,7,8, 9,17, 18,26, 27,35, 36,44, 45,46,47,51,52,53};
        for (int i : borderSlots) inv.setItem(i, filler);

        // --- 2. HEADER ---
        
        // Codex / Info (Slot 4)
        inv.setItem(4, plugin.getGuiManager().createActionItem(Material.WRITABLE_BOOK, 
            lang.getMsg(player, "button_info"), "open_info",
            lang.getMsgList(player, "info_lore").toArray(new String[0])));

        // Travel (Slot 13)
        if (plugin.getConfig().getBoolean("estates.travel_system.enabled", true)) {
             inv.setItem(13, plugin.getGuiManager().createActionItem(Material.COMPASS,
                lang.getGui("visit_gui_title"), "open_visit",
                "§7Visit other estates & warps."));
        }

        // --- 3. CORE MANAGEMENT (The Classic Layout) ---
        
        // v1.3.0 Update: Use EstateManager instead of Store
        Estate currentEstate = plugin.getEstateManager().getEstateAt(player.getLocation());
        boolean isOwner = currentEstate != null && currentEstate.getOwnerId().equals(player.getUniqueId());
        boolean isAdmin = plugin.isAdmin(player);
        boolean canManage = isOwner || isAdmin;

        // Claim Land (Slot 20)
        boolean hasSelection = plugin.getSelection().hasSelection(player);
        if (hasSelection) {
            inv.setItem(20, plugin.getGuiManager().createActionItem(Material.LIGHTNING_ROD, 
                lang.getGui("button_claim_land"), "start_claim",
                "§7Create estate from selection.", " ", "§eClick to Confirm"));
        } else {
            inv.setItem(20, plugin.getGuiManager().createActionItem(Material.BARRIER, 
                "§c" + lang.getGui("button_claim_land"), "get_wand",
                "§7You need a selection first.", "§eClick to get Wand"));
        }

        // Flags (Slot 22)
        Material flagIcon = canManage ? Material.OAK_SIGN : Material.OAK_HANGING_SIGN;
        String flagDesc = canManage ? "§eClick to Manage" : "§cStand in your land to edit.";
        inv.setItem(22, plugin.getGuiManager().createActionItem(flagIcon, 
            lang.getGui("button_plot_flags"), "manage_flags",
            "§7Edit PvP, Mobs, and Build rules.", " ", flagDesc));

        // Roles (Slot 24)
        Material roleIcon = canManage ? Material.PLAYER_HEAD : Material.SKELETON_SKULL;
        inv.setItem(24, plugin.getGuiManager().createActionItem(roleIcon, 
            lang.getGui("button_roles"), "manage_roles",
            "§7Trust friends and assign roles.", " ", flagDesc));
        
        // --- 4. v1.3.0 NEW FEATURE: THE KING'S LEDGER ---
        // Slot 15 (Top Right Area)
        Guild guild = plugin.getAllianceManager().getPlayerGuild(player.getUniqueId());
        String guildStatus = (guild != null) ? "&a" + guild.getName() : "&7None";
        inv.setItem(15, plugin.getGuiManager().createActionItem(Material.GOLDEN_HELMET, 
            "§6The King's Ledger", "open_guild",
            "§7Guild & Alliance Management.", "§7Current: " + guildStatus, " ", "§eClick to Open"));

        // --- 5. ADVANCED FEATURES ---
        
        // Leveling (Slot 29)
        if (plugin.cfg().isProgressionEnabled()) {
            inv.setItem(29, plugin.getGuiManager().createActionItem(Material.EXPERIENCE_BOTTLE, 
                lang.getGui("level_gui_title"), "open_leveling",
                "§7Upgrade estate limits."));
        }
        
        // Zoning (Slot 31)
        if (plugin.cfg().isZoningEnabled()) {
             inv.setItem(31, plugin.getGuiManager().createActionItem(Material.IRON_BARS, 
                lang.getGui("zone_gui_title"), "open_zoning",
                "§7Create sub-claim rentals."));
        }
        
        // Biomes (Slot 33)
        if (plugin.cfg().isBiomesEnabled()) {
             inv.setItem(33, plugin.getGuiManager().createActionItem(Material.SPORE_BLOSSOM, 
                lang.getGui("biome_gui_title"), "open_biomes",
                "§7Change estate biome/colors."));
        }

        // --- 6. ECONOMY & EXPANSION ---
        
        // Market (Slot 38)
        inv.setItem(38, plugin.getGuiManager().createActionItem(Material.GOLD_INGOT, 
            lang.getGui("button_market"), "open_market",
            "§7Buy and Sell Estates."));

        // Expansion (Slot 40) - Replaced with Petition/Land Grant
        inv.setItem(40, plugin.getGuiManager().createActionItem(Material.DIAMOND_PICKAXE, 
            lang.getGui("button_expand"), "open_petition",
            "§7Request size increase."));

        // Auctions (Slot 42)
        if (plugin.cfg().isUpkeepEnabled()) {
             inv.setItem(42, plugin.getGuiManager().createActionItem(Material.LAVA_BUCKET, 
                lang.getGui("button_auction"), "open_auction",
                "§7Bid on expired lands."));
        }

        // --- 7. FOOTER ---
        
        // Settings (Slot 48)
        inv.setItem(48, plugin.getGuiManager().createActionItem(Material.COMPARATOR, 
            lang.getGui("button_player_settings"), "open_settings",
            "§7Personal settings."));

        // Admin (Slot 49)
        if (plugin.isAdmin(player)) {
            inv.setItem(49, plugin.getGuiManager().createActionItem(Material.REDSTONE_BLOCK, 
                lang.getGui("admin_menu_title"), "open_admin",
                "§cOperator Access Only"));
        }

        // Exit (Slot 50)
        inv.setItem(50, plugin.getGuiManager().createActionItem(Material.BARRIER, 
            lang.getGui("button_exit"), "close",
            "§7Close Menu"));

        player.openInventory(inv);
        GUIManager.playClick(player);
    }
    
    public void openPerksMenu(Player player, Estate estate) {
        // (Keep your perks menu logic here)
        Inventory inv = Bukkit.createInventory(null, 27, "§8Active Perks");
        // ... items ...
        player.openInventory(inv);
    }
}
