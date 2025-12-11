package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.economy.CurrencyType;
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
 * - Fully localized with per-player language styles.
 */
public class ExpansionRequestAdminGUI {

    private final AegisGuard plugin;

    public ExpansionRequestAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

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

        String title = GUIManager.safeText(
                plugin.msg().get(player, "expansion_admin_title"),
                "§8Expansion Requests"
        );
        Inventory inv = Bukkit.createInventory(holder, 54, title);

        // Fill background footer
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        Collection<ExpansionRequest> requests = plugin.getExpansionRequestManager().getActiveRequests();

        if (requests.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(
                    Material.BARRIER,
                    line(player, "expansion_none_title", "§cNo Pending Requests"),
                    lines(player, "expansion_none_lore", List.of(
                            "§7There are no active expansion",
                            "§7requests awaiting review."
                    ))
            ));
        } else {
            int slot = 0;
            for (ExpansionRequest req : requests) {
                if (slot >= 45) break;

                OfflinePlayer requester = Bukkit.getOfflinePlayer(req.getRequester());
                String name = requester.getName() != null ? requester.getName() : "Unknown";

                List<String> lore = new ArrayList<>();
                lore.add("§7World: §f" + req.getWorldName());
                lore.add("§7Radius: §e" + req.getCurrentRadius() + " §7→ §a" + req.getRequestedRadius());
                lore.add("§7Cost Paid: §6" + plugin.eco().format(req.getCost(), CurrencyType.VAULT));
                lore.add(" ");

                // Localized admin action hints (with sane default)
                lore.addAll(lines(player, "expansion_admin_actions", List.of(
                        "§eLeft-click: §7Approve request.",
                        "§cRight-click: §7Deny request & handle refund."
                )));

                inv.setItem(slot, GUIManager.createItem(
                        Material.PAPER,
                        "§bRequest: " + name,
                        lore
                ));
                holder.addRequest(slot, req.getRequester());
                slot++;
            }
        }

        // Back Button
        inv.setItem(49, GUIManager.createItem(
                Material.ARROW,
                line(player, "button_back", "§fBack"),
                lines(player, "back_lore", List.of("§7Return to the previous menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ExpansionAdminHolder holder)) return;

        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();

        // Back to Admin menu
        if (slot == 49) {
            plugin.gui().admin().open(player);
            plugin.effects().playMenuFlip(player);
            return;
        }

        // Handle Request Click
        UUID requesterId = holder.getRequesterId(slot);
        if (requesterId == null) return;

        ExpansionRequestManager manager = plugin.getExpansionRequestManager();
        ExpansionRequest req = manager.getRequest(requesterId);

        if (req == null) {
            player.sendMessage(line(player, "request_expired", "§cThat request has already expired or been handled."));
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
                player.sendMessage("§cFailed to approve request (overlap or economy error).");
                plugin.effects().playError(player);
            }
        } else if (e.getClick().isRightClick()) {
            // Deny
            manager.denyRequest(req);
            plugin.msg().send(player, "admin_request_denied",
                    Map.of("PLAYER", Bukkit.getOfflinePlayer(requesterId).getName()));
            plugin.effects().playUnclaim(player);
        }

        open(player); // Refresh GUI
    }

    // --------------------------------
    // LANGUAGE HELPERS (New Engine)
    // --------------------------------

    private String line(Player p, String key, String fallback) {
        String s = plugin.msg().get(p, key);
        return (s == null || s.isEmpty()) ? fallback : s;
    }

    private List<String> lines(Player p, String key, List<String> fallback) {
        List<String> list = plugin.msg().getList(p, key);
        return (list == null || list.isEmpty()) ? fallback : list;
    }
}
