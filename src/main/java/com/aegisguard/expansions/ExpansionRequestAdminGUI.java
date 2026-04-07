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
 * - Queue view shows live pending requests; history view shows persisted audit decisions.
 * - Async list building (sorting/filtering) + inventory build on main thread.
 * - Click safety: only handles top-inventory clicks; ignores filler/air.
 * - Keeps 1.2.5 structure (45 list slots + footer nav).
 */
public class ExpansionRequestAdminGUI {

    private final AegisGuard plugin;
    private static final int REQS_PER_PAGE = 45; // slots 0..44

    private final NamespacedKey keyAction;

    public ExpansionRequestAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "aegis_action");
    }

    public static class ExpansionAdminHolder implements InventoryHolder {
        private final List<DisplayEntry> entries;
        private final int page;
        private final boolean showAll;

        public ExpansionAdminHolder(List<DisplayEntry> entries, int page, boolean showAll) {
            this.entries = entries;
            this.page = page;
            this.showAll = showAll;
        }

        public List<DisplayEntry> getEntries() { return entries; }
        public int getPage() { return page; }
        public boolean isShowAll() { return showAll; }

        @Override
        public Inventory getInventory() { return null; }
    }

    private static final class DisplayEntry {
        private final ExpansionRequest request;
        private final ExpansionRequestManager.DecisionRecord decision;

        private DisplayEntry(ExpansionRequest request, ExpansionRequestManager.DecisionRecord decision) {
            this.request = request;
            this.decision = decision;
        }

        static DisplayEntry pending(ExpansionRequest request) {
            return new DisplayEntry(request, null);
        }

        static DisplayEntry history(ExpansionRequestManager.DecisionRecord decision) {
            return new DisplayEntry(null, decision);
        }

        boolean isPendingEntry() { return request != null; }
        boolean isHistoryEntry() { return decision != null; }
        ExpansionRequest getRequest() { return request; }
        ExpansionRequestManager.DecisionRecord getDecision() { return decision; }
        UUID getRequesterId() {
            if (request != null) return request.getRequester();
            return decision == null ? null : decision.getRequester();
        }
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
            List<DisplayEntry> entries = new ArrayList<>();

            if (!requestedShowAll) {
                try {
                    List<ExpansionRequest> requests = new ArrayList<>(manager.getActiveRequests());
                    requests.removeIf(r -> r == null || !r.isPending());
                    requests.sort(Comparator.comparingLong(ExpansionRequest::getTimestamp));
                    for (ExpansionRequest request : requests) {
                        entries.add(DisplayEntry.pending(request));
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("[ExpansionRequestAdminGUI] getActiveRequests failed: " + t.getMessage());
                }
            } else {
                try {
                    for (ExpansionRequestManager.DecisionRecord record : manager.getRecentDecisions()) {
                        if (record != null) entries.add(DisplayEntry.history(record));
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("[ExpansionRequestAdminGUI] getRecentDecisions failed: " + t.getMessage());
                }
            }

            int maxPages = (int) Math.ceil((double) entries.size() / REQS_PER_PAGE);
            int fixedPage = requestedPage;
            if (fixedPage < 0) fixedPage = 0;
            if (maxPages > 0 && fixedPage >= maxPages) fixedPage = maxPages - 1;
            if (maxPages == 0) fixedPage = 0;

            int safePages = Math.max(1, maxPages);

            final int finalPage = fixedPage;
            final int finalSafePages = safePages;
            final int finalMaxPages = maxPages;

            plugin.runMain(player, () -> buildAndOpen(player, manager, entries, finalPage, finalSafePages, finalMaxPages, requestedShowAll));
        });
    }

    private void buildAndOpen(
            Player player,
            ExpansionRequestManager manager,
            List<DisplayEntry> entries,
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

        ExpansionAdminHolder holder = new ExpansionAdminHolder(entries, page, showAll);
        Inventory inv = Bukkit.createInventory(holder, 54, fullTitle);

        // Fill all slots
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        if (entries.isEmpty()) {
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
                if (index >= entries.size()) break;

                DisplayEntry entry = entries.get(index);
                if (entry == null) continue;

                ExpansionRequest req = null;
                ExpansionRequestManager.DecisionRecord decision = null;
                UUID requesterId = entry.getRequesterId();

                if (entry.isPendingEntry()) {
                    try {
                        req = manager.getRequest(requesterId);
                    } catch (Throwable t) {
                        req = null;
                    }
                    if (req == null || !req.isPending()) continue;
                } else {
                    decision = entry.getDecision();
                    if (decision == null) continue;
                }

                OfflinePlayer requester = requesterId == null ? null : Bukkit.getOfflinePlayer(requesterId);
                String name = (requester != null && requester.getName() != null) ? requester.getName() : "Unknown";

                String statusText = entry.isPendingEntry()
                        ? plugin.gui().tr(player, req.getStatusLangKey(), "&7Pending")
                        : statusText(player, decision.getStatus());
                String ageText = formatAge(System.currentTimeMillis() - (entry.isPendingEntry() ? req.getTimestamp() : decision.getTimestamp()));
                double cost = entry.isPendingEntry() ? req.getCost() : decision.getCost();
                String costStr = plugin.eco().format(cost, CurrencyType.VAULT);
                String worldName = safe(entry.isPendingEntry() ? req.getWorldName() : decision.getWorldName(), "Unknown");

                String decidedBy = entry.isPendingEntry()
                        ? buildDeciderLabel(player, req)
                        : buildDeciderLabel(player, decision);
                long decisionTimestamp = entry.isPendingEntry() ? req.getDecisionTimestamp() : decision.getDecisionTimestamp();
                String decisionAge = decisionTimestamp > 0L ? formatAge(System.currentTimeMillis() - decisionTimestamp) : "-";
                String note = buildAuditNoteText(player, entry);

                Map<String, String> vars = Map.of(
                        "PLAYER", name,
                        "WORLD", worldName,
                        "STATUS", statusText,
                        "AGE", ageText,
                        "CUR", String.valueOf(entry.isPendingEntry() ? req.getCurrentRadius() : decision.getCurrentRadius()),
                        "REQ", String.valueOf(entry.isPendingEntry() ? req.getRequestedRadius() : decision.getRequestedRadius()),
                        "COST", costStr,
                        "DECIDER", decidedBy,
                        "DECISION_AGE", decisionAge,
                        "NOTE", note
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
                                "&7Audit Note: &f{NOTE}",
                                " ",
                                "&eLeft-click: &7Approve request.",
                                "&cRight-click: &7Deny request."
                        ),
                        vars
                );

                if (entry.isHistoryEntry()) {
                    lore = new ArrayList<>(lore);
                    lore.remove(lore.size() - 1);
                    lore.remove(lore.size() - 1);
                    lore.add(" ");
                    lore.add(plugin.gui().tr(player, "expansion_history_read_only", "&7Read-only history entry. Review notes and snapshots if needed."));
                }

                Material icon = materialFor(entry);
                ItemStack item = GUIManager.createItem(icon, itemName, lore);

                tagAction(item, "expansion_entry");

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

        ItemStack close = GUIManager.createItem(
                Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&c✖ Close"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))
        );
        tagAction(close, "close_menu");
        inv.setItem(50, close);

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
                case "close_menu" -> { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
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

        int slot = e.getSlot();
        if (slot < 0 || slot >= REQS_PER_PAGE) return;

        int index = (page * REQS_PER_PAGE) + slot;
        if (index < 0 || index >= holder.getEntries().size()) return;

        DisplayEntry entry = holder.getEntries().get(index);
        if (entry == null) return;

        if (entry.isHistoryEntry()) {
            String note = buildAuditNoteText(player, entry);
            sendSystem(
                    player,
                    "expansion_history_read_only",
                    "&7This is a review-only history entry. Use snapshots to revert if needed."
            );
            if (!note.isBlank()) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.gui().tr(player, "expansion_request_note_line", "&7Audit Note: &f{NOTE}", Map.of("NOTE", note))));
            }
            plugin.effects().playError(player);
            return;
        }

        UUID requesterId = entry.getRequesterId();
        if (requesterId == null) return;

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
            boolean ok = manager.approveRequest(req, player.getUniqueId());
            if (ok) {
                plugin.msg().send(player, "admin_request_approved", Map.of("PLAYER", reqName));
                plugin.effects().playConfirm(player);
            } else {
                sendSystem(player, "expansion_admin_approve_failed", "&cFailed to approve request (overlap or economy error).");
                plugin.effects().playError(player);
            }
        } else if (e.getClick().isRightClick()) {
            manager.denyRequest(req, player.getUniqueId(), "Denied by staff review");
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

        if (req.getDecisionActorType() == ExpansionRequest.DecisionActorType.SYSTEM) {
            return plugin.gui().tr(viewer, "expansion_decided_by_system", "&7System");
        }

        return plugin.gui().tr(viewer, "expansion_decided_by_unknown", "&7Unknown");
    }

    private String buildDeciderLabel(Player viewer, ExpansionRequestManager.DecisionRecord record) {
        if (record == null) return plugin.gui().tr(viewer, "expansion_decided_by_unknown", "&7Unknown");
        return switch (record.getActorType()) {
            case AUTO -> plugin.gui().tr(viewer, "expansion_decided_by_auto", "&bAuto");
            case ADMIN -> {
                UUID actor = record.getActor();
                if (actor != null) {
                    OfflinePlayer p = Bukkit.getOfflinePlayer(actor);
                    yield (p.getName() != null) ? p.getName() : plugin.gui().tr(viewer, "expansion_decided_by_admin", "&eAdmin");
                }
                yield plugin.gui().tr(viewer, "expansion_decided_by_admin", "&eAdmin");
            }
            case SYSTEM -> plugin.gui().tr(viewer, "expansion_decided_by_system", "&7System");
            case UNKNOWN -> plugin.gui().tr(viewer, "expansion_decided_by_unknown", "&7Unknown");
        };
    }

    private String statusText(Player viewer, ExpansionRequest.Status status) {
        if (status == null) return plugin.gui().tr(viewer, "expansion_status_pending", "&ePending");
        return switch (status) {
            case APPROVED -> plugin.gui().tr(viewer, "expansion_status_approved", "&aApproved");
            case DENIED -> plugin.gui().tr(viewer, "expansion_status_denied", "&cDenied");
            case PENDING -> plugin.gui().tr(viewer, "expansion_status_pending", "&ePending");
        };
    }

    private String buildAuditNoteText(Player viewer, DisplayEntry entry) {
        if (entry == null) return "";
        if (entry.isPendingEntry()) {
            return plugin.gui().tr(viewer, "expansion_decided_by_none", "Awaiting review");
        }

        ExpansionRequestManager.DecisionRecord record = entry.getDecision();
        if (record == null) return "";
        String note = safe(record.getNote(), "");
        if (!note.isBlank()) return note;
        return switch (record.getActorType()) {
            case AUTO -> "Automatically handled by the expansion review system";
            case ADMIN -> "Handled by staff review";
            case SYSTEM -> "Handled by the server";
            case UNKNOWN -> "Handled by an unknown source";
        };
    }

    private Material materialFor(DisplayEntry entry) {
        if (entry == null) return Material.PAPER;
        if (entry.isPendingEntry()) return Material.PAPER;
        if (entry.getDecision() != null && entry.getDecision().getStatus() == ExpansionRequest.Status.APPROVED) {
            return Material.LIME_STAINED_GLASS_PANE;
        }
        return Material.RED_STAINED_GLASS_PANE;
    }

    private boolean isInstantOrAutoMode() {
        String mode = plugin.getConfig().getString(
                "expansions.approval_mode",
                plugin.getConfig().getString("expansions.mode", "QUEUE")
        );
        if (mode != null && mode.equalsIgnoreCase("INSTANT")) return true;

        if (plugin.getConfig().getBoolean("expansions.auto_approve.enabled", false)) return true;
        if (plugin.getConfig().getBoolean("expansions.auto_approve", false)) return true;
        return plugin.getConfig().getBoolean("expansions.approval.auto_when_no_reviewers.enabled", false);
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
