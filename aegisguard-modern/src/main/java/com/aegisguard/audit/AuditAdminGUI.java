package com.aegisguard.audit;

import com.aegisguard.AegisGuard;
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
 * AuditAdminGUI (Milestone 1 - Staff Audit Ledger)
 *
 * Staff-only, paginated viewer for {@link AuditService} entries with a simple category filter.
 * Modeled closely on {@code SnapshotAdminGUI}'s PDC-routed, fully-filled inventory pattern.
 */
public class AuditAdminGUI {

    private final AegisGuard plugin;

    private static final int ENTRIES_PER_PAGE = 45;

    private final NamespacedKey keyAction;
    private final NamespacedKey keyEntryId;

    public AuditAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "aegis_action");
        this.keyEntryId = new NamespacedKey(plugin, "aegis_audit_entry_id");
    }

    public static class AuditHolder implements InventoryHolder {
        private final AuditCategory filter;
        private final int page;
        private final List<UUID> entryIds;

        public AuditHolder(AuditCategory filter, int page, List<UUID> entryIds) {
            this.filter = filter;
            this.page = page;
            this.entryIds = entryIds;
        }

        public AuditCategory getFilter() { return filter; }
        public int getPage() { return page; }
        public List<UUID> getEntryIds() { return entryIds; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        open(player, null, 0);
    }

    public void open(Player player, AuditCategory filter, int page) {
        if (!player.hasPermission("aegis.admin.audit")) {
            plugin.msg().send(player, "no_perm");
            plugin.effects().playError(player);
            return;
        }
        if (plugin.audit() == null || !plugin.audit().isEnabled()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.gui().tr(player, "audit_disabled", "&cThe audit ledger is disabled.")));
            plugin.effects().playError(player);
            return;
        }

        List<AuditEntry> matching = plugin.audit().recent(filter, 0);
        List<UUID> ids = new ArrayList<>(matching.size());
        for (AuditEntry entry : matching) ids.add(entry.getId());

        int maxPages = Math.max(1, (int) Math.ceil((double) ids.size() / ENTRIES_PER_PAGE));
        int safePage = Math.max(0, Math.min(page, maxPages - 1));

        buildAndOpen(player, filter, matching, safePage, maxPages);
    }

    private void buildAndOpen(Player player, AuditCategory filter, List<AuditEntry> matching, int page, int maxPages) {
        String baseTitle = plugin.gui().title(player, "audit_admin_title", "&e&lStaff Audit Ledger");
        String suffix = GUIManager.color(" &7(" + (page + 1) + "/" + maxPages + ")");
        String title = clampTitleWithSuffix(baseTitle, suffix);

        List<UUID> ids = new ArrayList<>(matching.size());
        for (AuditEntry entry : matching) ids.add(entry.getId());

        AuditHolder holder = new AuditHolder(filter, page, ids);
        Inventory inv = Bukkit.createInventory(holder, 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        if (matching.isEmpty()) {
            ItemStack none = GUIManager.createItem(
                    Material.BARRIER,
                    plugin.gui().tr(player, "audit_none_title", "&cNo Audit Entries"),
                    plugin.gui().trList(player, "audit_none_lore", List.of(
                            "&7No recorded actions match this filter yet."
                    ))
            );
            tagAction(none, "audit_none");
            inv.setItem(22, none);
        } else {
            int startIndex = page * ENTRIES_PER_PAGE;
            for (int slot = 0; slot < ENTRIES_PER_PAGE; slot++) {
                int index = startIndex + slot;
                if (index >= matching.size()) break;
                inv.setItem(slot, buildEntryItem(player, matching.get(index)));
            }
        }

        ItemStack filterItem = GUIManager.createItem(
                Material.HOPPER,
                plugin.gui().tr(player, "audit_filter_button", "&bFilter: &f{FILTER}",
                        Map.of("FILTER", filterLabel(player, filter))),
                plugin.gui().trList(player, "audit_filter_lore", List.of(
                        "&7Click to cycle through categories."
                ))
        );
        tagAction(filterItem, "cycle_filter");
        inv.setItem(46, filterItem);

        if (page > 0) {
            ItemStack prev = GUIManager.createItem(
                    Material.ARROW,
                    plugin.gui().tr(player, "button_prev_page", "&fPrevious Page"),
                    plugin.gui().trList(player, "prev_page_lore", List.of("&7Go to the previous page."))
            );
            tagAction(prev, "prev_page");
            inv.setItem(45, prev);
        }

        ItemStack back = GUIManager.createItem(
                Material.ARROW,
                plugin.gui().tr(player, "button_back_admin", plugin.gui().tr(player, "button_back", "&fBack")),
                plugin.gui().trList(player, "back_lore", List.of("&7Return to admin menu."))
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

    private ItemStack buildEntryItem(Player player, AuditEntry entry) {
        OfflinePlayer actorPlayer = entry.getActorId() != null ? Bukkit.getOfflinePlayer(entry.getActorId()) : null;
        String actorName = actorPlayer != null && actorPlayer.getName() != null ? actorPlayer.getName() : entry.getActorName();

        Map<String, String> vars = Map.of(
                "CATEGORY", categoryLabel(player, entry.getCategory()),
                "ACTOR", actorName,
                "TARGET", entry.getTarget().isBlank() ? "-" : entry.getTarget(),
                "SUMMARY", entry.getSummary(),
                "AGE", formatAge(entry.getAgeMillis())
        );

        String itemName = plugin.gui().tr(player, "audit_entry_name", "&e{CATEGORY}", vars);
        List<String> lore = plugin.gui().trList(player, "audit_entry_lore", List.of(
                "&7Actor: &f{ACTOR}",
                "&7Target: &f{TARGET}",
                "&7When: &f{AGE} ago",
                " ",
                "&f{SUMMARY}"
        ), vars);

        ItemStack item = GUIManager.createItem(iconFor(entry.getCategory()), itemName, lore);
        tagAction(item, "audit_entry");
        tagEntryId(item, entry.getId());
        return item;
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof AuditHolder holder)) return;

        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        if (!player.hasPermission("aegis.admin.audit")) {
            plugin.effects().playError(player);
            player.closeInventory();
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        String action = getAction(clicked);
        if (action == null) return;

        switch (action) {
            case "prev_page" -> { open(player, holder.getFilter(), holder.getPage() - 1); plugin.effects().playMenuFlip(player); }
            case "next_page" -> { open(player, holder.getFilter(), holder.getPage() + 1); plugin.effects().playMenuFlip(player); }
            case "back_admin" -> { plugin.gui().admin().open(player); plugin.effects().playMenuFlip(player); }
            case "close_menu" -> { player.closeInventory(); plugin.effects().playMenuClose(player); }
            case "audit_none" -> plugin.effects().playError(player);
            case "cycle_filter" -> { open(player, nextFilter(holder.getFilter()), 0); plugin.effects().playMenuFlip(player); }
            case "audit_entry" -> showEntryDetail(player, clicked);
            default -> { /* ignore */ }
        }
    }

    private void showEntryDetail(Player player, ItemStack clicked) {
        UUID id = getEntryId(clicked);
        if (id == null || plugin.audit() == null) return;

        AuditEntry entry = plugin.audit().recent(0).stream()
                .filter(candidate -> candidate.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (entry == null) return;

        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.gui().tr(player, "audit_entry_detail", "&e[Audit] &7{SUMMARY}",
                        Map.of("SUMMARY", entry.getSummary()))));
        plugin.effects().playMenuFlip(player);
    }

    private AuditCategory nextFilter(AuditCategory current) {
        AuditCategory[] values = AuditCategory.values();
        if (current == null) return values[0];
        int nextIndex = current.ordinal() + 1;
        return nextIndex >= values.length ? null : values[nextIndex];
    }

    private String filterLabel(Player player, AuditCategory filter) {
        if (filter == null) return plugin.gui().tr(player, "audit_filter_all", "All Categories");
        return categoryLabel(player, filter);
    }

    private String categoryLabel(Player player, AuditCategory category) {
        String key = "audit_category_" + category.name().toLowerCase(Locale.ROOT);
        return plugin.gui().tr(player, key, category.name());
    }

    private Material iconFor(AuditCategory category) {
        return switch (category) {
            case SNAPSHOT_RESTORE -> Material.RECOVERY_COMPASS;
            case DOCTOR_REPAIR -> Material.COMPASS;
            case MIGRATION -> Material.BLAZE_ROD;
            case ADMIN_BYPASS -> Material.NETHER_STAR;
            case CLAIM_BLOCK_ADJUST -> Material.EMERALD;
            case GUEST_PASS -> Material.PAPER;
        };
    }

    // --- PDC helpers ---

    private void tagAction(ItemStack item, String action) {
        if (item == null || action == null || action.isBlank()) return;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action.trim().toLowerCase(Locale.ROOT));
            item.setItemMeta(meta);
        } catch (Throwable ignored) { }
    }

    private String getAction(ItemStack item) {
        if (item == null) return null;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String value = meta.getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
            return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void tagEntryId(ItemStack item, UUID id) {
        if (item == null || id == null) return;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            meta.getPersistentDataContainer().set(keyEntryId, PersistentDataType.STRING, id.toString());
            item.setItemMeta(meta);
        } catch (Throwable ignored) { }
    }

    private UUID getEntryId(ItemStack item) {
        if (item == null) return null;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String value = meta.getPersistentDataContainer().get(keyEntryId, PersistentDataType.STRING);
            if (value == null || value.isBlank()) return null;
            return UUID.fromString(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // --- Misc helpers ---

    private String clampTitleWithSuffix(String base, String suffix) {
        final int max = 32;
        if (base == null) base = "";
        if (suffix == null) suffix = "";

        String combined = base + suffix;
        if (combined.length() <= max) return combined;

        if (suffix.length() >= max) {
            String cut = suffix.substring(0, max);
            return cut.endsWith("§") ? cut.substring(0, max - 1) : cut;
        }

        int remainingForBase = max - suffix.length();
        String trimmedBase = base.length() > remainingForBase ? base.substring(0, remainingForBase) : base;
        if (trimmedBase.endsWith("§")) trimmedBase = trimmedBase.substring(0, Math.max(0, trimmedBase.length() - 1));

        return trimmedBase + suffix;
    }

    private String formatAge(long ms) {
        long seconds = ms / 1000L;
        long mins = seconds / 60L;
        long hrs = mins / 60L;
        long days = hrs / 24L;

        if (days > 0) return days + "d " + (hrs % 24) + "h";
        if (hrs > 0) return hrs + "h " + (mins % 60) + "m";
        if (mins > 0) return mins + "m";
        return seconds + "s";
    }
}
