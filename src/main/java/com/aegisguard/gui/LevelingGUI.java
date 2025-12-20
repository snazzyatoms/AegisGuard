package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotLevelUpEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LevelingGUI
 * - Updated to use CodexEngine (v1.2.4) List API.
 * - Fixes the "[Missing: level_track_...]" bugs.
 *
 * ✅ Language-aware + safe fallbacks everywhere:
 * - Titles: plugin.gui().title(...)
 * - Item names/lore: safe translate wrappers (prevents raw keys / [Missing] lines)
 */
public class LevelingGUI {

    private final AegisGuard plugin;

    public LevelingGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class LevelingHolder implements InventoryHolder {
        private final Plot plot;
        public LevelingHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }

        // ✅ Title fix: translate & colors + safe fallback + clamp length
        String title = plugin.gui().title(
                player,
                "level_gui_title",
                "&6✦ Dominion Ascension ✦"
        );

        Inventory inv = Bukkit.createInventory(new LevelingHolder(plot), 54, title);

        // --- Filler border ---
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        int currentLvl = plot.getLevel();
        int maxLvl = plugin.cfg().getMaxLevel();

        // ----------------------------------------------------------------
        // 1. HEADER: Plot & current level summary
        // ----------------------------------------------------------------
        List<String> headerLore = new ArrayList<>();

        headerLore.add(t(player,
                "level_header_owner",
                vars("owner", plot.getOwnerName()),
                "&7Owner: &f{OWNER}"
        ));

        headerLore.add(t(player,
                "level_header_world",
                vars("world", plot.getWorld()),
                "&7World: &f{WORLD}"
        ));

        headerLore.add("");

        headerLore.add(t(player,
                "level_header_level",
                vars("level", String.valueOf(currentLvl), "max", String.valueOf(maxLvl)),
                "&7Level: &e{LEVEL}&7/&f{MAX}"
        ));

        headerLore.add(t(player,
                "level_header_multiplier",
                vars("multiplier", String.valueOf(plugin.cfg().getLevelCostMultiplier())),
                "&7Cost Multiplier: &f{MULTIPLIER}"
        ));

        if (plugin.cfg().isLevelingExpansionEnabled()) {
            int amount = plugin.cfg().getLevelingExpansionAmount();
            headerLore.add("");

            headerLore.add(t(player,
                    "level_header_growth_title",
                    "&bTerritory Growth:"
            ));

            headerLore.add(t(player,
                    "level_header_growth_line",
                    vars("amount", String.valueOf(amount)),
                    "&7+&f{AMOUNT} &7block radius per level."
            ));
        }

        inv.setItem(4, GUIManager.createItem(
                Material.NETHER_STAR,
                plugin.gui().tr(player, "level_current_status", "&eDominion Status"),
                headerLore
        ));

        // ----------------------------------------------------------------
        // 2. LEVEL TRACK (window around current level)
        // ----------------------------------------------------------------
        int windowSize = 7;
        int half = windowSize / 2;
        int start = Math.max(1, currentLvl - half);
        int end = Math.min(maxLvl, start + windowSize - 1);

        if (end - start + 1 < windowSize && start > 1) {
            start = Math.max(1, end - windowSize + 1);
        }

        int slot = 19;
        for (int level = start; level <= end; level++) {
            inv.setItem(slot++, buildLevelTrackItem(player, plot, level, currentLvl, maxLvl));
        }

        // ----------------------------------------------------------------
        // 3. CURRENT BUFFS PANEL (left)
        // ----------------------------------------------------------------
        List<String> currentBuffLore = new ArrayList<>();
        currentBuffLore.add(t(player,
                "level_current_blessings_title",
                vars("level", String.valueOf(currentLvl)),
                "&bBlessings at Level &f{LEVEL}"
        ));
        currentBuffLore.addAll(formatBuffs(currentLvl));
        currentBuffLore.add("");

        currentBuffLore.add(t(player,
                "level_current_blessings_footer_1",
                "&7Only your strongest unlocked boons are shown."
        ));
        currentBuffLore.add(t(player,
                "level_current_blessings_footer_2",
                "&8(Keep this view clean and readable.)"
        ));

