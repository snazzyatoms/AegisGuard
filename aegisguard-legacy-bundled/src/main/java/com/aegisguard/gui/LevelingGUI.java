package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.api.events.PlotLevelUpEvent;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.util.CompatSound;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LevelingGUI
 *
 * Fix:
 * - Resolves unreplaced placeholders like "(COST)" and "(BALANCE)" showing in lore.
 *   Some language packs historically used parentheses placeholders. We now replace:
 *     {KEY}, (KEY), %KEY%, <KEY>
 *   for any provided vars map.
 *
 * - Also improves block balance display by safely reading EconomyManager balance methods when available.
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

        String title = plugin.gui().title(player, "level_gui_title", "&6✦ Dominion Ascension ✦");
        Inventory inv = Bukkit.createInventory(new LevelingHolder(plot), 54, title);

        // --- Filler border ---
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        int currentLvl = plot.getLevel();
        int maxLvl = plugin.cfg().getMaxLevel();

        // ----------------------------------------------------------------
        // 1) HEADER: Plot & current level summary
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

            headerLore.add(t(player, "level_header_growth_title", "&bTerritory Growth:"));

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
        // 2) LEVEL TRACK (window around current level)
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
        // 3) CURRENT BUFFS PANEL (left)
        // ----------------------------------------------------------------
        List<String> currentBuffLore = new ArrayList<>();
        currentBuffLore.add(t(player,
                "level_current_blessings_title",
                vars("level", String.valueOf(currentLvl)),
                "&bBlessings at Level &f{LEVEL}"
        ));
        currentBuffLore.addAll(formatActiveBuffs(player, currentLvl,
                "level_current_blessings_none",
                "&8No special bonuses are active at this level yet."
        ));
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
        // 4) NEXT LEVEL PREVIEW + UPGRADE OPTIONS (center)
        // ----------------------------------------------------------------
        int nextLvl = currentLvl + 1;
        if (nextLvl <= maxLvl) {

            // Money option (Vault/type selected in config)
            CurrencyType moneyType = plugin.cfg().getLevelCostType();
            double moneyCost = calculateCost(nextLvl);
            boolean moneyAllowed = isMoneyUpgradeAvailable(moneyType);

            // Blocks option (fallback/progression currency)
            CurrencyType blocksType = getBlocksTypeOrNull();
            double blocksCost = (blocksType == null || !isBlocksUpgradeEnabled())
                    ? -1
                    : calculateBlocksCost(nextLvl);
            boolean blocksAllowed = (blocksType != null && blocksCost > 0);

            if (!moneyAllowed && !blocksAllowed) {
                List<String> noLore = tl(player, "level_upgrade_unavailable_lore", Map.of(), List.of(
                        "&7No upgrade payment method is available.",
                        "&8Enable blocks progression or install/enable Vault.",
                        "",
                        "&cUpgrade locked."
                ));

                inv.setItem(31, GUIManager.createItem(
                        Material.BARRIER,
                        plugin.gui().tr(player, "level_upgrade_unavailable", "&cCannot Upgrade"),
                        noLore
                ));

            } else {
                // --- Preview item ---
                List<String> previewLore = new ArrayList<>();
                previewLore.add(t(player,
                        "level_upgrade_next_tier",
                        vars("level", String.valueOf(nextLvl)),
                        "&eNext Tier: &fLevel {LEVEL}"
                ));
                previewLore.add("");

                if (moneyAllowed) {
                    String moneyStr = plugin.eco().format(moneyCost, moneyType);
                    previewLore.add(t(player,
                            "level_upgrade_cost_money",
                            vars("cost", moneyStr, "type", moneyType.name()),
                            "&7Money: &6{COST} &8({TYPE})"
                    ));
                } else {
                    previewLore.add(t(player,
                            "level_upgrade_cost_money_disabled",
                            "&8Money: Disabled/Unavailable"
                    ));
                }

                if (blocksAllowed) {
                    String balStr = formatBalance(player, blocksType);
                    previewLore.add(t(player,
                            "level_upgrade_cost_blocks",
                            vars("cost", formatBlocks(blocksCost), "balance", balStr),
                            "&7Blocks: &b{COST} &7(You have &b{BALANCE}&7)"
                    ));
                } else {
                    previewLore.add(t(player,
                            "level_upgrade_cost_blocks_disabled",
                            "&8Blocks: Disabled/Unavailable"
                    ));
                }

                previewLore.add("");
                previewLore.add(t(player, "level_upgrade_new_buffs_title", "&bNew Blessings:"));
                previewLore.addAll(formatBuffs(player, nextLvl,
                        "level_upgrade_blessings_none",
                        "&8This upgrade focuses on growth or access rather than a new bonus."
                ));
                previewLore.add("");
                previewLore.add(t(player, "level_upgrade_active_after_title", "&dActive After Ascension:"));
                previewLore.addAll(formatActiveBuffs(player, nextLvl,
                        "level_current_blessings_none",
                        "&8No special bonuses will be active at that level yet."
                ));

                if (plugin.cfg().isLevelingExpansionEnabled()) {
                    previewLore.add("");
                    previewLore.add(t(player, "level_upgrade_territory_title", "&bTerritory Gain:"));
                    previewLore.add(t(player,
                            "level_upgrade_territory_line",
                            vars("amount", String.valueOf(plugin.cfg().getLevelingExpansionAmount())),
                            "&7+&f{AMOUNT} &7radius on this upgrade."
                    ));
                }

                inv.setItem(31, GUIManager.createItem(
                        Material.ENCHANTED_BOOK,
                        t(player,
                                "level_upgrade_preview_title",
                                vars("level", String.valueOf(nextLvl)),
                                "&eAscension Preview (Level {LEVEL})"
                        ),
                        previewLore
                ));

                // --- Buttons ---
                if (moneyAllowed && blocksAllowed) {

                    // Slot 30: money button
                    List<String> moneyLore = tl(player, "level_upgrade_button_money_lore",
                            vars("cost", plugin.eco().format(moneyCost, moneyType), "type", moneyType.name()),
                            List.of(
                                    "&7Pay using server economy.",
                                    "&7Cost: &6{COST} &8({TYPE})",
                                    "",
                                    "&eClick to upgrade"
                            )
                    );

                    inv.setItem(30, GUIManager.createItem(
                            Material.EXPERIENCE_BOTTLE,
                            t(player,
                                    "level_upgrade_button_money",
                                    vars("level", String.valueOf(nextLvl)),
                                    "&6Upgrade with Money (Level {LEVEL})"
                            ),
                            moneyLore
                    ));

                    // Slot 32: blocks button
                    List<String> blocksLore = tl(player, "level_upgrade_button_blocks_lore",
                            vars("cost", formatBlocks(blocksCost), "balance", formatBalance(player, blocksType)),
                            List.of(
                                    "&7Spend your earned blocks.",
                                    "&7Cost: &b{COST} Blocks",
                                    "&7Balance: &b{BALANCE}",
                                    "",
                                    "&eClick to upgrade"
                            )
                    );

                    inv.setItem(32, GUIManager.createItem(
                            Material.EXPERIENCE_BOTTLE,
                            t(player,
                                    "level_upgrade_button_blocks",
                                    vars("level", String.valueOf(nextLvl)),
                                    "&bUpgrade with Blocks (Level {LEVEL})"
                            ),
                            blocksLore
                    ));

                } else {
                    // Single method mode: put the one available upgrade button in the center (slot 31)
                    boolean useMoney = moneyAllowed;

                    CurrencyType payType = useMoney ? moneyType : blocksType;
                    double payCost = useMoney ? moneyCost : blocksCost;

                    List<String> upgradeLore = new ArrayList<>();

                    upgradeLore.add(t(player,
                            "level_upgrade_next_tier",
                            vars("level", String.valueOf(nextLvl)),
                            "&eNext Tier: &fLevel {LEVEL}"
                    ));

                    if (useMoney) {
                        upgradeLore.add(t(player,
                                "level_upgrade_cost",
                                vars("cost", plugin.eco().format(payCost, payType), "type", payType.name()),
                                "&7Cost: &6{COST} &8({TYPE})"
                        ));
                    } else {
                        upgradeLore.add(t(player,
                                "level_upgrade_cost_blocks_single",
                                vars("cost", formatBlocks(payCost), "balance", formatBalance(player, payType)),
                                "&7Cost: &b{COST} Blocks &7(You have &b{BALANCE}&7)"
                        ));
                    }

                    upgradeLore.add("");
                    upgradeLore.add(t(player, "level_upgrade_new_buffs_title", "&bNew Blessings:"));
                    upgradeLore.addAll(formatBuffs(player, nextLvl,
                            "level_upgrade_blessings_none",
                            "&8This upgrade focuses on growth or access rather than a new bonus."
                    ));
                    upgradeLore.add("");
                    upgradeLore.add(t(player, "level_upgrade_active_after_title", "&dActive After Ascension:"));
                    upgradeLore.addAll(formatActiveBuffs(player, nextLvl,
                            "level_current_blessings_none",
                            "&8No special bonuses will be active at that level yet."
                    ));

                    if (plugin.cfg().isLevelingExpansionEnabled()) {
                        upgradeLore.add("");
                        upgradeLore.add(t(player, "level_upgrade_territory_title", "&bTerritory Gain:"));
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

                    inv.setItem(31, GUIManager.createItem(
                            Material.EXPERIENCE_BOTTLE,
                            t(player,
                                    useMoney ? "level_upgrade_button_money" : "level_upgrade_button_blocks",
                                    vars("level", String.valueOf(nextLvl)),
                                    useMoney
                                            ? "&6Upgrade with Money (Level {LEVEL})"
                                            : "&bUpgrade with Blocks (Level {LEVEL})"
                            ),
                            upgradeLore
                    ));
                }
            }

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
        // 5) EXPANSION INFO PANEL (right)
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
        // 6) NAVIGATION
        // ----------------------------------------------------------------
        inv.setItem(49, GUIManager.createItem(
                Material.ARROW,
                plugin.gui().tr(player, "button_back", "&fBack"),
                plugin.gui().trList(player, "back_lore", List.of("&7Return to the previous menu."))
        ));
        inv.setItem(50, GUIManager.createItem(
                Material.BARRIER,
                plugin.gui().tr(player, "button_exit", "&cClose"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))
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

        if (slot == 50) {
            try { plugin.effects().playMenuClose(player); } catch (Throwable ignored) {}
            player.closeInventory();
            return;
        }

        // Track clicks (cosmetic)
        if (slot >= 19 && slot <= 25) {
            GUIManager.playClick(player);
            return;
        }

        if (e.getCurrentItem().getType() != Material.EXPERIENCE_BOTTLE) return;

        Plot plotNow = holder.getPlot();
        int nextLvl = plotNow.getLevel() + 1;
        int maxLvl = plugin.cfg().getMaxLevel();
        if (nextLvl > maxLvl) {
            GUIManager.playClick(player);
            return;
        }

        CurrencyType moneyType = plugin.cfg().getLevelCostType();
        double moneyCost = calculateCost(nextLvl);
        boolean moneyAllowed = isMoneyUpgradeAvailable(moneyType);

        CurrencyType blocksType = getBlocksTypeOrNull();
        double blocksCost = (blocksType == null || !isBlocksUpgradeEnabled())
                ? -1
                : calculateBlocksCost(nextLvl);
        boolean blocksAllowed = (blocksType != null && blocksCost > 0);

        boolean clickedMoney = (slot == 30);
        boolean clickedBlocks = (slot == 32);

        // Single-button mode uses slot 31:
        if (slot == 31) {
            if (moneyAllowed) {
                clickedMoney = true;
            } else if (blocksAllowed) {
                clickedBlocks = true;
            } else {
                plugin.effects().playError(player);
                return;
            }
        }

        if (clickedMoney && !moneyAllowed) {
            plugin.effects().playError(player);
            plugin.msg().send(player, "level_up_fail_cost");
            open(player, plotNow);
            return;
        }
        if (clickedBlocks && !blocksAllowed) {
            plugin.effects().playError(player);
            plugin.msg().send(player, "level_up_fail_cost");
            open(player, plotNow);
            return;
        }

        CurrencyType payType = clickedMoney ? moneyType : blocksType;
        double payCost = clickedMoney ? moneyCost : blocksCost;

        if (!plugin.eco().withdraw(player, payCost, payType)) {
            plugin.msg().send(player, "level_up_fail_cost");
            plugin.effects().playError(player);
            return;
        }

        // Expansion validation (overlap/world limit)
        if (plugin.cfg().isLevelingExpansionEnabled()) {
            int expandAmount = plugin.cfg().getLevelingExpansionAmount();
            int newX1 = plotNow.getX1() - expandAmount;
            int newZ1 = plotNow.getZ1() - expandAmount;
            int newX2 = plotNow.getX2() + expandAmount;
            int newZ2 = plotNow.getZ2() + expandAmount;

            if (plugin.store().isAreaOverlapping(plotNow, plotNow.getWorld(), newX1, newZ1, newX2, newZ2)) {
                plugin.eco().deposit(player, payCost, payType);
                plugin.msg().send(player, "level_up_fail_overlap");
                plugin.effects().playError(player);
                return;
            }

            int newRadius = (newX2 - newX1) / 2;
            int maxRadiusWorld = plugin.cfg().getWorldMaxRadius(player.getWorld());
            if (newRadius > maxRadiusWorld && !player.hasPermission("aegis.admin.bypass")) {
                plugin.eco().deposit(player, payCost, payType);
                plugin.msg().send(player, "level_up_fail_world_limit", Map.of("LIMIT", String.valueOf(maxRadiusWorld)));
                plugin.effects().playError(player);
                return;
            }

            plugin.store().removePlot(plotNow.getOwner(), plotNow.getPlotId());
            plotNow.setX1(newX1);
            plotNow.setX2(newX2);
            plotNow.setZ1(newZ1);
            plotNow.setZ2(newZ2);
            plugin.store().addPlot(plotNow);
        }

        PlotLevelUpEvent event = new PlotLevelUpEvent(plotNow, player, nextLvl);
        Bukkit.getPluginManager().callEvent(event);

        plotNow.setLevel(nextLvl);
        plugin.store().setDirty(true);

        plugin.msg().send(player, "level_up_success", Map.of("LEVEL", String.valueOf(nextLvl)));

        CompatSound.play(player, player.getLocation(), 1f, 1f,
                "UI_TOAST_CHALLENGE_COMPLETE", "ENTITY_PLAYER_LEVELUP");
        plugin.effects().playConfirm(player);

        open(player, plotNow);
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

        CurrencyType moneyType = plugin.cfg().getLevelCostType();
        boolean moneyAllowed = isMoneyUpgradeAvailable(moneyType);

        CurrencyType blocksType = getBlocksTypeOrNull();
        boolean blocksAllowed = (blocksType != null && isBlocksUpgradeEnabled() && calculateBlocksCost(level) > 0);

        String costDisplay;
        if (moneyAllowed) {
            costDisplay = plugin.eco().format(calculateCost(level), moneyType);
        } else if (blocksAllowed) {
            costDisplay = formatBlocks(calculateBlocksCost(level)) + " Blocks";
        } else {
            costDisplay = plugin.eco().format(calculateCost(level), moneyType);
        }

        Map<String, String> vars = vars(
                "level", String.valueOf(level),
                "cost", costDisplay
        );

        List<String> lore = tl(player, loreKey, vars, List.of());
        if (lore.isEmpty()) {
            lore = new ArrayList<>();
            lore.add("&7Tier &f{LEVEL}".replace("{LEVEL}", String.valueOf(level)));
            lore.add("&7Cost: &6{COST}".replace("{COST}", costDisplay));
        } else {
            lore = new ArrayList<>(lore);
        }

        lore.add("");
        lore.add(t(player, "level_track_buffs_title", "&bBlessings:"));
        lore.addAll(formatBuffs(player, level,
                "level_track_blessings_none",
                "&8No extra bonuses are tied to this tier."
        ));

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

    /**
     * Blocks upgrade cost:
     * Priority:
     * 1) leveling.blocks_costs.<level>
     * 2) ascension.costs.blocks.<level>
     * 3) formula fallback (leveling.blocks_base_cost * (level * leveling.blocks_cost_multiplier))
     */
    private double calculateBlocksCost(int level) {
        double fromMap1 = plugin.getConfig().getDouble("leveling.blocks_costs." + level, -1);
        if (fromMap1 > 0) return fromMap1;

        double fromMap2 = plugin.getConfig().getDouble("ascension.costs.blocks." + level, -1);
        if (fromMap2 > 0) return fromMap2;

        double base = plugin.getConfig().getDouble("leveling.blocks_base_cost", 250.0);
        double mult = plugin.getConfig().getDouble("leveling.blocks_cost_multiplier", 1.0);
        return base * (level * mult);
    }

    private boolean isBlocksUpgradeEnabled() {
        if (!plugin.getConfig().getBoolean("leveling.blocks_upgrades_enabled", true)) return false;
        if (!plugin.getConfig().getBoolean("economy.blocks.enabled", true)) return false;
        return true;
    }

    private boolean isMoneyUpgradeAvailable(CurrencyType configuredType) {
        boolean vaultEnabled = plugin.getConfig().getBoolean("economy.vault.enabled",
                plugin.getConfig().getBoolean("vault.enabled", true));

        if (configuredType == null) return false;

        if (isVaultLike(configuredType)) {
            if (!vaultEnabled) return false;
            return isVaultPresent();
        }

        return true;
    }

    private boolean isVaultPresent() {
        try {
            Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
            return vault != null && vault.isEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isVaultLike(CurrencyType type) {
        String n = type.name().toUpperCase();
        return n.contains("VAULT") || n.contains("MONEY") || n.contains("CASH") || n.contains("DOLLAR");
    }

    private CurrencyType getBlocksTypeOrNull() {
        try {
            return CurrencyType.valueOf("CLAIM_BLOCKS");
        } catch (Throwable ignored) { }

        try {
            return CurrencyType.valueOf("BLOCKS");
        } catch (Throwable ignored) { }

        return null;
    }

    private String formatBlocks(double d) {
        long v = Math.round(d);
        return String.valueOf(Math.max(0, v));
    }

    /**
     * Balance display:
     * - Tries EconomyManager#getBalance(Player, CurrencyType) or #balance(Player, CurrencyType) via reflection
     * - Falls back to ClaimBlockManager for CLAIM_BLOCKS/BLOCKS
     * - Returns "?" if unknown
     */
    private String formatBalance(Player p, CurrencyType type) {
        double bal = -1;

        // 1) Try eco balance methods via reflection (keeps this GUI compatible across builds)
        try {
            Object eco = plugin.eco();
            if (eco != null && p != null && type != null) {
                for (String mName : new String[]{"getBalance", "balance"}) {
                    try {
                        Method m = eco.getClass().getMethod(mName, Player.class, CurrencyType.class);
                        Object out = m.invoke(eco, p, type);
                        if (out instanceof Number n) {
                            bal = n.doubleValue();
                            break;
                        }
                    } catch (Throwable ignored) { }
                }
            }
        } catch (Throwable ignored) { }

        // 2) Hard fallback for claim-block currencies
        if (bal < 0 && p != null && type != null) {
            String n = type.name().toUpperCase();
            if ((n.equals("CLAIM_BLOCKS") || n.equals("BLOCKS")) && plugin.getClaimBlockManager() != null) {
                bal = plugin.getClaimBlockManager().getAvailableBlocks(p.getUniqueId());
            }
        }

        if (bal < 0) return "?";
        return formatBlocks(bal);
    }

    private List<String> formatBuffs(Player player, int level, String emptyKey, String emptyFallback) {
        List<String> rewards = plugin.cfg().getLevelRewards(level);
        List<String> formatted = new ArrayList<>();
        if (rewards == null || rewards.isEmpty()) {
            formatted.add(t(player, emptyKey, vars("level", String.valueOf(level)), emptyFallback));
        } else {
            for (String s : rewards) {
                formatted.add(formatReward(player, s));
            }
        }
        return formatted;
    }

    private List<String> formatActiveBuffs(Player player, int level, String emptyKey, String emptyFallback) {
        Map<String, Integer> strongestEffects = new HashMap<>();
        Map<String, Integer> effectOrder = new HashMap<>();
        List<String> effectKeys = new ArrayList<>();

        int totalMemberSlots = 0;
        List<String> unlockedFlags = new ArrayList<>();
        List<String> unlockedMisc = new ArrayList<>();

        for (int i = 1; i <= level; i++) {
            List<String> rewards = plugin.cfg().getLevelRewards(i);
            if (rewards == null || rewards.isEmpty()) {
                continue;
            }

            for (String rawReward : rewards) {
                if (rawReward == null) {
                    continue;
                }

                String reward = rawReward.trim();
                if (reward.isEmpty()) {
                    continue;
                }

                if (reward.startsWith("EFFECT:")) {
                    String[] parts = reward.split(":");
                    if (parts.length < 3) {
                        continue;
                    }

                    String effectKey = parts[1].trim().toUpperCase();
                    int tier;
                    try {
                        tier = Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException ignored) {
                        continue;
                    }

                    if (!effectOrder.containsKey(effectKey)) {
                        effectOrder.put(effectKey, effectOrder.size());
                        effectKeys.add(effectKey);
                    }
                    strongestEffects.merge(effectKey, tier, Math::max);
                    continue;
                }

                if (reward.startsWith("MEMBERS:")) {
                    try {
                        totalMemberSlots += Integer.parseInt(reward.substring("MEMBERS:".length()).trim());
                    } catch (NumberFormatException ignored) {
                    }
                    continue;
                }

                if (reward.startsWith("FLAG:")) {
                    String flag = reward.substring("FLAG:".length()).trim().toLowerCase();
                    if (!flag.isEmpty() && !unlockedFlags.contains(flag)) {
                        unlockedFlags.add(flag);
                    }
                    continue;
                }

                if (reward.equalsIgnoreCase("FLIGHT") || reward.equalsIgnoreCase("FLY")) {
                    if (!unlockedFlags.contains("fly")) {
                        unlockedFlags.add("fly");
                    }
                    continue;
                }

                if (!unlockedMisc.contains(reward)) {
                    unlockedMisc.add(reward);
                }
            }
        }

        List<String> formatted = new ArrayList<>();
        for (String effectKey : effectKeys) {
            Integer tier = strongestEffects.get(effectKey);
            if (tier == null) {
                continue;
            }
            formatted.add(formatReward(player, "EFFECT:" + effectKey + ":" + tier));
        }

        if (totalMemberSlots > 0) {
            formatted.add(formatReward(player, "MEMBERS:" + totalMemberSlots));
        }

        for (String flag : unlockedFlags) {
            formatted.add(formatReward(player, "FLAG:" + flag));
        }

        for (String misc : unlockedMisc) {
            formatted.add(formatReward(player, misc));
        }

        if (formatted.isEmpty()) {
            formatted.add(t(player, emptyKey, vars("level", String.valueOf(level)), emptyFallback));
        }
        return formatted;
    }

    private String formatReward(Player player, String reward) {
        if (reward == null || reward.isBlank()) {
            return "&8- Unknown bonus";
        }

        if (reward.startsWith("EFFECT:")) {
            try {
                String[] parts = reward.split(":");
                String effect = prettyEffectName(parts[1]);
                String tier = parts.length > 2 ? toRoman(Integer.parseInt(parts[2])) : "I";
                return t(player, "level_reward_effect_line",
                        vars("effect", effect, "tier", tier),
                        "&b✦ {EFFECT} {TIER}");
            } catch (Throwable ignored) {
                return "&b✦ " + reward;
            }
        }

        if (reward.startsWith("MEMBERS:")) {
            try {
                int amount = Integer.parseInt(reward.substring("MEMBERS:".length()));
                return t(player, "level_reward_members_line",
                        vars("amount", String.valueOf(amount), "suffix", amount == 1 ? "" : "s"),
                        "&a✦ +{AMOUNT} member slot{SUFFIX}");
            } catch (Throwable ignored) {
                return "&a✦ " + reward;
            }
        }

        if (reward.startsWith("FLAG:")) {
            String flag = prettyFlagName(reward.substring("FLAG:".length()));
            return t(player, "level_reward_flag_line",
                    vars("flag", flag),
                    "&d✦ Unlocks {FLAG}");
        }

        if (reward.equalsIgnoreCase("FLIGHT") || reward.equalsIgnoreCase("FLY")) {
            return t(player, "level_reward_flag_line",
                    vars("flag", prettyFlagName("fly")),
                    "&d✦ Unlocks {FLAG}");
        }

        return "&a✦ " + humanizeToken(reward);
    }

    private String prettyEffectName(String raw) {
        String key = raw == null ? "" : raw.trim().toUpperCase();
        return switch (key) {
            case "FAST_DIGGING" -> "Haste";
            case "INCREASE_DAMAGE" -> "Strength";
            case "DAMAGE_RESISTANCE" -> "Resistance";
            case "JUMP" -> "Jump Boost";
            case "SLOW_FALLING" -> "Slow Falling";
            case "NIGHT_VISION" -> "Night Vision";
            case "WATER_BREATHING" -> "Water Breathing";
            case "DOLPHINS_GRACE" -> "Dolphin's Grace";
            case "CONDUIT_POWER" -> "Conduit Power";
            default -> humanizeToken(key);
        };
    }

    private String prettyFlagName(String raw) {
        String key = raw == null ? "" : raw.trim().toLowerCase();
        return switch (key) {
            case "fly" -> "Claim Flight";
            case "safe-zone" -> "Safe Zone";
            case "shop-interact" -> "Trade Stall Access";
            default -> humanizeToken(key);
        };
    }

    private String humanizeToken(String raw) {
        if (raw == null || raw.isBlank()) return "Unknown";
        String[] parts = raw.replace('-', ' ').replace('_', ' ').toLowerCase().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.toString();
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
    // SAFE LANGUAGE WRAPPERS (prevents raw keys / [Missing])
    // + Legacy placeholder patching
    // --------------------------------------------------

    private String t(Player p, String key, String fallback) {
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(p, key);
        } catch (Throwable ignored) { }
        return GUIManager.safeText(key, raw, fallback);
    }

    private String t(Player p, String key, Map<String, String> vars, String fallback) {
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(p, key, (vars == null ? Map.of() : vars));
        } catch (Throwable ignored) { }
        String safe = GUIManager.safeText(key, raw, fallback);
        return applyVarsCompat(safe, vars);
    }

    private List<String> tl(Player p, String key, Map<String, String> vars, List<String> fallback) {
        List<String> out = null;
        try {
            if (plugin.codex() != null) out = plugin.codex().trList(p, key, (vars == null ? Map.of() : vars));
        } catch (Throwable ignored) { }

        if (out == null || out.isEmpty()) return (fallback == null ? List.of() : applyVarsCompat(fallback, vars));

        if (out.size() == 1) {
            String one = out.get(0);
            if (one == null) return (fallback == null ? List.of() : applyVarsCompat(fallback, vars));
            String s = one.trim();
            if (s.isEmpty() || s.contains("[Missing") || s.equalsIgnoreCase(key)) {
                return (fallback == null ? List.of() : applyVarsCompat(fallback, vars));
            }
        }

        return applyVarsCompat(out, vars);
    }

    /**
     * Supports multiple placeholder styles:
     *  - {KEY}
     *  - (KEY)
     *  - %KEY%
     *  - <KEY>
     */
    private String applyVarsCompat(String s, Map<String, String> vars) {
        if (s == null || s.isEmpty() || vars == null || vars.isEmpty()) return s;
        String out = s;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String k = e.getKey();
            String v = String.valueOf(e.getValue());
            out = out
                    .replace("{" + k + "}", v)
                    .replace("(" + k + ")", v)
                    .replace("%" + k + "%", v)
                    .replace("<" + k + ">", v);
        }
        return out;
    }

    private List<String> applyVarsCompat(List<String> lines, Map<String, String> vars) {
        if (lines == null || lines.isEmpty() || vars == null || vars.isEmpty()) {
            return (lines == null ? List.of() : lines);
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(applyVarsCompat(line, vars));
        }
        return out;
    }

    /**
     * Build a vars map that supports BOTH lowercase and uppercase keys.
     */
    private Map<String, String> vars(Object... kv) {
        Map<String, String> m = new HashMap<>();
        if (kv == null) return m;

        for (int i = 0; i + 1 < kv.length; i += 2) {
            String k = String.valueOf(kv[i]);
            String v = String.valueOf(kv[i + 1]);
            m.put(k, v);
            m.put(k.toLowerCase(), v);
            if (!k.isEmpty()) {
                m.put(Character.toUpperCase(k.charAt(0)) + k.substring(1).toLowerCase(), v);
            }
            m.put(k.toUpperCase(), v);
        }
        return m;
    }
}
