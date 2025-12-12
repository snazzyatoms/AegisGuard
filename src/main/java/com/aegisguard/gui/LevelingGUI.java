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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * LevelingGUI
 * - Richer progression view for plot levels.
 * - Shows a level track, current tier, and next tier preview.
 * - Dynamic lore: Changes based on whether plot expansion is enabled in config.
 * - Fully wired into the language engine helpers, with safe fallbacks.
 * - Placeholder names aligned to messages.yml / codex ({owner}, {world}, {level}, {multiplier}, {COST}, etc).
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

        String title = GUIManager.safeText(
                plugin.msg().get(player, "level_gui_title"),
                "§dPlot Ascension Codex"
        );
        Inventory inv = Bukkit.createInventory(new LevelingHolder(plot), 54, title);

        // --- Filler border ---
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        int currentLvl = plot.getLevel();
        int maxLvl = plugin.cfg().getMaxLevel();

        // ----------------------------------------------------------------
        // 1. HEADER: Plot & current level summary
        // ----------------------------------------------------------------
        List<String> headerLore = new ArrayList<>();

        // {owner}, {world}, {level}, {multiplier} – matches codex/messages.yml
        headerLore.add(line(
                player,
                "level_header_owner",
                "§7Owner: §f" + plot.getOwnerName(),
                Map.of("owner", plot.getOwnerName())
        ));

        headerLore.add(line(
                player,
                "level_header_world",
                "§7World: §f" + plot.getWorld(),
                Map.of("world", plot.getWorld())
        ));

        headerLore.add("");

        headerLore.add(line(
                player,
                "level_header_level",
                "§7Current Level: §b" + currentLvl + "§7 / §f" + maxLvl,
                Map.of(
                        "level", String.valueOf(currentLvl),
                        "max", String.valueOf(maxLvl)
                )
        ));

        headerLore.add(line(
                player,
                "level_header_multiplier",
                "§7XP Cost Multiplier: §f" + plugin.cfg().getLevelCostMultiplier(),
                Map.of("multiplier", String.valueOf(plugin.cfg().getLevelCostMultiplier()))
        ));

        if (plugin.cfg().isLevelingExpansionEnabled()) {
            int amount = plugin.cfg().getLevelingExpansionAmount();
            headerLore.add("");
            headerLore.add(line(
                    player,
                    "level_header_growth_title",
                    "§bTerritory Growth:",
                    Map.of()
            ));
            headerLore.add(line(
                    player,
                    "level_header_growth_line",
                    "§7+§f" + amount + " §7block radius per level.",
                    Map.of("AMOUNT", String.valueOf(amount))
            ));
        }

        inv.setItem(4, GUIManager.createItem(
                Material.NETHER_STAR,
                line(player, "level_current_status", "§bPlot Ascension", Map.of()),
                headerLore
        ));

        // ----------------------------------------------------------------
        // 2. LEVEL TRACK (window around current level)
        //    Slots 19 -> 25 (up to 7 levels)
        // ----------------------------------------------------------------
        int windowSize = 7;
        int half = windowSize / 2;
        int start = Math.max(1, currentLvl - half);
        int end = Math.min(maxLvl, start + windowSize - 1);
        // If we’re at top end and still not full window, slide back
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
        currentBuffLore.add(line(
                player,
                "level_current_blessings_title",
                "§7Level §b" + currentLvl + " §7Buffs:",
                Map.of("level", String.valueOf(currentLvl))
        ));

        currentBuffLore.addAll(formatBuffs(currentLvl));
        currentBuffLore.add("");

        currentBuffLore.add(line(
                player,
                "level_current_blessings_footer_1",
                "§8These buffs are active while",
                Map.of()
        ));
        currentBuffLore.add(line(
                player,
                "level_current_blessings_footer_2",
                "§8you are inside this dominion.",
                Map.of()
        ));

        inv.setItem(29, GUIManager.createItem(
                Material.BOOK,
                line(player, "level_current_blessings_name", "§aCurrent Blessings", Map.of()),
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
            upgradeLore.add(line(
                    player,
                    "level_upgrade_next_tier",
                    "§7Next Tier: §bLevel " + nextLvl,
                    Map.of("level", String.valueOf(nextLvl))
            ));

            // {COST} matches codex/messages; TYPE is optional
            upgradeLore.add(line(
                    player,
                    "level_upgrade_cost",
                    "§7Cost: §e" + costStr + " §7(" + type.name() + ")",
                    Map.of(
                            "COST", costStr,
                            "TYPE", type.name()
                    )
            ));

            upgradeLore.add("");

            upgradeLore.add(line(
                    player,
                    "level_upgrade_new_buffs_title",
                    "§7New Buffs Unlocked:",
                    Map.of()
            ));
            upgradeLore.addAll(formatBuffs(nextLvl));

            if (plugin.cfg().isLevelingExpansionEnabled()) {
                upgradeLore.add("");
                upgradeLore.add(line(
                        player,
                        "level_upgrade_territory_title",
                        "§bTerritory Gain:",
                        Map.of()
                ));
                upgradeLore.add(line(
                        player,
                        "level_upgrade_territory_line",
                        "§7+§f" + plugin.cfg().getLevelingExpansionAmount() + " §7radius on this upgrade.",
                        Map.of("AMOUNT", String.valueOf(plugin.cfg().getLevelingExpansionAmount()))
                ));
            }

            upgradeLore.add("");
            upgradeLore.add(line(
                    player,
                    "level_upgrade_click_hint",
                    "§eClick to ascend to §bLevel " + nextLvl,
                    Map.of("level", String.valueOf(nextLvl))
            ));
            upgradeLore.add("");

            // --- DYNAMIC LORE SWITCH ---
            List<String> buttonLore;
            if (plugin.cfg().isLevelingExpansionEnabled()) {
                buttonLore = plugin.msg().getList(player, "level_button_lore");
            } else {
                buttonLore = plugin.msg().getList(player, "level_button_lore_static");
                // Fallback if key missing in old configs
                if (buttonLore == null || buttonLore.isEmpty()) {
                    buttonLore = plugin.msg().getList(player, "level_button_lore");
                }
            }

            if (buttonLore != null && !buttonLore.isEmpty()) {
                upgradeLore.addAll(buttonLore);
            }

            // Button title – supports both {level} and {LEVEL}, with a nice fallback
            String upgradeTitle = line(
                    player,
                    "level_upgrade_button",
                    "§aUpgrade to Level §f" + nextLvl,
                    Map.of(
                            "level", String.valueOf(nextLvl),
                            "LEVEL", String.valueOf(nextLvl)
                    )
            );

            inv.setItem(31, GUIManager.createItem(
                    Material.EXPERIENCE_BOTTLE,
                    upgradeTitle,
                    upgradeLore
            ));
        } else {
            // Maxed out
            List<String> maxLore = lines(player, "level_max_reached_lore", Arrays.asList(
                    "§7Your dominion has reached",
                    "§7its highest tier.",
                    "",
                    "§aEnjoy your full power."
            ));

            inv.setItem(31, GUIManager.createItem(
                    Material.BEACON,
                    line(player, "level_max_reached", "§dMax Level Reached", Map.of()),
                    maxLore
            ));
        }

        // ----------------------------------------------------------------
        // 5. EXPANSION INFO PANEL (right)
        // ----------------------------------------------------------------
        if (plugin.cfg().isLevelingExpansionEnabled()) {
            List<String> defaultExpansionLore = new ArrayList<>();
            defaultExpansionLore.add("§bTerritory Growth Rules:");
            defaultExpansionLore.add("§7Each upgrade expands your");
            defaultExpansionLore.add("§7claim radius outward evenly.");
            defaultExpansionLore.add("");
            defaultExpansionLore.add("§7Max world limit: §f"
                    + plugin.cfg().getWorldMaxRadius(player.getWorld()) + " §7blocks.");
            defaultExpansionLore.add("§8Respects world + overlap rules.");

            List<String> expansionLore = lines(player, "level_expansion_lore", defaultExpansionLore);

            inv.setItem(33, GUIManager.createItem(
                    Material.GRASS_BLOCK,
                    line(player, "level_expansion_title", "§aTerritory Expansion", Map.of()),
                    expansionLore
            ));
        }

        // ----------------------------------------------------------------
        // 6. NAVIGATION
        // ----------------------------------------------------------------
        String backName = line(player, "button_back", "§fBack", Map.of());
        List<String> backLore = lines(player, "back_lore", List.of("§7Return to Aegis menu."));

        inv.setItem(49, GUIManager.createItem(
                Material.ARROW,
                backName,
                backLore
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

        // Back to main menu
        if (slot == 49) {
            plugin.gui().openMain(player);
            return;
        }

        // Upgrade button
        if (slot == 31 && e.getCurrentItem().getType() == Material.EXPERIENCE_BOTTLE) {
            int nextLvl = plot.getLevel() + 1;
            int maxLvl = plugin.cfg().getMaxLevel();
            if (nextLvl > maxLvl) {
                GUIManager.playClick(player);
                return;
            }

            double cost = calculateCost(nextLvl);
            CurrencyType type = plugin.cfg().getLevelCostType();

            // 1. Check Funds
            if (!plugin.eco().withdraw(player, cost, type)) {
                plugin.msg().send(player, "level_up_fail_cost");
                plugin.effects().playError(player);
                return;
            }

            // 2. Handle Expansion (If Enabled)
            if (plugin.cfg().isLevelingExpansionEnabled()) {
                int expandAmount = plugin.cfg().getLevelingExpansionAmount();
                int newX1 = plot.getX1() - expandAmount;
                int newZ1 = plot.getZ1() - expandAmount;
                int newX2 = plot.getX2() + expandAmount;
                int newZ2 = plot.getZ2() + expandAmount;

                // Overlap Check
                if (plugin.store().isAreaOverlapping(plot, plot.getWorld(), newX1, newZ1, newX2, newZ2)) {
                    plugin.eco().deposit(player, cost, type); // Refund

                    plugin.msg().send(player, "level_up_fail_overlap");
                    plugin.effects().playError(player);
                    return;
                }

                // Limit Check (Admin Bypass)
                int newRadius = (newX2 - newX1) / 2;
                int maxRadius = plugin.cfg().getWorldMaxRadius(player.getWorld());
                if (newRadius > maxRadius && !player.hasPermission("aegis.admin.bypass")) {
                    plugin.eco().deposit(player, cost, type); // Refund

                    plugin.msg().send(player, "level_up_fail_world_limit",
                            Map.of("LIMIT", String.valueOf(maxRadius)));
                    plugin.effects().playError(player);
                    return;
                }

                // Apply Resize
                plugin.store().removePlot(plot.getOwner(), plot.getPlotId()); // Remove old index
                plot.setX1(newX1); plot.setX2(newX2);
                plot.setZ1(newZ1); plot.setZ2(newZ2);
                plugin.store().addPlot(plot); // Add new index
            }

            // 3. Fire Event & Apply Level
            int nextLvlFinal = nextLvl;
            PlotLevelUpEvent event = new PlotLevelUpEvent(plot, player, nextLvlFinal);
            Bukkit.getPluginManager().callEvent(event);

            plot.setLevel(nextLvlFinal);
            plugin.store().setDirty(true);

            // 4. Feedback (fully localized success line)
            plugin.msg().send(player, "level_up_success", Map.of(
                    "level", String.valueOf(nextLvlFinal),
                    "LEVEL", String.valueOf(nextLvlFinal)
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            plugin.effects().playConfirm(player);

            // Refresh menu
            open(player, plot);
            return;
        }

        // Clicks on the track are just "preview" clicks for now
        if (slot >= 19 && slot <= 25) {
            GUIManager.playClick(player);
        }
    }

    // --------------------------------------------------
    // HELPERS
    // --------------------------------------------------

    private ItemStack buildLevelTrackItem(Player player, Plot plot, int level, int currentLvl, int maxLvl) {
        Material mat;
        String title;
        List<String> lore = new ArrayList<>();

        if (level < currentLvl) {
            mat = Material.EMERALD_BLOCK;
            title = line(
                    player,
                    "level_track_title_completed",
                    "§aLevel " + level + " §7(Completed)",
                    Map.of("level", String.valueOf(level))
            );
            lore.add(line(
                    player,
                    "level_track_completed_l1",
                    "§7You have already unlocked",
                    Map.of()
            ));
            lore.add(line(
                    player,
                    "level_track_completed_l2",
                    "§7this tier's blessings.",
                    Map.of()
            ));
        } else if (level == currentLvl) {
            mat = Material.GOLD_BLOCK;
            title = line(
                    player,
                    "level_track_title_current",
                    "§eLevel " + level + " §7(Current)",
                    Map.of("level", String.valueOf(level))
            );
            lore.add(line(
                    player,
                    "level_track_current_l1",
                    "§7These are your current active",
                    Map.of()
            ));
            lore.add(line(
                    player,
                    "level_track_current_l2",
                    "§7blessings inside this dominion.",
                    Map.of()
            ));
        } else {
            mat = Material.REDSTONE_BLOCK;
            title = line(
                    player,
                    "level_track_title_locked",
                    "§cLevel " + level + " §7(Locked)",
                    Map.of("level", String.valueOf(level))
            );
            if (level == currentLvl + 1) {
                lore.add(line(
                        player,
                        "level_track_next_l1",
                        "§7This is your §enext§7 tier.",
                        Map.of()
                ));
                double cost = calculateCost(level);
                CurrencyType type = plugin.cfg().getLevelCostType();
                lore.add(line(
                        player,
                        "level_track_next_cost",
                        "§7Cost: §e" + plugin.eco().format(cost, type),
                        Map.of(
                                "COST", plugin.eco().format(cost, type),
                                "TYPE", type.name()
                        )
                ));
            } else {
                lore.add(line(
                        player,
                        "level_track_locked_l1",
                        "§7Reach previous levels to",
                        Map.of()
                ));
                lore.add(line(
                        player,
                        "level_track_locked_l2",
                        "§7progress toward this tier.",
                        Map.of()
                ));
            }
        }

        lore.add("");
        lore.add(line(
                player,
                "level_track_buffs_title",
                "§7Buffs at this tier:",
                Map.of()
        ));
        lore.addAll(formatBuffs(level));

        // Little footer
        lore.add("");
        if (level == currentLvl + 1 && level <= maxLvl) {
            lore.add(line(
                    player,
                    "level_track_footer_next",
                    "§eUpgrade via the center button.",
                    Map.of()
            ));
        } else if (level > currentLvl + 1) {
            lore.add(line(
                    player,
                    "level_track_footer_progress",
                    "§8Progress step by step.",
                    Map.of()
            ));
        } else {
            lore.add(line(
                    player,
                    "level_track_footer_mastered",
                    "§8Already mastered.",
                    Map.of()
            ));
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
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    // --------------------------------------------------
    // LANGUAGE ENGINE BRIDGE
    // --------------------------------------------------

    /**
     * Single-line helper with variables + safe fallback.
     * Treats null, empty, key-name, and "[Missing: ...]" as missing.
     */
    private String line(Player player, String key, String fallback, Map<String, String> vars) {
        String raw = null;
        try {
            if (vars == null || vars.isEmpty()) {
                raw = plugin.msg().get(player, key);
            } else {
                raw = plugin.msg().get(player, key, vars);
            }
        } catch (Throwable ignored) {
        }
        if (raw == null
                || raw.isEmpty()
                || raw.equalsIgnoreCase(key)
                || raw.startsWith("[Missing:")) {
            return fallback;
        }
        return raw;
    }

    /**
     * Multi-line helper for bigger lore blocks.
     * If the key is missing, empty, or all lines are "[Missing: ...]",
     * returns the provided fallback list.
     */
    private List<String> lines(Player player, String key, List<String> fallback) {
        List<String> raw = null;
        try {
            raw = plugin.msg().getList(player, key);
        } catch (Throwable ignored) {
        }
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }

        List<String> cleaned = new ArrayList<>();
        for (String s : raw) {
            if (s == null) continue;
            if (s.startsWith("[Missing:")) continue;
            cleaned.add(s);
        }

        if (cleaned.isEmpty()) {
            return fallback;
        }
        return cleaned;
    }
}
