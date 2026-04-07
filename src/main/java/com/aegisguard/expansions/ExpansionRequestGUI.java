package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ExpansionRequestGUI
 * - Allows players to submit requests to increase their plot size.
 * - CodexEngine-backed localization (via plugin.gui() gateway).
 *
 * Upgrades:
 * - Displays current approval mode (Queue vs Instant) from config.
 * - Adapts validation/UX so Instant Mode feels immediate and clear.
 *
 * Fixes:
 * - Title uses plugin.gui().title (color + fallback + clamp safety)
 * - Null-safe translations (no [Missing] leaks)
 * - Correct createItem() argument order (material first)
 * - Ignores clicks from player inventory (rawSlot safety)
 */
public class ExpansionRequestGUI {

    private final AegisGuard plugin;

    public ExpansionRequestGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /** Matches config value expansions.approval_mode. */
    private enum ApprovalMode {
        QUEUE,
        INSTANT
    }

    public static class ExpansionHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        // Title via centralized gateway (colors + fallback + clamp handled)
        String title = plugin.gui().title(player, "expansion_gui_title", "&d✦ Frontier Expansion ✦");
        title = clampTitle(title);

        Inventory inv = Bukkit.createInventory(new ExpansionHolder(), 45, title);

        // Background filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        ApprovalMode mode = getApprovalMode();
        ExpansionRequest pending = plugin.getExpansionRequestManager() == null
                ? null
                : plugin.getExpansionRequestManager().getRequest(player.getUniqueId());
        boolean unattendedQueue = plugin.getExpansionRequestManager() != null
                && plugin.getExpansionRequestManager().isAutoApproveWhenNoReviewersEnabled()
                && plugin.getExpansionRequestManager().getOnlineReviewerCount() <= 0;
        String modeLabel = (mode == ApprovalMode.INSTANT)
                ? tr(player, "expansion_mode_instant", "&bInstant Mode")
                : tr(player, "expansion_mode_queue", "&6Queue Mode");
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        int currentRadius = getRadius(plot);
        boolean ownsPlotHere = plot != null && plot.getOwner() != null && plot.getOwner().equals(player.getUniqueId());

        inv.setItem(4, GUIManager.createItem(
                Material.NETHER_STAR,
                tr(player, "expansion_header_name", "&dFrontier Expansion Console"),
                buildHeaderLore(player, plot, ownsPlotHere, pending, mode, unattendedQueue)
        ));

        inv.setItem(11, GUIManager.createItem(
                Material.MAP,
                tr(player, "expansion_plot_overview_name", "&bCurrent Frontier"),
                buildPlotOverviewLore(player, plot, ownsPlotHere)
        ));

        // Mode info
        Map<String, String> vars = Map.of("MODE", modeLabel);
        inv.setItem(13, GUIManager.createItem(
                Material.CLOCK,
                tr(player, "expansion_mode_item_name", "&fApproval: {MODE}", vars),
                appendModeLore(player, trList(player, "expansion_mode_item_lore", List.of(
                        "&7This server is currently using:",
                        "&f{MODE}",
                        " ",
                        "&7Queue Mode: &fAdmins must approve.",
                        "&7Instant Mode: &fAuto-approved when valid."
                ), vars), mode, unattendedQueue)
        ));

        inv.setItem(15, GUIManager.createItem(
                pending == null ? Material.LIME_DYE : Material.CLOCK,
                pending == null
                        ? tr(player, "expansion_pending_none_name", "&aNo Pending Request")
                        : tr(player, "expansion_pending_name", "&ePending Expansion Request"),
                pending == null
                        ? buildPendingNoneLore(player, plot, ownsPlotHere, mode, unattendedQueue)
                        : pendingLore(player, pending, mode, unattendedQueue)
        ));

        inv.setItem(31, GUIManager.createItem(
                Material.AMETHYST_SHARD,
                tr(player, "expansion_projection_name", "&dExpansion Horizons"),
                buildProjectionLore(player, plot, ownsPlotHere)
        ));