        inv.setItem(29, GUIManager.createItem(
                Material.BOOK,
                plugin.gui().tr(player, "level_current_blessings_name", "&bCurrent Blessings"),
                currentBuffLore
        ));

        // ----------------------------------------------------------------
        // 4. NEXT LEVEL PREVIEW + UPGRADE BUTTON (center)
        // ----------------------------------------------------------------
        int nextLvl = currentLvl + 1;
        if (nextLvl <= maxLvl) {
            double cost = calculateCost(nextLvl);
            CurrencyType type = plugin.cfg().getLevelCostType();
            String costStr = plugin.eco().format(cost, type);

            List<String> upgradeLore = new ArrayList<>();
            upgradeLore.add(t(player,
                    "level_upgrade_next_tier",
                    vars("level", String.valueOf(nextLvl)),
                    "&eNext Tier: &fLevel {LEVEL}"
            ));

            upgradeLore.add(t(player,
                    "level_upgrade_cost",
                    vars("cost", costStr, "type", type.name()),
                    "&7Cost: &6{COST} &8({TYPE})"
            ));

            upgradeLore.add("");
            upgradeLore.add(t(player,
                    "level_upgrade_new_buffs_title",
                    "&bNew Blessings:"
            ));
            upgradeLore.addAll(formatBuffs(nextLvl));

            if (plugin.cfg().isLevelingExpansionEnabled()) {
                upgradeLore.add("");

                upgradeLore.add(t(player,
                        "level_upgrade_territory_title",
                        "&bTerritory Gain:"
                ));

                upgradeLore.add(t(player,
                        "level_upgrade_territory_line",
                        vars("amount", String.valueOf(plugin.cfg().getLevelingExpansionAmount())),
                        "&7+&f{AMOUNT} &7radius on this upgrade."
                ));
            }

            upgradeLore.add("");
            upgradeLore.add(t(player,
                    "level_upgrade_click_hint",
                    vars("level", String.valueOf(nextLvl)),
                    "&eClick to ascend to Level {LEVEL}"
            ));
            upgradeLore.add("");

            List<String> buttonLore;
            if (plugin.cfg().isLevelingExpansionEnabled()) {
                buttonLore = tl(player, "level_button_lore", Map.of(), List.of());
            } else {
                buttonLore = tl(player, "level_button_lore_static", Map.of(), List.of());
                if (buttonLore.isEmpty()) buttonLore = tl(player, "level_button_lore", Map.of(), List.of());
            }
            if (!buttonLore.isEmpty()) upgradeLore.addAll(buttonLore);

            inv.setItem(31, GUIManager.createItem(
                    Material.EXPERIENCE_BOTTLE,
                    t(player,
                            "level_upgrade_button",
                            vars("level", String.valueOf(nextLvl)),
                            "&aUpgrade to Level {LEVEL}"
                    ),
                    upgradeLore
            ));
        } else {
            List<String> maxLore = tl(player, "level_max_reached_lore", Map.of(),
                    List.of("&7Your dominion has reached", "&7its highest tier.", "", "&aEnjoy your full power.")
            );

            inv.setItem(31, GUIManager.createItem(
                    Material.BEACON,
                    plugin.gui().tr(player, "level_max_reached", "&aMax Level Reached"),
                    maxLore
            ));
        }

        // ----------------------------------------------------------------
        // 5. EXPANSION INFO PANEL (right)
        // ----------------------------------------------------------------
        if (plugin.cfg().isLevelingExpansionEnabled()) {
            List<String> expansionLore = tl(player, "level_expansion_lore", Map.of(),
                    List.of("&bTerritory Growth Rules:", "&7Each upgrade expands your", "&7claim radius outward evenly.")
            );

            String exTitle = plugin.gui().tr(player, "level_expansion_title", "&aTerritory Expansion");

            inv.setItem(33, GUIManager.createItem(
                    Material.GRASS_BLOCK,
                    exTitle,
                    expansionLore
            ));
        }

