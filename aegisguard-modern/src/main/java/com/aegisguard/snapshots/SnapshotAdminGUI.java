package com.aegisguard.snapshots;

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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SnapshotAdminGUI (1.2.6 QoL pass)
 *
 * Improvements:
 * - PDC action routing (aegis_action) for prev/next/back + snapshot entries.
 * - Safer destructive actions:
 *    - Shift-Left: Rollback
 *    - Shift-Right: Delete
 *    (Non-shift clicks show a hint instead of doing damage.)
 * - Async snapshot list loading + async rollback/delete (keeps main thread smooth).
 * - Fully filled inventory (no "holes" for weird interactions).
 * - Snapshot entries store snapshot id in PDC (aegis_snapshot_id) to avoid index desync.
 * - Keeps the 1.2.5 structure: 45 items, footer nav at 45/49/53.
 */
public class SnapshotAdminGUI {

    private final AegisGuard plugin;

    private static final int SNAPSHOTS_PER_PAGE = 45; // Slots 0-44
    private static final long RESTORE_CONFIRM_MS = 15_000L;

    private final NamespacedKey keyAction;
    private final NamespacedKey keySnapshotId;
    private final Map<UUID, AbstractMap.SimpleEntry<UUID, Long>> pendingRestoreConfirm = new ConcurrentHashMap<>();