        // --- TIERS ---
        inv.setItem(20, GUIManager.createItem(
                Material.WOODEN_PICKAXE,
                tr(player, "expansion_tier1_name", "&aTier I &7(+5)"),
                tierLore(player, plot, ownsPlotHere, currentRadius, currentRadius + 5, pending, mode, unattendedQueue,
                        "expansion_tier1_lore",
                        List.of(
                                "&7Open a careful first expansion lane.",
                                "&7Increase radius by &a+5&7 blocks.",
                                " ",
                                "&eClick to submit request."
                        )
                )
        ));

        inv.setItem(21, GUIManager.createItem(
                Material.STONE_PICKAXE,
                tr(player, "expansion_tier2_name", "&aTier II &7(+10)"),
                tierLore(player, plot, ownsPlotHere, currentRadius, currentRadius + 10, pending, mode, unattendedQueue,
                        "expansion_tier2_lore",
                        List.of(
                                "&7Push your borders a little farther.",
                                "&7Increase radius by &a+10&7 blocks.",
                                " ",
                                "&eClick to submit request."
                        )
                )
        ));

        inv.setItem(22, GUIManager.createItem(
                Material.IRON_PICKAXE,
                tr(player, "expansion_tier3_name", "&aTier III &7(+20)"),
                tierLore(player, plot, ownsPlotHere, currentRadius, currentRadius + 20, pending, mode, unattendedQueue,
                        "expansion_tier3_lore",
                        List.of(
                                "&7Open room for larger builds and paths.",
                                "&7Increase radius by &a+20&7 blocks.",
                                " ",
                                "&eClick to submit request."
                        )
                )
        ));

        inv.setItem(23, GUIManager.createItem(
                Material.GOLDEN_PICKAXE,
                tr(player, "expansion_tier4_name", "&aTier IV &7(+35)"),
                tierLore(player, plot, ownsPlotHere, currentRadius, currentRadius + 35, pending, mode, unattendedQueue,
                        "expansion_tier4_lore",
                        List.of(
                                "&7Shape a major district around your claim.",
                                "&7Increase radius by &a+35&7 blocks.",
                                " ",
                                "&eClick to submit request."
                        )
                )
        ));

        inv.setItem(24, GUIManager.createItem(
                Material.DIAMOND_PICKAXE,
                tr(player, "expansion_tier5_name", "&aTier V &7(+50)"),
                tierLore(player, plot, ownsPlotHere, currentRadius, currentRadius + 50, pending, mode, unattendedQueue,
                        "expansion_tier5_lore",
                        List.of(
                                "&7Reach for a full landmark-scale frontier.",
                                "&7Increase radius by &a+50&7 blocks.",
                                " ",
                                "&eClick to submit request."
                        )
                )
        ));

        // --- ADMIN VIEW ---
        if (plugin.isAdmin(player)) {
            inv.setItem(42, GUIManager.createItem(
                    Material.COMPASS,
                    tr(player, "button_view_requests_admin", "&bView Pending Requests"),
                    trList(player, "view_requests_admin_lore", List.of(
                            "&7Review all pending expansion requests.",
                            " ",
                            "&eClick to open admin queue"
                    ))
            ));
        }

        // --- BACK BUTTON ---
        inv.setItem(40, GUIManager.createItem(
                Material.NETHER_STAR,
                tr(player, "button_back_menu", "&fReturn to Menu"),
                trList(player, "back_menu_lore", List.of("&7Go back to the main dashboard."))
        ));

