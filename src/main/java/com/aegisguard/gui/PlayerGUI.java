package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import com.aegisguard.objects.Guild;
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

    // Critical: This specific class is checked by GUIListener to prevent moving items
    public static class PlayerMenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        
        // Localized Title (Defaults to "The Guardian Codex" if missing)
        String title = lang.getGui("menu_title");
        if (title.contains("Missing")) title = "§8The Guardian Codex";
        
        Inventory inv = Bukkit.createInventory(new PlayerMenuHolder(), 54, title);

        // --- 1. BORDERS (The v1.2.0 Glass Frame) ---
        ItemStack filler = GUIManager.getFiller();
        int[] borderSlots = {0,1,2,3,4,5,6,7,8, 9,17, 18,26, 27,35, 36,44, 45,46,47,51,52,53};
        for (int i : borderSlots) inv.setItem(i, filler);

        // --- 2. CENTER STATUS (Dynamic) ---
        ItemStack statusItem;
        if (estate != null) {
            // Standing in Estate -> Show "Manage Current"
            statusItem = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = statusItem.getItemMeta();
            
            String ownerName = Bukkit.getOfflinePlayer(estate.getOwnerId()).getName();
            String estateName = estate.getName();
            
            meta.setDisplayName(lang.getMsg(player, "title_entering", Map.of("PLOT_NAME", estateName)));
            
            List<String> lore = new ArrayList<>();
            lore.add("§8----------------");
            lore.add("§7Owner: §f" + ownerName);
            lore.add("§7Level: §e" + estate.getLevel());
            lore.add(" ");
            
            if (estate.getOwnerId().equals(player.getUniqueId()) || plugin.isAdmin(player)) {
                lore.add("§eClick to Manage Settings");
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "manage_current_estate");
            } else {
                lore.add("§7(You are a guest here)");
            }
            
            meta.setLore(colorize(lore));
            statusItem.setItemMeta(meta);
            
            // ACTIVE PERKS (Side Window) - Slot 17
            inv.setItem(17, plugin.getGuiManager().createActionItem(Material.ENCHANTED_BOOK, 
                "&d🔮 Active Perks", "view_perks", 
                "&7View active effects & buffs.", " ", "&eClick to View ➡"));

        } else {
            // Wilderness -> Show "Claim Here"
            statusItem = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta meta = statusItem.getItemMeta();
            meta.setDisplayName(lang.getMsg(player, "title_entering_wilderness"));
            
            List<String> lore = new ArrayList<>();
            lore.add("§7You are in the wild.");
            lore.add(" ");
            lore.add("§eClick to Claim Land");
            
            meta.setLore(colorize(lore));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "start_claim");
            statusItem.setItemMeta(meta);
        }
        inv.setItem(13, statusItem); // Center Slot

        // --- 3. MAIN CHAPTERS (The Layout) ---

        // [Slot 11] Claiming Tools
        inv.setItem(11, plugin.getGuiManager().createActionItem(Material.GOLDEN_HOE, 
            "§e§lI. Land Claiming", "start_claim",
            "§7Get the Wand to claim land.", " ", "§eClick to Equip"));

        // [Slot 20] The King's Ledger (Guilds)
        // This replaces the old generic "My Plots" with the new v1.3.0 Guild System
        Guild guild = plugin.getAllianceManager().getPlayerGuild(player.getUniqueId());
        String guildStatus = (guild != null) ? "&a" + guild.getName() : "&7None";
        
        inv.setItem(20, plugin.getGuiManager().createActionItem(Material.GOLDEN_HELMET, 
            "§6§lII. The King's Ledger", "open_guild",
            "§7Alliance & Guild Management.", "§7Current: " + guildStatus, " ", "§eClick to Open Ledger"));

        // [Slot 22] My Private Estates
        inv.setItem(22, plugin.getGuiManager().createActionItem(Material.OAK_DOOR, 
            "§3§lIII. My Private Estates", "open_estates",
            "§7List all personal lands.", " ", "§eClick to View List"));

        // [Slot 24] Travel / Visit
        inv.setItem(24, plugin.getGuiManager().createActionItem(Material.COMPASS, 
            "§b§lIV. World Travel", "open_visit",
            "§7Visit allies and warps.", " ", "§eClick to Browse"));

        // [Slot 29] Economy / Market
        inv.setItem(29, plugin.getGuiManager().createActionItem(Material.GOLD_INGOT, 
            "§a§lV. Real Estate Market", "open_market",
            "§7Buy and Sell Land Deeds.", " ", "§eClick to Shop"));

        // [Slot 33] Auctions
        inv.setItem(33, plugin.getGuiManager().createActionItem(Material.LAVA_BUCKET, 
            "§c§lVI. Lost Land Auctions", "open_auction",
            "§7Bid on expired estates.", " ", "§eClick to Bid"));

        // --- 4. FOOTER UTILITIES ---
        
        // [Slot 48] Settings
        inv.setItem(48, plugin.getGuiManager().createActionItem(Material.COMPARATOR, 
            lang.getGui("settings_menu_title"), "open_settings",
            "§7Language, Sounds, Notifications."));

        // [Slot 49] Admin Panel (If OP)
        if (plugin.isAdmin(player)) {
            inv.setItem(49, plugin.getGuiManager().createActionItem(Material.REDSTONE_BLOCK, 
                lang.getGui("admin_menu_title"), "open_admin",
                "§cOperator Control Panel"));
        }

        // [Slot 50] Exit
        inv.setItem(50, plugin.getGuiManager().createActionItem(Material.BARRIER, 
            lang.getGui("button_exit"), "close",
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