    public SnapshotAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "aegis_action");
        this.keySnapshotId = new NamespacedKey(plugin, "aegis_snapshot_id");
    }

    private boolean isRestoreConfirmed(UUID playerId, UUID snapshotId) {
        AbstractMap.SimpleEntry<UUID, Long> pending = pendingRestoreConfirm.get(playerId);
        if (pending == null) return false;
        if (!Objects.equals(pending.getKey(), snapshotId)) return false;
        return pending.getValue() != null && pending.getValue() >= System.currentTimeMillis();
    }

    private void markRestoreConfirm(UUID playerId, UUID snapshotId) {
        pendingRestoreConfirm.put(playerId,
                new AbstractMap.SimpleEntry<>(snapshotId, System.currentTimeMillis() + RESTORE_CONFIRM_MS));
    }

    private void clearRestoreConfirm(UUID playerId) {
        pendingRestoreConfirm.remove(playerId);
    }

    public static class SnapshotHolder implements InventoryHolder {
        private final int page;
        private final List<UUID> snapshotIds;

        public SnapshotHolder(List<UUID> snapshotIds, int page) {
            this.snapshotIds = snapshotIds;
            this.page = page;
        }

        public int getPage() { return page; }
        public List<UUID> getSnapshotIds() { return snapshotIds; }

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

        if (plugin.getSnapshotManager() == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.gui().tr(player, "snapshots_disabled", "&cSnapshots are disabled.")));
            plugin.effects().playError(player);
            return;
        }

        final int requestedPage = page;

        // 1.2.6: load snapshot list async
        plugin.runGlobalAsync(() -> {
            List<ClaimSnapshot> snapshots;
            try {
                snapshots = plugin.getSnapshotManager().getAllSnapshots();
            } catch (Throwable t) {
                snapshots = new ArrayList<>();
                plugin.getLogger().warning("[SnapshotAdminGUI] getAllSnapshots failed: " + t.getMessage());
            }

            // QoL: newest first (smaller age = newer)
            snapshots.sort(Comparator.comparingLong(ClaimSnapshot::getAgeMillis));

            List<UUID> ids = new ArrayList<>(snapshots.size());
            for (ClaimSnapshot s : snapshots) {
                if (s != null && s.getSnapshotId() != null) ids.add(s.getSnapshotId());
            }

            int maxPages = (int) Math.ceil((double) ids.size() / SNAPSHOTS_PER_PAGE);
            int fixedPage = requestedPage;
            if (fixedPage < 0) fixedPage = 0;
            if (maxPages > 0 && fixedPage >= maxPages) fixedPage = maxPages - 1;
            int safePages = Math.max(1, maxPages);

            final int finalPage = fixedPage;
            final int finalSafePages = safePages;

            plugin.runMain(player, () -> buildAndOpen(player, ids, finalPage, finalSafePages));
        });
    }

    private void buildAndOpen(Player player, List<UUID> ids, int page, int safePages) {
        String baseTitle = plugin.gui().title(
                player,
                "snapshots_admin_title",
                "&c&lClaim Snapshots",
                Map.of("PAGE", String.valueOf(page + 1), "PAGES", String.valueOf(safePages))
        );

        // Add a compact page suffix (and clamp)
        String suffix = GUIManager.color(" &7(" + (page + 1) + "/" + safePages + ")");
        String title = clampTitleWithSuffix(baseTitle, suffix);

        SnapshotHolder holder = new SnapshotHolder(ids, page);
        Inventory inv = Bukkit.createInventory(holder, 54, title);

        // 1.2.6: fill ALL slots
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        if (ids.isEmpty()) {
            ItemStack none = GUIManager.createItem(
                    Material.BARRIER,
                    plugin.gui().tr(player, "snapshots_none_title", "&cNo Snapshots"),
                    plugin.gui().trList(player, "snapshots_none_lore", List.of(
                            "&7No claim snapshots exist yet.",
                            "&7Snapshots are created before",
                            "&7expansions and merges."
                    ))
            );
            tagAction(none, "snapshots_none");
            inv.setItem(22, none);
        } else {
            int startIndex = page * SNAPSHOTS_PER_PAGE;

            for (int slot = 0; slot < SNAPSHOTS_PER_PAGE; slot++) {
                int index = startIndex + slot;
                if (index >= ids.size()) break;

                UUID id = ids.get(index);

                ClaimSnapshot snapshot;
                try {
                    snapshot = plugin.getSnapshotManager().getSnapshot(id);
                } catch (Throwable t) {
                    snapshot = null;
                }
                if (snapshot == null) continue;

                OfflinePlayer owner = Bukkit.getOfflinePlayer(snapshot.getOwner());
                String ownerName = owner.getName() != null ? owner.getName() : "Unknown";

                OfflinePlayer actor = snapshot.getTriggeredBy() != null
                        ? Bukkit.getOfflinePlayer(snapshot.getTriggeredBy())
                        : null;
                String actorName = (actor != null && actor.getName() != null) ? actor.getName() : "Auto";

                String age = formatAge(snapshot.getAgeMillis());

                Map<String, String> vars = Map.of(
                        "OWNER", ownerName,
                        "WORLD", snapshot.getWorldName(),
                        "TYPE", snapshot.getType().name(),
                        "REASON", snapshot.getReason(),
                        "AGE", age,
                        "ACTOR", actorName,
                        "RADIUS", String.valueOf(snapshot.getRadius())
                );

                String itemName = plugin.gui().tr(
                        player,
                        "snapshot_item_name",
                        "&e" + snapshot.getType().name() + " &7Snapshot",
                        vars
                );

                // 1.2.6: safer action text (shift)
                List<String> lore = plugin.gui().trList(player, "snapshot_item_lore", List.of(
                        "&7Owner: &f{OWNER}",
                        "&7World: &f{WORLD}",
                        "&7Type: &e{TYPE}",
                        "&7Reason: &f{REASON}",
                        "&7Age: &f{AGE}",
                        "&7Triggered By: &f{ACTOR}",
                        "&7Radius: &a{RADIUS}",
                        " ",
                        "&aShift-Left-Click: &7Rollback to this state",
                        "&cShift-Right-Click: &7Delete snapshot"
                ), vars);

                Material icon = snapshot.getType() == ClaimSnapshot.SnapshotType.PRE_EXPANSION
                        ? Material.SPYGLASS
                        : Material.COMPASS;

                ItemStack item = GUIManager.createItem(icon, itemName, lore);
                tagAction(item, "snapshot_entry");
                tagSnapshotId(item, id);

                inv.setItem(slot, item);
            }
        }

        // Navigation (45 / 49 / 53) - PDC tagged
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
                Material.BARRIER,
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

        if (!ids.isEmpty()) {
            int maxPages = (int) Math.ceil((double) ids.size() / SNAPSHOTS_PER_PAGE);
            if (page < maxPages - 1) {
                ItemStack next = GUIManager.createItem(
                        Material.ARROW,
                        plugin.gui().tr(player, "button_next_page", "&fNext Page"),
                        plugin.gui().trList(player, "next_page_lore", List.of("&7Go to the next page."))
                );
                tagAction(next, "next_page");
                inv.setItem(53, next);
            }
        }

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof SnapshotHolder holder)) return;

        e.setCancelled(true);

        // Extra safety: only handle clicks in the top inventory
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
                case "back_admin" -> { plugin.gui().admin().open(player); plugin.effects().playMenuFlip(player); return; }
                case "close_menu" -> { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
                case "snapshots_none" -> { plugin.effects().playError(player); return; }
                case "snapshot_entry" -> { /* continue */ }
                default -> { return; }
            }
        }

        // Snapshot action
        UUID snapshotId = getSnapshotId(clicked);
        if (snapshotId == null) {
            // Fallback to index, if item wasn't tagged (legacy safety)
            int slot = e.getSlot();
            if (slot < 0 || slot >= SNAPSHOTS_PER_PAGE) return;

            int index = (page * SNAPSHOTS_PER_PAGE) + slot;
            if (index < 0 || index >= holder.getSnapshotIds().size()) return;
            snapshotId = holder.getSnapshotIds().get(index);
        }

        ClaimSnapshot snapshot;
        try {
            snapshot = plugin.getSnapshotManager().getSnapshot(snapshotId);
        } catch (Throwable t) {
            snapshot = null;
        }

        if (snapshot == null) {
            plugin.msg().send(player, "snapshot_not_found", Map.of());
            open(player, page);
            return;
        }

        // 1.2.6: safety gate destructive actions with shift
        boolean shift = e.getClick().isShiftClick();
        final UUID finalSnapshotId = snapshotId;
        final ClaimSnapshot finalSnapshot = snapshot;
        final int finalPage = page;

        if (e.getClick().isLeftClick()) {
            if (!shift) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.gui().tr(player, "snapshot_rollback_hint", "&eTip: &7Shift-Left-Click to rollback this snapshot.")));
                plugin.effects().playError(player);
                return;
            }

            if (plugin.getConfig().getBoolean("snapshots.require_restore_confirmation", true)
                    && !isRestoreConfirmed(player.getUniqueId(), finalSnapshotId)) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.gui().tr(player, "snapshot_restore_summary",
                                "&eRestore summary: &f{TYPE} &7| &f{REASON} &7| age &f{AGE}&7. Shift-Left again to confirm.",
                                Map.of(
                                        "TYPE", finalSnapshot.getType().name(),
                                        "REASON", finalSnapshot.getReason() == null || finalSnapshot.getReason().isBlank()
                                                ? "No reason recorded" : finalSnapshot.getReason(),
                                        "AGE", formatAge(finalSnapshot.getAgeMillis())
                                ))));
                markRestoreConfirm(player.getUniqueId(), finalSnapshotId);
                plugin.effects().playMenuFlip(player);
                return;
            }

            plugin.effects().playMenuFlip(player);
            clearRestoreConfirm(player.getUniqueId());
            plugin.runGlobalAsync(() -> {
                boolean ok;
                try {
                    ok = plugin.getSnapshotManager().rollback(finalSnapshotId);
                } catch (Throwable t) {
                    ok = false;
                    plugin.getLogger().warning("[SnapshotAdminGUI] rollback failed: " + t.getMessage());
                }

                boolean finalOk = ok;
                plugin.runMain(player, () -> {
                    if (finalOk) {
                        plugin.msg().send(player, "snapshot_rollback_success",
                                Map.of("ID", finalSnapshot.getPlotId().toString()));
                        plugin.effects().playConfirm(player);
                    } else {
                        plugin.msg().send(player, "snapshot_rollback_failed", Map.of());
                        plugin.effects().playError(player);
                    }
                    open(player, finalPage);
                });
            });
            return;
        }

        if (e.getClick().isRightClick()) {
            if (!shift) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.gui().tr(player, "snapshot_delete_hint", "&cTip: &7Shift-Right-Click to delete this snapshot.")));
                plugin.effects().playError(player);
                return;
            }

            plugin.effects().playMenuFlip(player);
            plugin.runGlobalAsync(() -> {
                boolean ok;
                try {
                    ok = plugin.getSnapshotManager().deleteSnapshot(finalSnapshotId);
                } catch (Throwable t) {
                    ok = false;
                    plugin.getLogger().warning("[SnapshotAdminGUI] deleteSnapshot failed: " + t.getMessage());
                }

                boolean finalOk = ok;
                plugin.runMain(player, () -> {
                    if (finalOk) {
                        plugin.msg().send(player, "snapshot_deleted", Map.of());
                        plugin.effects().playUnclaim(player);
                    } else {
                        plugin.msg().send(player, "snapshot_delete_failed", Map.of());
                        plugin.effects().playError(player);
                    }
                    open(player, finalPage);
                });
            });
        }
    }

    // -------------------
    // PDC helpers
    // -------------------

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

    private void tagSnapshotId(ItemStack item, UUID id) {
        if (item == null || id == null) return;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            meta.getPersistentDataContainer().set(keySnapshotId, PersistentDataType.STRING, id.toString());
            item.setItemMeta(meta);
        } catch (Throwable ignored) {}
    }

    private UUID getSnapshotId(ItemStack item) {
        if (item == null) return null;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String s = meta.getPersistentDataContainer().get(keySnapshotId, PersistentDataType.STRING);
            if (s == null || s.isBlank()) return null;
            return UUID.fromString(s);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // -------------------
    // Misc helpers
    // -------------------

    private String clampTitleWithSuffix(String base, String suffix) {
        final int MAX = 32;
        if (base == null) base = "";
        if (suffix == null) suffix = "";

        String combined = base + suffix;
        if (combined.length() <= MAX) return combined;

        if (suffix.length() >= MAX) {
            String cut = suffix.substring(0, MAX);
            return cut.endsWith("§") ? cut.substring(0, MAX - 1) : cut;
        }

        int remainingForBase = MAX - suffix.length();
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
