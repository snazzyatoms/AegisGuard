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
 * ExpansionRequestAdminGUI — Pending / Review Requests queue only.
 *
 * Instant / auto-approved history lives in {@link ExpansionInstantApprovalsGUI}.
 * This menu never mixes decided (AUTO/ADMIN) records into the pending list.
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
        private final List<ExpansionRequest> entries;
        private final int page;

        public ExpansionAdminHolder(List<ExpansionRequest> entries, int page) {
            this.entries = entries;
            this.page = page;
        }

        public List<ExpansionRequest> getEntries() { return entries; }
        public int getPage() { return page; }

        /** @deprecated Always false — pending GUI no longer embeds history. */
        @Deprecated
        public boolean isShowAll() { return false; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int page) {
        if (!plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            plugin.effects().playError(player);
            return;
        }

        ExpansionRequestManager manager = plugin.getExpansionRequestManager();
        if (manager == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.gui().tr(player, "expansion_manager_unavailable",
                            "&c[AegisGuard] Expansion request manager is unavailable.")));
            plugin.effects().playError(player);
            return;
        }

        final int requestedPage = page;

        plugin.runGlobalAsync(() -> {
            List<ExpansionRequest> entries = new ArrayList<>();
            try {
                // Pending queue only — never include AUTO/instant history here.
                for (ExpansionRequest request : manager.getPendingQueueRequests()) {
                    if (request != null) entries.add(request);
                }
                entries.sort(Comparator.comparingLong(ExpansionRequest::getTimestamp));
            } catch (Throwable t) {
                plugin.getLogger().warning("[ExpansionRequestAdminGUI] getPendingQueueRequests failed: " + t.getMessage());
            }

            int maxPages = (int) Math.ceil((double) entries.size() / REQS_PER_PAGE);
            int fixedPage = Math.max(0, requestedPage);
            if (maxPages > 0 && fixedPage >= maxPages) fixedPage = maxPages - 1;
            if (maxPages == 0) fixedPage = 0;

            final int finalPage = fixedPage;
            final int finalSafePages = Math.max(1, maxPages);
            final int finalMaxPages = maxPages;
            final List<ExpansionRequest> finalEntries = entries;

            plugin.runMain(player, () -> buildAndOpen(player, manager, finalEntries, finalPage, finalSafePages, finalMaxPages));
        });
    }

    /** Back-compat overload: showAll is ignored (pending queue only). */
    public void open(Player player, int page, boolean showAllIgnored) {
        open(player, page);
    }

    private void buildAndOpen(
            Player player,
            ExpansionRequestManager manager,
            List<ExpansionRequest> entries,
            int page,
            int safePages,
            int maxPages
    ) {
        String baseTitle = plugin.gui().tr(player, "expansion_admin_title", "&6&lPending Petitions");
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
        fullTitle = ChatColor.translateAlternateColorCodes('&', fullTitle);

        ExpansionAdminHolder holder = new ExpansionAdminHolder(entries, page);
        Inventory inv = Bukkit.createInventory(holder, 54, fullTitle);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        if (entries.isEmpty()) {
            ItemStack none = GUIManager.createItem(
                    Material.BARRIER,
                    plugin.gui().tr(player, "expansion_none_title", "&cNo Pending Requests"),
                    plugin.gui().trList(player, "expansion_none_lore", List.of(
                            "&7There are no active expansion",
                            "&7requests awaiting review."
                    ))
            );
            tagAction(none, "expansion_none");
            inv.setItem(22, none);
        } else {
            int startIndex = page * REQS_PER_PAGE;

            for (int slot = 0; slot < REQS_PER_PAGE; slot++) {
                int index = startIndex + slot;
                if (index >= entries.size()) break;

                ExpansionRequest snapshot = entries.get(index);
                if (snapshot == null) continue;

                UUID requesterId = snapshot.getRequester();
                ExpansionRequest req;
                try {
                    req = manager.getRequest(requesterId);
                } catch (Throwable t) {
                    req = null;
                }
                // Re-validate: only true pending queue items are actionable.
                if (req == null || !ExpansionRequestManager.isPendingQueueEntry(req)) continue;

                OfflinePlayer requester = Bukkit.getOfflinePlayer(requesterId);
                String name = (requester.getName() != null) ? requester.getName() : "Unknown";

                String statusText = plugin.gui().tr(player, req.getStatusLangKey(), "&7Pending");
                String ageText = formatAge(System.currentTimeMillis() - req.getTimestamp());
                String costStr = plugin.eco().format(req.getCost(), CurrencyType.VAULT);
                String worldName = safe(req.getWorldName(), "Unknown");
                String decidedBy = buildDeciderLabel(player, req);

                Map<String, String> vars = Map.of(
                        "PLAYER", name,
                        "WORLD", worldName,
                        "STATUS", statusText,
                        "AGE", ageText,
                        "CUR", String.valueOf(req.getCurrentRadius()),
                        "REQ", String.valueOf(req.getRequestedRadius()),
                        "COST", costStr,
                        "DECIDER", decidedBy,
                        "DECISION_AGE", "-",
                        "NOTE", plugin.gui().tr(player, "expansion_decided_by_none", "Awaiting review")
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

                ItemStack item = GUIManager.createItem(Material.PAPER, itemName, lore);
                tagAction(item, "expansion_entry");
                inv.setItem(slot, item);
            }
        }

        if (page > 0) {
            ItemStack prev = GUIManager.createItem(
                    Material.ARROW,
                    plugin.gui().tr(player, "button_prev_page", "&fPrevious Page"),
                    plugin.gui().trList(player, "prev_page_lore", List.of("&7Go to the previous page."))
            );
            tagAction(prev, "prev_page");
            inv.setItem(45, prev);
        }

        ItemStack instant = GUIManager.createItem(
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                plugin.gui().tr(player, "button_open_instant_approvals", "&bInstant Approvals"),
                plugin.gui().trList(player, "open_instant_approvals_lore", List.of(
                        "&7View expansions approved automatically.",
                        "&7These are history entries, not the pending queue.",
                        " ",
                        "&eClick to open."
                ))
        );
        tagAction(instant, "open_instant");
        inv.setItem(48, instant);

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

        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        if (!plugin.isAdmin(player)) {
            plugin.effects().playError(player);
            player.closeInventory();
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int page = holder.getPage();

        String action = getAction(clicked);
        if (action != null) {
            switch (action) {
                case "prev_page" -> { open(player, page - 1); plugin.effects().playMenuFlip(player); return; }
                case "next_page" -> { open(player, page + 1); plugin.effects().playMenuFlip(player); return; }
                case "open_instant" -> {
                    plugin.gui().expansionInstantApprovals().open(player);
                    plugin.effects().playMenuFlip(player);
                    return;
                }
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

        ExpansionRequest entry = holder.getEntries().get(index);
        if (entry == null) return;

        UUID requesterId = entry.getRequester();
        if (requesterId == null) return;

        ExpansionRequest req = manager.getRequest(requesterId);
        if (req == null || !ExpansionRequestManager.isPendingQueueEntry(req)) {
            sendSystem(player, "request_expired", "&cThat request has already expired or been handled.");
            open(player, page);
            return;
        }

        String reqName = safe(Bukkit.getOfflinePlayer(requesterId).getName(), "Unknown");

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

        open(player, page);
    }

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

    private void sendSystem(Player p, String key, String fallback) {
        String msg = ChatColor.translateAlternateColorCodes('&', plugin.gui().tr(p, key, fallback));
        p.sendMessage(msg);
    }

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
