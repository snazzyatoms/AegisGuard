package com.aegisguard.expansions;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
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
        String title = plugin.gui().title(player, "expansion_gui_title", "&dLand Expansion Request");
        title = clampTitle(title);

        Inventory inv = Bukkit.createInventory(new ExpansionHolder(), 36, title);

        // Background filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        ApprovalMode mode = getApprovalMode();
        String modeLabel = (mode == ApprovalMode.INSTANT)
                ? tr(player, "expansion_mode_instant", "&bInstant Mode")
                : tr(player, "expansion_mode_queue", "&6Queue Mode");

        // Mode info (Slot 22)
        Map<String, String> vars = Map.of("MODE", modeLabel);
        inv.setItem(22, GUIManager.createItem(
                Material.CLOCK,
                tr(player, "expansion_mode_item_name", "&fApproval: {MODE}", vars),
                trList(player, "expansion_mode_item_lore", List.of(
                        "&7This server is currently using:",
                        "&f{MODE}",
                        " ",
                        "&7Queue Mode: &fAdmins must approve.",
                        "&7Instant Mode: &fAuto-approved when valid."
                ), vars)
        ));

        // --- TIERS ---
        inv.setItem(11, GUIManager.createItem(
                Material.WOODEN_PICKAXE,
                tr(player, "expansion_tier1_name", "&aTier I &7(+5)"),
                tierLore(player, mode,
                        "expansion_tier1_lore",
                        List.of(
                                "&7Request a small expansion.",
                                "&7Increase radius by &a+5&7.",
                                " ",
                                "&eClick to submit request"
                        )
                )
        ));

        inv.setItem(12, GUIManager.createItem(
                Material.STONE_PICKAXE,
                tr(player, "expansion_tier2_name", "&aTier II &7(+10)"),
                tierLore(player, mode,
                        "expansion_tier2_lore",
                        List.of(
                                "&7Request a modest expansion.",
                                "&7Increase radius by &a+10&7.",
                                " ",
                                "&eClick to submit request"
                        )
                )
        ));

        inv.setItem(13, GUIManager.createItem(
                Material.IRON_PICKAXE,
                tr(player, "expansion_tier3_name", "&aTier III &7(+20)"),
                tierLore(player, mode,
                        "expansion_tier3_lore",
                        List.of(
                                "&7Request a major expansion.",
                                "&7Increase radius by &a+20&7.",
                                " ",
                                "&eClick to submit request"
                        )
                )
        ));

        inv.setItem(14, GUIManager.createItem(
                Material.GOLDEN_PICKAXE,
                tr(player, "expansion_tier4_name", "&aTier IV &7(+35)"),
                tierLore(player, mode,
                        "expansion_tier4_lore",
                        List.of(
                                "&7Request a large expansion.",
                                "&7Increase radius by &a+35&7.",
                                " ",
                                "&eClick to submit request"
                        )
                )
        ));

        inv.setItem(15, GUIManager.createItem(
                Material.DIAMOND_PICKAXE,
                tr(player, "expansion_tier5_name", "&aTier V &7(+50)"),
                tierLore(player, mode,
                        "expansion_tier5_lore",
                        List.of(
                                "&7Request a massive expansion.",
                                "&7Increase radius by &a+50&7.",
                                " ",
                                "&eClick to submit request"
                        )
                )
        ));

        // --- ADMIN VIEW (Slot 31) ---
        if (plugin.isAdmin(player)) {
            inv.setItem(31, GUIManager.createItem(
                    Material.COMPASS,
                    tr(player, "button_view_requests_admin", "&bView Pending Requests"),
                    trList(player, "view_requests_admin_lore", List.of(
                            "&7Review all pending expansion requests.",
                            " ",
                            "&eClick to open admin queue"
                    ))
            ));
        }

        // --- BACK BUTTON (Slot 27) ---
        inv.setItem(27, GUIManager.createItem(
                Material.NETHER_STAR,
                tr(player, "button_back_menu", "&fReturn to Menu"),
                trList(player, "back_menu_lore", List.of("&7Go back to the main dashboard."))
        ));

        // --- EXIT BUTTON (Slot 35) ---
        inv.setItem(35, GUIManager.createItem(
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
            case 11 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 5); }
            case 12 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 10); }
            case 13 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 20); }
            case 14 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 35); }
            case 15 -> { if (validatePlot(player, plot)) submit(player, plot, currentRadius + 50); }

            case 31 -> {
                if (plugin.isAdmin(player)) {
                    plugin.gui().expansionAdmin().open(player);
                    plugin.effects().playMenuFlip(player);
                } else {
                    plugin.effects().playError(player);
                }
            }

            case 27 -> {
                plugin.gui().openMain(player);
                plugin.effects().playMenuFlip(player);
            }

            case 35 -> {
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
            raw = plugin.getConfig().getString("expansions.approval_mode", "QUEUE");
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

    private List<String> tierLore(Player p, ApprovalMode mode, String key, List<String> fallback) {
        // Allow packs to override, but also inject a mode hint if the pack does not include one.
        // We insert the hint before the click line, so it reads nicely.

        List<String> base = trList(p, key, fallback);
        if (base == null || base.isEmpty()) base = fallback;

        List<String> out = new ArrayList<>();
        for (int i = 0; i < base.size(); i++) {
            String line = base.get(i);

            // Right before the click prompt, add a mode hint.
            if (line != null && GUIManager.color(line).toLowerCase(Locale.ROOT).contains("click")) {
                out.add(modeHintLine(p, mode));
                out.add(" ");
            }

            out.add(line);
        }

        // If the pack had no click prompt, just append the hint at the end.
        if (!containsModeHint(out)) {
            out.add(" ");
            out.add(modeHintLine(p, mode));
        }

        return out;
    }

    private boolean containsModeHint(List<String> lines) {
        if (lines == null) return false;
        for (String s : lines) {
            if (s == null) continue;
            String t = GUIManager.color(s).toLowerCase(Locale.ROOT);
            if (t.contains("queue mode") || t.contains("instant mode") || t.contains("auto")) return true;
        }
        return false;
    }

    private String modeHintLine(Player p, ApprovalMode mode) {
        if (mode == ApprovalMode.INSTANT) {
            return tr(p, "expansion_mode_hint_instant", "&bInstant Mode:&7 Auto-approved when valid.");
        }
        return tr(p, "expansion_mode_hint_queue", "&6Queue Mode:&7 Admin approval required.");
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

    private String clampTitle(String t) {
        if (t == null) return "";
        t = GUIManager.color(t);
        if (t.length() > 32) t = t.substring(0, 32);
        if (t.endsWith("§")) t = t.substring(0, t.length() - 1);
        return t;
    }
}
