package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.*; // Imports all GUIs (Petition, Admin, Guild, etc.)
import com.aegisguard.objects.Estate;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class GUIListener implements Listener {

    private final AegisGuard plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey guildActionKey;

    public GUIListener(AegisGuard plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "ag_action");
        this.guildActionKey = new NamespacedKey(plugin, "guild_action");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        Inventory top = event.getView().getTopInventory();
        
        // 1. GLOBAL SAFETY: Stop moving items in ANY Aegis GUI
        // This prevents "Glass Pane theft" and broken layouts
        if (isAegisInventory(top)) {
            event.setCancelled(true);
        }

        if (event.getClickedInventory() == null) return;
        
        // Extra Safety: If the menu is ours, prevent Shift-Clicking from bottom inventory
        if (isAegisInventory(top) && event.getClickedInventory().equals(event.getView().getBottomInventory())) {
             event.setCancelled(true);
             return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        ItemMeta meta = clicked.getItemMeta();
        InventoryHolder holder = top.getHolder();

        // --- 2. HOLDER ROUTING (Sub-Menus) ---
        // This delegates clicks inside sub-menus (like Admin GUI, Flags GUI) to their specific handlers
        
        if (holder instanceof AdminPlotListGUI.EstateListHolder castHolder) {
            plugin.getGuiManager().plotList().handleClick(player, event, castHolder);
            return;
        }
        if (holder instanceof BiomeGUI.BiomeHolder castHolder) {
            plugin.getGuiManager().biomes().handleClick(player, event, castHolder);
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
        if (holder instanceof PlotCosmeticsGUI.CosmeticsHolder castHolder) {
            plugin.getGuiManager().cosmetics().handleClick(player, event, castHolder);
            return;
        }
        if (holder instanceof PlotFlagsGUI.PlotFlagsHolder castHolder) {
            plugin.getGuiManager().flags().handleClick(player, event, castHolder);
            return;
        }
        if (holder instanceof RolesGUI.RolesMenuHolder castHolder) {
            plugin.getGuiManager().roles().handleRolesMenuClick(player, event, castHolder);
            return;
        }
        if (holder instanceof RolesGUI.RoleAddHolder castHolder) {
            plugin.getGuiManager().roles().handleAddTrustedClick(player, event, castHolder);
            return;
        }
        if (holder instanceof RolesGUI.RoleManageHolder castHolder) {
            plugin.getGuiManager().roles().handleManageRoleClick(player, event, castHolder);
            return;
        }
        if (holder instanceof EstateMarketGUI.MarketHolder castHolder) {
             plugin.getGuiManager().market().handleClick(player, event, castHolder);
             return;
        }
        if (holder instanceof EstateAuctionGUI.AuctionHolder castHolder) {
             plugin.getGuiManager().auction().handleClick(player, event, castHolder);
             return;
        }
        if (holder instanceof VisitGUI.VisitHolder castHolder) {
             plugin.getGuiManager().visit().handleClick(player, event, castHolder);
             return;
        }
        if (holder instanceof LevelingGUI.LevelingHolder castHolder) {
             plugin.getGuiManager().leveling().handleClick(player, event, castHolder);
             return;
        }
        if (holder instanceof ZoningGUI.ZoningHolder castHolder) {
             plugin.getGuiManager().zoning().handleClick(player, event, castHolder);
             return;
        }
        if (holder instanceof SettingsGUI.SettingsHolder) {
             plugin.getGuiManager().settings().handleClick(player, event);
             return;
        }
        if (holder instanceof InfoGUI.InfoHolder) {
             plugin.getGuiManager().info().handleClick(player, event);
             return;
        }
        if (holder instanceof GuildGUI.GuildHolder) {
             // Handled via NBT Routing below, but blocking here for safety
             event.setCancelled(true);
        }
        
        // --- 3. NBT ROUTING (Main Menu & Guild Dashboard) ---
        if (meta != null) {
            // Main Menu Actions (PlayerGUI)
            if (meta.getPersistentDataContainer().has(actionKey, PersistentDataType.STRING)) {
                event.setCancelled(true);
                handleMainMenuClick(player, meta);
                return;
            }
            
            // Guild Dashboard Actions (GuildGUI)
            if (meta.getPersistentDataContainer().has(guildActionKey, PersistentDataType.STRING)) {
                event.setCancelled(true);
                String action = meta.getPersistentDataContainer().get(guildActionKey, PersistentDataType.STRING);
                handleGuildClick(player, action);
                return;
            }
        }
    }
    
    /**
     * Checks if the inventory belongs to AegisGuard to enforce global cancellation.
     */
    private boolean isAegisInventory(Inventory inv) {
        if (inv == null) return false;
        InventoryHolder holder = inv.getHolder();
        return holder instanceof PlayerGUI.PlayerMenuHolder ||
               holder instanceof AdminGUI.AdminHolder ||
               holder instanceof SettingsGUI.SettingsHolder ||
               holder instanceof InfoGUI.InfoHolder ||
               holder instanceof VisitGUI.VisitHolder ||
               holder instanceof GuildGUI.GuildHolder ||
               holder instanceof EstateMarketGUI.MarketHolder ||
               holder instanceof EstateAuctionGUI.AuctionHolder ||
               holder instanceof PetitionGUI.PetitionHolder ||
               holder instanceof PetitionAdminGUI.PetitionAdminHolder ||
               holder instanceof LandGrantGUI.LandGrantHolder ||
               holder instanceof PlotCosmeticsGUI.CosmeticsHolder ||
               holder instanceof PlotFlagsGUI.PlotFlagsHolder ||
               holder instanceof RolesGUI.RolesMenuHolder ||
               holder instanceof RolesGUI.RoleAddHolder ||
               holder instanceof RolesGUI.RoleManageHolder ||
               holder instanceof LevelingGUI.LevelingHolder ||
               holder instanceof ZoningGUI.ZoningHolder ||
               holder instanceof BiomeGUI.BiomeHolder ||
               holder instanceof AdminPlotListGUI.EstateListHolder;
    }
    
    /**
     * Routes clicks from the Main Menu (PlayerGUI) to the correct sub-managers.
     */
    private void handleMainMenuClick(Player player, ItemMeta meta) {
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        // Context: Is player standing in their own estate?
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        boolean isOwner = estate != null && (estate.getOwnerId().equals(player.getUniqueId()) || plugin.isAdmin(player));

        switch (action) {
            // --- GENERAL NAVIGATION ---
            case "start_claim":
                player.closeInventory();
                plugin.getSelection().confirmClaim(player);
                break;
            case "get_wand":
                player.closeInventory();
                player.performCommand("ag wand");
                break;
            case "open_info":
                plugin.getGuiManager().info().open(player);
                break;
            case "open_visit":
                plugin.getGuiManager().visit().open(player, 0, false);
                break;
            case "open_guild":
                // Opens "The King's Ledger"
                plugin.getGuiManager().guild().openDashboard(player);
                break;
                
            // --- ECONOMY ---
            case "open_market":
                plugin.getGuiManager().market().open(player, 0);
                break;
            case "open_estates":
                // For now, using PlotList. Future update: MyEstatesGUI
                plugin.getGuiManager().plotList().open(player, 0);
                break;
            case "open_auction":
                plugin.getGuiManager().auction().open(player, 0);
                break;

            // --- ESTATE MANAGEMENT (Context Sensitive) ---
            case "manage_current_estate":
            case "manage_flags":
                if (isOwner) plugin.getGuiManager().flags().open(player, estate);
                else player.sendMessage(plugin.getLanguageManager().getMsg(player, "no_permission"));
                break;

            case "manage_roles":
                if (isOwner) plugin.getGuiManager().roles().open(player);
                else player.sendMessage(plugin.getLanguageManager().getMsg(player, "no_permission"));
                break;

            case "open_leveling":
                if (isOwner) plugin.getGuiManager().leveling().open(player, estate);
                else player.sendMessage(plugin.getLanguageManager().getMsg(player, "no_permission"));
                break;

            case "open_zoning":
                if (isOwner) plugin.getGuiManager().zoning().open(player, estate);
                else player.sendMessage(plugin.getLanguageManager().getMsg(player, "no_permission"));
                break;
            
            case "open_biomes":
                if (isOwner) plugin.getGuiManager().biomes().open(player, estate);
                else player.sendMessage(plugin.getLanguageManager().getMsg(player, "no_permission"));
                break;
                
            case "open_petition": // "Expand" button
                if (isOwner) plugin.getGuiManager().petition().open(player);
                else player.sendMessage(plugin.getLanguageManager().getMsg(player, "no_permission"));
                break;

            case "view_perks":
                if (estate != null) plugin.getGuiManager().openPerksMenu(player, estate);
                else player.sendMessage("§cYou are not in an estate.");
                break;

            // --- UTILS / ADMIN ---
            case "open_settings":
                plugin.getGuiManager().settings().open(player);
                break;
            case "open_admin":
                plugin.getGuiManager().admin().open(player);
                break;
            case "back_to_codex":
                plugin.getGuiManager().openGuardianCodex(player);
                break;
            case "close":
                player.closeInventory();
                break;
        }
        
        GUIManager.playClick(player);
    }

    /**
     * Routes clicks from the Guild Dashboard (King's Ledger).
     */
    private void handleGuildClick(Player player, String action) {
        if (action == null) return;
        
        switch (action) {
            case "guild_bank":
                player.sendMessage("§eGuild Treasury opening... (Coming Soon in v1.3.1)");
                break;
            case "guild_members":
                player.sendMessage("§eGuild Roster opening... (Coming Soon in v1.3.1)");
                break;
            case "guild_upgrade":
                player.sendMessage("§eBastion Upgrades opening... (Coming Soon in v1.3.1)");
                break;
            case "guild_settings":
                player.sendMessage("§eGuild Settings opening... (Coming Soon in v1.3.1)");
                break;
            case "guild_leave":
                player.closeInventory();
                player.performCommand("ag guild leave");
                break;
            case "guild_create":
                player.closeInventory();
                player.sendMessage("§aType /ag guild create <Name> to start your Alliance.");
                break;
            case "guild_join":
                player.closeInventory();
                player.sendMessage("§aType /ag guild join <Name> to accept an invitation.");
                break;
        }
        GUIManager.playClick(player);
    }
}
