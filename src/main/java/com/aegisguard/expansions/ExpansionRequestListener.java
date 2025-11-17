package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer; // --- NEW IMPORT ---
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ExpansionRequestListener
 * ------------------------------------------------------
 * Handles all GUI interactions and events related to
 * Expansion Requests for AegisGuard.
 *
 * Features:
 * - Integrated multilingual message system
 * - Admin approval / denial with broadcast
 * - GUI refresh and persistence support
 * - Multi-world & Vault compatible
 */
public class ExpansionRequestListener implements Listener {

    private final AegisGuard plugin;

    public ExpansionRequestListener(AegisGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
// ... existing code ...

        String title = ChatColor.stripColor(e.getView().getTitle());
        ExpansionRequestManager manager = plugin.getExpansionRequestManager();

        // Match GUI title (using language tone)
// ... existing code ...
        String guiTitle = ChatColor.stripColor(plugin.msg().get(player, "expansion_admin_title"));
        if (!title.equalsIgnoreCase(guiTitle)) return;

        e.setCancelled(true);
// ... existing code ...

        UUID requesterId = manager.getRequesterFromItem(item);
        if (requesterId == null) {
// ... existing code ...
            // ... (rest of your null check) ...
            return;
        }

        // --- IMPROVEMENT ---
        // Get an OfflinePlayer object. This will not be null even if the player is offline.
        OfflinePlayer target = Bukkit.getOfflinePlayer(requesterId);
        ExpansionRequest request = manager.getRequest(requesterId);

        if (request == null) {
// ... existing code ...
            // ... (rest of your null check) ...
            return;
        }

        switch (item.getType()) {
            case EMERALD_BLOCK -> { // Approve
                manager.approveRequest(request);
                notifyPlayers(target, player, true); // Pass OfflinePlayer
                logAction(player, target, true);     // Pass OfflinePlayer
                plugin.sounds().playConfirm(player);
            }

            case REDSTONE_BLOCK -> { // Deny
                manager.denyRequest(request);
                notifyPlayers(target, player, false); // Pass OfflinePlayer
                logAction(player, target, false);     // Pass OfflinePlayer
                plugin.sounds().playError(player);
            }

            default -> plugin.sounds().playClick(player);
        }

        // Refresh GUI
// ... existing code ...
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                plugin.gui().expansionAdmin().open(player), 2L);
    }

    /* ------------------------------------------------------
     * Notifications
     * --- IMPROVED --- to handle offline players
     * ------------------------------------------------------ */
    private void notifyPlayers(OfflinePlayer requester, Player admin, boolean approved) {
        // --- IMPROVEMENT ---
        // Check if the requester is online before trying to send a message
        if (requester.isOnline()) {
            Player requesterPlayer = requester.getPlayer();
            if (requesterPlayer != null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("PLAYER", admin.getName());

                plugin.msg().send(requesterPlayer,
                        approved ? "expansion_approved" : "expansion_denied",
                        placeholders);
            }
        }

        Map<String, String> placeholders = new HashMap<>();
        // --- IMPROVEMENT ---
        // Use .getName() - this works even if the player is offline
        placeholders.put("PLAYER", requester.getName() != null ? requester.getName() : "Unknown");

        plugin.msg().send(admin,
                approved ? "admin_expansion_approved" : "admin_expansion_denied", // (Using different keys for clarity)
                placeholders);

        if (plugin.cfg().broadcastAdminActions()) {
            Map<String, String> broadcastPlaceholders = new HashMap<>();
            broadcastPlaceholders.put("ADMIN", admin.getName());
            broadcastPlaceholders.put("PLAYER", requester.getName() != null ? requester.getName() : "Unknown");

            // --- IMPROVEMENT ---
            // Send a pre-formatted, placeholder-driven message to all online players
            String msgKey = approved ? "expansion_broadcast_approved" : "expansion_broadcast_denied";
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.msg().send(p, msgKey, broadcastPlaceholders);
            }
        }
    }

    /* ------------------------------------------------------
     * Logging
     * --- IMPROVED --- to handle offline players and remove I/O
     * ------------------------------------------------------ */
    private void logAction(Player admin, OfflinePlayer requester, boolean approved) {
        String status = approved ? "APPROVED" : "DENIED";
        // --- IMPROVEMENT ---
        // Use .getName() - this works even if the player is offline
        String requesterName = requester.getName() != null ? requester.getName() : requester.getUniqueId().toString();

        plugin.getLogger().info("[Expansion] " + admin.getName() + " " + status +
                " expansion request for " + requesterName);

        // --- CRITICAL PERFORMANCE FIX ---
        // DO NOT save here. This runs on the main thread and will cause lag.
        // Instead, mark the manager as "dirty" so the auto-saver can handle it.
        // plugin.getExpansionRequestManager().saveAll(); // <-- REMOVED
        plugin.getExpansionRequestManager().setDirty(true); // <-- ADDED
    }
}