        // --- EXIT BUTTON ---
        inv.setItem(44, GUIManager.createItem(
                Material.BARRIER,
                tr(player, "button_exit", "&c✖ Close"),
                trList(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ExpansionHolder)) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Ignore clicks from the player's own inventory
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        int currentRadius = getRadius(plot);

        switch (e.getSlot()) {
            case 20 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 5); }
            case 21 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 10); }
            case 22 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 20); }
            case 23 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 35); }
            case 24 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 50); }

            case 42 -> {
                if (plugin.isAdmin(player)) {
                    plugin.gui().expansionAdmin().open(player);
                    plugin.effects().playMenuFlip(player);
                } else {
                    plugin.effects().playError(player);
                }
            }

            case 40 -> {
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
            }

            case 44 -> {
                player.closeInventory();
                plugin.effects().playMenuClose(player);
            }
        }
    }

    private void submit(Player player, Plot plot, int newRadius) {
        // We do a tiny post-submit check so UX matches the server mode:
        // - Queue Mode: expect a pending request to exist
        // - Instant Mode: expect the plot radius to actually change
        int beforeRadius = getRadius(plot);

        if (plugin.getExpansionRequestManager() == null) {
            plugin.effects().playError(player);
            return;
        }

        plugin.getExpansionRequestManager().createRequest(player, plot, newRadius);

        // Manager sends localized success/failure messages.
        // We just provide correct feedback and navigation.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            boolean pending = false;
            try {
                pending = plugin.getExpansionRequestManager().hasPendingRequest(player.getUniqueId());
            } catch (Throwable ignored) {
                // defensive: older builds might not expose hasPendingRequest
            }

            Plot refreshed = plugin.store().getPlotAt(player.getLocation());
            int afterRadius = getRadius(refreshed);

            if (pending || afterRadius > beforeRadius) {
                plugin.effects().playConfirm(player);
                plugin.gui().openMain(player);
            } else {
                // Likely failed (economy/limits/overlap/etc). Manager already messaged.
                plugin.effects().playError(player);
            }
        }, 1L);
    }

    private List<String> tierLore(Player player,
                                  Plot plot,
                                  boolean ownsPlotHere,
                                  int currentRadius,
                                  int targetRadius,
                                  ExpansionRequest pending,
                                  ApprovalMode mode,
                                  boolean unattendedQueue,
                                  String key,
                                  List<String> fallback) {
        List<String> lore = new ArrayList<>(trList(player, key, fallback));
        if (plot != null) {
            double cost = plugin.getExpansionRequestManager() == null
                    ? 0.0
                    : plugin.getExpansionRequestManager().calculateSmartCost(currentRadius, targetRadius);
            int targetArea = estimateArea(targetRadius);
            lore.add(" ");
            lore.add(color(tr(player, "expansion_tier_radius_line",
                    "&7Radius: &f{CURRENT} &8→ &a{TARGET}",
                    vars("CURRENT", String.valueOf(currentRadius), "TARGET", String.valueOf(targetRadius)))));
            lore.add(color(tr(player, "expansion_tier_area_line",
                    "&7Projected Footprint: &b{AREA} blocks",
                    vars("AREA", String.valueOf(targetArea)))));
            lore.add(color(tr(player, "expansion_tier_cost_line",
                    "&7Estimated Cost: &6{COST}",
                    vars("COST", formatCurrency(cost)))));
        }

        if (!ownsPlotHere) {
            lore.add(" ");
            lore.addAll(trList(player, "expansion_locked_lore", List.of(
                    "&cYou must stand inside",
                    "&cyour own claim to do this."
            )));
        } else if (pending != null) {
            lore.add(" ");
            lore.add(color(tr(player, "expansion_tier_pending_block",
                    "&eYou already have a pending request in review.")));
        } else if (mode == ApprovalMode.INSTANT) {
            lore.add(" ");
            lore.add(color(tr(player, "expansion_mode_hint_instant",
                    "&bInstant Mode:&7 Auto-approved when valid.")));
        } else if (unattendedQueue) {
            lore.add(" ");
            lore.add(color(tr(player, "expansion_mode_hint_unattended",
                    "&bUnattended Queue:&7 No reviewers are online, so valid requests may be auto-approved and logged.")));
        } else {
            lore.add(" ");
            lore.add(color(tr(player, "expansion_mode_hint_queue",
                    "&6Queue Mode:&7 Admin approval required.")));
        }

        return lore;
    }

    private List<String> pendingLore(Player player, ExpansionRequest pending, ApprovalMode mode, boolean unattendedQueue) {
        List<String> lore = new ArrayList<>();
        lore.add(color(tr(player, "expansion_pending_radius_line",
                "&7Current Radius: &f{CURRENT}",
                vars("CURRENT", String.valueOf(pending.getCurrentRadius())))));
        lore.add(color(tr(player, "expansion_pending_target_line",
                "&7Requested Radius: &a{TARGET}",
                vars("TARGET", String.valueOf(pending.getRequestedRadius())))));
        lore.add(color(tr(player, "expansion_pending_cost_line",
                "&7Reserved Cost: &6{COST}",
                vars("COST", formatCurrency(pending.getCost())))));
        lore.add(color(tr(player, "expansion_pending_world_line",
                "&7World Anchor: &f{WORLD}",
                vars("WORLD", pending.getWorldName()))));
        lore.add(color(tr(player, "expansion_pending_status_line",
                "&7Status: &eAwaiting review")));
        lore.add(" ");
        if (mode == ApprovalMode.INSTANT) {
            lore.add(color(tr(player, "expansion_pending_mode_line",
                    "&bThis realm uses instant approval for valid expansions.")));
        } else if (unattendedQueue) {
            lore.add(color(tr(player, "expansion_mode_hint_unattended",
                    "&bUnattended Queue:&7 No reviewers are online, so valid requests may be auto-approved and logged.")));
        } else {
            lore.add(color(tr(player, "expansion_pending_mode_queue_line",
                    "&6Staff review is required before this frontier expands.")));
        }
        return lore;
    }

    private List<String> buildHeaderLore(Player player,
                                         Plot plot,
                                         boolean ownsPlotHere,
                                         ExpansionRequest pending,
                                         ApprovalMode mode,
                                         boolean unattendedQueue) {
        List<String> lore = new ArrayList<>(trList(player, "expansion_header_lore", List.of(
                "&7Shape more room for homes, roads, farms,",
                "&7markets, and future district plans.",
                " ",
                "&8Every request is checked for limits,",
                "&8overlap safety, and payment readiness."
        )));
        lore.add(" ");
        if (!ownsPlotHere) {
            lore.add(color(tr(player, "expansion_header_state_outside",
                    "&cStand inside one of your own plots to submit a request.")));
        } else if (pending != null) {
            lore.add(color(tr(player, "expansion_header_state_pending",
                    "&eA request is already in review for this frontier.")));
        } else if (mode == ApprovalMode.INSTANT) {
            lore.add(color(tr(player, "expansion_header_state_ready_instant",
                    "&bValid requests can resolve immediately on this server.")));
        } else if (unattendedQueue) {
            lore.add(color(tr(player, "expansion_header_state_ready_unattended",
                    "&bNo reviewers are online, so valid queued requests may auto-approve and be logged.")));
        } else if (plot != null) {
            lore.add(color(tr(player, "expansion_header_state_ready_queue",
                    "&6Choose a tier below and send it to staff review.")));
        }
        return lore;
    }

    private List<String> buildPlotOverviewLore(Player player, Plot plot, boolean ownsPlotHere) {
        List<String> lore = new ArrayList<>();
        if (plot == null) {
            lore.addAll(trList(player, "expansion_plot_overview_missing_lore", List.of(
                    "&7No claim was detected at your feet.",
                    "&7Move into one of your own plots to",
                    "&7open a live expansion request."
            )));
            return lore;
        }

        lore.add(color(tr(player, "expansion_plot_type_line",
                "&7Plot Type: &f{TYPE}",
                vars("TYPE", describePlotType(player, plot)))));
        lore.add(color(tr(player, "expansion_plot_owner_line",
                "&7Owner: &f{OWNER}",
                vars("OWNER", plot.getOwnerName() == null ? "Unknown" : plot.getOwnerName()))));
        lore.add(color(tr(player, "expansion_plot_world_line",
                "&7World: &f{WORLD}",
                vars("WORLD", plot.getWorld()))));
        lore.add(color(tr(player, "expansion_plot_radius_line",
                "&7Current Radius: &e{RADIUS}",
                vars("RADIUS", String.valueOf(getRadius(plot))))));
        lore.add(color(tr(player, "expansion_plot_footprint_line",
                "&7Protected Footprint: &b{AREA} blocks",
                vars("AREA", String.valueOf(getArea(plot))))));
        lore.add(color(tr(player, "expansion_plot_limit_line",
                "&7Server Limit: &f{LIMIT} radius",
                vars("LIMIT", String.valueOf(getMaxExpansionRadius(player))))));
        lore.add(" ");
        lore.add(color(tr(player,
                ownsPlotHere ? "expansion_plot_control_ready" : "expansion_plot_control_blocked",
                ownsPlotHere
                        ? "&aYou can request expansion from this position."
                        : "&cYou are not the owner of this plot, so you cannot expand it."
        )));
        return lore;
    }

    private List<String> buildPendingNoneLore(Player player,
                                              Plot plot,
                                              boolean ownsPlotHere,
                                              ApprovalMode mode,
                                              boolean unattendedQueue) {
        List<String> lore = new ArrayList<>(trList(player, "expansion_pending_none_lore", List.of(
                "&7There is no active expansion request",
                "&7waiting in the queue right now."
        )));
        lore.add(" ");
        if (!ownsPlotHere) {
            lore.add(color(tr(player, "expansion_pending_none_hint_outside",
                    "&8Move into one of your own plots to begin.")));
        } else if (plot != null) {
            lore.add(color(tr(player, "expansion_pending_none_hint_ready",
                    "&aPick a tier below to prepare your next frontier step.")));
            if (mode == ApprovalMode.QUEUE && unattendedQueue) {
                lore.add(color(tr(player, "expansion_mode_hint_unattended",
                        "&bUnattended Queue:&7 No reviewers are online, so valid requests may be auto-approved and logged.")));
            }
        }
        return lore;
    }

    private List<String> buildProjectionLore(Player player, Plot plot, boolean ownsPlotHere) {
        List<String> lore = new ArrayList<>(trList(player, "expansion_projection_lore", List.of(
                "&7Each tier widens your frontier in a",
                "&7clean, reviewable step.",
                " ",
                "&8Larger requests cost more, claim more",
                "&8space, and are checked against limits."
        )));
        lore.add(" ");
        if (plot == null || !ownsPlotHere) {
            lore.add(color(tr(player, "expansion_projection_hint_outside",
                    "&8Enter one of your own plots to view live numbers.")));
            return lore;
        }

        int currentRadius = getRadius(plot);
        lore.add(color(tr(player, "expansion_projection_smallest_line",
                "&7Smallest Step: &f{TARGET} radius",
                vars("TARGET", String.valueOf(currentRadius + 5)))));
        lore.add(color(tr(player, "expansion_projection_largest_line",
                "&7Largest Step: &f{TARGET} radius",
                vars("TARGET", String.valueOf(currentRadius + 50)))));
        lore.add(color(tr(player, "expansion_projection_limit_line",
                "&7World Ceiling: &f{LIMIT} radius",
                vars("LIMIT", String.valueOf(getMaxExpansionRadius(player))))));
        lore.add(color(tr(player, "expansion_projection_audit_line",
                "&7Every handled request is written to the audit trail.")));
        return lore;
    }

    private String formatCurrency(double amount) {
        if (plugin.eco() != null && plugin.eco().isVaultEnabled()) {
            return plugin.eco().format(amount, CurrencyType.VAULT);
        }
        return String.format(Locale.US, "%.2f", amount);
    }

    private String color(String input) {
        return GUIManager.color(input);
    }

    private boolean validatePlot(Player player, Plot plot) {
        if (plot == null || !plot.getOwner().equals(player.getUniqueId())) {
            plugin.msg().send(player, "no_plot_here");
            plugin.effects().playError(player);
            return false;
        }

        // Always block if a pending request exists (even if the server switched modes mid-flight).
        if (plugin.getExpansionRequestManager() != null) {
            try {
                if (plugin.getExpansionRequestManager().hasPendingRequest(player.getUniqueId())) {
                    plugin.msg().send(player, "expansion_exists");
                    plugin.effects().playError(player);
                    return false;
                }
            } catch (Throwable ignored) {
                // defensive: older builds might not expose hasPendingRequest
            }
        }

        return true;
    }

    // -----------------------------
    // Mode helpers
    // -----------------------------

    private ApprovalMode getApprovalMode() {
        String raw = null;
        try {
            raw = plugin.getConfig().getString(
                    "expansions.approval.mode",
                    plugin.getConfig().getString("expansions.approval_mode", "QUEUE")
            );
        } catch (Throwable ignored) {
            // If config path changes in older forks, stay safe.
        }

        if (raw == null) return ApprovalMode.QUEUE;
        String s = raw.trim().toUpperCase(Locale.ROOT);

        // Support a few friendly synonyms to be resilient.
        if (s.equals("INSTANT") || s.equals("AUTO") || s.equals("AUTO_APPROVE") || s.equals("AUTOAPPROVE")) {
            return ApprovalMode.INSTANT;
        }
        return ApprovalMode.QUEUE;
    }

    private List<String> appendModeLore(Player player, List<String> baseLore, ApprovalMode mode, boolean unattendedQueue) {
        List<String> lore = new ArrayList<>(baseLore == null ? List.of() : baseLore);
        if (mode == ApprovalMode.QUEUE && unattendedQueue) {
            lore.add(" ");
            lore.add(color(tr(player, "expansion_mode_hint_unattended",
                    "&bUnattended Queue:&7 No reviewers are online, so valid requests may be auto-approved and logged.")));
        }
        return lore;
    }

    // -----------------------------
    // Plot helpers
    // -----------------------------

    private int getRadius(Plot plot) {
        if (plot == null) return 0;

        // Current code style used in your original GUI.
        // If you ever change Plot to store radius directly, update this helper.
        return (plot.getX2() - plot.getX1()) / 2;
    }

    private int getArea(Plot plot) {
        if (plot == null) return 0;
        int width = Math.abs(plot.getX2() - plot.getX1()) + 1;
        int depth = Math.abs(plot.getZ2() - plot.getZ1()) + 1;
        return width * depth;
    }

    private int estimateArea(int radius) {
        int diameter = Math.max(1, (radius * 2) + 1);
        return diameter * diameter;
    }

    private int getMaxExpansionRadius(Player player) {
        int expansionLimit = plugin.cfg().raw().getInt("expansions.max_radius_global", 0);
        if (expansionLimit > 0) return expansionLimit;
        return plugin.cfg().getWorldMaxRadius(player.getWorld());
    }

    private String describePlotType(Player player, Plot plot) {
        if (plot == null) return "Unknown";
        if (plot.isServerZone()) return tr(player, "expansion_plot_type_server", "&dServer Zone");
        if (plot.isGroupPlot()) {
            String groupName = null;
            if (plugin.getGroupManager() != null) {
                try {
                    com.aegisguard.groups.PlotGroup group = plugin.getGroupManager().getGroup(plot.getGroupId());
                    if (group != null) groupName = group.getName();
                } catch (Throwable ignored) {
                    // Older forks may not expose full group metadata.
                }
            }
            if (groupName != null && !groupName.isBlank()) {
                return tr(player, "expansion_plot_type_group_named", "&bGroup Plot &8({GROUP})", vars("GROUP", groupName));
            }
            return tr(player, "expansion_plot_type_group", "&bGroup Plot");
        }
        return tr(player, "expansion_plot_type_personal", "&aPersonal Plot");
    }

    // -----------------------------
    // Localization helpers
    // -----------------------------

    private String tr(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private String tr(Player p, String key, String fallback, Map<String, String> vars) {
        return plugin.gui().tr(p, key, fallback, vars);
    }

    private List<String> trList(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private List<String> trList(Player p, String key, List<String> fallback, Map<String, String> vars) {
        return plugin.gui().trList(p, key, fallback, vars);
    }

    private Map<String, String> vars(String... pairs) {
        Map<String, String> out = new HashMap<>();
        if (pairs == null) return out;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i] != null && pairs[i + 1] != null) {
                out.put(pairs[i], pairs[i + 1]);
            }
        }
        return out;
    }

    private String clampTitle(String t) {
        if (t == null) return "";
        t = GUIManager.color(t);
        if (t.length() > 32) t = t.substring(0, 32);
        if (t.endsWith("§")) t = t.substring(0, t.length() - 1);
        return t;
    }
}
