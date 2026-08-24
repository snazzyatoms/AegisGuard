package com.aegisguard.snapshots;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
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

    private static final int SNAPSHOTS_PER_PAGE = 36; // Slots 0-35; 36-38 are create actions
    private static final long RESTORE_CONFIRM_MS = 15_000L;

    private final NamespacedKey keyAction;
    private final NamespacedKey keySnapshotId;
    private final NamespacedKey keyOperationId;
    private record PendingRestore(UUID snapshotId, EnumSet<RestoreScope> scopes, long expiresAt) { }
    private final Map<UUID, PendingRestore> pendingRestoreConfirm = new ConcurrentHashMap<>();

    public SnapshotAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.keyAction = new NamespacedKey(plugin, "aegis_action");
        this.keySnapshotId = new NamespacedKey(plugin, "aegis_snapshot_id");
        this.keyOperationId = new NamespacedKey(plugin, "aegis_operation_id");
    }

    private EnumSet<RestoreScope> confirmedScopes(UUID playerId, UUID snapshotId) {
        PendingRestore pending = pendingRestoreConfirm.get(playerId);
        if (pending == null || !Objects.equals(pending.snapshotId(), snapshotId)
                || pending.expiresAt() < System.currentTimeMillis()) return null;
        return EnumSet.copyOf(pending.scopes());
    }

    private void markRestoreConfirm(UUID playerId, UUID snapshotId, Set<RestoreScope> scopes) {
        pendingRestoreConfirm.put(playerId, new PendingRestore(snapshotId,
                RestoreScope.normalize(scopes), System.currentTimeMillis() + RESTORE_CONFIRM_MS));
    }

    private void clearRestoreConfirm(UUID playerId) {
        pendingRestoreConfirm.remove(playerId);
    }

    public enum SnapshotFilter {
        ALL, CURRENT_PLOT, CURRENT_OWNER, LAST_24_HOURS, AUTOMATIC,
        RESCUE, BUILD_BACKED, DATA_ONLY, INTEGRITY_ISSUES;

        SnapshotFilter next() {
            SnapshotFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private record BrowserEntry(UUID snapshotId, PlotBuildBackup.BackupInspection inspection) { }

    public static class SnapshotHolder implements InventoryHolder {
        private final int page;
        private final List<UUID> snapshotIds;
        private final SnapshotFilter filter;

        public SnapshotHolder(List<UUID> snapshotIds, int page, SnapshotFilter filter) {
            this.snapshotIds = snapshotIds;
            this.page = page;
            this.filter = filter == null ? SnapshotFilter.ALL : filter;
        }

        public int getPage() { return page; }
        public List<UUID> getSnapshotIds() { return snapshotIds; }
        public SnapshotFilter getFilter() { return filter; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public static class OperationHolder implements InventoryHolder {
        private final List<UUID> operationIds;
        OperationHolder(List<UUID> operationIds) { this.operationIds = List.copyOf(operationIds); }
        List<UUID> operationIds() { return operationIds; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        open(player, 0, SnapshotFilter.ALL);
    }

    public void open(Player player, int page) {
        open(player, page, SnapshotFilter.ALL);
    }

    public void open(Player player, int page, SnapshotFilter filter) {
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
        final SnapshotFilter requestedFilter = filter == null ? SnapshotFilter.ALL : filter;
        Plot context = plugin.store() == null ? null : plugin.store().getPlotAt(player.getLocation());
        UUID contextPlotId = context == null ? null : context.getPlotId();
        UUID contextOwnerId = context == null ? player.getUniqueId() : context.getOwner();
        List<ClaimSnapshot> snapshots;
        try {
            snapshots = plugin.getSnapshotManager().getAllSnapshots();
        } catch (Throwable error) {
            snapshots = new ArrayList<>();
            plugin.getLogger().warning("[SnapshotAdminGUI] getAllSnapshots failed: " + error.getMessage());
        }
        snapshots.sort(Comparator.comparingLong(ClaimSnapshot::getAgeMillis));
        final List<ClaimSnapshot> snapshotList = List.copyOf(snapshots);
        plugin.getSnapshotManager().buildBackup().inspectBatchAsync(snapshotList)
                .whenComplete((inspections, error) -> {
            Map<UUID, PlotBuildBackup.BackupInspection> safeInspections = error == null && inspections != null
                    ? inspections : Map.of();
            List<BrowserEntry> entries = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (ClaimSnapshot snapshot : snapshotList) {
                PlotBuildBackup.BackupInspection inspection = safeInspections.getOrDefault(
                        snapshot.getSnapshotId(), new PlotBuildBackup.BackupInspection(false, 0L, 0));
                if (matchesFilter(snapshot, inspection, requestedFilter,
                        contextPlotId, contextOwnerId, now)) {
                    entries.add(new BrowserEntry(snapshot.getSnapshotId(), inspection));
                }
            }
            int maxPages = (int) Math.ceil((double) entries.size() / SNAPSHOTS_PER_PAGE);
            int fixedPage = requestedPage;
            if (fixedPage < 0) fixedPage = 0;
            if (maxPages > 0 && fixedPage >= maxPages) fixedPage = maxPages - 1;
            int safePages = Math.max(1, maxPages);

            final int finalPage = fixedPage;
            final int finalSafePages = safePages;

            plugin.runMain(player, () -> buildAndOpen(player, entries, finalPage,
                    finalSafePages, requestedFilter));
        });
    }

    private void buildAndOpen(Player player, List<BrowserEntry> entries, int page, int safePages,
                              SnapshotFilter filter) {
        String baseTitle = plugin.gui().title(
                player,
                "snapshots_admin_title",
                "&c&lClaim Snapshots",
                Map.of("PAGE", String.valueOf(page + 1), "PAGES", String.valueOf(safePages))
        );

        // Add a compact page suffix (and clamp)
        String suffix = GUIManager.color(" &7(" + (page + 1) + "/" + safePages + ")");
        String title = clampTitleWithSuffix(baseTitle, suffix);

        List<UUID> ids = entries.stream().map(BrowserEntry::snapshotId).toList();
        SnapshotHolder holder = new SnapshotHolder(ids, page, filter);
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
                            "&7Use Create Snapshot Here or wait",
                            "&7for expansions, merges, or the timer.",
                            "&cThese copies save plot data, not builds."
                    ))
            );
            tagAction(none, "snapshots_none");
            inv.setItem(22, none);
        } else {
            int startIndex = page * SNAPSHOTS_PER_PAGE;

            for (int slot = 0; slot < SNAPSHOTS_PER_PAGE; slot++) {
                int index = startIndex + slot;
                if (index >= ids.size()) break;

                BrowserEntry entry = entries.get(index);
                UUID id = entry.snapshotId();

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

                PlotBuildBackup.BackupInspection inspection = entry.inspection();
                boolean hasBuild = inspection != null && inspection.present();
                String itemLoreKey = hasBuild ? "snapshot_item_lore_builds" : "snapshot_item_lore";
                List<String> lore = new ArrayList<>(plugin.gui().trList(player, itemLoreKey, List.of(
                        "&7Owner: &f{OWNER}",
                        "&7World: &f{WORLD}",
                        "&7Type: &e{TYPE}",
                        "&7Reason: &f{REASON}",
                        "&7Age: &f{AGE}",
                        "&7Triggered By: &f{ACTOR}",
                        "&7Radius: &a{RADIUS}",
                        " ",
                        hasBuild
                                ? "&aBuild: &f" + inspection.bytes() + " bytes / "
                                + inspection.files() + " file(s)"
                                : "&cRestores plot data only — not world blocks.",
                        hasBuild ? "&7Integrity: &f" + inspection.integrity().name() : "&7Integrity: &8N/A",
                        hasBuild ? "&7Integration: &f" + inspection.integrationName() + " "
                                + inspection.integrationVersion() : "&7Integration: &8N/A",
                        hasBuild && !inspection.compatible()
                                ? "&cBuild restore unavailable: " + inspection.detail()
                                : "&7Checksum: &f" + shortChecksum(inspection.aggregateChecksum()),
                        "&cRollback overwrites the live claim.",
                        "&aShift-Left-Click: &7Rollback (confirm twice)",
                        "&cShift-Right-Click: &7Delete this snapshot"
                ), vars));
                // Technical restore safety must remain visible even when an older language value
                // overrides the richer fallback list.
                if (hasBuild && lore.stream().noneMatch(line -> line.contains("Integrity:"))) {
                    lore.add(" ");
                    lore.add("&aBuild: &f" + inspection.bytes() + " bytes / "
                            + inspection.files() + " file(s)");
                    lore.add("&7Integrity: &f" + inspection.integrity().name());
                    lore.add("&7Integration: &f" + inspection.integrationName() + " "
                            + inspection.integrationVersion());
                    lore.add(inspection.compatible()
                            ? "&7Checksum: &f" + shortChecksum(inspection.aggregateChecksum())
                            : "&cBuild restore unavailable: " + inspection.detail());
                } else if (!hasBuild && lore.stream().noneMatch(line -> line.contains("Build backup:"))) {
                    lore.add("&cBuild backup: none (data-only restore available)");
                }

                Material icon = snapshot.getType() == ClaimSnapshot.SnapshotType.PRE_EXPANSION
                        ? Material.SPYGLASS
                        : Material.COMPASS;

                ItemStack item = GUIManager.createItem(icon, itemName, lore);
                tagAction(item, "snapshot_entry");
                tagSnapshotId(item, id);

                inv.setItem(slot, item);
            }
        }

        ItemStack createHere = GUIManager.createItem(
                Material.WRITABLE_BOOK,
                plugin.gui().tr(player, "snapshot_create_here_name", "&aCreate Snapshot Here"),
                plugin.gui().trList(player, createHereLoreKey(), createHereLoreDefault())
        );
        tagAction(createHere, "create_here");
        inv.setItem(36, createHere);

        ItemStack createZones = GUIManager.createItem(
                Material.BEACON,
                plugin.gui().tr(player, "snapshot_create_server_zones_name", "&bSnapshot All Server Zones"),
                plugin.gui().trList(player, "snapshot_create_server_zones_lore", List.of(
                        "&7Copy plot data for every server/spawn plot.",
                        "&aQueues full build backups where enabled and compatible.",
                        " ",
                        "&eClick to create."
                ))
        );
        tagAction(createZones, "create_server_zones");
        inv.setItem(37, createZones);

        ItemStack scope = GUIManager.createItem(
                Material.KNOWLEDGE_BOOK,
                plugin.gui().tr(player, "snapshot_scope_header_name", "&dSnapshot Scope"),
                plugin.gui().trList(player, scopeLoreKey(), scopeLoreDefault())
        );
        tagAction(scope, "snapshot_scope");
        inv.setItem(38, scope);

        ItemStack filterItem = GUIManager.createItem(Material.HOPPER,
                "&eSnapshot Filter: &f" + filterLabel(filter), List.of(
                        "&7Filter by plot, owner, date, type,",
                        "&7build availability, or integrity state.",
                        " ", "&eClick: &fNext filter"));
        tagAction(filterItem, "cycle_filter");
        inv.setItem(39, filterItem);

        long reviewOperations = plugin.getSnapshotManager().getRestoreOperations().stream()
                .filter(operation -> operation.status() == RestoreOperation.Status.PAUSED_REVIEW
                        || operation.status() == RestoreOperation.Status.PARTIAL
                        || operation.status() == RestoreOperation.Status.FAILED).count();
        ItemStack operations = GUIManager.createItem(Material.COMPARATOR,
                "&6Restore Operations", List.of(
                        "&7Review pending, paused, partial,",
                        "&7failed, and completed restores.",
                        "&7Needs review: &f" + reviewOperations,
                        " ", "&eClick to open."));
        tagAction(operations, "restore_operations");
        inv.setItem(40, operations);

        ItemStack storage = GUIManager.createItem(Material.CHEST,
                "&bBackup Storage Check", List.of(
                        "&7Run a safe dry-run integrity, retention,",
                        "&7and orphan-storage report.",
                        "&7No files are changed from this button.",
                        " ", "&eClick to inspect."));
        tagAction(storage, "storage_dry_run");
        inv.setItem(41, storage);

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
        if (e.getInventory().getHolder() instanceof OperationHolder operationHolder) {
            handleOperationClick(player, e, operationHolder);
            return;
        }
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
                case "prev_page" -> { open(player, page - 1, holder.getFilter()); plugin.effects().playMenuFlip(player); return; }
                case "next_page" -> { open(player, page + 1, holder.getFilter()); plugin.effects().playMenuFlip(player); return; }
                case "back_admin" -> { plugin.gui().admin().open(player); plugin.effects().playMenuFlip(player); return; }
                case "close_menu" -> { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
                case "snapshots_none" -> { plugin.effects().playError(player); return; }
                case "create_here" -> { createStandingSnapshot(player, page, holder.getFilter()); return; }
                case "create_server_zones" -> { createServerZoneSnapshots(player, page, holder.getFilter()); return; }
                case "snapshot_scope" -> { return; }
                case "cycle_filter" -> { open(player, 0, holder.getFilter().next()); plugin.effects().playMenuFlip(player); return; }
                case "restore_operations" -> { openOperations(player); plugin.effects().playMenuFlip(player); return; }
                case "storage_dry_run" -> { runStorageDryRun(player, page, holder.getFilter()); return; }
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
            open(player, page, holder.getFilter());
            return;
        }

        // 1.2.6: safety gate destructive actions with shift
        boolean shift = e.getClick().isShiftClick();
        final UUID finalSnapshotId = snapshotId;
        final ClaimSnapshot finalSnapshot = snapshot;
        final int finalPage = page;
        final SnapshotFilter finalFilter = holder.getFilter();

        if (e.getClick().isLeftClick()) {
            if (!shift) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.gui().tr(player, "snapshot_rollback_hint", "&eTip: &7Shift-Left-Click to rollback this snapshot.")));
                plugin.effects().playError(player);
                return;
            }

            EnumSet<RestoreScope> confirmed = confirmedScopes(player.getUniqueId(), finalSnapshotId);
            if (plugin.getConfig().getBoolean("snapshots.require_restore_confirmation", true)
                    && confirmed == null) {
                prepareRestoreConfirmation(player, finalSnapshot, finalSnapshotId);
                return;
            }

            plugin.effects().playMenuFlip(player);
            clearRestoreConfirm(player.getUniqueId());
            runRollbackOnPlotThread(player, finalSnapshot, finalSnapshotId, finalPage, finalFilter,
                    confirmed == null ? EnumSet.of(RestoreScope.FULL_DATA, RestoreScope.BUILD) : confirmed);
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
            plugin.getSnapshotManager().deleteSnapshotDurableAsync(finalSnapshotId)
                    .whenComplete((ok, error) -> {
                plugin.runMain(player, () -> {
                    if (error == null && Boolean.TRUE.equals(ok)) {
                        plugin.msg().send(player, "snapshot_deleted", Map.of());
                        plugin.effects().playUnclaim(player);
                    } else {
                        plugin.msg().send(player, "snapshot_delete_failed", Map.of());
                        plugin.effects().playError(player);
                    }
                    open(player, finalPage, finalFilter);
                });
            });
        }
    }

    private void createStandingSnapshot(Player player, int page, SnapshotFilter filter) {
        Plot plot = plugin.store() == null ? null : plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.gui().tr(player, "snapshot_create_no_plot",
                            "&cStand inside a plot to snapshot it.")));
            plugin.effects().playError(player);
            return;
        }
        plugin.getSnapshotManager().createSnapshotDurableAsync(plot, ClaimSnapshot.SnapshotType.MANUAL,
                "Staff menu snapshot", player.getUniqueId()).whenComplete((created, error) ->
                plugin.runMain(player, () -> {
                    if (error != null || created == null) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                plugin.gui().tr(player, "snapshot_create_failed",
                                        "&cSnapshot data could not be saved. Nothing was acknowledged as complete.")));
                        plugin.effects().playError(player);
                        open(player, page, filter);
                        return;
                    }
                    PlotBuildBackup.CaptureResult capture = plugin.getSnapshotManager().buildBackup().preview(created);
                    String key = "snapshot_created_here";
                    String fallback = "&aSaved plot-data snapshot. Builds were not copied.";
                    if (capture == PlotBuildBackup.CaptureResult.QUEUED) {
                        key = "snapshot_created_here_builds";
                        fallback = "&aSaved plot data and queued a WorldEdit build backup.";
                    } else if (capture == PlotBuildBackup.CaptureResult.SKIPPED_VOLUME) {
                        key = "snapshot_created_here_too_large";
                        fallback = "&aSaved plot data. Build copy skipped: plot exceeds max_volume.";
                    }
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.gui().tr(player, key, fallback)));
                    plugin.effects().playConfirm(player);
                    open(player, page, filter);
                }));
    }

    private void createServerZoneSnapshots(Player player, int page, SnapshotFilter filter) {
        plugin.getSnapshotManager().createServerZoneSnapshotsDurableAsync(
                player.getUniqueId(), "Staff menu server-zone snapshot", ClaimSnapshot.SnapshotType.MANUAL)
                .whenComplete((count, error) -> plugin.runMain(player, () -> {
                    if (error != null || count == null || count <= 0) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                plugin.gui().tr(player, "snapshot_create_no_server_zones",
                                        "&cNo server plots were found, or the snapshots could not be saved.")));
                        plugin.effects().playError(player);
                        return;
                    }
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.gui().tr(player, "snapshot_created_server_zones",
                                    "&aSaved {COUNT} server-zone snapshots. Full build backups were queued where enabled and compatible.",
                                    Map.of("COUNT", String.valueOf(count)))));
                    plugin.effects().playConfirm(player);
                    open(player, page, filter);
                }));
    }

    private void openOperations(Player player) {
        List<RestoreOperation> operations = plugin.getSnapshotManager().getRestoreOperations();
        List<UUID> ids = operations.stream().limit(45).map(RestoreOperation::operationId).toList();
        OperationHolder holder = new OperationHolder(ids);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                GUIManager.color("&6&lRestore Operations"));
        ItemStack filler = GUIManager.getFiller();
        for (int slot = 0; slot < 54; slot++) inventory.setItem(slot, filler);
        if (ids.isEmpty()) {
            ItemStack none = GUIManager.createItem(Material.BARRIER, "&cNo Restore Operations",
                    List.of("&7No durable restoration history exists yet."));
            tagAction(none, "operation_none");
            inventory.setItem(22, none);
        } else {
            for (int slot = 0; slot < ids.size(); slot++) {
                RestoreOperation operation = plugin.getSnapshotManager().getRestoreOperation(ids.get(slot));
                if (operation == null) continue;
                Material material = switch (operation.status()) {
                    case COMPLETE -> Material.LIME_CONCRETE;
                    case PARTIAL, PAUSED_REVIEW -> Material.ORANGE_CONCRETE;
                    case FAILED -> Material.RED_CONCRETE;
                    case RELEASED -> Material.GRAY_CONCRETE;
                    default -> Material.YELLOW_CONCRETE;
                };
                List<String> lore = new ArrayList<>();
                lore.add("&7Operation: &f" + operation.operationId());
                lore.add("&7Plot: &f" + operation.plotId());
                lore.add("&7Snapshot: &f" + operation.snapshotId());
                lore.add("&7Rescue: &f" + value(operation.rescueSnapshotId()));
                lore.add("&7Scopes: &f" + operation.scopes());
                lore.add("&7Data: &f" + operation.dataResult());
                lore.add("&7Build: &f" + operation.buildResult());
                lore.add("&7Tiles: &a" + operation.completedBuildTiles().size()
                        + " complete &e" + operation.pendingBuildTiles().size()
                        + " pending &c" + operation.failedBuildTiles().size() + " failed");
                lore.add("&7Detail: &f" + operation.detail());
                if (operation.status() == RestoreOperation.Status.PARTIAL
                        || operation.status() == RestoreOperation.Status.PAUSED_REVIEW) {
                    lore.add(" ");
                    lore.add("&aShift-Left: &fRetry incomplete work");
                    lore.add("&cShift-Right: &fRelease maintenance lock");
                }
                ItemStack item = GUIManager.createItem(material,
                        "&f" + operation.status() + " &7Restore", lore);
                tagAction(item, "operation_entry");
                tagOperationId(item, operation.operationId());
                inventory.setItem(slot, item);
            }
        }
        ItemStack back = GUIManager.createItem(Material.ARROW, "&fBack to Snapshots",
                List.of("&7Return to the snapshot browser."));
        tagAction(back, "operations_back");
        inventory.setItem(49, back);
        player.openInventory(inventory);
    }

    private void handleOperationClick(Player player, InventoryClickEvent event, OperationHolder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (!plugin.isAdmin(player)) {
            player.closeInventory();
            plugin.effects().playError(player);
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        String action = getAction(clicked);
        if ("operations_back".equals(action)) {
            open(player);
            return;
        }
        if (!"operation_entry".equals(action)) return;
        UUID operationId = getOperationId(clicked);
        RestoreOperation operation = plugin.getSnapshotManager().getRestoreOperation(operationId);
        if (operation == null) {
            player.sendMessage(ChatColor.RED + "Restore operation no longer exists.");
            openOperations(player);
            return;
        }
        if (!event.getClick().isShiftClick()) {
            player.sendMessage(ChatColor.YELLOW + "Operation " + operation.operationId()
                    + ": " + operation.detail());
            return;
        }
        if (event.getClick().isLeftClick()) {
            player.closeInventory();
            plugin.getSnapshotManager().retryRestore(operationId, player.getUniqueId())
                    .whenComplete((restore, error) -> plugin.runMain(player, () -> {
                        if (error != null || restore == null) {
                            player.sendMessage(ChatColor.RED + "Restore retry failed to start.");
                        } else {
                            player.sendMessage(ChatColor.YELLOW + restore.detail());
                        }
                        openOperations(player);
                    }));
        } else if (event.getClick().isRightClick()) {
            plugin.getSnapshotManager().releaseRestoreLockAsync(operationId)
                    .whenComplete((released, error) -> plugin.runMain(player, () -> {
                        player.sendMessage(Boolean.TRUE.equals(released)
                                ? ChatColor.GREEN + "Maintenance lock released after staff review."
                                : ChatColor.RED + "The maintenance lock could not be released.");
                        openOperations(player);
                    }));
        }
    }

    private void runStorageDryRun(Player player, int page, SnapshotFilter filter) {
        player.sendMessage(ChatColor.YELLOW + "Inspecting build-backup storage asynchronously...");
        plugin.getSnapshotManager().buildBackup().maintainStorageAsync(true)
                .whenComplete((report, error) -> plugin.runMain(player, () -> {
                    if (error != null || report == null) {
                        player.sendMessage(ChatColor.RED + "Backup storage inspection failed: "
                                + (error == null ? "unknown error" : error.getMessage()));
                    } else {
                        player.sendMessage(ChatColor.AQUA + "Build storage: " + report.totalBytes()
                                + " / " + report.configuredLimitBytes() + " bytes; manifests="
                                + report.manifests() + ", corrupt=" + report.corruptBackups()
                                + ", missing=" + report.missingBackups() + ", incompatible="
                                + report.incompatibleBackups() + ", orphans="
                                + report.orphanFiles() + ", protected=" + report.protectedBackups()
                                + ", would prune=" + report.prunedBackups() + ".");
                        report.details().stream().limit(5).forEach(detail ->
                                player.sendMessage(ChatColor.GRAY + "- " + detail));
                    }
                    open(player, page, filter);
                }));
    }

    private static boolean matchesFilter(ClaimSnapshot snapshot,
                                         PlotBuildBackup.BackupInspection inspection,
                                         SnapshotFilter filter, UUID contextPlotId,
                                         UUID contextOwnerId, long now) {
        if (snapshot == null) return false;
        return switch (filter) {
            case ALL -> true;
            case CURRENT_PLOT -> contextPlotId != null && contextPlotId.equals(snapshot.getPlotId());
            case CURRENT_OWNER -> contextOwnerId != null && contextOwnerId.equals(snapshot.getOwner());
            case LAST_24_HOURS -> now - snapshot.getTimestamp() <= 86_400_000L;
            case AUTOMATIC -> snapshot.getType() == ClaimSnapshot.SnapshotType.AUTOMATIC_PLAYER
                    || snapshot.getType() == ClaimSnapshot.SnapshotType.AUTOMATIC_SERVER_ZONE
                    || snapshot.getType() == ClaimSnapshot.SnapshotType.SCHEDULED;
            case RESCUE -> snapshot.getType() == ClaimSnapshot.SnapshotType.PRE_RESTORE_RESCUE;
            case BUILD_BACKED -> inspection != null && inspection.present();
            case DATA_ONLY -> inspection == null || !inspection.present();
            case INTEGRITY_ISSUES -> inspection != null && inspection.present()
                    && (inspection.integrity() != PlotBuildBackup.IntegrityStatus.VALID
                    || !inspection.compatible());
        };
    }

    private static String filterLabel(SnapshotFilter filter) {
        return switch (filter) {
            case ALL -> "All";
            case CURRENT_PLOT -> "Current Plot";
            case CURRENT_OWNER -> "Current Owner";
            case LAST_24_HOURS -> "Last 24 Hours";
            case AUTOMATIC -> "Automatic Types";
            case RESCUE -> "Rescue Snapshots";
            case BUILD_BACKED -> "Build-Backed";
            case DATA_ONLY -> "Data-Only";
            case INTEGRITY_ISSUES -> "Integrity Issues";
        };
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

    private void tagOperationId(ItemStack item, UUID id) {
        if (item == null || id == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(keyOperationId, PersistentDataType.STRING, id.toString());
        item.setItemMeta(meta);
    }

    private UUID getOperationId(ItemStack item) {
        if (item == null) return null;
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            String value = meta.getPersistentDataContainer().get(keyOperationId, PersistentDataType.STRING);
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (RuntimeException ignored) {
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

    private static String shortChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) return "none";
        return checksum.length() <= 12 ? checksum : checksum.substring(0, 12) + "…";
    }

    private static String value(Object value) {
        return value == null ? "none" : String.valueOf(value);
    }

    private void prepareRestoreConfirmation(Player player, ClaimSnapshot snapshot, UUID snapshotId) {
        EnumSet<RestoreScope> full = EnumSet.of(RestoreScope.FULL_DATA, RestoreScope.BUILD);
        plugin.getSnapshotManager().previewAsync(snapshotId, full).whenComplete((fullPreview, fullError) -> {
            if (fullError == null && fullPreview != null && fullPreview.ready()) {
                plugin.runMain(player, () -> showRestoreConfirmation(player, snapshot, fullPreview, full));
                return;
            }
            EnumSet<RestoreScope> dataOnly = EnumSet.of(RestoreScope.FULL_DATA);
            plugin.getSnapshotManager().previewAsync(snapshotId, dataOnly)
                    .whenComplete((dataPreview, dataError) -> plugin.runMain(player, () -> {
                        if (dataError != null || dataPreview == null || !dataPreview.ready()) {
                            RestorePreview refusal = fullPreview != null ? fullPreview : dataPreview;
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                    "&cRestore preflight refused this operation: &f"
                                            + (refusal == null ? "Preview unavailable"
                                            : refusal.preflightMessage())));
                            plugin.effects().playError(player);
                            return;
                        }
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                "&6Full build restore is unavailable: &f"
                                        + (fullPreview == null ? "Build preview unavailable"
                                        : fullPreview.preflightMessage())));
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                "&eThe confirmation has safely changed to DATA-ONLY. "
                                        + "Shift-Left again within 15 seconds to continue."));
                        showRestoreConfirmation(player, snapshot, dataPreview, dataOnly);
                    }));
        });
    }

    private void showRestoreConfirmation(Player player, ClaimSnapshot snapshot,
                                         RestorePreview preview, Set<RestoreScope> scopes) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.gui().tr(player, "snapshot_restore_summary",
                        "&eRestore summary: &f{TYPE} &7| &f{REASON} &7| age &f{AGE}&7. Shift-Left again to confirm.",
                        Map.of("TYPE", snapshot.getType().name(),
                                "REASON", snapshot.getReason() == null || snapshot.getReason().isBlank()
                                        ? "No reason recorded" : snapshot.getReason(),
                                "AGE", formatAge(snapshot.getAgeMillis())))));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Scopes: &f" + RestoreScope.normalize(scopes)
                        + " &7| Owner: &f" + preview.currentOwnerName() + " &8-> &f"
                        + preview.snapshotOwnerName() + " &7| World: &f" + preview.worldName()
                        + " &7| Bounds: &f" + preview.x1() + "," + preview.z1() + " to "
                        + preview.x2() + "," + preview.z2()));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Build backup: &f" + (preview.buildBackupPresent()
                        ? preview.buildBackupBytes() + " bytes / " + preview.buildBackupFiles() + " file(s)"
                        : "none") + " &7| Integrity: &f" + preview.buildIntegrity()
                        + " &7| Compatible: &f" + preview.buildCompatible()
                        + " &7| Destination safe: &f" + preview.buildDestinationSafe()));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&7Format: &f" + preview.buildFormat() + " &7| Integration: &f"
                        + preview.buildIntegration() + " " + preview.buildIntegrationVersion()
                        + " &7| Checksum: &f" + shortChecksum(preview.buildChecksum())
                        + " &7| Chunks: &f" + preview.estimatedChunks()));
        markRestoreConfirm(player.getUniqueId(), snapshot.getSnapshotId(), scopes);
        plugin.effects().playMenuFlip(player);
    }

    private void runRollbackOnPlotThread(Player player, ClaimSnapshot snapshot, UUID snapshotId,
                                         int page, SnapshotFilter filter,
                                         Set<RestoreScope> scopes) {
        plugin.getSnapshotManager().restoreAsync(snapshotId, player.getUniqueId(),
                scopes).whenComplete((result, error) -> {
            plugin.runMain(player, () -> {
                if (error == null && result != null && result.dataRestored()) {
                    if (result.status() == SnapshotManager.RestoreStatus.PARTIAL
                            || result.status() == SnapshotManager.RestoreStatus.BUILD_PARTIALLY_QUEUED
                            || result.status() == SnapshotManager.RestoreStatus.BUILD_UNAVAILABLE) {
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                plugin.gui().tr(player, "snapshot_rollback_partial",
                                        "&6Restore is partial; the plot remains maintenance-locked for staff review.")));
                    } else {
                        plugin.msg().send(player, "snapshot_rollback_success",
                                Map.of("ID", snapshot.getPlotId().toString()));
                    }
                    plugin.effects().playConfirm(player);
                } else {
                    plugin.msg().send(player, "snapshot_rollback_failed", Map.of());
                    plugin.effects().playError(player);
                }
                open(player, page, filter);
            });
        });
    }

    private BuildBackupService backup() {
        return plugin.getSnapshotManager() == null ? null : plugin.getSnapshotManager().buildBackup();
    }

    private String createHereLoreKey() {
        BuildBackupService backup = backup();
        if (backup != null && backup.isReady()) return "snapshot_create_here_lore_builds";
        if (backup != null && backup.isConfiguredOn()) return "snapshot_create_here_lore_need_we";
        return "snapshot_create_here_lore";
    }

    private List<String> createHereLoreDefault() {
        BuildBackupService backup = backup();
        if (backup != null && backup.isReady()) {
            return List.of(
                    "&7Copy plot data for the claim you stand in.",
                    "&aAlso copies world blocks via WorldEdit/FAWE.",
                    " ",
                    "&eClick to create."
            );
        }
        if (backup != null && backup.isConfiguredOn()) {
            return List.of(
                    "&7Copy plot data for the claim you stand in.",
                    "&eInstall WorldEdit or FAWE to enable build copies.",
                    " ",
                    "&eClick to create plot-data snapshot."
            );
        }
        return List.of(
                "&7Copy plot data for the claim you stand in.",
                "&7Saves flags, members, bounds, and names.",
                "&cDoes not copy world blocks or builds.",
                " ",
                "&eClick to create.",
                " ",
                "&eEnable snapshots.build_backup and install",
                "&eWorldEdit or FAWE for full plot backups."
        );
    }

    private String scopeLoreKey() {
        BuildBackupService backup = backup();
        if (backup != null && backup.isReady()) return "snapshot_scope_header_lore_builds";
        if (backup != null && backup.isConfiguredOn()) return "snapshot_scope_header_lore_need_we";
        return "snapshot_scope_header_lore";
    }

    private List<String> scopeLoreDefault() {
        BuildBackupService backup = backup();
        if (backup != null && backup.isReady()) {
            return List.of(
                    "&7Claim snapshots save plot records.",
                    "&aBuild copies are on for this plot when size allows.",
                    "&7Restore pastes blocks after claim-data rollback."
            );
        }
        if (backup != null && backup.isConfiguredOn()) {
            return List.of(
                    "&7Claim snapshots save plot records.",
                    "&eInstall WorldEdit or FAWE to copy builds."
            );
        }
        return List.of(
                "&7Claim snapshots save plot records:",
                "&7owner, flags, members, and bounds.",
                " ",
                "&eEnable snapshots.build_backup and install",
                "&eWorldEdit or FAWE for full plot backups."
        );
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
