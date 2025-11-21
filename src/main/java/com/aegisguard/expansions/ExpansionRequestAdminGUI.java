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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ExpansionRequestAdminGUI {

    private final AegisGuard plugin;

    public ExpansionRequestAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ExpansionAdminHolder implements InventoryHolder {
        private final Map<Integer, UUID> slotMap = new HashMap<>();
        public void addRequest(int slot, UUID requesterId) { slotMap.put(slot, requesterId); }
        public UUID getRequesterId(int slot) { return slotMap.get(slot); }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        ExpansionAdminHolder holder = new ExpansionAdminHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, "§8Expansion Requests");

        Collection<ExpansionRequest> requests = plugin.getExpansionRequestManager().getActiveRequests();
        
        if (requests.isEmpty()) {
            inv.setItem(22, GUIManager.icon(Material.BARRIER, "§cNo Pending Requests", List.of("§7There are no requests to review.")));
        } else {
            int slot = 0;
            for (ExpansionRequest req : requests) {
                if (slot >= 45) break;
                OfflinePlayer requester = Bukkit.getOfflinePlayer(req.getRequester());
                String name = requester.getName() != null ? requester.getName() : "Unknown";

                List<String> lore = new ArrayList<>();
                lore.add("§7World: §f" + req.getWorldName());
                lore.add("§7Radius: §e" + req.getCurrentRadius() + " §7-> §a" + req.getRequestedRadius());
                lore.add("§7Cost: §6$" + String.format("%.2f", req.getCost()));
                lore.add(" ");
                lore.add("§a§lLEFT CLICK §7to Approve");
                lore.add("§c§lRIGHT CLICK §7to Deny");

                inv.setItem(slot, GUIManager.icon(Material.PAPER, "§bRequest: " + name, lore));
                holder.addRequest(slot, req.getRequester());
                slot++;
            }
        }
        inv.setItem(49, GUIManager.icon(Material.ARROW, "§fBack", List.of("§7Return to Admin Menu")));
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ExpansionAdminHolder holder)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();
        if (slot == 49) {
            plugin.gui().admin().open(player);
            plugin.effects().playMenuFlip(player);
            return;
        }

        UUID requesterId = holder.getRequesterId(slot);
        if (requesterId == null) return;

        ExpansionRequestManager manager = plugin.getExpansionRequestManager();
        ExpansionRequest req = manager.getRequest(requesterId);

        if (req == null) {
            plugin.msg().send(player, "request_expired");
            open(player);
            return;
        }

        if (e.getClick().isLeftClick()) {
            if (manager.approveRequest(req)) {
                plugin.msg().send(player, "admin_request_approved", Map.of("PLAYER", Bukkit.getOfflinePlayer(requesterId).getName()));
                plugin.effects().playConfirm(player);
            } else {
                plugin.msg().send(player, "admin_request_fail");
                plugin.effects().playError(player);
            }
        } else if (e.getClick().isRightClick()) {
            manager.denyRequest(req);
            plugin.msg().send(player, "admin_request_denied", Map.of("PLAYER", Bukkit.getOfflinePlayer(requesterId).getName()));
            plugin.effects().playUnclaim(player);
        }
        open(player);
    }
}
