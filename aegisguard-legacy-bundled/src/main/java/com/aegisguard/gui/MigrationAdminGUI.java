package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.migration.MigrationManager;
import com.aegisguard.migration.MigrationManager.MigrationOptions;
import com.aegisguard.migration.MigrationManager.MigrationResult;
import com.aegisguard.migration.MigrationManager.SourcePlugin;
import com.aegisguard.util.CompatMaterial;
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
        public MigrationPreviewHolder(SourcePlugin source, MigrationResult result) {
            this.source = source;
            this.result = result;
        }
        public SourcePlugin getSource() { return source; }
        public MigrationResult getResult() { return result; }
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
        inv.setItem(4, GUIManager.createItem(
                blockingHook == null ? CompatMaterial.resolve("RECOVERY_COMPASS", "COMPASS") : Material.COMPASS,
                "&eCurrent Location Scan",
                currentLocationLore(player, blockingHook, suggested)
        ));

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
            String name = (recommended ? "&a" : detected ? "&e" : "&7") + source.getDisplayName();
            List<String> lore = new ArrayList<>();
            lore.add(GUIManager.color(detected ? "&7Data/source detected." : "&7No source data detected yet."));
            if (recommended) lore.add(GUIManager.color("&bDetected around your current location."));
            lore.add(GUIManager.color("&7Trusted import: " + state(options.importTrusted)));
            lore.add(GUIManager.color("&7Flag import: " + state(options.importFlags)));
            lore.add(GUIManager.color("&7Force overlap: " + state(options.forceOverlap)));
            lore.add(" ");
            lore.add(GUIManager.color("&eClick to preview migration"));
            inv.setItem(slots.get(source), GUIManager.createItem(icon, name, lore));
        }

        inv.setItem(29, toggleItem("&bImport Trusted", options.importTrusted, Material.PLAYER_HEAD));
        inv.setItem(31, toggleItem("&dImport Flags", options.importFlags, Material.COMPARATOR));
        inv.setItem(33, toggleItem("&cForce Overlap", options.forceOverlap, Material.TNT));
        inv.setItem(37, GUIManager.createItem(Material.TARGET, "&eFocused Claim Filter", focusedClaimLore(options)));
        inv.setItem(39, GUIManager.createItem(Material.BLAZE_ROD, "&6Get Migration Wand",
                List.of(GUIManager.color("&7Right-click in another plugin's protected area."),
                        GUIManager.color("&7AegisGuard will open the migration wizard"),
                        GUIManager.color("&7with the detected source preselected."))));
        inv.setItem(40, GUIManager.createItem(Material.WRITABLE_BOOK, "&bRun Doctor Report",
                List.of(GUIManager.color("&7Generate a support report file"),
                        GUIManager.color("&7inside the plugin folder."))));
        inv.setItem(41, GUIManager.createItem(
                Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&c✖ Close"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))
        ));
        inv.setItem(44, GUIManager.createItem(Material.ARROW, "&fBack",
                List.of(GUIManager.color("&7Return to the admin menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void openLoading(Player player, SourcePlugin source) {
        String title = plugin.gui().title(player, "migration_loading_title", "&6Scanning Migration");
        Inventory inv = Bukkit.createInventory(new MigrationMainHolder(source), 27, title);
        fill(inv);
        inv.setItem(18, GUIManager.createItem(
                Material.ARROW,
                plugin.gui().tr(player, "button_back", "&fBack"),
                plugin.gui().trList(player, "back_lore", List.of("&7Return to the previous menu."))
        ));
        inv.setItem(13, GUIManager.createItem(Material.CLOCK, "&eScanning " + source.getDisplayName(),
                List.of(GUIManager.color("&7Running a dry-run preview."),
                        GUIManager.color("&7This may take a moment on larger servers."))));
        inv.setItem(22, GUIManager.createItem(
                Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&c✖ Close"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))
        ));
        player.openInventory(inv);
    }

    public void openPreview(Player player, SourcePlugin source, MigrationResult result) {
        String title = plugin.gui().title(player, "migration_preview_title", "&6Migration Preview");
        Inventory inv = Bukkit.createInventory(new MigrationPreviewHolder(source, result), 45, title);
        fill(inv);

        inv.setItem(4, GUIManager.createItem(Material.BOOK,
                "&e" + source.getDisplayName() + " Preview",
                List.of(
                        GUIManager.color("&7Claims found: &f" + result.totalFound),
                        GUIManager.color("&7Would import: &a" + result.imported),
                        GUIManager.color("&7Overlap skips: &e" + result.skippedOverlap),
                        GUIManager.color("&7Filter skips: &e" + result.skippedFiltered),
                        GUIManager.color("&7Errors: &c" + result.skippedError),
                        GUIManager.color("&7Duration: &f" + result.durationMs + "ms")
                )));
        inv.setItem(13, GUIManager.createItem(Material.TARGET, "&bFocused Claim", focusedPreviewLore(result)));

        List<String> importLore = new ArrayList<>();
        importLore.add(GUIManager.color("&7Import this source using the"));
        importLore.add(GUIManager.color("&7currently selected options."));
        importLore.add(" ");
        importLore.add(GUIManager.color("&eClick to start import"));
        inv.setItem(20, GUIManager.createItem(Material.EMERALD_BLOCK, "&aRun Import", importLore));

        List<String> rerunLore = new ArrayList<>();
        rerunLore.add(GUIManager.color("&7Go back and change preview options"));
        rerunLore.add(GUIManager.color("&7or re-scan another source."));
        inv.setItem(24, GUIManager.createItem(Material.COMPASS, "&bBack to Wizard", rerunLore));

        List<String> errorLore = new ArrayList<>();
        if (result.errors.isEmpty()) {
            errorLore.add(GUIManager.color("&aNo preview errors were reported."));
        } else {
            for (int i = 0; i < Math.min(5, result.errors.size()); i++) {
                errorLore.add(GUIManager.color("&7- " + trim(result.errors.get(i), 48)));
            }
        }
        inv.setItem(31, GUIManager.createItem(Material.PAPER, "&fPreview Notes", errorLore));
        inv.setItem(40, GUIManager.createItem(
                Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&c✖ Close"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))
        ));
        inv.setItem(44, GUIManager.createItem(Material.ARROW, "&fBack", List.of(GUIManager.color("&7Return to migration sources."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, MigrationMainHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int raw = e.getRawSlot();
        if (raw < 0 || raw >= e.getInventory().getSize()) return;

        MigrationOptions options = getOptions(player);

        if (raw == 29) { options.importTrusted = !options.importTrusted; open(player, holder.getSuggested()); return; }
        if (raw == 31) { options.importFlags = !options.importFlags; open(player, holder.getSuggested()); return; }
        if (raw == 33) { options.forceOverlap = !options.forceOverlap; open(player, holder.getSuggested()); return; }
        if (raw == 37) { clearFocus(options); open(player, holder.getSuggested()); return; }
        if (raw == 39) { giveMigrationWand(player); return; }
        if (raw == 40) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            player.performCommand("aegisadmin doctor");
            return;
        }
        if (raw == 41 || raw == 22) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }
        if (raw == 18) {
            open(player, holder.getSuggested());
            return;
        }
        if (raw == 44) { plugin.gui().admin().open(player); return; }

        SourcePlugin source = sourceForSlot(raw);
        if (source == null) return;
        preview(player, source);
    }

    public void handlePreviewClick(Player player, InventoryClickEvent e, MigrationPreviewHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int raw = e.getRawSlot();
        if (raw < 0 || raw >= e.getInventory().getSize()) return;

        if (raw == 20) {
            startImport(player, holder.getSource());
            return;
        }
        if (raw == 40) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }
        if (raw == 24 || raw == 44) {
            open(player, holder.getSource());
        }
    }

    public ItemStack createMigrationWand() {
        ItemStack wand = GUIManager.createItem(Material.BLAZE_ROD, "&6Migration Wand",
                List.of(
                        GUIManager.color("&7Right-click a protected area from"),
                        GUIManager.color("&7another plugin to open the"),
                        GUIManager.color("&7AegisGuard migration wizard.")
                ));
        plugin.gui().tagAction(wand, "migration_wand");
        return wand;
    }

    public boolean isMigrationWand(ItemStack item) {
        if (item == null) return false;
        String action = plugin.gui().getAction(item);
        return action != null && action.equalsIgnoreCase("migration_wand");
    }

    public void giveMigrationWand(Player player) {
        player.getInventory().addItem(createMigrationWand());
        player.sendMessage(GUIManager.color("&aYou received the Migration Wand."));
        plugin.effects().playConfirm(player);
    }

    private void preview(Player player, SourcePlugin source) {
        if (plugin.migration() == null) {
            player.sendMessage(GUIManager.color("&cMigration system is unavailable."));
            return;
        }

        openLoading(player, source);
        MigrationOptions options = copyOptions(getOptions(player));
        plugin.migration().previewMigration(player, source, options)
                .whenComplete((result, error) -> plugin.runMain(player, () -> {
                    if (error != null || result == null) {
                        player.sendMessage(GUIManager.color("&cMigration preview failed: "
                                + (error == null ? "unknown error" : trim(error.getMessage(), 96))));
                        plugin.effects().playError(player);
                        open(player, source);
                        return;
                    }
                    openPreview(player, source, result);
                }));
    }

    private void startImport(Player player, SourcePlugin source) {
        if (plugin.migration() == null) {
            player.sendMessage(GUIManager.color("&cMigration system is unavailable."));
            return;
        }

        player.closeInventory();
        player.sendMessage(GUIManager.color("&6[AegisGuard] &7Starting live import for &e" + source.getDisplayName() + "&7..."));
        plugin.effects().playConfirm(player);

        MigrationOptions options = copyOptions(getOptions(player));
        plugin.migration().startMigration(player, source, options)
                .whenComplete((result, error) -> plugin.runMain(player, () -> {
                    if (error != null) {
                        player.sendMessage(GUIManager.color("&cMigration failed: " + trim(error.getMessage(), 96)));
                        plugin.effects().playError(player);
                    } else {
                        player.sendMessage(GUIManager.color("&aMigration finished. Opening summary..."));
                        openPreview(player, source, result);
                    }
                }));
    }

    private List<String> currentLocationLore(Player player, String blockingHook, SourcePlugin suggested) {
        List<String> lore = new ArrayList<>();
        MigrationOptions options = getOptions(player);
        if (blockingHook == null) {
            lore.add(GUIManager.color("&7No external protection hook is"));
            lore.add(GUIManager.color("&7reporting protection at your location."));
        } else {
            lore.add(GUIManager.color("&7Detected source: &e" + blockingHook));
            if (suggested != null) {
                lore.add(GUIManager.color("&7Suggested migration source: &a" + suggested.getDisplayName()));
            }
        }

        try {
            Plot plot = plugin.store().getPlotAt(player.getLocation());
            if (plot != null) {
                String owner = plot.getOwnerName() == null ? "Unknown" : plot.getOwnerName();
                lore.add(" ");
                lore.add(GUIManager.color("&7Aegis plot here: &fYes"));
                lore.add(GUIManager.color("&7Owner: &f" + owner));
            }
        } catch (Throwable ignored) {}

        lore.add(" ");
        if (options.focusX != null && options.focusZ != null) {
            lore.add(GUIManager.color("&7Focused claim filter: &aACTIVE"));
            lore.add(GUIManager.color("&7Target: &f" + options.focusX + ", " + options.focusZ));
            lore.add(" ");
        }
        lore.add(GUIManager.color("&eUse the source items below to run a preview."));
        return lore;
    }

    private ItemStack toggleItem(String name, boolean enabled, Material material) {
        return GUIManager.createItem(enabled ? material : Material.GRAY_DYE,
                name + " " + state(enabled),
                List.of(GUIManager.color("&7Click to toggle this preview/import option.")));
    }

    private String state(boolean enabled) {
        return enabled ? "&aON" : "&cOFF";
    }

    private SourcePlugin sourceForSlot(int rawSlot) {
        return switch (rawSlot) {
            case 20 -> SourcePlugin.GRIEFPREVENTION;
            case 22 -> SourcePlugin.GRIEFDEFENDER;
            case 24 -> SourcePlugin.LANDS;
            default -> null;
        };
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

    private SourcePlugin detectSourceAt(org.bukkit.Location location) {
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

    private String detectBlockingHook(org.bukkit.Location location) {
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

    private List<String> focusedClaimLore(MigrationOptions options) {
        List<String> lore = new ArrayList<>();
        if (options == null || options.focusX == null || options.focusZ == null) {
            lore.add(GUIManager.color("&7No focused external claim."));
            lore.add(GUIManager.color("&7The preview will scan the entire selected source."));
            lore.add(" ");
            lore.add(GUIManager.color("&eUse the migration wand to target one exact claim."));
            return lore;
        }

        lore.add(GUIManager.color("&7Focused coordinates: &f" + options.focusX + ", " + options.focusZ));
        if (options.worldFilter != null && !options.worldFilter.isBlank()) {
            lore.add(GUIManager.color("&7World: &f" + options.worldFilter));
        }
        lore.add(" ");
        lore.add(GUIManager.color("&eClick to clear this exact-claim filter."));
        return lore;
    }

    private List<String> focusedPreviewLore(MigrationResult result) {
        List<String> lore = new ArrayList<>();
        if (result == null || result.focusedWorld == null) {
            lore.add(GUIManager.color("&7No specific focused claim matched."));
            lore.add(GUIManager.color("&7This preview represents the full selected source."));
            return lore;
        }

        lore.add(GUIManager.color("&7Owner: &f" + trim(result.focusedOwnerName, 28)));
        lore.add(GUIManager.color("&7World: &f" + result.focusedWorld));
        if (result.focusedName != null && !result.focusedName.isBlank()) {
            lore.add(GUIManager.color("&7Name: &f" + trim(result.focusedName, 28)));
        }
        if (result.focusedX1 != null && result.focusedZ1 != null
                && result.focusedX2 != null && result.focusedZ2 != null) {
            lore.add(GUIManager.color("&7Bounds: &f" + result.focusedX1 + "," + result.focusedZ1
                    + " &7-> &f" + result.focusedX2 + "," + result.focusedZ2));
        }
        lore.add(GUIManager.color("&7Trusted entries: &f" + result.focusedTrustedCount));
        lore.add(GUIManager.color("&7Imported flags: &f" + result.focusedFlagCount));
        return lore;
    }

    private String trim(String input, int max) {
        if (input == null || input.isBlank()) return "unknown";
        return input.length() <= max ? input : input.substring(0, max - 3) + "...";
    }
}