        // ----------------------------------------------------------------
        // 6. NAVIGATION
        // ----------------------------------------------------------------
        inv.setItem(49, GUIManager.createItem(
                Material.ARROW,
                plugin.gui().tr(player, "button_back", "&fBack"),
                plugin.gui().trList(player, "back_lore", List.of("&7Return to the previous menu."))
        ));

        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, LevelingHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }

        int slot = e.getSlot();

        if (slot == 49) {
            plugin.gui().openMain(player);
            return;
        }

        if (slot == 31 && e.getCurrentItem().getType() == Material.EXPERIENCE_BOTTLE) {
            int nextLvl = plot.getLevel() + 1;
            int maxLvl = plugin.cfg().getMaxLevel();
            if (nextLvl > maxLvl) {
                GUIManager.playClick(player);
                return;
            }

            double cost = calculateCost(nextLvl);
            CurrencyType type = plugin.cfg().getLevelCostType();

            if (!plugin.eco().withdraw(player, cost, type)) {
                plugin.msg().send(player, "level_up_fail_cost");
                plugin.effects().playError(player);
                return;
            }

            if (plugin.cfg().isLevelingExpansionEnabled()) {
                int expandAmount = plugin.cfg().getLevelingExpansionAmount();
                int newX1 = plot.getX1() - expandAmount;
                int newZ1 = plot.getZ1() - expandAmount;
                int newX2 = plot.getX2() + expandAmount;
                int newZ2 = plot.getZ2() + expandAmount;

                if (plugin.store().isAreaOverlapping(plot, plot.getWorld(), newX1, newZ1, newX2, newZ2)) {
                    plugin.eco().deposit(player, cost, type);
                    plugin.msg().send(player, "level_up_fail_overlap");
                    plugin.effects().playError(player);
                    return;
                }

                int newRadius = (newX2 - newX1) / 2;
                int maxRadiusWorld = plugin.cfg().getWorldMaxRadius(player.getWorld());
                if (newRadius > maxRadiusWorld && !player.hasPermission("aegis.admin.bypass")) {
                    plugin.eco().deposit(player, cost, type);
                    plugin.msg().send(player, "level_up_fail_world_limit", Map.of("LIMIT", String.valueOf(maxRadiusWorld)));
                    plugin.effects().playError(player);
                    return;
                }

                plugin.store().removePlot(plot.getOwner(), plot.getPlotId());
                plot.setX1(newX1); plot.setX2(newX2);
                plot.setZ1(newZ1); plot.setZ2(newZ2);
                plugin.store().addPlot(plot);
            }

            int nextLvlFinal = nextLvl;
            PlotLevelUpEvent event = new PlotLevelUpEvent(plot, player, nextLvlFinal);
            Bukkit.getPluginManager().callEvent(event);

            plot.setLevel(nextLvlFinal);
            plugin.store().setDirty(true);

            plugin.msg().send(player, "level_up_success", Map.of("LEVEL", String.valueOf(nextLvlFinal)));

            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            plugin.effects().playConfirm(player);

            open(player, plot);
            return;
        }

        if (slot >= 19 && slot <= 25) {
            GUIManager.playClick(player);
        }
    }

    // --------------------------------------------------
    // HELPERS
    // --------------------------------------------------

    private ItemStack buildLevelTrackItem(Player player, Plot plot, int level, int currentLvl, int maxLvl) {
        Material mat;
        String titleKey;
        String loreKey;

        if (level < currentLvl) {
            mat = Material.EMERALD_BLOCK;
            titleKey = "level_track_completed_name";
            loreKey = "level_track_completed_lore";
        } else if (level == currentLvl) {
            mat = Material.GOLD_BLOCK;
            titleKey = "level_track_current_name";
            loreKey = "level_track_current_lore";
        } else {
            mat = Material.REDSTONE_BLOCK;
            titleKey = "level_track_locked_name";
            loreKey = "level_track_locked_lore";

            if (level == currentLvl + 1) {
                titleKey = "level_track_next_name";
                loreKey = "level_track_next_lore";
            }
        }

        String title = t(player, titleKey, vars("level", String.valueOf(level)), "&7Level " + level);

        Map<String, String> vars = vars(
                "level", String.valueOf(level),
                "cost", plugin.eco().format(calculateCost(level), plugin.cfg().getLevelCostType())
        );

        List<String> lore = tl(player, loreKey, vars, List.of());
        if (lore.isEmpty()) {
            lore = new ArrayList<>();
            lore.add("&7Tier &f{LEVEL}".replace("{LEVEL}", String.valueOf(level)));
            lore.add("&7Cost: &6{COST}".replace("{COST}", plugin.eco().format(calculateCost(level), plugin.cfg().getLevelCostType())));
        } else {
            lore = new ArrayList<>(lore);
        }

        lore.add("");
        lore.add(t(player, "level_track_buffs_title", "&bBlessings:"));
        lore.addAll(formatBuffs(level));

        lore.add("");
        if (level == currentLvl + 1 && level <= maxLvl) {
            lore.add(t(player, "level_track_footer_next", "&eNext tier awaits."));
        } else if (level > currentLvl + 1) {
            lore.add(t(player, "level_track_footer_progress", "&7Advance to unlock this tier."));
        } else if (level <= currentLvl) {
            lore.add(t(player, "level_track_footer_mastered", "&aMastered."));
        }

        return GUIManager.createItem(mat, title, lore);
    }

    private double calculateCost(int level) {
        double base = plugin.cfg().getLevelBaseCost();
        double mult = plugin.cfg().getLevelCostMultiplier();
        return base * (level * mult);
    }

    private List<String> formatBuffs(int level) {
        List<String> rewards = plugin.cfg().getLevelRewards(level);
        List<String> formatted = new ArrayList<>();
        if (rewards == null || rewards.isEmpty()) {
            formatted.add("§8- (None)");
        } else {
            for (String s : rewards) {
                if (s.startsWith("EFFECT:")) {
                    try {
                        String[] parts = s.split(":");
                        String type = parts[1].toLowerCase().replace("_", " ");
                        type = type.substring(0, 1).toUpperCase() + type.substring(1);
                        formatted.add("§b✦ " + type + " " + toRoman(Integer.parseInt(parts[2])));
                    } catch (Exception e) {
                        formatted.add("§b✦ " + s);
                    }
                } else {
                    formatted.add("§a✦ " + s);
                }
            }
        }
        return formatted;
    }

    private String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n);
        };
    }

    // --------------------------------------------------
    // SAFE LANGUAGE WRAPPERS (prevents raw keys / [Missing])
    // --------------------------------------------------

    private String t(Player p, String key, String fallback) {
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(p, key);
        } catch (Throwable ignored) {}
        return GUIManager.safeText(key, raw, fallback);
    }

    private String t(Player p, String key, Map<String, String> vars, String fallback) {
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(p, key, (vars == null ? Map.of() : vars));
        } catch (Throwable ignored) {}
        return GUIManager.safeText(key, raw, fallback);
    }

    private List<String> tl(Player p, String key, Map<String, String> vars, List<String> fallback) {
        List<String> out = null;
        try {
            if (plugin.codex() != null) out = plugin.codex().trList(p, key, (vars == null ? Map.of() : vars));
        } catch (Throwable ignored) {}

        if (out == null || out.isEmpty()) return (fallback == null ? List.of() : fallback);

        // Guard against Codex “missing” behaviors leaking into lore
        if (out.size() == 1) {
            String one = out.get(0);
            if (one == null) return (fallback == null ? List.of() : fallback);
            String s = one.trim();
            if (s.isEmpty() || s.contains("[Missing") || s.equalsIgnoreCase(key)) {
                return (fallback == null ? List.of() : fallback);
            }
        }

        return out;
    }

    /**
     * Build a vars map that supports BOTH {key} and {KEY}.
     * Example: vars("owner","Kai") provides "owner" and "OWNER".
     */
    private Map<String, String> vars(Object... kv) {
        Map<String, String> m = new HashMap<>();
        if (kv == null) return m;

        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = String.valueOf(kv[i]);
            String v = String.valueOf(kv[i + 1]);
            m.put(k, v);
            m.put(k.toUpperCase(), v);
        }
        return m;
    }
}
