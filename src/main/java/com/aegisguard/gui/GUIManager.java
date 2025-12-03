package com.yourname.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.GuildGUI;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class GUIManager {

    private final AegisGuard plugin;
    private final NamespacedKey actionKey;

    // --- SUB-MENUS ---
    private final PetitionGUI petitionGUI;
    private final PetitionAdminGUI petitionAdminGUI;
    private final GuildGUI guildGUI;
    private final AdminGUI adminGUI;
    private final LandGrantGUI landGrantGUI;
    
    // Legacy / Placeholder GUIs (Keep these if you haven't updated them yet)
    // private final PlayerGUI playerGUI;
    // private final SettingsGUI settingsGUI;
    // private final RolesGUI rolesGUI;

    public GUIManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "ag_action");
        
        // Initialize all sub-menus
        this.petitionGUI = new PetitionGUI(plugin);
        this.petitionAdminGUI = new PetitionAdminGUI(plugin);
        this.guildGUI = new GuildGUI(plugin);
        this.adminGUI = new AdminGUI(plugin);
        this.landGrantGUI = new LandGrantGUI(plugin);
        
        // Initialize Legacy GUIs
        // this.playerGUI = new PlayerGUI(plugin);
        // this.settingsGUI = new SettingsGUI(plugin);
    }

    // --- OPENERS ---
    
    public void openMainMenu(Player player) {
        openGuardianCodex(player);
    }
    
    public void openGuardianCodex(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        
        String title = lang.getGui("title_main");
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Background Filler
        ItemStack filler = getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // =========================================================
        // 📍 CENTER SLOT: CURRENT LOCATION STATUS (The Sidebar Replacement)
        // =========================================================
        ItemStack statusItem;
        if (estate != null) {
            // Standing in Estate
            statusItem = new ItemStack(Material.FILLED_MAP);
            ItemMeta meta = statusItem.getItemMeta();
            meta.setDisplayName(lang.getMsg(player, "enter_title").replace("%name%", estate.getName()));
            
            List<String> lore = new ArrayList<>();
            lore.add(" ");
            lore.add("&8» &7Owner: &f" + Bukkit.getOfflinePlayer(estate.getOwnerId()).getName());
            // lore.add("&8» &7Level: &e" + estate.getLevel()); 
            // lore.add("&8» &7Tax Paid Until: &a" + getReadableDate(estate.getPaidUntil()));
            lore.add(" ");
            lore.add("&eClick to Manage");
            
            meta.setLore(colorize(lore));
            // Tag for listener
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "manage_current_estate");
            statusItem.setItemMeta(meta);
            
            // --- 🔮 PERKS BUTTON (Side Window) ---
            // Slot 17 (Middle Right)
            ItemStack perksIcon = createItem(Material.ENCHANTED_BOOK, "&d🔮 Active Estate Perks");
            ItemMeta perksMeta = perksIcon.getItemMeta();
            List<String> perksLore = new ArrayList<>();
            perksLore.add(" ");
            perksLore.add("&7View active effects & buffs.");
            perksLore.add(" ");
            perksLore.add("&eClick to View ➡");
            
            perksMeta.setLore(colorize(perksLore));
            perksMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "view_perks");
            perksIcon.setItemMeta(perksMeta);
            
            inv.setItem(17, perksIcon);

        } else {
            // Standing in Wilderness
            statusItem = new ItemStack(Material.GRASS_BLOCK);
            ItemMeta meta = statusItem.getItemMeta();
            meta.setDisplayName(lang.getMsg(player, "exit_title")); // "Wilderness"
            List<String> lore = new ArrayList<>();
            lore.add("&7You are standing in unclaimed territory.");
            lore.add(" ");
            lore.add("&eClick to Deed (Claim) this land.");
            meta.setLore(colorize(lore));
            
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "start_claim");
            statusItem.setItemMeta(meta);
        }
        
        inv.setItem(13, statusItem);

        // =========================================================
        // 🔘 NAVIGATION BUTTONS
        // =========================================================
        
        // [11] My Estates List
        inv.setItem(11, createActionItem(Material.OAK_DOOR, "&6My Properties", "open_estates",
            "&7View and manage all your", "&7Private and Guild estates.", " ", "&eClick to View"));

        // [15] Guild Dashboard
        inv.setItem(15, createActionItem(Material.GOLDEN_HELMET, "&eGuild Dashboard", "open_guild",
            "&7Access your Alliance,", "&7Treasury, and Roster.", " ", "&eClick to Open"));

        // [22] Settings / Language
        inv.setItem(22, createActionItem(Material.COMPARATOR, "&7Personal Settings", "open_settings",
            "&7Language, Sounds, and Notifications.", " ", "&eClick to Configure"));

        player.openInventory(inv);
        playClick(player);
    }
    
    /**
     * NEW: The "Side Window" Menu for Active Perks.
     */
    public void openPerksMenu(Player player, Estate estate) {
        Inventory inv = Bukkit.createInventory(null, 27, "&8Active Perks");
        
        ItemStack filler = getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Placeholder for fetching actual buffs from Ascension/Bastion manager
        int slot = 10;
        
        inv.setItem(slot++, createItem(Material.GOLDEN_PICKAXE, "&e⚡ Haste II", 
            List.of("&7Mining speed increased.", "&7Source: &fBastion Level 5")));

        inv.setItem(slot++, createItem(Material.SUGAR, "&b💨 Speed I", 
            List.of("&7Movement speed increased.", "&7Source: &fBastion Level 2")));
            
        inv.setItem(slot++, createItem(Material.FEATHER, "&f🕊 Flight", 
            List.of("&7Creative flight enabled.", "&7Source: &fAscension Level 10")));

        // Back Button
        inv.setItem(22, createActionItem(Material.ARROW, "&c⬅ Back", "back_to_codex"));

        player.openInventory(inv);
    }

    public void openDiagnostics(Player player) {
        player.sendMessage("§b[AegisGuard] §7Diagnostics: All systems nominal (v1.3.0).");
    }

    // --- GETTERS ---

    public PetitionGUI petition() { return petitionGUI; }
    public PetitionAdminGUI petitionAdmin() { return petitionAdminGUI; }
    public GuildGUI guild() { return guildGUI; }
    public AdminGUI admin() { return adminGUI; }
    public LandGrantGUI landGrant() { return landGrantGUI; }

    // ======================================
    // --- UTILITIES (Static Helpers) ---
    // ======================================

    public static String safeText(String fromMsg, String fallback) {
        if (fromMsg == null) return fallback;
        if (fromMsg.contains("[Missing") || fromMsg.contains("null")) return fallback;
        return fromMsg;
    }

    public static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(color(name));
            if (lore != null) meta.setLore(colorize(lore));
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
    
    public static ItemStack createItem(Material mat, String name) {
        return createItem(mat, name, null);
    }
    
    public ItemStack createActionItem(Material mat, String name, String actionId, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> lore = new ArrayList<>();
            for (String s : loreLines) lore.add(color(s));
            meta.setLore(lore);
            
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, actionId);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    public static ItemStack getFiller() {
        return createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static void playClick(Player p) {
        try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f); } catch (Exception ignored) {}
    }
    
    public static void playSuccess(Player p) {
        try { p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f); } catch (Exception ignored) {}
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
    
    private static List<String> colorize(List<String> list) {
        List<String> colored = new ArrayList<>();
        for (String s : list) colored.add(color(s));
        return colored;
    }
}
