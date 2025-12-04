package com.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class InfoGUI {

    private final AegisGuard plugin;

    public InfoGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class InfoHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        LanguageManager lang = plugin.getLanguageManager();
        String title = lang.getGui("title_main"); // Reusing Main Title or create "title_help"
        Inventory inv = Bukkit.createInventory(new InfoHolder(), 45, title);

        ItemStack filler = GUIManager.getFiller();
        for(int i=0; i<45; i++) inv.setItem(i, filler);

        // --- 1. CLAIMING / ESTATES ---
        inv.setItem(10, GUIManager.createItem(Material.GOLDEN_HOE, 
            lang.getMsg(player, "codex_claim_title"), 
            lang.getMsgList(player, "codex_claim_lore")));

        // --- 2. GUILDS / ALLIANCES (New v1.3.0) ---
        inv.setItem(12, GUIManager.createItem(Material.GOLDEN_HELMET, 
            lang.getMsg(player, "codex_guild_title"), 
            lang.getMsgList(player, "codex_guild_lore")));

        // --- 3. MENUS & COMMANDS ---
        inv.setItem(14, GUIManager.createItem(Material.WRITABLE_BOOK, 
            lang.getMsg(player, "codex_menus_title"), 
            lang.getMsgList(player, "codex_menus_lore")));

        // --- 4. SECURITY & ROLES ---
        inv.setItem(16, GUIManager.createItem(Material.SHIELD, 
            lang.getMsg(player, "codex_security_title"), 
            lang.getMsgList(player, "codex_security_lore")));
        
        // --- 5. ECONOMY & LIQUIDATION ---
        inv.setItem(22, GUIManager.createItem(Material.GOLD_INGOT, 
            lang.getMsg(player, "codex_economy_title"), 
            lang.getMsgList(player, "codex_economy_lore")));

        // --- 6. IDENTITY ---
        inv.setItem(24, GUIManager.createItem(Material.NAME_TAG, 
            lang.getMsg(player, "codex_identity_title"), 
            lang.getMsgList(player, "codex_identity_lore")));
        
        // --- 7. ADVANCED (Zoning/Banned) ---
        inv.setItem(31, GUIManager.createItem(Material.EXPERIENCE_BOTTLE, 
            lang.getMsg(player, "codex_advanced_title"), 
            lang.getMsgList(player, "codex_advanced_lore")));

        // --- Navigation ---
        inv.setItem(40, GUIManager.createItem(Material.NETHER_STAR, 
            lang.getGui("button_back"), 
            null));
            
        inv.setItem(44, GUIManager.createItem(Material.BARRIER, 
            lang.getGui("button_close"), 
            null));
        
        player.openInventory(inv);
        // plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        
        if (e.getSlot() == 40) { 
            plugin.getGuiManager().openGuardianCodex(player); // Return to Dashboard
            // plugin.effects().playMenuFlip(player);
        } else if (e.getSlot() == 44) { 
            player.closeInventory();
            // plugin.effects().playMenuClose(player);
        } else {
            if (e.getCurrentItem().getType() != Material.GRAY_STAINED_GLASS_PANE) {
                // plugin.effects().playMenuFlip(player);
            }
        }
    }
}
