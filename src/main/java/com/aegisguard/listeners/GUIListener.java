package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.AdminGUI;
import com.aegisguard.gui.AdminPlotListGUI;
import com.aegisguard.gui.BiomeGUI;
import com.aegisguard.gui.LandGrantGUI;
import com.aegisguard.gui.PetitionAdminGUI;
import com.aegisguard.gui.PetitionGUI;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class GUIListener implements Listener {

    private final AegisGuard plugin;
    
    // Action Keys
    private final NamespacedKey actionKey;
    private final NamespacedKey guildActionKey;
    private final NamespacedKey adminActionKey;

    public GUIListener(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "ag_action");
        this.guildActionKey = new NamespacedKey(plugin, "guild_action");
        this.adminActionKey = new NamespacedKey(plugin, "admin_action");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // 1. Safety Checks
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        // 2. Route by Holder Type (If using Holders)
        // OR Route by NBT Tag (The v1.3.0 way)
        
        // --- HOLDER ROUTING (For Complex Menus) ---
        if (holder instanceof AdminPlotListGUI.EstateListHolder castHolder) {
            new AdminPlotListGUI(plugin).handleClick(player, event, castHolder);
            return;
        }
        if (holder instanceof BiomeGUI.BiomeHolder castHolder) {
            new BiomeGUI(plugin).handleClick(player, event, castHolder);
            return;
        }
        if (holder instanceof PetitionGUI.PetitionHolder) {
            plugin.getGuiManager().petition().handleClick(player, event);
            return;
        }
        if (holder instanceof PetitionAdminGUI.PetitionAdminHolder) {
            plugin.getGuiManager().petitionAdmin().handleClick(player, event);
            return;
        }
        if (holder instanceof AdminGUI.AdminHolder) {
            plugin.getGuiManager().admin().handleClick(player, event);
            return;
        }
        if (holder instanceof LandGrantGUI.LandGrantHolder) {
            plugin.getGuiManager().landGrant().handleClick(player, event);
            return;
        }

        // --- NBT TAG ROUTING (For Simple Menus / Main Menu) ---
        if (meta.getPersistentDataContainer().has(actionKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            handleMainMenuClick(player, meta);
            return;
        }
        
        if (meta.getPersistentDataContainer().has(guildActionKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            // Delegate to GuildGUI logic (if not using Holder)
            // For v1.3.0 GuildGUI, we usually use a holder or a handler method.
            // Assuming GuildGUI handles its own clicks via Holder or NBT:
            // plugin.getGuiManager().guild().handleClick(player, event); 
        }
    }
    
    // ==========================================================
    // 🏠 MAIN MENU LOGIC
    // ==========================================================
    private void handleMainMenuClick(Player player, ItemMeta meta) {
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "start_claim":
                player.closeInventory();
                player.performCommand("ag wand");
                break;

            case "open_guild":
                plugin.getGuiManager().guild().openDashboard(player);
                break;

            case "open_estates":
                // plugin.getGuiManager().openEstateList(player);
                player.sendMessage("§eOpening Estate List... (Coming Soon)");
                break;

            case "open_settings":
                // plugin.getGuiManager().openSettings(player);
                player.sendMessage("§eOpening Settings... (Coming Soon)");
                break;
                
            case "view_perks":
                com.yourname.aegisguard.objects.Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
                if (estate != null) {
                    plugin.getGuiManager().openPerksMenu(player, estate);
                }
                break;
                
            case "back_to_codex":
                plugin.getGuiManager().openGuardianCodex(player);
                break;
        }
    }
}
