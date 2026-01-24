package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * ExpansionRequestAdminGUI (1.2.6 QoL pass)
 *
 * Improvements:
 * - PDC action routing (aegis_action) for prev/next/back + view toggle + request entries.
 * - Request entries store requester UUID in PDC (aegis_requester_id) to avoid index desync.
 * - Async list building (sorting/filtering) + inventory build on main thread.
 * - Click safety: only handles top-inventory clicks; ignores filler/air.
 * - Keeps 1.2.5 structure (45 list slots + footer nav).
 */
public class ExpansionRequestAdminGUI {

    private final AegisGuard plugin;
    private static final int REQS_PER_PAGE = 45; // slots 0..44

    private final NamespacedKey keyAction;
    private final NamespacedKey keyRequesterId;

    public ExpansionRequestAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "aegis_action");
        this.keyRequesterId = new NamespacedKey(plugin, "aegis_requester_id");
    }

    public static class ExpansionAdminHolder implements InventoryHolder {
        private final int page;
        private final boolean showAll;
        private final List<UUID> requesterIds;

        public ExpansionAdminHolder(List<UUID> requesterIds, int page, boolean showAll) {
            this.requesterIds = requesterIds;
            this.page = page;
            this.showAll = showAll;
        }

        public int getPage() { return page; }
        public boolean isShowAll() { return showAll; }
        public List<UUID> getRequesterIds() { return requesterIds; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        boolean defaultShowAll = isInstantOrAutoMode();
        open(player, page, defaultShowAll);
    }

    private void open(Player player, int page, boolean showAll) {
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

        final int requestedPage = page;
        final boolean requestedShowAll = showAll;

        // 1.2.6: sort/filter async, build inventory on main
        plugin.runGlobalAsync(() -> {
            List<ExpansionRequest> requests;
            try {
                requests = new ArrayList<>(manager.getActiveRequests());
                requests.removeIf(r -> r == null);
            } catch (Throwable t) {
                requests = new ArrayList<>();
                plugin.getLogger().warning("[ExpansionRequestAdminGUI] getActiveRequests failed: " + t.getMessage());
            }

            if (!requestedShowAll) {
                requests.removeIf(r -> !r.isPending());
                requests.sort(Comparator.comparingLong(ExpansionRequest::getTimestamp));
            } else {
                requests.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
            }

            List<UUID> ids = new ArrayList<>();
            for (ExpansionRequest r : requests) {
                if (r != null && r.getRequester() != null) ids.add(r.getRequester());
            }

            int maxPages = (int) Math.ceil((double) ids.size() / REQS_PER_PAGE);
            int fixedPage = requestedPage;
            if (fixedPage < 0) fixedPage = 0;
            if (maxPages > 0 && fixedPage >= maxPages) fixedPage = maxPages - 1;
            if (maxPages == 0) fixedPage = 0;

            int safePages = Math.max(1, maxPages);

            final int finalPage = fixedPage;
            final int finalSafePages = safePages;
            final int finalMaxPages = maxPages;

            plugin.runMain(player, () -> buildAndOpen(player, manager, ids, finalPage, finalSafePages, finalMaxPages, requestedShowAll));
        });
    }

    private void buildAndOpen(
            Player player,
            ExpansionRequestManager manager,
            List<UUID> ids,
            int page,
            int safePages,
            int maxPages,
            boolean showAll
    ) {
        String baseTitle = plugin.gui().tr(player, "expansion_admin_title", "&6&lPending Petitions");

        String modeLabel = plugin.gui().tr(
                player,
                showAll ? "expansion_admin_view_all" : "expansion_admin_view_pending",
                showAll ? "&bHistory" : "&eQueue"
        );

        Map<String, String> ph = Map.of(
                "PAGE", String.valueOf(page + 1),
                "PAGES", String.valueOf(safePages),
                "MODE", modeLabel
        );

        String fullTitle = plugin.gui().title(
                player,
                "expansion_admin_title_paged_mode",
                baseTitle + " &7({PAGE}/{PAGES}) &8- {MODE}",
                ph
        );
        fullTitle = ChatColor.translateAlternateColorCodes('&', fullTitle);

        ExpansionAdminHolder holder = new ExpansionAdminHolder(ids, page, showAll);
        Inventory inv = Bukkit.createInventory(holder, 54, fullTitle);

        // Fill all slots
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        if (ids.isEmpty()) {
            ItemStack none = GUIManager.createItem(
                    Material.BARRIER,
                    plugin.gui().tr(
                            player,
                            showAll ? "expansion_none_history_title" : "expansion_none_title",
                            showAll ? "&7No Recent Requests" : "&cNo Pending Requests"
                    ),
                    plugin.gui().trList(
                            player,
                            showAll ? "expansion_none_history_lore" : "expansion_none_lore",
                            showAll
                                    ? List.of("&7There are no recent expansion", "&7requests to display.")
                                    : List.of("&7There are no active expansion", "&7requests awaiting review.")
                    )
            );
            tagAction(none, "expansion_none");
            inv.setItem(22, none);
        } else {
            int startIndex = page * REQS_PER_PAGE;

            for (int slot = 0; slot < REQS_PER_PAGE; slot++) {
                int index = startIndex + slot;
                if (index >= ids.size()) break;

                UUID requesterId = ids.get(index);

                ExpansionRequest req;
                try {
                    req = manager.getRequest(requesterId);
                } catch (Throwable t) {
                    req = null;
                }
                if (req == null) continue;
                if (!showAll && !req.isPending()) continue;

                OfflinePlayer requester = Bukkit.getOfflinePlayer(req.getRequester());
                String name = (requester.getName() != null) ? requester.getName() : "Unknown";

                String statusText = plugin.gui().tr(player, req.getStatusLangKey(), "&7Pending");
                String ageText = formatAge(System.currentTimeMillis() - req.getTimestamp());
                String costStr = plugin.eco().format(req.getCost(), CurrencyType.VAULT);
                String worldName = safe(req.getWorldName(), "Unknown");

                String decidedBy = buildDeciderLabel(player, req);
                String decisionAge = req.isPending() ? "-" : formatAge(System.currentTimeMillis() - req.getDecisionTimestamp());

                Map<String, String> vars = Map.of(
                        "PLAYER", name,
                        "WORLD", worldName,
                        "STATUS", statusText,
                        "AGE", ageText,
                        "CUR", String.valueOf(req.getCurrentRadius()),
                        "REQ", String.valueOf(req.getRequestedRadius()),
                        "COST", costStr,
                        "DECIDER", decidedBy,
                        "DECISION_AGE", decisionAge
                );

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
                                "&7Decided By: &f{DECIDER}",
                                "&7Decision Age: &f{DECISION_AGE}",
                                " ",
                                "&eLeft-click: &7Approve request.",
                                "&cRight-click: &7Deny request."
                        ),
                        vars
                );

                Material icon = materialFor(req);
                ItemStack item = GUIManager.createItem(icon, itemName, lore);

                tagAction(item, "expansion_entry");
                tagRequester(item, requesterId);

                inv.setItem(slot, item);
            }
        }

        // Footer nav (45 / 48 / 49 / 53) - PDC tagged
        if (page > 0) {
            ItemStack prev = GUIManager.createItem(
                    Material.ARROW,
                    plugin.gui().tr(player, "button_prev_page", "&fPrevious Page"),
                    plugin.gui().trList(player, "prev_page_lore", List.of("&7Go to the previous page."))
            );
            tagAction(prev, "prev_page");
            inv.setItem(45, prev);
        }

        ItemStack toggle = GUIManager.createItem(
                Material.COMPASS,
                plugin.gui().tr(player, "expansion_admin_view_toggle", showAll ? "&bView: &fAll" : "&eView: &fPending"),
                plugin.gui().trList(
                        player,
                        "expansion_admin_view_toggle_lore",
                        List.of(
                                "&7Toggle between queue and history.",
                                "&7Useful for auditing auto-approvals.",
                                " ",
                                "&eClick to switch view."
                        )
                )
        );
        tagAction(toggle, "toggle_view");
        inv.setItem(48, toggle);

        ItemStack back = GUIManager.createItem(
                Material.ARROW,
                plugin.gui().tr(player, "button_back_admin", plugin.gui().tr(player, "button_back", "&fBack")),
                plugin.gui().trList(player, "back_lore", List.of("&7Return to the previous menu."))
        );
        tagAction(back, "back_admin");
        inv.setItem(49, back);

        if (page < maxPages - 1) {
            ItemStack next = GUIManager.createItem(
                    Material.ARROW,
                    plugin.gui().tr(player, "button_next_page", "&fNext Page"),
                    plugin.gui().trList(player, "next_page_lore", List.of("&7Go to the next page."))
            );
            tagAction(next, "next_page");
            inv.setItem(53, next);
        }

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ExpansionAdminHolder holder)) return;
        e.setCancelled(true);

        // Only handle top inventory clicks
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        if (!plugin.isAdmin(player)) {
            plugin.effects().playError(player);
            player.closeInventory();
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int page = holder.getPage();
        boolean showAll = holder.isShowAll();

        String action = getAction(clicked);
        if (action != null) {
            switch (action) {
                case "prev_page" -> { open(player, page - 1, showAll); plugin.effects().playMenuFlip(player); return; }
                case "next_page" -> { open(player, page + 1, showAll); plugin.effects().playMenuFlip(player); return; }
                case "toggle_view" -> { open(player, 0, !showAll); plugin.effects().playMenuFlip(player); return; }
                case "back_admin" -> { plugin.gui().admin().open(player); plugin.effects().playMenuFlip(player); return; }
                case "expansion_none" -> { plugin.effects().playError(player); return; }
                case "expansion_entry" -> { /* continue */ }
                default -> { return; }
            }
        }

        ExpansionRequestManager manager = plugin.getExpansionRequestManager();
        if (manager == null) {
            plugin.effects().playError(player);
            return;
        }

        UUID requesterId = getRequester(clicked);
        if (requesterId == null) {
            // Legacy fallback to index if item wasn't tagged for some reason
            int slot = e.getSlot();
            if (slot < 0 || slot >= REQS_PER_PAGE) return;

            int index = (page * REQS_PER_PAGE) + slot;
            if (index < 0 || index >= holder.getRequesterIds().size()) return;

            requesterId = holder.getRequesterIds().get(index);
        }

        ExpansionRequest req = manager.getRequest(requesterId);
        if (req == null) {
            sendSystem(player, "request_expired", "&cThat request has already expired or been handled.");
            open(player, page, showAll);
            return;
        }

        String reqName = safe(Bukkit.getOfflinePlayer(requesterId).getName(), "Unknown");

        // History view: do not allow re-approving or denying handled requests.
        if (!req.isPending()) {
            String decidedBy = buildDeciderLabel(player, req);
            sendSystem(
                    player,
                    "expansion_admin_already_handled",
                    "&eThat request has already been handled. &7(Decided by: " + decidedBy + "&7)"
            );
            plugin.effects().playError(player);
            return;
        }

        if (e.getClick().isLeftClick()) {
            boolean ok = manager.approveRequest(req);
            if (ok) {
                plugin.msg().send(player, "admin_request_approved", Map.of("PLAYER", reqName));
                plugin.effects().playConfirm(player);
            } else {
                sendSystem(player, "expansion_admin_approve_failed", "&cFailed to approve request (overlap or economy error).");
                plugin.effects().playError(player);
            }
        } else if (e.getClick().isRightClick()) {
            manager.denyRequest(req);
            plugin.msg().send(player, "admin_request_denied", Map.of("PLAYER", reqName));
            plugin.effects().playUnclaim(player);
        } else {
            return;
        }

        open(player, page, showAll);
    }

    // --------------------------------
    // PDC helpers
    // --------------------------------

    private void tagAction(ItemStack item, String action) {
        if (item == null || action == null || action.isBlank()) return;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action.trim().toLowerCase(Locale.ROOT));
            item.setItemMeta(meta);
        } catch (Throwable ignored) {}
    }

    private String getAction(ItemStack item) {
        if (item == null) return null;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String v = meta.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
            return v == null ? null : v.trim().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void tagRequester(ItemStack item, UUID requesterId) {
        if (item == null || requesterId == null) return;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            meta.getPersistentDataContainer().set(keyRequesterId, PersistentDataType.STRING, requesterId.toString());
            item.setItemMeta(meta);
        } catch (Throwable ignored) {}
    }

    private UUID getRequester(ItemStack item) {
        if (item == null) return null;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String s = meta.getPersistentDataContainer().get(keyRequesterId, PersistentDataType.STRING);
            if (s == null || s.isBlank()) return null;
            return UUID.fromString(s);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // --------------------------------
    // System message helper
    // --------------------------------

    private void sendSystem(Player p, String key, String fallback) {
        String msg = ChatColor.translateAlternateColorCodes('&', plugin.gui().tr(p, key, fallback));
        p.sendMessage(msg);
    }

    // --------------------------------
    // Audit helpers
    // --------------------------------

    private String buildDeciderLabel(Player viewer, ExpansionRequest req) {
        if (req == null) return plugin.gui().tr(viewer, "expansion_decided_by_unknown", "&7Unknown");
        if (req.isPending()) return plugin.gui().tr(viewer, "expansion_decided_by_none", "&7Awaiting review");

        if (req.getDecisionActorType() == ExpansionRequest.DecisionActorType.AUTO) {
            return plugin.gui().tr(viewer, "expansion_decided_by_auto", "&bAuto");
        }

        UUID actor = req.getDecisionActor();
        if (actor != null) {
            OfflinePlayer p = Bukkit.getOfflinePlayer(actor);
            return (p.getName() != null) ? p.getName() : "Admin";
        }

        if (req.getDecisionActorType() == ExpansionRequest.DecisionActorType.CONSOLE) {
            return plugin.gui().tr(viewer, "expansion_decided_by_console", "&7Console");
        }

        return plugin.gui().tr(viewer, "expansion_decided_by_unknown", "&7Unknown");
    }

    private Material materialFor(ExpansionRequest req) {
        if (req == null) return Material.PAPER;
        if (req.isPending()) return Material.PAPER;
        if (req.isApproved()) return Material.LIME_STAINED_GLASS_PANE;
        return Material.RED_STAINED_GLASS_PANE;
    }

    private boolean isInstantOrAutoMode() {
        String mode = plugin.getConfig().getString(
                "expansions.approval_mode",
                plugin.getConfig().getString("expansions.mode", "QUEUE")
        );
        if (mode != null && mode.equalsIgnoreCase("INSTANT")) return true;

        if (plugin.getConfig().getBoolean("expansions.auto_approve.enabled", false)) return true;
        return plugin.getConfig().getBoolean("expansions.auto_approve", false);
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
