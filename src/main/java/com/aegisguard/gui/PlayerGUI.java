package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
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

    public static class PlayerMenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        
        String title = lang.getGui("menu_title");
        if (title.contains("Missing")) title = "§8The Guardian Codex";
        
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 54, title);

        // --- 1. BORDERS ---
        ItemStack filler = GUIManager.getFiller();
        int[] borderSlots = {0,1,2,3,4,5,6,7,8, 9,17, 18,26, 27,35, 36,44, 45,46,47,51,52,53};
        for (int i : borderSlots) inv.setItem(i, filler);

        // --- 2. HEADER / STATUS (Center) ---
        ItemStack statusItem;
        if (estate != null) {
            statusItem = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = statusItem.getItemMeta();
            meta.setDisplayName(lang.getMsg(player, "title_entering", Map.of("PLOT_NAME", estate.getName())));
            List<String> lore = new ArrayList<>();
            lore.add("§7Owner: §f" + Bukkit.getOfflinePlayer(estate.getOwnerId()).getName());
            lore.add(" ");
            lore.add("§eClick to Manage Estate");
            meta.setLore(lore);
            // Tag for listener
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "manage_current_estate");
            statusItem.setItemMeta(meta);
            
            // PERKS BUTTON (Side Window) - Slot 17
            inv.setItem(17, plugin.getGuiManager().createActionItem(Material.ENCHANTED_BOOK, 
                "§d🔮 Active Perks", "view_perks", 
                "§7View active effects.", " ", "§eClick to View"));
        } else {
            statusItem = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta meta = statusItem.getItemMeta();
            meta.setDisplayName(lang.getMsg(player, "title_entering_wilderness"));
            List<String> lore = new ArrayList<>();
            lore.add("§7Wilderness");
            lore.add(" ");
            lore.add("§eClick to Claim Here");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "start_claim");
            statusItem.setItemMeta(meta);
        }
        inv.setItem(4, statusItem); // Top Center

        // --- 3. MAIN CHAPTERS (The 1.2.0 Layout) ---

        // 11: Claiming Tools
        inv.setItem(11, plugin.getGuiManager().createActionItem(Material.GOLDEN_HOE, 
            "§e§lI. Land Claiming", "start_claim",
            "§7Get the Wand to claim land.", " ", "§eClick to Equip"));

        // 13: Travel
        inv.setItem(13, plugin.getGuiManager().createActionItem(Material.COMPASS, 
            "§b§lII. Travel", "open_visit",
            "§7Visit other estates or warps.", " ", "§eClick to Browse"));
            
        // 15: Guilds (NEW 1.3.0 Feature)
        inv.setItem(15, plugin.getGuiManager().createActionItem(Material.GOLDEN_HELMET, 
            "§6§lIII. Guild Alliance", "open_guild",
            "§7Manage your Guild, Bank,", "§7and Bastion upgrades.", " ", "§eClick to Open"));

        // 29: Economy (Market)
        inv.setItem(29, plugin.getGuiManager().createActionItem(Material.GOLD_INGOT, 
            "§a§lIV. Economy", "open_market",
            "§7Buy and Sell Land Deeds.", " ", "§eClick to View"));

        // 31: My Estates (Management)
        inv.setItem(31, plugin.getGuiManager().createActionItem(Material.OAK_DOOR, 
            "§3§lV. My Estates", "open_estates",
            "§7List all lands you own.", " ", "§eClick to List"));

        // 33: Auction House
        inv.setItem(33, plugin.getGuiManager().createActionItem(Material.LAVA_BUCKET, 
            "§c§lVI. Auctions", "open_auction",
            "§7Bid on expired lands.", " ", "§eClick to Bid"));

        // --- 4. FOOTER ---
        
        // 48: Settings
        inv.setItem(48, plugin.getGuiManager().createActionItem(Material.COMPARATOR, 
            lang.getGui("settings_menu_title"), "open_settings",
            "§7Language, Sounds, Notifications."));

        // 49: Admin (If Op)
        if (plugin.isAdmin(player)) {
            inv.setItem(49, plugin.getGuiManager().createActionItem(Material.REDSTONE_BLOCK, 
                lang.getGui("admin_menu_title"), "open_admin",
                "§cOperator Control Panel"));
        }

        // 50: Exit
        inv.setItem(50, plugin.getGuiManager().createActionItem(Material.BARRIER, 
            lang.getGui("button_exit"), "close"));

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
}
