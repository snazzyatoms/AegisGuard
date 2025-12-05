package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.*;
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
        
        // 1. GLOBAL SAFETY: Prevent item moving in Aegis GUIs
        if (isAegisInventory(top)) {
            event.setCancelled(true);
        }

        if (event.getClickedInventory() == null) return;
        // Prevent clicking in own inventory if top is Aegis GUI (Optional, mostly for safety)
        if (isAegisInventory(top) && event.getClickedInventory().equals(event.getView().getBottomInventory())) {
             event.setCancelled(true);
             return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        ItemMeta meta = clicked.getItemMeta();
        
        InventoryHolder holder = top.getHolder();

        // --- 2. HOLDER ROUTING (Sub-Menus) ---
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
        
        // --- 3. NBT ROUTING (Main Menu & Guilds) ---
        if (meta != null) {
            if (meta.getPersistentDataContainer().has(actionKey, PersistentDataType.STRING)) {
                event.setCancelled(true);
                handleMainMenuClick(player, meta);
                return;
            }
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
               holder instanceof EstateAuctionGUI.AuctionHolder;
    }
    
    private void handleMainMenuClick(Player player, ItemMeta meta) {
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        // Get current estate for context-sensitive buttons
        Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
        boolean isOwner = estate != null && (estate.getOwnerId().equals(player.getUniqueId()) || plugin.isAdmin(player));

        switch (action) {
            // --- GENERAL ---
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
                plugin.getGuiManager().guild().openDashboard(player);
                break;

            // --- ESTATE MANAGEMENT ---
            case "manage_current_estate":
            case "manage_flags":
                if (isOwner) plugin.getGuiManager().flags().open(player, estate);
                else player.sendMessage("§cYou must stand in your estate to manage it.");
                break;
                
            case "manage_roles":
                // RolesGUI handles selection logic internally if not provided
                if (isOwner) plugin.getGuiManager().roles().open(player);
                else player.sendMessage("§cYou must stand in your estate to manage roles.");
                break;

            case "open_leveling":
                if (isOwner) plugin.getGuiManager().leveling().open(player, estate);
                else player.sendMessage("§cYou must stand in your estate to level it up.");
                break;

            case "open_zoning":
                if (isOwner) plugin.getGuiManager().zoning().open(player, estate);
                else player.sendMessage("§cYou must stand in your estate to manage zones.");
                break;
            
            case "open_biomes":
                if (isOwner) plugin.getGuiManager().biomes().open(player, estate);
                else player.sendMessage("§cYou must stand in your estate to change biomes.");
                break;
                
            case "open_petition": // "Expand" button
                if (isOwner) plugin.getGuiManager().petition().open(player);
                else player.sendMessage("§cYou must stand in your estate to expand it.");
                break;

            case "view_perks":
                if (estate != null) plugin.getGuiManager().openPerksMenu(player, estate);
                break;

            // --- ECONOMY ---
            case "open_market":
                plugin.getGuiManager().market().open(player, 0);
                break;
            case "open_estates":
                plugin.getGuiManager().plotList().open(player, 0); // Shows all for now, ideal is "My Plots" filter
                break;
            case "open_auction":
                plugin.getGuiManager().auction().open(player, 0);
                break;

            // --- UTILS ---
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

    private void handleGuildClick(Player player, String action) {
        if (action == null) return;
        switch (action) {
            case "guild_bank":
                player.sendMessage("§eGuild Treasury opening... (Coming Soon)");
                break;
            case "guild_members":
                player.sendMessage("§eGuild Roster opening... (Coming Soon)");
                break;
            case "guild_upgrade":
                player.sendMessage("§eBastion Upgrades opening... (Coming Soon)");
                break;
            case "guild_settings":
                player.sendMessage("§eGuild Settings opening... (Coming Soon)");
                break;
            case "guild_leave":
                player.closeInventory();
                player.performCommand("ag guild leave");
                break;
            case "guild_create":
                player.closeInventory();
                player.sendMessage("§aType /ag guild create <Name> to start.");
                break;
            case "guild_join":
                player.closeInventory();
                player.sendMessage("§aType /ag guild join <Name> to accept invite.");
                break;
        }
        GUIManager.playClick(player);
    }
}
