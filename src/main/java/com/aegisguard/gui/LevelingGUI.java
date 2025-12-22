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
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LevelingGUI
 * - Currency-aware Ascension:
 *   - If Vault (or money economy) is enabled + present -> show "Pay Money" button.
 *   - If Vault is disabled or missing -> fallback to "Spend Blocks" upgrades.
 *   - If both are available -> show both buttons (money + blocks).
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

            // If money is not available, we want to clearly fallback to blocks.
            // If BOTH are not available, we show a "cannot upgrade" item.
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
                // --- Preview item (always shown in the middle if we have at least one method) ---
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
                previewLore.addAll(formatBuffs(nextLvl));

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

                // --- Buttons: if both available, show two buttons; otherwise one centered button ---
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
                    // Single method mode: put the one available upgrade button in the center (slot 31),
                    // replacing the preview, but we keep a nice “preview” inside the lore.
                    boolean useMoney = moneyAllowed; // if moneyAllowed true and blocks not, use money; else blocks.

                    CurrencyType payType = useMoney ? moneyType : blocksType;
                    double payCost = useMoney ? moneyCost : blocksCost;

                    List<String> upgradeLore = new ArrayList<>();

                    // Reuse a compact preview inside the button
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
                    upgradeLore.addAll(formatBuffs(nextLvl));

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

        // Track clicks (cosmetic)
        if (slot >= 19 && slot <= 25) {
            GUIManager.playClick(player);
            return;
        }

        // Upgrade buttons:
        // - Dual button mode: slot 30 = money, slot 32 = blocks
        // - Single button mode: slot 31 = whichever is available
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
        // If money is available -> money
        // else -> blocks
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

        // Guard: if button exists but method not allowed (stale GUI / config changed)
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

        // Withdraw first
        if (!plugin.eco().withdraw(player, payCost, payType)) {
            // You can make this message key more specific later (money vs blocks),
            // but this preserves existing behavior safely.
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
            plotNow.setX1(newX1); plotNow.setX2(newX2);
            plotNow.setZ1(newZ1); plotNow.setZ2(newZ2);
            plugin.store().addPlot(plotNow);
        }

        // Level up event + persist
        PlotLevelUpEvent event = new PlotLevelUpEvent(plotNow, player, nextLvl);
        Bukkit.getPluginManager().callEvent(event);

        plotNow.setLevel(nextLvl);
        plugin.store().setDirty(true);

        plugin.msg().send(player, "level_up_success", Map.of("LEVEL", String.valueOf(nextLvl)));

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
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

        // Show the *effective* cost (money if available, otherwise blocks fallback)
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
        // If you already have a config key, keep it. This supports a few likely ones.
        if (!plugin.getConfig().getBoolean("leveling.blocks_upgrades_enabled", true)) return false;
        if (!plugin.getConfig().getBoolean("economy.blocks.enabled", true)) return false;
        return true;
    }

    private boolean isMoneyUpgradeAvailable(CurrencyType configuredType) {
        // If config is explicitly disabling Vault-based money upgrades, respect it.
        // (Supports common key names; you can standardize later.)
        boolean vaultEnabled = plugin.getConfig().getBoolean("economy.vault.enabled",
                plugin.getConfig().getBoolean("vault.enabled", true));

        if (configuredType == null) return false;

        // If it's a Vault-like currency type, require Vault to be present+enabled.
        if (isVaultLike(configuredType)) {
            if (!vaultEnabled) return false;
            return isVaultPresent();
        }

        // For non-vault currencies, assume available (your eco system handles it).
        // If you want a master economy toggle later, you can add it here.
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
            // Prefer the obvious
            return CurrencyType.valueOf("BLOCKS");
        } catch (Throwable ignored) {}

        try {
            // Common alternative naming
            return CurrencyType.valueOf("CLAIM_BLOCKS");
        } catch (Throwable ignored) {}

        return null;
    }

    private String formatBlocks(double d) {
        long v = Math.round(d);
        return String.valueOf(Math.max(0, v));
    }

    /**
     * Attempts to display player balance for the currency if your eco supports it.
     * If not found, returns "?" safely.
     */
    private String formatBalance(Player p, CurrencyType type) {
        double bal = tryGetBalance(p, type);
        if (bal < 0) return "?";
        return formatBlocks(bal);
    }

    private double tryGetBalance(Player p, CurrencyType type) {
        if (p == null || type == null) return -1;
        Object eco = null;
        try { eco = plugin.eco(); } catch (Throwable ignored) {}
        if (eco == null) return -1;

        // Try common method signatures via reflection:
        // getBalance(Player, CurrencyType) / balance(Player, CurrencyType)
        try {
            var m = eco.getClass().getMethod("getBalance", Player.class, CurrencyType.class);
            Object out = m.invoke(eco, p, type);
            return asDouble(out);
        } catch (Throwable ignored) {}

        try {
            var m = eco.getClass().getMethod("balance", Player.class, CurrencyType.class);
            Object out = m.invoke(eco, p, type);
            return asDouble(out);
        } catch (Throwable ignored) {}

        return -1;
    }

    private double asDouble(Object o) {
        if (o == null) return -1;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Throwable ignored) {}
        return -1;
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
