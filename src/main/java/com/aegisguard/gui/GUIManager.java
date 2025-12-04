package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.ArrayList;

public class GUIManager {

    private final AegisGuard plugin;
    private final NamespacedKey actionKey;

    // --- SUB-MENUS ---
    // Note: No imports needed because they are all in the same package (com.aegisguard.gui)
    private final PetitionGUI petitionGUI;
    private final PetitionAdminGUI petitionAdminGUI;
    private final GuildGUI guildGUI;
    private final AdminGUI adminGUI;
    private final LandGrantGUI landGrantGUI;
    private final InfoGUI infoGUI;
    private final PlotCosmeticsGUI cosmeticsGUI;
    private final AdminPlotListGUI plotListGUI;
    private final VisitGUI visitGUI;
    
    // Renamed to match v1.3.0 Estate System
    private final EstateMarketGUI marketGUI; 
    private final EstateAuctionGUI auctionGUI;
    
    private final RolesGUI rolesGUI;
    private final PlotFlagsGUI flagsGUI;
    private final LevelingGUI levelingGUI;
    private final ZoningGUI zoningGUI;
    private final BiomeGUI biomeGUI;
    private final SettingsGUI settingsGUI;
    private final PlayerGUI playerGUI;

    public GUIManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "ag_action");
        
        // Initialize all sub-menus
        this.petitionGUI = new PetitionGUI(plugin);
        this.petitionAdminGUI = new PetitionAdminGUI(plugin);
        this.guildGUI = new GuildGUI(plugin);
        this.adminGUI = new AdminGUI(plugin);
        this.landGrantGUI = new LandGrantGUI(plugin);
        this.infoGUI = new InfoGUI(plugin);
        this.cosmeticsGUI = new PlotCosmeticsGUI(plugin);
        this.plotListGUI = new AdminPlotListGUI(plugin);
        this.visitGUI = new VisitGUI(plugin);
        
        // Initialize new Estate GUIs
        this.marketGUI = new EstateMarketGUI(plugin);
        this.auctionGUI = new EstateAuctionGUI(plugin);
        
        this.rolesGUI = new RolesGUI(plugin);
        this.flagsGUI = new PlotFlagsGUI(plugin);
        this.levelingGUI = new LevelingGUI(plugin);
        this.zoningGUI = new ZoningGUI(plugin);
        this.biomeGUI = new BiomeGUI(plugin);
        this.settingsGUI = new SettingsGUI(plugin);
        
        // Main Menu Logic moved here
        this.playerGUI = new PlayerGUI(plugin); 
    }
    
    public void openGuardianCodex(Player player) {
        // Delegate to the dedicated PlayerGUI class
        playerGUI.open(player);
    }
    
    public void openPerksMenu(Player player, Estate estate) {
        // Delegate to PlayerGUI for the side window
        playerGUI.openPerksMenu(player, estate);
    }

    // --- GETTERS ---
    public PetitionGUI petition() { return petitionGUI; }
    public PetitionAdminGUI petitionAdmin() { return petitionAdminGUI; }
    public GuildGUI guild() { return guildGUI; }
    public AdminGUI admin() { return adminGUI; }
    public LandGrantGUI landGrant() { return landGrantGUI; }
    public InfoGUI info() { return infoGUI; }
    public PlotCosmeticsGUI cosmetics() { return cosmeticsGUI; }
    public AdminPlotListGUI plotList() { return plotListGUI; }
    public VisitGUI visit() { return visitGUI; }
    
    // Fixed Types
    public EstateMarketGUI market() { return marketGUI; }
    public EstateAuctionGUI auction() { return auctionGUI; }
    
    public RolesGUI roles() { return rolesGUI; }
    public PlotFlagsGUI flags() { return flagsGUI; }
    public LevelingGUI leveling() { return levelingGUI; }
    public ZoningGUI zoning() { return zoningGUI; }
    public BiomeGUI biomes() { return biomeGUI; }
    public SettingsGUI settings() { return settingsGUI; }
    
    // ======================================
    // --- UTILITIES (Static Helpers) ---
    // ======================================

    public static String safeText(String fromMsg, String fallback) {
        if (fromMsg == null) return fallback;
        return fromMsg;
    }
    
    public static ItemStack getFiller() {
        return createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
    }
    
    public static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(color(name));
            if (lore != null) {
                List<String> colored = new ArrayList<>();
                for(String l : lore) colored.add(color(l));
                meta.setLore(colored);
            }
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
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
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
}
