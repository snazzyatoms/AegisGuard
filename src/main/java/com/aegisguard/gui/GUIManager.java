package com.yourname.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.GuildGUI;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;

public class GUIManager {

    private final AegisGuard plugin;
    private final NamespacedKey actionKey;

    // --- SUB-MENUS ---
    private final PlayerGUI playerGUI; // Restored!
    private final PetitionGUI petitionGUI;
    private final PetitionAdminGUI petitionAdminGUI;
    private final GuildGUI guildGUI;
    private final AdminGUI adminGUI;
    private final LandGrantGUI landGrantGUI;
    private final InfoGUI infoGUI; // Added back
    // private final VisitGUI visitGUI; // Add back if VisitGUI is updated
    
    public GUIManager(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "ag_action");
        
        // Initialize all sub-menus
        this.playerGUI = new PlayerGUI(plugin); // The Main Menu Logic is here now
        this.petitionGUI = new PetitionGUI(plugin);
        this.petitionAdminGUI = new PetitionAdminGUI(plugin);
        this.guildGUI = new GuildGUI(plugin);
        this.adminGUI = new AdminGUI(plugin);
        this.landGrantGUI = new LandGrantGUI(plugin);
        this.infoGUI = new InfoGUI(plugin);
    }

    // --- OPENERS ---
    
    /**
     * Opens the Main Menu (Guardian Codex).
     * Delegates to PlayerGUI.
     */
    public void openMain(Player player) {
        playerGUI.open(player);
    }
    
    // Alias for older code
    public void openGuardianCodex(Player player) {
        openMain(player);
    }
    
    // --- GETTERS ---

    public PlayerGUI main() { return playerGUI; }
    public PetitionGUI petition() { return petitionGUI; }
    public PetitionAdminGUI petitionAdmin() { return petitionAdminGUI; }
    public GuildGUI guild() { return guildGUI; }
    public AdminGUI admin() { return adminGUI; }
    public LandGrantGUI landGrant() { return landGrantGUI; }
    public InfoGUI info() { return infoGUI; }

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
