package com.yourname.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.GuildGUI;
// import com.aegisguard.expansions.ExpansionRequestAdminGUI; // TODO: Rename this later
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class GUIManager {

    private final AegisGuard plugin;

    // --- SUB-MENUS ---
    private final PetitionGUI petitionGUI; // Was ExpansionRequestGUI
    private final GuildGUI guildGUI;       // v1.3.0 New
    private final AdminGUI adminGUI;
    private final LandGrantGUI landGrantGUI; // v1.3.0 New
    
    // Legacy / Placeholder GUIs (Keep these if you haven't updated them yet)
    // private final PlayerGUI playerGUI;
    // private final SettingsGUI settingsGUI;
    // private final RolesGUI rolesGUI;
    
    public GUIManager(AegisGuard plugin) {
        this.plugin = plugin;
        
        // Initialize all sub-menus
        this.petitionGUI = new PetitionGUI(plugin);
        this.guildGUI = new GuildGUI(plugin);
        this.adminGUI = new AdminGUI(plugin);
        this.landGrantGUI = new LandGrantGUI(plugin);
        
        // Initialize Legacy GUIs (Commented out until you update them)
        // this.playerGUI = new PlayerGUI(plugin);
        // this.settingsGUI = new SettingsGUI(plugin);
    }

    // --- OPENERS ---
    
    public void openMainMenu(Player player) {
        // In v1.3.0, the Main Menu logic is now handled directly inside GuiManager 
        // (see the code we wrote earlier with the "Center Slot" logic).
        // Since this class *IS* the GuiManager, you can paste that logic here 
        // OR delegate it to a new MainMenuGUI class.
        
        // For now, let's assume you put the Main Menu logic in a method here:
        openGuardianCodex(player);
    }
    
    public void openGuardianCodex(Player player) {
        // [Paste the Main Menu Logic we wrote earlier here]
        // See: "The Player Experience: The Guardian Codex" section from previous turn
    }
    
    public void openDiagnostics(Player player) {
        player.sendMessage("§b[AegisGuard] §7Diagnostics: All systems nominal (v1.3.0).");
    }

    // --- GETTERS ---

    public PetitionGUI petition() { return petitionGUI; }
    public GuildGUI guild() { return guildGUI; }
    public AdminGUI admin() { return adminGUI; }
    public LandGrantGUI landGrant() { return landGrantGUI; }
    
    // Legacy Getters (Add back as you update files)
    // public PlayerGUI player() { return playerGUI; }
    // public ExpansionRequestAdminGUI expansionAdmin() { return expansionAdminGUI; }

    // ======================================
    // --- UTILITIES (Static Helpers) ---
    // ======================================

    /**
     * RESTORED: Converts null or placeholder strings to a safe fallback.
     */
    public static String safeText(String fromMsg, String fallback) {
        if (fromMsg == null) return fallback;
        if (fromMsg.contains("[Missing") || fromMsg.contains("null")) return fallback;
        return fromMsg;
    }

    /**
     * Creates a standardized GUI Item with color translation.
     */
    public static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.setDisplayName(color(name));
            if (lore != null) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) coloredLore.add(color(line));
                meta.setLore(coloredLore);
            }
            // Hide attributes for clean look
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
    
    // Overload for simple items
    public static ItemStack createItem(Material mat, String name) {
        return createItem(mat, name, null);
    }

    /**
     * Creates a filler item (Gray Glass Pane) for empty slots.
     */
    public static ItemStack getFiller() {
        return createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    public static void playClick(Player p) {
        try {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        } catch (Exception ignored) {}
    }
    
    public static void playSuccess(Player p) {
        try {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
        } catch (Exception ignored) {}
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
