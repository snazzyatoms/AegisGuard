package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.admin.AdminDiagnostics;
import com.aegisguard.admin.DoctorRepairService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class DoctorRepairGUI {

    public static final class DoctorHolder implements InventoryHolder {
        private final UUID requestId;
        private final DoctorRepairService.ScanResult result;
        private final boolean repairConfirmation;

        DoctorHolder(UUID requestId, DoctorRepairService.ScanResult result, boolean repairConfirmation) {
            this.requestId = requestId;
            this.result = result;
            this.repairConfirmation = repairConfirmation;
        }

        public UUID getRequestId() { return requestId; }
        public DoctorRepairService.ScanResult getResult() { return result; }
        public boolean isRepairConfirmation() { return repairConfirmation; }
        @Override public Inventory getInventory() { return null; }
    }

    private static final int SLOT_SUMMARY = 13;
    private static final int SLOT_SCAN = 20;
    private static final int SLOT_REPORT = 22;
    private static final int SLOT_REPAIR = 24;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_EXIT = 53;
    private final AegisGuard plugin;

    public DoctorRepairGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        if (player == null) return;
        if (!player.hasPermission("aegis.admin")) {
            player.sendMessage(GUIManager.color(plugin.gui().tr(player, "no_permission", "&cYou do not have permission.")));
            return;
        }
        scanAndOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent event, DoctorHolder holder) {
        if (player == null || holder == null) return;
        ItemStack clicked = event.getCurrentItem();
        String action = plugin.gui().getAction(clicked);
        if (action == null) return;

        switch (action) {
            case "doctor_back" -> plugin.gui().admin().open(player);
            case "doctor_exit" -> {
                player.closeInventory();
                plugin.effects().playMenuClose(player);
            }
            case "doctor_scan" -> scanAndOpen(player);
            case "doctor_report" -> generateReport(player);
            case "doctor_repair" -> {
                if (!player.hasPermission("aegis.admin.doctor.repair")) {
                    send(player, "doctor_repair_no_permission", "&cYou cannot run automatic Doctor repairs.");
                    plugin.effects().playError(player);
                    return;
                }
                openResult(player, holder.getResult(), true, holder.getRequestId());
            }
            case "doctor_repair_confirm" -> repairAndOpen(player);
            default -> { }
        }
    }

    private void scanAndOpen(Player player) {
        UUID requestId = UUID.randomUUID();
        openLoading(player, requestId, "doctor_scan_running", "&bScanning territory consistency...");
        plugin.runSync(() -> {
            DoctorRepairService.ScanResult result = DoctorRepairService.scan(plugin);
            plugin.runMain(player, () -> {
                if (isCurrentRequest(player, requestId)) openResult(player, result, false, requestId);
            });
        });
    }

    private void repairAndOpen(Player player) {
        if (!player.hasPermission("aegis.admin.doctor.repair")) {
            send(player, "doctor_repair_no_permission", "&cYou cannot run automatic Doctor repairs.");
            return;
        }
        UUID requestId = UUID.randomUUID();
        openLoading(player, requestId, "doctor_repair_running", "&eApplying deterministic Doctor repairs...");
        plugin.runSync(() -> {
            DoctorRepairService.RepairResult result = DoctorRepairService.repair(plugin);
            plugin.runMain(player, () -> {
                send(player, "doctor_repair_complete", "&aDoctor repaired {PLOTS} plot(s); {REMAINING} issue(s) remain.", Map.of(
                        "PLOTS", Integer.toString(result.repairedPlots()),
                        "REMAINING", Integer.toString(result.after().issues().size())
                ));
                if (isCurrentRequest(player, requestId)) openResult(player, result.after(), false, requestId);
            });
        });
    }

    private void generateReport(Player player) {
        send(player, "doctor_report_running", "&bGenerating Doctor report...");
        plugin.runGlobalAsync(() -> {
            try {
                Path report = AdminDiagnostics.writeReport(plugin);
                plugin.runMain(player, () -> send(player, "doctor_report_saved", "&aDoctor report saved: &f{FILE}",
                        Map.of("FILE", report.getFileName().toString())));
            } catch (Exception error) {
                plugin.runMain(player, () -> send(player, "doctor_report_failed", "&cDoctor report failed: {ERROR}",
                        Map.of("ERROR", safe(error.getMessage()))));
            }
        });
    }

    private void openLoading(Player player, UUID requestId, String messageKey, String fallback) {
        String title = plugin.gui().title(player, "doctor_menu_title", "&4Territory Doctor");
        Inventory inventory = Bukkit.createInventory(new DoctorHolder(requestId, null, false), 27, title);
        fill(inventory);
        inventory.setItem(13, item(player, Material.CLOCK, messageKey, fallback,
                "doctor_loading_lore", List.of("&7Please wait. You may safely use", "&7Back or Exit while this runs."), "doctor_loading"));
        inventory.setItem(18, navigation(player, Material.ARROW, "button_back_admin", "&eBack to Admin",
                "back_admin_lore", List.of("&7Return to the admin tools."), "doctor_back"));
        inventory.setItem(26, navigation(player, Material.BARRIER, "button_exit", "&cClose",
                "button_exit_lore", List.of("&7Close this menu."), "doctor_exit"));
        player.openInventory(inventory);
    }

    private void openResult(Player player, DoctorRepairService.ScanResult result, boolean confirmation, UUID requestId) {
        if (result == null) {
            scanAndOpen(player);
            return;
        }
        String title = plugin.gui().title(player, confirmation ? "doctor_confirm_title" : "doctor_menu_title",
                confirmation ? "&4Confirm Doctor Repair" : "&4Territory Doctor");
        Inventory inventory = Bukkit.createInventory(new DoctorHolder(requestId, result, confirmation), 54, title);
        fill(inventory);

        Map<String, String> summary = Map.of(
                "PLOTS", Integer.toString(result.plotsScanned()),
                "ISSUES", Integer.toString(result.issues().size()),
                "CRITICAL", Long.toString(result.criticalCount()),
                "REPAIRABLE", Long.toString(result.repairableCount())
        );
        ItemStack summaryItem = GUIManager.createItem(Material.HEART_OF_THE_SEA,
                plugin.gui().tr(player, "doctor_summary_name", "&bTerritory Health"),
                plugin.gui().trList(player, "doctor_summary_lore", List.of(
                        "&7Plots scanned: &f{PLOTS}",
                        "&7Issues: &e{ISSUES}",
                        "&7Critical: &c{CRITICAL}",
                        "&7Automatically repairable: &a{REPAIRABLE}"
                ), summary));
        inventory.setItem(SLOT_SUMMARY, summaryItem);

        inventory.setItem(SLOT_SCAN, item(player, Material.SPYGLASS, "doctor_scan_name", "&aScan Again",
                "doctor_scan_lore", List.of("&7Recheck plot, contract, market,", "&7world, overlap, and payment state.", " ", "&eClick to scan"), "doctor_scan"));
        inventory.setItem(SLOT_REPORT, item(player, Material.WRITABLE_BOOK, "doctor_report_name", "&eWrite Full Report",
                "doctor_report_lore", List.of("&7Write complete diagnostics to", "&7plugins/AegisGuard/reports/.", " ", "&eClick to create report"), "doctor_report"));

        if (confirmation) {
            inventory.setItem(SLOT_REPAIR, item(player, Material.RED_CONCRETE, "doctor_repair_confirm_name", "&cConfirm Safe Repairs",
                    "doctor_repair_confirm_lore", List.of("&7Snapshots are created before changes.", "&7Ambiguous damage is never guessed.", " ", "&cClick to confirm repair"), "doctor_repair_confirm"));
        } else {
            inventory.setItem(SLOT_REPAIR, item(player, Material.ANVIL, "doctor_repair_name", "&cRepair Deterministic Issues",
                    "doctor_repair_lore", List.of("&7Fix only states with one safe answer.", "&7Requires a second confirmation.", " ", "&eClick to review repair"), "doctor_repair"));
        }

        int slot = 28;
        for (DoctorRepairService.Issue issue : result.issues().stream().limit(7).toList()) {
            Material material = switch (issue.severity()) {
                case CRITICAL -> Material.REDSTONE_BLOCK;
                case WARNING -> Material.YELLOW_CONCRETE;
                case INFO -> Material.LIGHT_BLUE_STAINED_GLASS;
            };
            String code = issue.code().toLowerCase(Locale.ROOT);
            Map<String, String> values = Map.of(
                    "CODE", issue.code(),
                    "PLOT", issue.plotId() == null ? "-" : issue.plotId().toString(),
                    "DETAIL", issue.message()
            );
            inventory.setItem(slot++, GUIManager.createItem(material,
                    plugin.gui().tr(player, "doctor_issue_name", "&e{CODE}", values),
                    plugin.gui().trList(player, "doctor_issue_" + code,
                            List.of("&7" + issue.message(), "&8Plot: {PLOT}"), values)));
        }
        if (result.issues().isEmpty()) {
            inventory.setItem(31, GUIManager.createItem(Material.LIME_CONCRETE,
                    plugin.gui().tr(player, "doctor_no_issues", "&aNo Consistency Issues"),
                    plugin.gui().trList(player, "doctor_no_issues_lore", List.of("&7Territory state passed this scan."))));
        }

        inventory.setItem(SLOT_BACK, navigation(player, Material.ARROW, "button_back_admin", "&eBack to Admin",
                "back_admin_lore", List.of("&7Return to the admin tools."), "doctor_back"));
        inventory.setItem(SLOT_EXIT, navigation(player, Material.BARRIER, "button_exit", "&cClose",
                "button_exit_lore", List.of("&7Close this menu."), "doctor_exit"));
        player.openInventory(inventory);
        plugin.effects().playMenuOpen(player);
    }

    private boolean isCurrentRequest(Player player, UUID requestId) {
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        return holder instanceof DoctorHolder doctorHolder && requestId.equals(doctorHolder.getRequestId());
    }

    private ItemStack item(Player player, Material material, String nameKey, String fallbackName,
                           String loreKey, List<String> fallbackLore, String action) {
        ItemStack item = GUIManager.createItem(material, plugin.gui().tr(player, nameKey, fallbackName),
                plugin.gui().trList(player, loreKey, fallbackLore));
        plugin.gui().tagAction(item, action);
        return item;
    }

    private ItemStack navigation(Player player, Material material, String nameKey, String fallbackName,
                                 String loreKey, List<String> fallbackLore, String action) {
        return item(player, material, nameKey, fallbackName, loreKey, fallbackLore, action);
    }

    private void fill(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, GUIManager.getFiller());
    }

    private void send(Player player, String key, String fallback) {
        send(player, key, fallback, Map.of());
    }

    private void send(Player player, String key, String fallback, Map<String, String> values) {
        player.sendMessage(GUIManager.color(plugin.gui().tr(player, key, fallback, values)));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown error" : ChatColor.stripColor(value);
    }
}
