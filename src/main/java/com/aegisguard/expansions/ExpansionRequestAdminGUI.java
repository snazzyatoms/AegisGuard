package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ExpansionRequestAdminGUI
 * - Allows admins to view, approve, or deny land expansion requests.
 */
public class ExpansionRequestAdminGUI {

    private final AegisGuard plugin;

    public ExpansionRequestAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Holder identifying the Admin GUI and mapping slots to Request IDs.
     */
    public static class ExpansionAdminHolder implements InventoryHolder {
        private final Map<Integer, UUID> slotMap = new HashMap<>();

        public void addRequest(int slot, UUID requesterId) {
            slotMap.put(slot, requesterId);
        }

        public UUID getRequesterId(int slot) {
            return slotMap.get(slot);
        }

        @Override
        public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        ExpansionAdminHolder holder = new ExpansionAdminHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, "§8Expansion Requests");

        // Fill background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        Collection<ExpansionRequest> requests = plugin.getExpansionRequestManager().getActiveRequests();
        
        if (requests.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER, "§cNo Pending Requests", 
                List.of("§7There are no requests to review.")));
        } else {
            int slot = 0;
            for (ExpansionRequest req : requests) {
                if (slot >= 45) break;

                OfflinePlayer requester = Bukkit.getOfflinePlayer(req.getRequester());
                String name = requester.getName() != null ? requester.getName() : "Unknown";

                List<String> lore = new ArrayList<>();
                lore.add("§7World: §f" + req.getWorldName());
                lore.add("§7Current Radius: §e" + req.getCurrentRadius());
                lore.add("§7Requested: §a" + req.getRequestedRadius() + " §7(+" + (req.getRequestedRadius() - req.getCurrentRadius()) + ")");
                lore.add("§7Cost Paid: §6" + plugin.eco().format(req.getCost(), com.aegisguard.economy.CurrencyType.VAULT)); // Assuming Vault for expansions
                lore.add(" ");
                lore.add("§a§lLEFT CLICK §7to Approve");
                lore.add("§c§lRIGHT CLICK §7to Deny");

                inv.setItem(slot, GUIManager.createItem(Material.PAPER, "§bRequest: " + name, lore));
                holder.addRequest(slot, req.getRequester());
                slot++;
            }
        }

        // Back Button
        inv.setItem(49, GUIManager.createItem(Material.ARROW, "§fBack", List.of("§7Return to Admin Menu")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ExpansionAdminHolder holder)) return;

        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();

        // Handle Back Button
        if (slot == 49) {
            plugin.gui().admin().open(player);
            return;
        }

        // Handle Request Click
        UUID requesterId = holder.getRequesterId(slot);
        if (requesterId == null) return;

        ExpansionRequestManager manager = plugin.getExpansionRequestManager();
        ExpansionRequest req = manager.getRequest(requesterId);

        if (req == null) {
            player.sendMessage("§cThis request is no longer valid.");
            open(player); // Refresh
            return;
        }

        if (e.getClick().isLeftClick()) {
            // Approve
            if (manager.approveRequest(req)) {
                plugin.msg().send(player, "admin_request_approved", 
                    Map.of("PLAYER", Bukkit.getOfflinePlayer(requesterId).getName()));
                plugin.effects().playConfirm(player);
            } else {
                player.sendMessage("§cFailed to approve request (Overlap or Economy error).");
                plugin.effects().playError(player);
            }
        } else if (e.getClick().isRightClick()) {
            // Deny
            manager.denyRequest(req);
            plugin.msg().send(player, "admin_request_denied", 
                Map.of("PLAYER", Bukkit.getOfflinePlayer(requesterId).getName()));
            plugin.effects().playUnclaim(player);
        }

        open(player); // Refresh GUI to remove the processed item
    }
}
