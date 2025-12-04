package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.GuildGUI;
import com.aegisguard.managers.LanguageManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
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

    private final PetitionGUI petitionGUI;
    private final PetitionAdminGUI petitionAdminGUI;
    private final GuildGUI guildGUI;
    private final AdminGUI adminGUI;
    private final LandGrantGUI landGrantGUI;
    private final InfoGUI infoGUI;
    private final PlotCosmeticsGUI cosmeticsGUI; // Added
    private final AdminPlotListGUI plotListGUI; // Added
    private final VisitGUI visitGUI; // Added
    private final PlotMarketGUI marketGUI; // Added
    private final PlotAuctionGUI auctionGUI; // Added
    private final RolesGUI rolesGUI;
    private final PlotFlagsGUI flagsGUI;
    private final LevelingGUI levelingGUI;
    private final ZoningGUI zoningGUI;
    private final BiomeGUI biomeGUI;

    public GUIManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "ag_action");
        
        this.petitionGUI = new PetitionGUI(plugin);
        this.petitionAdminGUI = new PetitionAdminGUI(plugin);
        this.guildGUI = new GuildGUI(plugin);
        this.adminGUI = new AdminGUI(plugin);
        this.landGrantGUI = new LandGrantGUI(plugin);
        this.infoGUI = new InfoGUI(plugin);
        this.cosmeticsGUI = new PlotCosmeticsGUI(plugin);
        this.plotListGUI = new AdminPlotListGUI(plugin);
        this.visitGUI = new VisitGUI(plugin);
        this.marketGUI = new PlotMarketGUI(plugin);
        this.auctionGUI = new PlotAuctionGUI(plugin);
        this.rolesGUI = new RolesGUI(plugin);
        this.flagsGUI = new PlotFlagsGUI(plugin);
        this.levelingGUI = new LevelingGUI(plugin);
        this.zoningGUI = new ZoningGUI(plugin);
        this.biomeGUI = new BiomeGUI(plugin);
    }
    
    public void openGuardianCodex(Player player) {
        // ... existing logic ...
        new PlayerGUI(plugin).open(player);
    }

    // Getters
    public PetitionGUI petition() { return petitionGUI; }
    public PetitionAdminGUI petitionAdmin() { return petitionAdminGUI; }
    public GuildGUI guild() { return guildGUI; }
    public AdminGUI admin() { return adminGUI; }
    public LandGrantGUI landGrant() { return landGrantGUI; }
    public InfoGUI info() { return infoGUI; }
    public PlotCosmeticsGUI cosmetics() { return cosmeticsGUI; }
    public AdminPlotListGUI plotList() { return plotListGUI; }
    public VisitGUI visit() { return visitGUI; }
    public PlotMarketGUI market() { return marketGUI; }
    public PlotAuctionGUI auction() { return auctionGUI; }
    public RolesGUI roles() { return rolesGUI; }
    public PlotFlagsGUI flags() { return flagsGUI; }
    public LevelingGUI leveling() { return levelingGUI; }
    public ZoningGUI zoning() { return zoningGUI; }
    public BiomeGUI biomes() { return biomeGUI; }
    
    // ... Utilities (createItem, safeText, etc) ...
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
            if (name != null) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            // ... lore logic ...
            item.setItemMeta(meta);
        }
        return item;
    }
    
    public static ItemStack createItem(Material mat, String name) {
        return createItem(mat, name, null);
    }
}
