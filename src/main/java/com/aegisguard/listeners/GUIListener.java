package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.*; // Imports PetitionGUI, PetitionAdminGUI, etc.
import com.aegisguard.objects.Estate;

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
        if (event.getClickedInventory() == null) return;
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();

        // --- HOLDER ROUTING ---
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
        if (holder instanceof EstateMarketGUI.MarketHolder castHolder) {
             plugin.getGuiManager().market().handleClick(player, event, castHolder);
             return;
        }
        if (holder instanceof EstateAuctionGUI.AuctionHolder castHolder) {
             plugin.getGuiManager().auction().handleClick(player, event, castHolder);
             return;
        }
        
        // --- NBT ROUTING ---
        if (meta.getPersistentDataContainer().has(actionKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            handleMainMenuClick(player, meta);
        }
    }
    
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
                player.sendMessage("§eOpening Estate List...");
                plugin.getGuiManager().plotList().open(player, 0);
                break;
            case "open_settings":
                plugin.getGuiManager().settings().open(player);
                break;
            case "view_perks":
                Estate estate = plugin.getEstateManager().getEstateAt(player.getLocation());
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
