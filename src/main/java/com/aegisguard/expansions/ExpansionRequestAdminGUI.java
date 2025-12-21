package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ExpansionRequestAdminGUI
 * - Allows admins to view, approve, or deny land expansion requests.
 * - Fully localized with per-player language styles.
 *
 * Notes:
 * - Cost is charged on approval in ExpansionRequestManager (not at submit),
 *   so the GUI labels it as "Cost Due".
 * - Pagination prevents silent truncation beyond 45 requests.
 */
public class ExpansionRequestAdminGUI {

    private final AegisGuard plugin;
    private static final int REQS_PER_PAGE = 45; // slots 0..44 (top 5 rows)

    public ExpansionRequestAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ExpansionAdminHolder implements InventoryHolder {
        private final int page;
        private final List<UUID> requesterIds;

        public ExpansionAdminHolder(List<UUID> requesterIds, int page) {
            this.requesterIds = requesterIds;
            this.page = page;
        }

        public int getPage() { return page; }
        public List<UUID> getRequesterIds() { return requesterIds; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        // ✅ Hard admin gate (prevents any accidental access path)
        if (!plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            plugin.effects().playError(player);
            return;
        }

        ExpansionRequestManager manager = plugin.getExpansionRequestManager();
        if (manager == null) {
            player.sendMessage("§c[AegisGuard] ExpansionRequestManager not loaded.");
            plugin.effects().playError(player);
            return;
        }

        // Collect + filter pending only (defensive)
        List<ExpansionRequest> requests = new ArrayList<>(manager.getActiveRequests());
        requests.removeIf(r -> r == null || !r.isPending());

        // Oldest first feels best for queues
        requests.sort(Comparator.comparingLong(ExpansionRequest::getTimestamp));

        // Extract IDs for holder
        List<UUID> ids = new ArrayList<>();
        for (ExpansionRequest r : requests) ids.add(r.getRequester());

        int maxPages = (int) Math.ceil((double) ids.size() / REQS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;
        if (maxPages == 0) page = 0;

        int safePages = Math.max(1, maxPages);

        // ✅ Title:
        // 1) Localized base title: expansion_admin_title
        // 2) Optional paged variant: expansion_admin_title_paged (recommended)
        //    Fallback stays localized by embedding the baseTitle.
        String baseTitle = plugin.gui().tr(player, "expansion_admin_title", "&6&lPending Requests");
        Map<String, String> ph = Map.of(
                "PAGE", String.valueOf(page + 1),
                "PAGES", String.valueOf(safePages)
        );

        String fullTitle = plugin.gui().title(
                player,
                "expansion_admin_title_paged",
                baseTitle + " &7({PAGE}/{PAGES})",
                ph
        );

        ExpansionAdminHolder holder = new ExpansionAdminHolder(ids, page);
        Inventory inv = Bukkit.createInventory(holder, 54, fullTitle);

        // ✅ Fill all slots for a clean look
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        // Empty state
        if (ids.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(
                    Material.BARRIER,
                    plugin.gui().tr(player, "expansion_none_title", "&cNo Pending Requests"),
                    plugin.gui().trList(player, "expansion_none_lore", List.of(
                            "&7There are no active expansion",
                            "&7requests awaiting review."
                    ))
            ));
        } else {
            int startIndex = page * REQS_PER_PAGE;

            for (int slot = 0; slot < REQS_PER_PAGE; slot++) {
                int index = startIndex + slot;
                if (index >= ids.size()) break;

                UUID requesterId = ids.get(index);
                ExpansionRequest req = manager.getRequest(requesterId);
                if (req == null || !req.isPending()) continue;

                OfflinePlayer requester = Bukkit.getOfflinePlayer(req.getRequester());
                String name = (requester.getName() != null) ? requester.getName() : "Unknown";

                String statusText = plugin.gui().tr(player, req.getStatusLangKey(), "&7Pending");
                String ageText = formatAge(System.currentTimeMillis() - req.getTimestamp());
                String costStr = plugin.eco().format(req.getCost(), CurrencyType.VAULT);

                String worldName = safe(req.getWorldName(), "Unknown");

                Map<String, String> vars = Map.of(
                        "PLAYER", name,
                        "WORLD", worldName,
                        "STATUS", statusText,
                        "AGE", ageText,
                        "CUR", String.valueOf(req.getCurrentRadius()),
                        "REQ", String.valueOf(req.getRequestedRadius()),
                        "COST", costStr
                );

                // ✅ Uses placeholder-aware GUI gateway (Codex or fallback gets vars applied)
                String itemName = plugin.gui().tr(
                        player,
                        "expansion_request_item_name",
                        "&bRequest: &f{PLAYER}",
                        vars
                );

                List<String> lore = plugin.gui().trList(
                        player,
                        "expansion_request_item_lore",
                        List.of(
                                "&7World: &f{WORLD}",
                                "&7Status: {STATUS}",
                                "&7Age: &f{AGE}",
                                "&7Radius: &e{CUR} &7→ &a{REQ}",
                                "&7Cost Due: &6{COST}",
                                " ",
                                "&eLeft-click: &7Approve request.",
                                "&cRight-click: &7Deny request."
                        ),
                        vars
                );

                inv.setItem(slot, GUIManager.createItem(Material.PAPER, itemName, lore));
            }
        }

        // Navigation: Prev / Back / Next
        if (page > 0) {
            inv.setItem(45, GUIManager.createItem(
                    Material.ARROW,
                    plugin.gui().tr(player, "button_prev_page", "&fPrevious Page"),
                    plugin.gui().trList(player, "prev_page_lore", List.of("&7Go to the previous page."))
            ));
        }

        inv.setItem(49, GUIManager.createItem(
                Material.ARROW,
                plugin.gui().tr(player, "button_back", "&fBack"),
                plugin.gui().trList(player, "back_lore", List.of("&7Return to the previous menu."))
        ));

        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(
                    Material.ARROW,
                    plugin.gui().tr(player, "button_next_page", "&fNext Page"),
                    plugin.gui().trList(player, "next_page_lore", List.of("&7Go to the next page."))
            ));
        }

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ExpansionAdminHolder holder)) return;
        e.setCancelled(true);

        // ✅ Hard admin gate in click handler too
        if (!plugin.isAdmin(player)) {
            plugin.effects().playError(player);
            player.closeInventory();
            return;
        }

        if (e.getCurrentItem() == null) return;

        // Ignore clicks from the player's own inventory
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        int slot = e.getSlot();
        int page = holder.getPage();

        // Nav
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) {
            open(player, page - 1);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) {
            open(player, page + 1);
            plugin.effects().playMenuFlip(player);
            return;
        }

        // Back to Admin menu
        if (slot == 49) {
            plugin.gui().admin().open(player);
            plugin.effects().playMenuFlip(player);
            return;
        }

        // Listing click (only 0..44)
        if (slot < 0 || slot >= REQS_PER_PAGE) return;

        int index = (page * REQS_PER_PAGE) + slot;
        if (index < 0 || index >= holder.getRequesterIds().size()) return;

        UUID requesterId = holder.getRequesterIds().get(index);

        ExpansionRequestManager manager = plugin.getExpansionRequestManager();
        ExpansionRequest req = manager.getRequest(requesterId);

        if (req == null || !req.isPending()) {
            sendSystem(player, "request_expired", "&cThat request has already expired or been handled.");
            open(player, page);
            return;
        }

        String reqName = safe(Bukkit.getOfflinePlayer(requesterId).getName(), "Unknown");

        if (e.getClick().isLeftClick()) {
            // Approve
            if (manager.approveRequest(req)) {
                plugin.msg().send(player, "admin_request_approved", Map.of("PLAYER", reqName));
                plugin.effects().playConfirm(player);
            } else {
                sendSystem(player, "expansion_admin_approve_failed", "&cFailed to approve request (overlap or economy error).");
                plugin.effects().playError(player);
            }
        } else if (e.getClick().isRightClick()) {
            // Deny
            manager.denyRequest(req);
            plugin.msg().send(player, "admin_request_denied", Map.of("PLAYER", reqName));
            plugin.effects().playUnclaim(player);
        }

        open(player, page); // Refresh GUI
    }

    // --------------------------------
    // System message helper
    // --------------------------------

    private void sendSystem(Player p, String key, String fallback) {
        // plugin.gui().tr already returns colorized output
        String msg = plugin.gui().tr(p, key, fallback);
        p.sendMessage(msg);
    }

    // --------------------------------
    // Small utilities
    // --------------------------------

    private String safe(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    private String formatAge(long ms) {
        if (ms < 0) ms = 0;

        long seconds = ms / 1000L;
        long mins = seconds / 60L;
        long hrs = mins / 60L;
        long days = hrs / 24L;

        long rSec = seconds % 60L;
        long rMin = mins % 60L;
        long rHr = hrs % 24L;

        if (days > 0) return days + "d " + rHr + "h";
        if (hrs > 0) return hrs + "h " + rMin + "m";
        if (mins > 0) return mins + "m " + rSec + "s";
        return seconds + "s";
    }
}
