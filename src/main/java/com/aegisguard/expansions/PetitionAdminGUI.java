package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.managers.PetitionManager;
import com.aegisguard.objects.PetitionRequest; // FIXED IMPORT
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
        
        String title = lang.getGui("title_petition_admin"); 
        if (title.contains("Missing")) title = "§8Expansion Requests";

        Inventory inv = Bukkit.createInventory(new PetitionAdminHolder(), 54, title);

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
                lore.add("§7World: §f" + req.getWorldName());
                lore.add("§7Expansion: §e" + req.getCurrentRadius() + " §7➡ §a" + req.getRequestedRadius());
                lore.add("§7Cost Paid: §6$" + String.format("%.2f", req.getCost()));
                lore.add(" ");
                lore.add("§aLeft-Click to Approve");
                lore.add("§cRight-Click to Deny");

                ItemStack item = GUIManager.createItem(Material.PAPER, "§bPetition: " + name, lore);
                
                ItemMeta meta = item.getItemMeta();
                meta.getPersistentDataContainer().set(reqKey, PersistentDataType.STRING, req.getRequester().toString());
                item.setItemMeta(meta);

                inv.setItem(slot, item);
                slot++;
            }
        }

        inv.setItem(49, GUIManager.createItem(Material.ARROW, lang.getGui("button_back")));
        admin.openInventory(inv);
    }

    public void handleClick(Player admin, InventoryClickEvent e) {
        e.setCancelled(true);
        ItemStack item = e.getCurrentItem();
        
        if (item == null || item.getItemMeta() == null) return;

        if (e.getSlot() == 49) {
            plugin.getGuiManager().admin().open(admin);
            return;
        }

        if (item.getItemMeta().getPersistentDataContainer().has(reqKey, PersistentDataType.STRING)) {
            String uuidStr = item.getItemMeta().getPersistentDataContainer().get(reqKey, PersistentDataType.STRING);
            UUID requesterId = UUID.fromString(uuidStr);
            
            PetitionManager manager = plugin.getPetitionManager();
            PetitionRequest req = manager.getRequest(requesterId);

            if (req == null) {
                admin.sendMessage("§cThis petition has expired or was handled.");
                open(admin);
                return;
            }

            if (e.isLeftClick()) {
                if (manager.approveRequest(req, admin)) {
                    admin.sendMessage("§a✔ Petition Approved.");
                    GUIManager.playSuccess(admin);
                } else {
                    admin.sendMessage("§cFailed to approve (Overlap or Error).");
                }
            } else if (e.isRightClick()) {
                manager.denyRequest(req, admin);
                admin.sendMessage("§c✖ Petition Denied.");
            }
            open(admin);
        }
    }
}
