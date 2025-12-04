package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.managers.LanguageManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class GuiListener implements Listener {

    private final AegisGuard plugin;
    private final NamespacedKey actionKey;      // Main Menu Actions
    private final NamespacedKey guildActionKey; // Guild Menu Actions

    public GuiListener(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "ag_action");
        this.guildActionKey = new NamespacedKey(plugin, "guild_action");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 1. CRITICAL SAFETY CHECKS (The v1.2.1 Lesson)
        if (event.getClickedInventory() == null) return;
        if (event.getCurrentItem() == null) return;
        if (event.getCurrentItem().getType() == Material.AIR) return;
        
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        ItemMeta meta = clicked.getItemMeta();

        if (meta == null) return;

        // 2. CHECK FOR OUR KEYS
        // Does this item have a hidden action tag?
        if (meta.getPersistentDataContainer().has(actionKey, PersistentDataType.STRING)) {
            event.setCancelled(true); // Stop theft
            handleMainMenuClick(player, meta);
        } 
        else if (meta.getPersistentDataContainer().has(guildActionKey, PersistentDataType.STRING)) {
            event.setCancelled(true); // Stop theft
            handleGuildMenuClick(player, meta);
        }
    }

    // ==========================================================
    // 🏠 MAIN MENU LOGIC (Guardian Codex)
    // ==========================================================
    private void handleMainMenuClick(Player player, ItemMeta meta) {
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

        switch (action) {
            case "start_claim":
                player.closeInventory();
                player.performCommand("ag wand"); // Give wand
                // Or open a specific "Claiming Mode" guide
                break;

            case "open_guild":
                // Open the Guild Dashboard
                plugin.getGuildGUI().openDashboard(player);
                break;

            case "open_estates":
                // TODO: Open "My Estates" list
                player.sendMessage("§eOpening Estate List... (Coming Soon)");
                break;

            case "open_settings":
                // TODO: Open Language/Settings Selector
                player.sendMessage("§eOpening Settings... (Coming Soon)");
                break;
                
            case "open_liquidation":
                // Open the "Trash to Cash" chute
                // plugin.getLiquidationManager().openChute(player);
                player.sendMessage("§eOpening Liquidation Chute... (Coming Soon)");
                break;
        }
    }

    // ==========================================================
    // 🏰 GUILD DASHBOARD LOGIC
    // ==========================================================
    private void handleGuildMenuClick(Player player, ItemMeta meta) {
        String action = meta.getPersistentDataContainer().get(guildActionKey, PersistentDataType.STRING);
        if (action == null) return;

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

        switch (action) {
            case "guild_create":
                player.closeInventory();
                player.sendMessage("§aTo create a Guild, type: §f/ag guild create <Name>");
                // Future: Use Chat Prompt here instead
                break;

            case "guild_bank":
                // Open Treasury GUI
                player.sendMessage("§eOpening Treasury Vault... (Coming Soon)");
                break;

            case "guild_members":
                // Open Roster GUI
                player.sendMessage("§eOpening Member Roster... (Coming Soon)");
                break;

            case "guild_upgrade":
                // Open Bastion Tree
                player.sendMessage("§eOpening Bastion Upgrades... (Coming Soon)");
                break;
                
            case "guild_leave":
                player.closeInventory();
                player.performCommand("ag guild leave");
                break;
        }
    }
}
