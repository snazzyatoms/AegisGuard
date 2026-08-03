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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Staff-only read-only history of Instant / auto-approved expansions.
 * Kept separate from {@link ExpansionRequestAdminGUI} so Pending/Review stays a decision queue.
 */
public class ExpansionInstantApprovalsGUI {

    private final AegisGuard plugin;
    private static final int ENTRIES_PER_PAGE = 45;

    private final NamespacedKey keyAction;

    public ExpansionInstantApprovalsGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "aegis_action");
    }

    public static class InstantApprovalsHolder implements InventoryHolder {
        private final List<ExpansionRequestManager.DecisionRecord> entries;
        private final int page;

        public InstantApprovalsHolder(List<ExpansionRequestManager.DecisionRecord> entries, int page) {
            this.entries = entries;
            this.page = page;
        }

        public List<ExpansionRequestManager.DecisionRecord> getEntries() { return entries; }
        public int getPage() { return page; }

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
            player.sendMessage("§c[AegisGuard] ExpansionRequestManager not loaded.");
            plugin.effects().playError(player);
            return;
        }

        final int requestedPage = page;
        plugin.runGlobalAsync(() -> {
            List<ExpansionRequestManager.DecisionRecord> entries = new ArrayList<>();
            try {
                entries.addAll(manager.getRecentInstantApprovals());
            } catch (Throwable t) {
                plugin.getLogger().warning("[ExpansionInstantApprovalsGUI] getRecentInstantApprovals failed: " + t.getMessage());
            }

            int maxPages = (int) Math.ceil((double) entries.size() / ENTRIES_PER_PAGE);
            int fixedPage = Math.max(0, requestedPage);
            if (maxPages > 0 && fixedPage >= maxPages) fixedPage = maxPages - 1;
            if (maxPages == 0) fixedPage = 0;

            final int finalPage = fixedPage;
            final int finalMaxPages = maxPages;
            final int finalSafePages = Math.max(1, maxPages);
            final List<ExpansionRequestManager.DecisionRecord> finalEntries = entries;

            plugin.runMain(player, () -> buildAndOpen(player, finalEntries, finalPage, finalSafePages, finalMaxPages));
        });
    }

    private void buildAndOpen(
            Player player,
            List<ExpansionRequestManager.DecisionRecord> entries,
            int page,
            int safePages,
            int maxPages
    ) {
        String baseTitle = plugin.gui().tr(player, "expansion_instant_title", "&b&lInstant Approvals");
        Map<String, String> ph = Map.of(
                "PAGE", String.valueOf(page + 1),
                "PAGES", String.valueOf(safePages)
        );
        String fullTitle = plugin.gui().title(
                player,
                "expansion_instant_title_paged",
                baseTitle + " &7({PAGE}/{PAGES})",
                ph
        );
        fullTitle = ChatColor.translateAlternateColorCodes('&', fullTitle);

        InstantApprovalsHolder holder = new InstantApprovalsHolder(entries, page);
        Inventory inv = Bukkit.createInventory(holder, 54, fullTitle);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        if (entries.isEmpty()) {
            ItemStack none = GUIManager.createItem(
                    Material.BARRIER,
                    plugin.gui().tr(player, "expansion_instant_none_title", "&7No Instant Approvals"),
                    plugin.gui().trList(player, "expansion_instant_none_lore", List.of(
                            "&7No auto-approved expansions have been",
                            "&7recorded yet."
                    ))
            );
            tagAction(none, "instant_none");
            inv.setItem(22, none);
        } else {
            int startIndex = page * ENTRIES_PER_PAGE;
            for (int slot = 0; slot < ENTRIES_PER_PAGE; slot++) {
                int index = startIndex + slot;
                if (index >= entries.size()) break;

                ExpansionRequestManager.DecisionRecord decision = entries.get(index);
                if (decision == null) continue;

                UUID requesterId = decision.getRequester();
                OfflinePlayer requester = requesterId == null ? null : Bukkit.getOfflinePlayer(requesterId);
                String name = (requester != null && requester.getName() != null) ? requester.getName() : "Unknown";

                String statusText = statusText(player, decision.getStatus());
                String ageText = formatAge(System.currentTimeMillis() - decision.getTimestamp());
                String costStr = plugin.eco().format(decision.getCost(), CurrencyType.VAULT);
                String worldName = safe(decision.getWorldName(), "Unknown");
                String decidedBy = buildDeciderLabel(player, decision);
                long decisionTimestamp = decision.getDecisionTimestamp();
                String decisionAge = decisionTimestamp > 0L
                        ? formatAge(System.currentTimeMillis() - decisionTimestamp)
                        : "-";
                String note = buildNoteText(player, decision);

                Map<String, String> vars = Map.of(
                        "PLAYER", name,
                        "WORLD", worldName,
                        "STATUS", statusText,
                        "AGE", ageText,
                        "CUR", String.valueOf(decision.getCurrentRadius()),
                        "REQ", String.valueOf(decision.getRequestedRadius()),
                        "COST", costStr,
                        "DECIDER", decidedBy,
                        "DECISION_AGE", decisionAge,
                        "NOTE", note
                );

                String itemName = plugin.gui().tr(
                        player,
                        "expansion_instant_item_name",
                        "&bAuto-Approved: &f{PLAYER}",
                        vars
                );
                List<String> lore = plugin.gui().trList(
                        player,
                        "expansion_instant_item_lore",
                        List.of(
                                "&7World: &f{WORLD}",
                                "&7Status: {STATUS}",
                                "&7Requested: &f{AGE} ago",
                                "&7Radius: &e{CUR} &7→ &a{REQ}",
                                "&7Cost: &6{COST}",
                                "&7Approved By: &f{DECIDER}",
                                "&7Decision Age: &f{DECISION_AGE}",
                                "&7Note: &f{NOTE}",
                                " ",
                                "&7Read-only automatic approval history."
                        ),
                        vars
                );

                ItemStack item = GUIManager.createItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, itemName, lore);
                tagAction(item, "instant_entry");
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

        ItemStack pending = GUIManager.createItem(
                Material.AMETHYST_CLUSTER,
                plugin.gui().tr(player, "button_open_pending_requests", "&ePending Requests"),
                plugin.gui().trList(player, "open_pending_requests_lore", List.of(
                        "&7Return to the queue of requests",
                        "&7awaiting approve or deny.",
                        " ",
                        "&eClick to open."
                ))
        );
        tagAction(pending, "open_pending");
        inv.setItem(48, pending);

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
        if (!(e.getInventory().getHolder() instanceof InstantApprovalsHolder holder)) return;
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
        if (action == null) return;

        switch (action) {
            case "prev_page" -> { open(player, page - 1); plugin.effects().playMenuFlip(player); }
            case "next_page" -> { open(player, page + 1); plugin.effects().playMenuFlip(player); }
            case "open_pending" -> { plugin.gui().expansionAdmin().open(player); plugin.effects().playMenuFlip(player); }
            case "back_admin" -> { plugin.gui().admin().open(player); plugin.effects().playMenuFlip(player); }
            case "close_menu" -> { player.closeInventory(); plugin.effects().playMenuClose(player); }
            case "instant_none", "instant_entry" -> {
                sendSystem(player, "expansion_instant_read_only",
                        "&7This was approved automatically. It is not awaiting staff review.");
                plugin.effects().playError(player);
            }
            default -> { }
        }
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

    private String buildDeciderLabel(Player viewer, ExpansionRequestManager.DecisionRecord record) {
        if (record == null) return plugin.gui().tr(viewer, "expansion_decided_by_unknown", "&7Unknown");
        return switch (record.getActorType()) {
            case AUTO -> plugin.gui().tr(viewer, "expansion_decided_by_auto", "&bAuto");
            case ADMIN -> plugin.gui().tr(viewer, "expansion_decided_by_admin", "&eAdmin");
            case SYSTEM -> plugin.gui().tr(viewer, "expansion_decided_by_system", "&7System");
            case UNKNOWN -> plugin.gui().tr(viewer, "expansion_decided_by_unknown", "&7Unknown");
        };
    }

    private String statusText(Player viewer, ExpansionRequest.Status status) {
        if (status == null) return plugin.gui().tr(viewer, "expansion_status_approved", "&aApproved");
        return switch (status) {
            case APPROVED -> plugin.gui().tr(viewer, "expansion_status_approved", "&aApproved");
            case DENIED -> plugin.gui().tr(viewer, "expansion_status_denied", "&cDenied");
            case PENDING -> plugin.gui().tr(viewer, "expansion_status_pending", "&ePending");
        };
    }

    private String buildNoteText(Player viewer, ExpansionRequestManager.DecisionRecord record) {
        if (record == null) return "";
        String note = safe(record.getNote(), "");
        if (!note.isBlank()) return note;
        return plugin.gui().tr(viewer, "expansion_instant_default_note",
                "Automatically approved by Instant Mode / unattended queue");
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
