package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.horizons.HorizonRank;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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

    private enum Page {
        FRONTIER,
        HORIZONS
    }

    private record HorizonTier(int index, String id, long requiredRenown, int radiusGain, Material material) {}

    public static class ExpansionHolder implements InventoryHolder {
        private final Page page;

        private ExpansionHolder(Page page) {
            this.page = page;
        }

        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        if (!plugin.modules().on(com.aegisguard.config.Modules.Id.EXPANSIONS)) {
            plugin.msg().send(player, "module_disabled", java.util.Map.of("MODULE", "Expansions"));
            return;
        }
        // Title via centralized gateway (colors + fallback + clamp handled)
        String title = plugin.gui().title(player, "expansion_gui_title", "&d✦ Frontier Expansion ✦");
        title = clampTitle(title);

        Inventory inv = Bukkit.createInventory(new ExpansionHolder(Page.FRONTIER), 54, title);

        // Background filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        ItemStack frontierGlass = GUIManager.createItem(Material.BROWN_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : new int[]{9, 10, 12, 14, 16, 17, 18, 19, 25, 26, 27, 28, 29, 30, 32, 33, 34, 35}) {
            inv.setItem(slot, frontierGlass);
        }

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

        inv.setItem(1, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                tr(player, "expansion_frontier_guide_name", "&eFrontier Field Guide"),
                trList(player, "expansion_frontier_guide_lore", List.of(
                        "&7Choose one measured radius increase.",
                        "&7Every request is checked for overlap,",
                        "&7world limits, cost, and approval mode.",
                        " ",
                        "&8Horizons remain the journey beyond Level 30."
                ))
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

        inv.setItem(37, GUIManager.createItem(
                isHorizonsUnlocked(plot) ? Material.ECHO_SHARD : Material.AMETHYST_SHARD,
                tr(player, "expansion_projection_name", "&d&lExpansion Horizons"),
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
            inv.setItem(52, GUIManager.createItem(
                    Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                    tr(player, "button_view_instant_approvals", "&bInstant Approvals"),
                    trList(player, "view_instant_approvals_lore", List.of(
                            "&7What: browse auto-approved expansion history.",
                            "&7When: auditing Instant Mode or unattended queue approvals.",
                            " ",
                            "&eClick to open."
                    ))
            ));
            inv.setItem(53, GUIManager.createItem(
                    Material.COMPASS,
                    tr(player, "button_view_requests_admin", "&bView Pending Requests"),
                    trList(player, "view_requests_admin_lore", List.of(
                            "&7Review pending expansion requests awaiting approve/deny.",
                            " ",
                            "&eClick to open admin queue"
                    ))
            ));
        }

        // --- BACK BUTTON ---
        inv.setItem(48, GUIManager.createItem(
                Material.ARROW,
                tr(player, "button_back_menu", "&fReturn to Menu"),
                trList(player, "back_menu_lore", List.of("&7Go back to the main dashboard."))
        ));

        // --- EXIT BUTTON ---
        inv.setItem(50, GUIManager.createItem(
                Material.BARRIER,
                tr(player, "button_exit", "&c✖ Close"),
                trList(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ExpansionHolder holder)) return;

        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Ignore clicks from the player's own inventory
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        int currentRadius = getRadius(plot);

        if (holder.page == Page.HORIZONS) {
            handleHorizonsClick(player, e, plot, currentRadius);
            return;
        }

        switch (e.getSlot()) {
            case 20 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 5); }
            case 21 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 10); }
            case 22 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 20); }
            case 23 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 35); }
            case 24 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 50); }
            case 37 -> {
                openHorizons(player);
                plugin.effects().playMenuFlip(player);
            }

            case 52 -> {
                if (plugin.isAdmin(player)) {
                    plugin.gui().expansionInstantApprovals().open(player);
                    plugin.effects().playMenuFlip(player);
                } else {
                    plugin.effects().playError(player);
                }
            }

            case 53 -> {
                if (plugin.isAdmin(player)) {
                    plugin.gui().expansionAdmin().open(player);
                    plugin.effects().playMenuFlip(player);
                } else {
                    plugin.effects().playError(player);
                }
            }

            case 48 -> {
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
            }

            case 50 -> {
                player.closeInventory();
                plugin.effects().playMenuClose(player);
            }
        }
    }

    private void openHorizons(Player player) {
        String title = clampTitle(plugin.gui().title(player,
                "expansion_horizons_title", "&5✦ Expansion Horizons ✦"));
        Inventory inv = Bukkit.createInventory(new ExpansionHolder(Page.HORIZONS), 54, title);
        ItemStack filler = GUIManager.createItem(Material.PURPLE_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inv.getSize(); slot++) inv.setItem(slot, filler);

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        boolean ownsPlot = plot != null && player.getUniqueId().equals(plot.getOwner());
        int level = plot == null ? 0 : plot.getLevel();
        int unlockLevel = getHorizonsUnlockLevel();
        int currentRadius = getRadius(plot);
        ExpansionRequest pending = plugin.getExpansionRequestManager() == null
                ? null : plugin.getExpansionRequestManager().getRequest(player.getUniqueId());

        inv.setItem(4, GUIManager.createItem(Material.BEACON,
                tr(player, "expansion_horizons_header", "&d&lBeyond the Frontier"),
                trList(player, "expansion_horizons_header_lore", List.of(
                        "&7Transform an established plot into a",
                        "&7landmark-scale territory through Ascension.",
                        " ",
                        "&8Every Horizon still obeys pricing, overlap,",
                        "&8approval, snapshots, and world limits."
                ))));

        inv.setItem(10, GUIManager.createItem(Material.FILLED_MAP,
                tr(player, "expansion_horizons_plot", "&bTerritory Projection"),
                buildHorizonPlotLore(player, plot, ownsPlot, currentRadius)));

        inv.setItem(13, GUIManager.createItem(
                level >= unlockLevel ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE,
                tr(player, "expansion_horizons_level", "&eAscension Requirement"),
                buildHorizonLevelLore(player, level, unlockLevel, ownsPlot)));

        inv.setItem(16, GUIManager.createItem(Material.SHIELD,
                tr(player, "expansion_horizons_safety", "&aProtected Growth"),
                trList(player, "expansion_horizons_safety_lore", List.of(
                        "&7Pre-expansion recovery snapshot",
                        "&7Live overlap and world-limit validation",
                        "&7Queue or instant approval support",
                        "&7Payment rollback if expansion fails"
                ))));

        List<HorizonTier> tiers = getHorizonTiers();
        int[] slots = {28, 29, 30, 31, 32};
        for (int i = 0; i < tiers.size() && i < slots.length; i++) {
            HorizonTier tier = tiers.get(i);
            inv.setItem(slots[i], buildHorizonTierItem(
                    player, plot, ownsPlot, currentRadius, level, pending, tier));
        }

        inv.setItem(37, GUIManager.createItem(plot != null && plot.getHorizonRank() >= 2 ? Material.CLOCK : Material.GRAY_DYE,
                tr(player, "horizon_climate_name", "&bClimate Lens"),
                buildClimateLore(player, plot)));
        inv.setItem(38, GUIManager.createItem(plot != null && plot.getHorizonRank() >= 3 ? Material.ENDER_EYE : Material.GRAY_DYE,
                tr(player, "horizon_wards_name", "&9Guardian Wards"),
                buildWardLore(player, plot)));
        inv.setItem(39, GUIManager.createItem(plot != null && plot.getHorizonRank() >= 4 ? Material.RECOVERY_COMPASS : Material.GRAY_DYE,
                tr(player, "horizon_pulse_name", "&dStarward Pulse"),
                buildPulseLore(player, plot)));
        inv.setItem(40, GUIManager.createItem(plot != null && plot.getHorizonRank() >= 5 ? Material.HEART_OF_THE_SEA : Material.GRAY_DYE,
                tr(player, "horizon_heart_name", "&dEternal Aegis Heart"),
                buildHeartLore(player, plot)));

        inv.setItem(48, GUIManager.createItem(Material.ARROW,
                tr(player, "expansion_horizons_back", "&eBack to Frontier Expansion"),
                trList(player, "expansion_horizons_back_lore", List.of("&7Return to standard expansion tiers."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER,
                tr(player, "button_exit", "&c✖ Close"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
    }

    private void handleHorizonsClick(Player player, InventoryClickEvent event, Plot plot, int currentRadius) {
        if (event.getSlot() == 48) {
            open(player);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (event.getSlot() == 50) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }

        if (event.getSlot() == 37 && validateOwnedPlot(player, plot) && plot.getHorizonRank() >= 2) {
            plugin.horizons().cycleClimate(player, plot);
            plugin.effects().playConfirm(player);
            openHorizons(player);
            return;
        }
        if (event.getSlot() == 38 && validateOwnedPlot(player, plot) && plot.getHorizonRank() >= 3) {
            String[] flags = {"horizon-projectile-veil", "horizon-ender-seal", "horizon-phantom-ward"};
            if (event.isLeftClick() && !event.isShiftClick()) {
                int bits = 0;
                for (int i = 0; i < flags.length; i++) {
                    if (plot.getFlag(flags[i], true)) bits |= (1 << i);
                }
                int next = (bits + 1) & 7;
                for (int i = 0; i < flags.length; i++) {
                    plot.setFlag(flags[i], (next & (1 << i)) != 0);
                }
            } else {
                String flag = event.isShiftClick() ? flags[2] : flags[1];
                plot.setFlag(flag, !plot.getFlag(flag, true));
            }
            plugin.store().savePlot(plot);
            plugin.effects().playConfirm(player);
            openHorizons(player);
            return;
        }
        if (event.getSlot() == 39 && validateOwnedPlot(player, plot) && plot.getHorizonRank() >= 4) {
            plugin.horizons().territoryPulse(player, plot);
            return;
        }

        int tierIndex = switch (event.getSlot()) {
            case 28 -> 0;
            case 29 -> 1;
            case 30 -> 2;
            case 31 -> 3;
            case 32 -> 4;
            default -> -1;
        };
        List<HorizonTier> tiers = getHorizonTiers();
        if (tierIndex < 0 || tierIndex >= tiers.size()) return;

        HorizonTier tier = tiers.get(tierIndex);
        if (!isHorizonsEnabled()) {
            player.sendMessage(color(tr(player, "expansion_horizons_disabled", "&cExpansion Horizons is disabled on this server.")));
            plugin.effects().playError(player);
            return;
        }
        if (!validatePlot(player, plot)) return;
        if (plot.getLevel() < getHorizonsUnlockLevel()) {
            player.sendMessage(color(tr(player, "expansion_horizons_locked_message",
                    "&cThis Horizon requires Plot Level {LEVEL}.",
                    vars("LEVEL", String.valueOf(getHorizonsUnlockLevel())))));
            plugin.effects().playError(player);
            return;
        }
        HorizonRank rank = HorizonRank.byIndex(tier.index());
        if (tier.index() == plot.getHorizonRank() + 1) {
            plugin.horizons().issueSigil(player, plot, rank);
            openHorizons(player);
            return;
        }
        if (tier.index() == plot.getHorizonExpansionRank() + 1 && tier.index() <= plot.getHorizonRank()) {
            if (plugin.horizons().requestNextExpansion(player, plot)) plugin.effects().playConfirm(player);
            else plugin.effects().playError(player);
            return;
        }
        plugin.effects().playError(player);
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
        plugin.runEntityLater(player, () -> {
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
                    : plugin.getExpansionRequestManager().calculateSmartCost(plot, targetRadius);
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
                "&7Quoted Cost: &6{COST}",
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

        int currentRadius = getRadius(plot);
        int baseRadius = plugin.cfg().getWorldMinRadius(player.getWorld());
        int growth = Math.max(0, currentRadius - baseRadius);
        String stage = currentRadius <= baseRadius
                ? tr(player, "expansion_plot_stage_base", "&fBase Frontier")
                : tr(player, "expansion_plot_stage_expanded", "&aExpanded Frontier");

        lore.add(color(tr(player, "expansion_plot_stage_line",
                "&7Current Tier: {STAGE}",
                vars("STAGE", stage))));
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
                vars("RADIUS", String.valueOf(currentRadius)))));
        lore.add(color(tr(player, "expansion_plot_base_line",
                "&7Base Frontier Radius: &f{BASE}",
                vars("BASE", String.valueOf(baseRadius)))));
        lore.add(color(tr(player, "expansion_plot_growth_line",
                growth > 0
                        ? "&7Growth Beyond Base: &a+{GROWTH}"
                        : "&7Growth Beyond Base: &8None yet",
                vars("GROWTH", String.valueOf(growth)))));
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
                "&7Unlock landmark-scale territory growth",
                "&7through your plot's Ascension level.",
                " ",
                "&8Five Horizon milestones extend beyond",
                "&8the standard Frontier expansion tiers."
        )));
        lore.add(" ");
        if (plot == null || !ownsPlotHere) {
            lore.add(color(tr(player, "expansion_projection_hint_outside",
                    "&8Enter one of your own plots to view live numbers.")));
            return lore;
        }

        int unlockLevel = getHorizonsUnlockLevel();
        lore.add(color(tr(player, "expansion_projection_level_line",
                "&7Plot Level: &f{CURRENT} &8/ &d{REQUIRED}",
                vars("CURRENT", String.valueOf(plot.getLevel()), "REQUIRED", String.valueOf(unlockLevel)))));
        lore.add(color(tr(player,
                isHorizonsUnlocked(plot) ? "expansion_projection_unlocked" : "expansion_projection_locked",
                isHorizonsUnlocked(plot)
                        ? "&aHorizons unlocked — click to explore."
                        : "&eReach Plot Level {LEVEL} to unlock.",
                vars("LEVEL", String.valueOf(unlockLevel)))));
        lore.add(color(tr(player, "expansion_projection_limit_line",
                "&7World Ceiling: &f{LIMIT} radius",
                vars("LIMIT", String.valueOf(plugin.horizons().maximumRadius())))));
        lore.add(" ");
        lore.add(color(tr(player, "expansion_projection_open",
                "&dClick to open Expansion Horizons.")));
        return lore;
    }

    private List<String> buildHorizonPlotLore(Player player, Plot plot, boolean ownsPlot, int currentRadius) {
        if (plot == null) {
            return trList(player, "expansion_horizons_no_plot_lore", List.of(
                    "&cNo plot detected.",
                    "&7Stand inside your own plot to continue."
            ));
        }
        List<String> lore = new ArrayList<>();
        lore.add(color(tr(player, "expansion_horizons_plot_name_line", "&7Plot: &f{PLOT}",
                vars("PLOT", plot.getPlotName() == null || plot.getPlotName().isBlank()
                        ? plot.getOwnerName() + "'s Plot" : plot.getPlotName()))));
        lore.add(color(tr(player, "expansion_horizons_current_radius", "&7Current Radius: &b{RADIUS}",
                vars("RADIUS", String.valueOf(currentRadius)))));
        lore.add(color(tr(player, "expansion_horizons_current_area", "&7Current Footprint: &b{AREA} blocks",
                vars("AREA", String.valueOf(getArea(plot))))));
        lore.add(color(tr(player, "expansion_horizons_world_limit", "&7World Ceiling: &f{LIMIT} radius",
                vars("LIMIT", String.valueOf(plugin.horizons().maximumRadius())))));
        lore.add(" ");
        lore.add(color(tr(player, ownsPlot ? "expansion_horizons_plot_ready" : "expansion_horizons_plot_denied",
                ownsPlot ? "&aThis territory is ready for projection." : "&cYou do not own this territory.")));
        return lore;
    }

    private List<String> buildHorizonLevelLore(Player player, int level, int unlockLevel, boolean ownsPlot) {
        List<String> lore = new ArrayList<>();
        lore.add(color(tr(player, "expansion_horizons_level_line", "&7Current Plot Level: &f{LEVEL}",
                vars("LEVEL", String.valueOf(level)))));
        lore.add(color(tr(player, "expansion_horizons_unlock_line", "&7Horizons Unlock: &dLevel {LEVEL}",
                vars("LEVEL", String.valueOf(unlockLevel)))));
        lore.add(" ");
        if (!ownsPlot) {
            lore.add(color(tr(player, "expansion_horizons_level_no_plot", "&8Enter your own plot to inspect its progression.")));
        } else if (level >= unlockLevel) {
            lore.add(color(tr(player, "expansion_horizons_level_ready", "&aExpansion Horizons is unlocked.")));
        } else {
            lore.add(color(tr(player, "expansion_horizons_level_locked", "&eAscend {LEVELS} more level(s) to unlock.",
                    vars("LEVELS", String.valueOf(unlockLevel - level)))));
        }
        return lore;
    }

    private ItemStack buildHorizonTierItem(Player player,
                                           Plot plot,
                                           boolean ownsPlot,
                                           int currentRadius,
                                           int currentLevel,
                                           ExpansionRequest pending,
                                           HorizonTier tier) {
        int targetRadius = currentRadius + tier.radiusGain();
        int maxRadius = plugin.horizons().maximumRadius();
        boolean levelReady = currentLevel >= getHorizonsUnlockLevel();
        boolean renownReady = plot != null && plot.getHorizonRenown() >= tier.requiredRenown();
        boolean withinLimit = targetRadius <= maxRadius || player.hasPermission("aegis.admin.bypass-limits");
        boolean nextRank = plot != null && tier.index() == plot.getHorizonRank() + 1;
        boolean expansionReady = plot != null && tier.index() == plot.getHorizonExpansionRank() + 1
                && tier.index() <= plot.getHorizonRank();
        boolean mastered = plot != null && tier.index() <= plot.getHorizonExpansionRank();
        boolean available = isHorizonsEnabled() && ownsPlot && levelReady && withinLimit && pending == null
                && ((nextRank && renownReady) || expansionReady);

        String keyBase = "expansion_horizon_tier_" + tier.index();
        String name = tr(player, keyBase + "_name", "&dHorizon " + tier.index() + " &8(+" + tier.radiusGain() + ")",
                vars("GAIN", String.valueOf(tier.radiusGain()), "LEVEL", String.valueOf(getHorizonsUnlockLevel())));
        List<String> lore = new ArrayList<>(trList(player, keyBase + "_lore", List.of(
                "&7A landmark-scale territory milestone."
        )));
        lore.addAll(trList(player, "horizon_rank_" + tier.id() + "_perks", switch (tier.index()) {
            case 1 -> List.of("&bUnlocks safe landings outside combat.");
            case 2 -> List.of("&bUnlocks the personal Climate Lens.");
            case 3 -> List.of("&bUnlocks three selectable Guardian Wards.");
            case 4 -> List.of("&bUnlocks the Starward territory pulse.");
            default -> List.of("&bUnlocks the Eternal Aegis Heart blessing.");
        }));
        lore.add(" ");
        lore.add(color(tr(player, "expansion_horizon_requirement_line", "&7Gateway: &ePlot Level {LEVEL}",
                vars("LEVEL", String.valueOf(getHorizonsUnlockLevel())))));
        lore.add(color(tr(player, "horizon_rank_renown_line", "&7Renown: &d{CURRENT} &8/ &f{REQUIRED}",
                vars("CURRENT", plot == null ? "0" : String.valueOf(plot.getHorizonRenown()),
                        "REQUIRED", String.valueOf(tier.requiredRenown())))));
        lore.add(color(tr(player, "expansion_horizon_radius_line", "&7Radius: &f{CURRENT} &8→ &d{TARGET}",
                vars("CURRENT", String.valueOf(currentRadius), "TARGET", String.valueOf(targetRadius)))));
        lore.add(color(tr(player, "expansion_horizon_area_line", "&7Projected Footprint: &b{AREA} blocks",
                vars("AREA", String.valueOf(estimateArea(targetRadius))))));
        double cost = plugin.getExpansionRequestManager() == null || plot == null
                ? 0.0 : plugin.getExpansionRequestManager().calculateSmartCost(plot, targetRadius);
        lore.add(color(tr(player, "expansion_horizon_cost_line", "&7Estimated Cost: &6{COST}",
                vars("COST", formatCurrency(cost)))));
        lore.add(" ");

        if (!isHorizonsEnabled()) {
            lore.add(color(tr(player, "expansion_horizon_state_disabled", "&cDisabled by this server.")));
        } else if (!ownsPlot) {
            lore.add(color(tr(player, "expansion_horizon_state_no_plot", "&cStand inside your own plot.")));
        } else if (mastered) {
            lore.add(color(tr(player, "horizon_rank_state_mastered", "&aAwakened and expanded.")));
        } else if (!levelReady) {
            lore.add(color(tr(player, "expansion_horizon_state_level_locked", "&eLocked until Plot Level {LEVEL}.",
                    vars("LEVEL", String.valueOf(getHorizonsUnlockLevel())))));
        } else if (!withinLimit) {
            lore.add(color(tr(player, "expansion_horizon_state_world_locked", "&cThis projection exceeds the world ceiling.")));
        } else if (pending != null) {
            lore.add(color(tr(player, "expansion_horizon_state_pending", "&eResolve your pending request first.")));
        } else if (expansionReady) {
            lore.add(color(tr(player, "horizon_rank_state_expansion_ready", "&bClick to request this awakened rank's expansion.")));
        } else if (nextRank && renownReady) {
            lore.add(color(tr(player, "horizon_rank_state_sigil_ready", "&dClick to receive your bound Horizon Sigil.")));
        } else if (nextRank) {
            lore.add(color(tr(player, "horizon_rank_state_renown_locked", "&eEarn {REMAINING} more Renown.",
                    vars("REMAINING", String.valueOf(Math.max(0L, tier.requiredRenown() - (plot == null ? 0L : plot.getHorizonRenown())))))));
        } else {
            lore.add(color(tr(player, "horizon_rank_state_previous_locked", "&8Awaken the previous Horizon Rank first.")));
        }

        Material material = available ? tier.material() : Material.GRAY_DYE;
        return GUIManager.createItem(material, name, lore);
    }

    private boolean isHorizonsEnabled() {
        return plugin.cfg().raw().getBoolean("expansions.horizons.enabled", true);
    }

    private int getHorizonsUnlockLevel() {
        return plugin.horizons() == null ? 30 : plugin.horizons().unlockLevel();
    }

    private boolean isHorizonsUnlocked(Plot plot) {
        return isHorizonsEnabled() && plot != null && plot.getLevel() >= getHorizonsUnlockLevel();
    }

    private List<HorizonTier> getHorizonTiers() {
        List<HorizonTier> tiers = new ArrayList<>();
        for (HorizonRank rank : HorizonRank.values()) {
            tiers.add(new HorizonTier(rank.index(), rank.key(),
                    plugin.horizons().requiredRenown(rank), plugin.horizons().radiusGain(rank), plugin.horizons().material(rank)));
        }
        return tiers;
    }

    private boolean validateOwnedPlot(Player player, Plot plot) {
        if (plot != null && plot.getOwner().equals(player.getUniqueId())) return true;
        plugin.effects().playError(player);
        return false;
    }

    private List<String> buildClimateLore(Player player, Plot plot) {
        if (plot == null || plot.getHorizonRank() < 2) return trList(player, "horizon_climate_locked_lore", List.of("&8Unlocks at Skybound."));
        return trList(player, "horizon_climate_lore", List.of("&7Current: &f{CLIMATE}", " ", "&eClick to cycle personal plot ambience."))
                .stream().map(line -> line.replace("{CLIMATE}", plot.getHorizonClimate())).toList();
    }

    private List<String> buildWardLore(Player player, Plot plot) {
        if (plot == null || plot.getHorizonRank() < 3) return trList(player, "horizon_wards_locked_lore", List.of("&8Unlocks at Realmforge."));
        String on = tr(player, "label_on", "ON");
        String off = tr(player, "label_off", "OFF");
        return trList(player, "horizon_wards_lore", List.of(
                        "&7Projectile Veil: &f{PROJECTILE}", "&7Ender Seal: &f{ENDER}",
                        "&7Phantom Ward: &f{PHANTOM}", " ", "&eLeft-click cycles all three wards", "&eRight: Ender &8| &eShift: Phantom"))
                .stream()
                .map(line -> line.replace("{PROJECTILE}", plot.getFlag("horizon-projectile-veil", true) ? on : off)
                        .replace("{ENDER}", plot.getFlag("horizon-ender-seal", true) ? on : off)
                        .replace("{PHANTOM}", plot.getFlag("horizon-phantom-ward", true) ? on : off))
                .toList();
    }

    private List<String> buildPulseLore(Player player, Plot plot) {
        return plot == null || plot.getHorizonRank() < 4
                ? trList(player, "horizon_pulse_locked_lore", List.of("&8Unlocks at Starward Dominion."))
                : trList(player, "horizon_pulse_lore", List.of("&7Release a harmless visual pulse", "&7through your awakened territory.", " ", "&eClick to invoke."));
    }

    private List<String> buildHeartLore(Player player, Plot plot) {
        return plot == null || plot.getHorizonRank() < 5
                ? trList(player, "horizon_heart_locked_lore", List.of("&8Unlocks at Eternal Aegis."))
                : trList(player, "horizon_heart_lore", List.of("&7Trusted residents receive a protective", "&7welcome when returning outside combat.", "&8Thirty-minute personal cooldown."));
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
        if (plugin.getExpansionRequestManager() != null) {
            return plugin.getExpansionRequestManager().getRequiredRadius(plot);
        }
        int width = Math.abs(plot.getX2() - plot.getX1()) + 1;
        int depth = Math.abs(plot.getZ2() - plot.getZ1()) + 1;
        return Math.max(width, depth) / 2;
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
