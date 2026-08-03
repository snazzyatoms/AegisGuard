package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.audit.AuditCategory;
import com.aegisguard.data.Plot;
import com.aegisguard.migration.MigrationManager;
import com.aegisguard.migration.MigrationManager.MigrationOptions;
import com.aegisguard.migration.MigrationManager.MigrationResult;
import com.aegisguard.migration.MigrationManager.SourcePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Claim-migration wizard (GriefPrevention / GriefDefender / Lands → AegisGuard).
 * Clicks are routed by PDC {@code aegis_action} tags — never by fragile slot numbers alone.
 */
public class MigrationAdminGUI {

    private final AegisGuard plugin;
    private final Map<UUID, MigrationOptions> optionsByPlayer = new ConcurrentHashMap<>();

    public MigrationAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class MigrationMainHolder implements InventoryHolder {
        private final SourcePlugin suggested;
        public MigrationMainHolder(SourcePlugin suggested) { this.suggested = suggested; }
        public SourcePlugin getSuggested() { return suggested; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class MigrationPreviewHolder implements InventoryHolder {
        private final SourcePlugin source;
        private final MigrationResult result;
        private final boolean confirmImport;
        public MigrationPreviewHolder(SourcePlugin source, MigrationResult result) {
            this(source, result, false);
        }
        public MigrationPreviewHolder(SourcePlugin source, MigrationResult result, boolean confirmImport) {
            this.source = source;
            this.result = result;
            this.confirmImport = confirmImport;
        }
        public SourcePlugin getSource() { return source; }
        public MigrationResult getResult() { return result; }
        public boolean isConfirmImport() { return confirmImport; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        if (player == null) return;
        clearFocus(getOptions(player));
        open(player, detectSourceAt(player.getLocation()));
    }

    public void openAt(Player player, Location location) {
        if (player == null) return;
        Location target = location == null ? player.getLocation() : location;
        MigrationOptions options = getOptions(player);
        if (target != null) {
            options.worldFilter = target.getWorld() == null ? options.worldFilter : target.getWorld().getName();
            options.focusX = target.getBlockX();
            options.focusZ = target.getBlockZ();
        } else {
            clearFocus(options);
        }
        open(player, detectSourceAt(target));
    }

    public void open(Player player, SourcePlugin suggested) {
        if (player == null) return;
        if (!player.hasPermission("aegis.admin.migrate")) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        String title = plugin.gui().title(player, "migration_gui_title", "&6Migration Wizard");
        Inventory inv = Bukkit.createInventory(new MigrationMainHolder(suggested), 45, title);
        fill(inv);

        MigrationOptions options = getOptions(player);
        String blockingHook = detectBlockingHook(player);
        inv.setItem(4, tagged(GUIManager.createItem(
                blockingHook == null ? Material.RECOVERY_COMPASS : Material.COMPASS,
                tr(player, "migration_scan_name", "&eCurrent Location Scan"),
                currentLocationLore(player, blockingHook, suggested)
        ), "migration_scan_info"));

        Map<SourcePlugin, Integer> slots = new EnumMap<>(SourcePlugin.class);
        slots.put(SourcePlugin.GRIEFPREVENTION, 20);
        slots.put(SourcePlugin.GRIEFDEFENDER, 22);
        slots.put(SourcePlugin.LANDS, 24);

        List<SourcePlugin> available = plugin.migration() == null ? List.of() : plugin.migration().getAvailableSources();
        for (SourcePlugin source : SourcePlugin.values()) {
            boolean detected = available.contains(source);
            boolean recommended = suggested == source;
            Material icon = switch (source) {
                case GRIEFPREVENTION -> Material.GOLDEN_SHOVEL;
                case GRIEFDEFENDER -> Material.SHIELD;
                case LANDS -> Material.MAP;
            };
            String color = recommended ? "&a" : detected ? "&e" : "&7";
            String name = color + tr(player,
                    "migration_source_" + source.name().toLowerCase(Locale.ROOT) + "_name",
                    source.getDisplayName());
            List<String> lore = new ArrayList<>();
            lore.addAll(trList(player, detected ? "migration_source_detected_lore" : "migration_source_missing_lore",
                    detected
                            ? List.of("&7Source data was detected on this server.", "&7Run a dry-run preview before importing.")
                            : List.of("&7No source data detected yet.", "&7Install/enable the plugin, then reopen.")));
            if (recommended) {
                lore.addAll(trList(player, "migration_source_recommended_lore",
                        List.of("&bDetected around your current location.")));
            }
            lore.add(" ");
            lore.add(GUIManager.color(tr(player, "migration_option_trusted_line", "&7Trusted import: {STATE}",
                    Map.of("STATE", stateLabel(player, options.importTrusted)))));
            lore.add(GUIManager.color(tr(player, "migration_option_flags_line", "&7Flag import: {STATE}",
                    Map.of("STATE", stateLabel(player, options.importFlags)))));
            lore.add(GUIManager.color(tr(player, "migration_option_force_line", "&7Force overlap: {STATE}",
                    Map.of("STATE", stateLabel(player, options.forceOverlap)))));
            lore.add(" ");
            lore.addAll(trList(player, "migration_source_click_lore",
                    List.of("&eClick to preview (dry-run).", "&8No claims are written until you confirm import.")));
            inv.setItem(slots.get(source), tagged(GUIManager.createItem(icon, name, lore),
                    "preview_" + source.name().toLowerCase(Locale.ROOT)));
        }

        inv.setItem(29, tagged(toggleItem(player, "migration_toggle_trusted_name", "&bImport Trusted",
                options.importTrusted, Material.PLAYER_HEAD,
                "migration_toggle_trusted_lore",
                List.of("&7Copy trusted/member access from the source.",
                        "&7Use when you want players to keep access.",
                        " ",
                        "&eClick to toggle.")), "toggle_trusted"));
        inv.setItem(31, tagged(toggleItem(player, "migration_toggle_flags_name", "&dImport Flags",
                options.importFlags, Material.COMPARATOR,
                "migration_toggle_flags_lore",
                List.of("&7Copy compatible protection flags when possible.",
                        "&7Use for closer feature parity after import.",
                        " ",
                        "&eClick to toggle.")), "toggle_flags"));
        inv.setItem(33, tagged(toggleItem(player, "migration_toggle_force_name", "&cForce Overlap",
                options.forceOverlap, Material.TNT,
                "migration_toggle_force_lore",
                List.of("&7Allow import even when an Aegis claim overlaps.",
                        "&cCaution: can create conflicting claim geometry.",
                        "&cPrefer leaving this OFF unless recovering a broken world.",
                        " ",
                        "&eClick to toggle.")), "toggle_force"));

        inv.setItem(37, tagged(GUIManager.createItem(Material.TARGET,
                tr(player, "migration_focus_name", "&eFocused Claim Filter"),
                focusedClaimLore(player, options)), "clear_focus"));
        inv.setItem(39, tagged(GUIManager.createItem(Material.BLAZE_ROD,
                tr(player, "migration_wand_name", "&6Get Migration Wand"),
                trList(player, "migration_wand_lore", List.of(
                        "&7Right-click in another plugin's protected area.",
                        "&7Opens this wizard with that source preselected.",
                        " ",
                        "&eClick to receive the wand."))), "give_wand"));
        inv.setItem(40, tagged(GUIManager.createItem(Material.WRITABLE_BOOK,
                tr(player, "migration_open_doctor_name", "&bOpen Territory Doctor"),
                trList(player, "migration_open_doctor_lore", List.of(
                        "&7After importing, scan for overlaps,",
                        "&7orphaned contracts, and repairable state.",
                        " ",
                        "&eClick to open Doctor."))), "open_doctor"));
        inv.setItem(41, tagged(GUIManager.createItem(
                Material.BARRIER,
                tr(player, "button_exit", "&c✖ Close"),
                trList(player, "exit_lore", List.of("&7Close this menu."))
        ), "close_menu"));
        inv.setItem(44, tagged(GUIManager.createItem(Material.ARROW,
                tr(player, "button_back_admin", "&fBack"),
                trList(player, "back_admin_lore", List.of("&7Return to the admin menu."))), "back_admin"));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void openLoading(Player player, SourcePlugin source) {
        String title = plugin.gui().title(player, "migration_loading_title", "&6Scanning Migration");
        Inventory inv = Bukkit.createInventory(new MigrationMainHolder(source), 27, title);
        fill(inv);
        inv.setItem(13, tagged(GUIManager.createItem(Material.CLOCK,
                tr(player, "migration_scanning_name", "&eScanning {SOURCE}",
                        Map.of("SOURCE", source.getDisplayName())),
                trList(player, "migration_scanning_lore", List.of(
                        "&7Running a dry-run preview.",
                        "&7This may take a moment on larger servers.",
                        " ",
                        "&8No claims are written during preview."))), "migration_loading"));
        inv.setItem(18, tagged(GUIManager.createItem(
                Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to the previous menu."))
        ), "back_wizard"));
        inv.setItem(26, tagged(GUIManager.createItem(
                Material.BARRIER,
                tr(player, "button_exit", "&c✖ Close"),
                trList(player, "exit_lore", List.of("&7Close this menu."))
        ), "close_menu"));
        player.openInventory(inv);
    }

    public void openPreview(Player player, SourcePlugin source, MigrationResult result) {
        openPreview(player, source, result, false);
    }

    public void openPreview(Player player, SourcePlugin source, MigrationResult result, boolean confirmImport) {
        String title = plugin.gui().title(player,
                confirmImport ? "migration_confirm_title" : "migration_preview_title",
                confirmImport ? "&cConfirm Migration Import" : "&6Migration Preview");
        Inventory inv = Bukkit.createInventory(new MigrationPreviewHolder(source, result, confirmImport), 45, title);
        fill(inv);

        Map<String, String> counts = Map.of(
                "SOURCE", source.getDisplayName(),
                "FOUND", Integer.toString(result.totalFound),
                "IMPORT", Integer.toString(result.imported),
                "OVERLAP", Integer.toString(result.skippedOverlap),
                "FILTER", Integer.toString(result.skippedFiltered),
                "ERRORS", Integer.toString(result.skippedError),
                "MS", Long.toString(result.durationMs)
        );
        inv.setItem(4, tagged(GUIManager.createItem(Material.BOOK,
                tr(player, "migration_preview_summary_name", "&e{SOURCE} Preview", counts),
                trList(player, "migration_preview_summary_lore", List.of(
                        "&7Claims found: &f{FOUND}",
                        "&7Would import: &a{IMPORT}",
                        "&7Overlap skips: &e{OVERLAP}",
                        "&7Filter skips: &e{FILTER}",
                        "&7Errors: &c{ERRORS}",
                        "&7Duration: &f{MS}ms"
                ), counts)), "preview_summary"));

        inv.setItem(13, tagged(GUIManager.createItem(Material.TARGET,
                tr(player, "migration_focused_claim_name", "&bFocused Claim"),
                focusedPreviewLore(player, result)), "focused_claim_info"));

        if (confirmImport) {
            inv.setItem(20, tagged(GUIManager.createItem(Material.RED_CONCRETE,
                    tr(player, "migration_confirm_import_name", "&cConfirm Live Import"),
                    trList(player, "migration_confirm_import_lore", List.of(
                            "&7This writes new AegisGuard claims from the source.",
                            "&cIrreversible without snapshots / manual cleanup.",
                            "&7Run a Doctor scan afterward to verify health.",
                            " ",
                            "&cClick to import now."))), "confirm_import"));
            inv.setItem(24, tagged(GUIManager.createItem(Material.YELLOW_CONCRETE,
                    tr(player, "migration_cancel_confirm_name", "&eCancel"),
                    trList(player, "migration_cancel_confirm_lore", List.of(
                            "&7Return to the dry-run preview",
                            "&7without importing anything."))), "cancel_confirm"));
        } else {
            inv.setItem(20, tagged(GUIManager.createItem(Material.EMERALD_BLOCK,
                    tr(player, "migration_run_import_name", "&aRun Import"),
                    trList(player, "migration_run_import_lore", List.of(
                            "&7Import this source using the",
                            "&7currently selected options.",
                            " ",
                            "&eClick to review a final confirmation.",
                            "&cLive import changes claim data."))), "request_import"));
            inv.setItem(24, tagged(GUIManager.createItem(Material.COMPASS,
                    tr(player, "migration_back_wizard_name", "&bBack to Wizard"),
                    trList(player, "migration_back_wizard_lore", List.of(
                            "&7Change preview options or",
                            "&7re-scan another source."))), "back_wizard"));
        }

        List<String> errorLore = new ArrayList<>();
        if (result.errors == null || result.errors.isEmpty()) {
            errorLore.addAll(trList(player, "migration_preview_no_errors_lore",
                    List.of("&aNo preview errors were reported.")));
        } else {
            errorLore.addAll(trList(player, "migration_preview_errors_header_lore",
                    List.of("&ePreview notes / errors:")));
            for (int i = 0; i < Math.min(5, result.errors.size()); i++) {
                errorLore.add(GUIManager.color("&7- " + trim(result.errors.get(i), 48)));
            }
        }
        inv.setItem(31, tagged(GUIManager.createItem(Material.PAPER,
                tr(player, "migration_preview_notes_name", "&fPreview Notes"),
                errorLore), "preview_notes"));

        inv.setItem(40, tagged(GUIManager.createItem(
                Material.BARRIER,
                tr(player, "button_exit", "&c✖ Close"),
                trList(player, "exit_lore", List.of("&7Close this menu."))
        ), "close_menu"));
        inv.setItem(44, tagged(GUIManager.createItem(Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "migration_back_sources_lore",
                        List.of("&7Return to migration sources."))), "back_wizard"));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, MigrationMainHolder holder) {
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getCurrentItem() == null) return;
        if (!player.hasPermission("aegis.admin.migrate")) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        String action = plugin.gui().getAction(e.getCurrentItem());
        if (action == null || action.isBlank()) return;

        MigrationOptions options = getOptions(player);
        switch (action) {
            case "toggle_trusted" -> { options.importTrusted = !options.importTrusted; open(player, holder.getSuggested()); }
            case "toggle_flags" -> { options.importFlags = !options.importFlags; open(player, holder.getSuggested()); }
            case "toggle_force" -> { options.forceOverlap = !options.forceOverlap; open(player, holder.getSuggested()); }
            case "clear_focus" -> { clearFocus(options); open(player, holder.getSuggested()); }
            case "give_wand" -> giveMigrationWand(player);
            case "open_doctor" -> {
                plugin.effects().playMenuFlip(player);
                plugin.gui().doctor().open(player);
            }
            case "close_menu" -> { player.closeInventory(); plugin.effects().playMenuClose(player); }
            case "back_admin" -> { plugin.gui().admin().open(player); plugin.effects().playMenuFlip(player); }
            case "back_wizard" -> open(player, holder.getSuggested());
            case "preview_griefprevention" -> preview(player, SourcePlugin.GRIEFPREVENTION);
            case "preview_griefdefender" -> preview(player, SourcePlugin.GRIEFDEFENDER);
            case "preview_lands" -> preview(player, SourcePlugin.LANDS);
            default -> { }
        }
    }

    public void handlePreviewClick(Player player, InventoryClickEvent e, MigrationPreviewHolder holder) {
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getCurrentItem() == null) return;
        if (!player.hasPermission("aegis.admin.migrate")) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        String action = plugin.gui().getAction(e.getCurrentItem());
        if (action == null || action.isBlank()) return;

        switch (action) {
            case "request_import" -> openPreview(player, holder.getSource(), holder.getResult(), true);
            case "confirm_import" -> startImport(player, holder.getSource());
            case "cancel_confirm" -> openPreview(player, holder.getSource(), holder.getResult(), false);
            case "back_wizard" -> open(player, holder.getSource());
            case "close_menu" -> { player.closeInventory(); plugin.effects().playMenuClose(player); }
            default -> { }
        }
    }

    public ItemStack createMigrationWand() {
        return createMigrationWand(null);
    }

    public ItemStack createMigrationWand(Player player) {
        ItemStack wand = GUIManager.createItem(Material.BLAZE_ROD,
                tr(player, "migration_wand_item_name", "&6Migration Wand"),
                trList(player, "migration_wand_item_lore", List.of(
                        "&7Right-click a protected area from",
                        "&7another plugin to open the",
                        "&7AegisGuard migration wizard."
                )));
        plugin.gui().tagAction(wand, "migration_wand");
        return wand;
    }

    public boolean isMigrationWand(ItemStack item) {
        if (item == null) return false;
        String action = plugin.gui().getAction(item);
        return action != null && action.equalsIgnoreCase("migration_wand");
    }

    public void giveMigrationWand(Player player) {
        player.getInventory().addItem(createMigrationWand(player));
        player.sendMessage(GUIManager.color(tr(player, "migration_wand_received",
                "&aYou received the Migration Wand.")));
        plugin.effects().playConfirm(player);
    }

    private void preview(Player player, SourcePlugin source) {
        if (plugin.migration() == null) {
            player.sendMessage(GUIManager.color(tr(player, "migration_unavailable",
                    "&cMigration system is unavailable.")));
            return;
        }

        openLoading(player, source);
        MigrationOptions options = copyOptions(getOptions(player));
        plugin.migration().previewMigration(player, source, options)
                .whenComplete((result, error) -> plugin.runMain(player, () -> {
                    if (error != null || result == null) {
                        player.sendMessage(GUIManager.color(tr(player, "migration_preview_failed",
                                "&cMigration preview failed: {ERROR}",
                                Map.of("ERROR", error == null ? "unknown error" : trim(error.getMessage(), 96)))));
                        plugin.effects().playError(player);
                        open(player, source);
                        return;
                    }
                    openPreview(player, source, result, false);
                }));
    }

    private void startImport(Player player, SourcePlugin source) {
        if (plugin.migration() == null) {
            player.sendMessage(GUIManager.color(tr(player, "migration_unavailable",
                    "&cMigration system is unavailable.")));
            return;
        }

        player.closeInventory();
        player.sendMessage(GUIManager.color(tr(player, "migration_import_starting",
                "&6[AegisGuard] &7Starting live import for &e{SOURCE}&7...",
                Map.of("SOURCE", source.getDisplayName()))));
        plugin.effects().playConfirm(player);

        MigrationOptions options = copyOptions(getOptions(player));
        plugin.migration().startMigration(player, source, options)
                .whenComplete((result, error) -> plugin.runMain(player, () -> {
                    if (error != null) {
                        player.sendMessage(GUIManager.color(tr(player, "migration_import_failed",
                                "&cMigration failed: {ERROR}",
                                Map.of("ERROR", trim(error.getMessage(), 96)))));
                        plugin.effects().playError(player);
                    } else {
                        player.sendMessage(GUIManager.color(tr(player, "migration_import_finished",
                                "&aMigration finished. Opening summary...")));
                        if (plugin.audit() != null) {
                            plugin.audit().record(AuditCategory.MIGRATION, player, source.getDisplayName(),
                                    "Imported claims from " + source.getDisplayName()
                                            + " (imported=" + (result == null ? 0 : result.imported) + ")");
                        }
                        openPreview(player, source, result, false);
                    }
                }));
    }

    private List<String> currentLocationLore(Player player, String blockingHook, SourcePlugin suggested) {
        List<String> lore = new ArrayList<>();
        MigrationOptions options = getOptions(player);
        if (blockingHook == null) {
            lore.addAll(trList(player, "migration_scan_clear_lore", List.of(
                    "&7No external protection hook is",
                    "&7reporting protection at your location.")));
        } else {
            lore.addAll(trList(player, "migration_scan_detected_lore", List.of(
                    "&7Detected source: &e{HOOK}",
                    "&7Suggested migration source: &a{SOURCE}"
            ), Map.of(
                    "HOOK", blockingHook,
                    "SOURCE", suggested == null ? "-" : suggested.getDisplayName()
            )));
        }

        try {
            Plot plot = plugin.store().getPlotAt(player.getLocation());
            if (plot != null) {
                String owner = plot.getOwnerName() == null ? "Unknown" : plot.getOwnerName();
                lore.add(" ");
                lore.addAll(trList(player, "migration_scan_aegis_plot_lore", List.of(
                        "&7Aegis plot here: &fYes",
                        "&7Owner: &f{OWNER}"
                ), Map.of("OWNER", owner)));
            }
        } catch (Throwable ignored) {}

        lore.add(" ");
        if (options.focusX != null && options.focusZ != null) {
            lore.addAll(trList(player, "migration_scan_focus_active_lore", List.of(
                    "&7Focused claim filter: &aACTIVE",
                    "&7Target: &f{X}, {Z}"
            ), Map.of("X", Integer.toString(options.focusX), "Z", Integer.toString(options.focusZ))));
            lore.add(" ");
        }
        lore.addAll(trList(player, "migration_scan_footer_lore", List.of(
                "&eUse the source buttons below to run a dry-run preview.",
                "&8Import never starts until you confirm.")));
        return lore;
    }

    private ItemStack toggleItem(Player player, String nameKey, String nameFallback,
                                 boolean enabled, Material material,
                                 String loreKey, List<String> loreFallback) {
        String name = tr(player, nameKey, nameFallback) + " " + stateLabel(player, enabled);
        return GUIManager.createItem(enabled ? material : Material.GRAY_DYE, name,
                trList(player, loreKey, loreFallback));
    }

    private String stateLabel(Player player, boolean enabled) {
        return tr(player, enabled ? "toggle_on" : "toggle_off", enabled ? "&aON" : "&cOFF");
    }

    private MigrationOptions getOptions(Player player) {
        return optionsByPlayer.computeIfAbsent(player.getUniqueId(), uuid -> new MigrationOptions());
    }

    private MigrationOptions copyOptions(MigrationOptions original) {
        MigrationOptions copy = new MigrationOptions();
        copy.dryRun = original.dryRun;
        copy.forceOverlap = original.forceOverlap;
        copy.importTrusted = original.importTrusted;
        copy.importFlags = original.importFlags;
        copy.worldFilter = original.worldFilter;
        copy.ownerFilter = original.ownerFilter;
        copy.focusX = original.focusX;
        copy.focusZ = original.focusZ;
        return copy;
    }

    private SourcePlugin detectSourceAt(Location location) {
        String hookId = detectBlockingHook(location);
        if (hookId == null) return null;
        String normalized = hookId.toLowerCase(Locale.ROOT);
        if (normalized.contains("griefprevention")) return SourcePlugin.GRIEFPREVENTION;
        if (normalized.contains("griefdefender")) return SourcePlugin.GRIEFDEFENDER;
        if (normalized.contains("lands")) return SourcePlugin.LANDS;
        return null;
    }

    private String detectBlockingHook(Player player) {
        return detectBlockingHook(player == null ? null : player.getLocation());
    }

    private String detectBlockingHook(Location location) {
        try {
            if (plugin.protectionHooks() != null) {
                return plugin.protectionHooks().getBlockingHookId(location);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void fill(Inventory inv) {
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private void clearFocus(MigrationOptions options) {
        if (options == null) return;
        options.focusX = null;
        options.focusZ = null;
        options.worldFilter = null;
    }

    private List<String> focusedClaimLore(Player player, MigrationOptions options) {
        if (options == null || options.focusX == null || options.focusZ == null) {
            return trList(player, "migration_focus_inactive_lore", List.of(
                    "&7No focused external claim.",
                    "&7The preview will scan the entire selected source.",
                    " ",
                    "&eUse the migration wand to target one exact claim.",
                    "&8Click does nothing until a focus is active."));
        }
        List<String> lore = new ArrayList<>(trList(player, "migration_focus_active_lore", List.of(
                "&7Focused coordinates: &f{X}, {Z}",
                "&7World: &f{WORLD}",
                " ",
                "&eClick to clear this exact-claim filter."
        ), Map.of(
                "X", Integer.toString(options.focusX),
                "Z", Integer.toString(options.focusZ),
                "WORLD", options.worldFilter == null || options.worldFilter.isBlank() ? "-" : options.worldFilter
        )));
        return lore;
    }

    private List<String> focusedPreviewLore(Player player, MigrationResult result) {
        if (result == null || result.focusedWorld == null) {
            return trList(player, "migration_focused_none_lore", List.of(
                    "&7No specific focused claim matched.",
                    "&7This preview represents the full selected source."));
        }
        return trList(player, "migration_focused_detail_lore", List.of(
                "&7Owner: &f{OWNER}",
                "&7World: &f{WORLD}",
                "&7Name: &f{NAME}",
                "&7Bounds: &f{BOUNDS}",
                "&7Trusted entries: &f{TRUSTED}",
                "&7Imported flags: &f{FLAGS}"
        ), Map.of(
                "OWNER", trim(result.focusedOwnerName, 28),
                "WORLD", result.focusedWorld,
                "NAME", result.focusedName == null || result.focusedName.isBlank() ? "-" : trim(result.focusedName, 28),
                "BOUNDS", (result.focusedX1 != null && result.focusedZ1 != null
                        && result.focusedX2 != null && result.focusedZ2 != null)
                        ? result.focusedX1 + "," + result.focusedZ1 + " -> " + result.focusedX2 + "," + result.focusedZ2
                        : "-",
                "TRUSTED", Integer.toString(result.focusedTrustedCount),
                "FLAGS", Integer.toString(result.focusedFlagCount)
        ));
    }

    private ItemStack tagged(ItemStack item, String action) {
        plugin.gui().tagAction(item, action);
        return item;
    }

    private String tr(Player player, String key, String fallback) {
        return plugin.gui().tr(player, key, fallback);
    }

    private String tr(Player player, String key, String fallback, Map<String, String> values) {
        return plugin.gui().tr(player, key, fallback, values);
    }

    private List<String> trList(Player player, String key, List<String> fallback) {
        return plugin.gui().trList(player, key, fallback);
    }

    private List<String> trList(Player player, String key, List<String> fallback, Map<String, String> values) {
        return plugin.gui().trList(player, key, fallback, values);
    }

    private String trim(String input, int max) {
        if (input == null || input.isBlank()) return "unknown";
        return input.length() <= max ? input : input.substring(0, max - 3) + "...";
    }
}
