package com.yourname.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.managers.PetitionManager;
import com.yourname.aegisguard.managers.PetitionManager.PetitionRequest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import com.aegisguard.objects.PetitionRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class PetitionAdminGUI {

    private final AegisGuard plugin;
    private final NamespacedKey reqKey;

    public PetitionAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.reqKey = new NamespacedKey(plugin, "req_uuid");
    }

    public static class PetitionAdminHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player admin) {
        LanguageManager lang = plugin.getLanguageManager();
        PetitionManager manager = plugin.getPetitionManager();
        
        // Title: "Expansion Requests" or "Royal Petitions"
        String title = lang.getGui("title_petition_admin"); 
        Inventory inv = Bukkit.createInventory(new PetitionAdminHolder(), 54, title);

        // Fill background
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        Collection<PetitionRequest> requests = manager.getActiveRequests();
        
        if (requests.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER, 
                "§cNo Pending Petitions", 
                List.of("§7All requests have been handled.")
            ));
        } else {
            int slot = 0;
            for (PetitionRequest req : requests) {
                if (slot >= 45) break;

                OfflinePlayer requester = Bukkit.getOfflinePlayer(req.getRequester());
                String name = requester.getName() != null ? requester.getName() : "Unknown";

                List<String> lore = new ArrayList<>();
                lore.add("§7World: §f" + req.getWorld()); // Fixed getter
                lore.add("§7Expansion: §e" + req.getCurrentRadius() + " §7➡ §a" + req.getRequestedRadius());
                lore.add("§7Cost Paid: §6$" + String.format("%.2f", req.getCost()));
                lore.add(" ");
                lore.add("§aLeft-Click to Approve");
                lore.add("§cRight-Click to Deny");

                ItemStack item = GUIManager.createItem(Material.PAPER, "§bPetition: " + name, lore);
                
                // Store Request ID in the item
                ItemMeta meta = item.getItemMeta();
                meta.getPersistentDataContainer().set(reqKey, PersistentDataType.STRING, req.getRequester().toString());
                item.setItemMeta(meta);

                inv.setItem(slot, item);
                slot++;
            }
        }

        // Back Button
        inv.setItem(49, GUIManager.createItem(Material.ARROW, lang.getGui("button_back")));

        admin.openInventory(inv);
    }

    public void handleClick(Player admin, InventoryClickEvent e) {
        e.setCancelled(true);
        ItemStack item = e.getCurrentItem();
        
        if (item == null || item.getItemMeta() == null) return;

        // Handle Back Button
        if (e.getSlot() == 49) {
            plugin.getGuiManager().admin().open(admin);
            return;
        }

        // Handle Request Click
        if (item.getItemMeta().getPersistentDataContainer().has(reqKey, PersistentDataType.STRING)) {
            String uuidStr = item.getItemMeta().getPersistentDataContainer().get(reqKey, PersistentDataType.STRING);
            UUID requesterId = UUID.fromString(uuidStr);
            
            PetitionManager manager = plugin.getPetitionManager();
            PetitionRequest req = manager.getRequest(requesterId);

            if (req == null) {
                admin.sendMessage("§cThis petition has expired or was handled.");
                open(admin); // Refresh
                return;
            }

            if (e.isLeftClick()) {
                // Approve
                if (manager.approveRequest(req, admin)) {
                    admin.sendMessage("§a✔ Petition Approved.");
                    GUIManager.playSuccess(admin);
                } else {
                    admin.sendMessage("§cFailed to approve (Overlap or Error).");
                    // GUIManager.playError(admin);
                }
            } else if (e.isRightClick()) {
                // Deny
                manager.denyRequest(req, admin);
                admin.sendMessage("§c✖ Petition Denied.");
                // GUIManager.playClick(admin);
            }
            open(admin); // Refresh list
        }
    }
}
