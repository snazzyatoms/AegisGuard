package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * PlotStatusGUI
 * - Replaces the old Sidebar / scoreboard view.
 * - Shows current plot level, biome, and all unlocked perks inside a GUI page.
 */
public class PlotStatusGUI {

    private final AegisGuard plugin;

    public PlotStatusGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlotStatusHolder implements InventoryHolder {
        private final Plot plot;

        public PlotStatusHolder(Plot plot) {
            this.plot = plot;
        }

        public Plot getPlot() {
            return plot;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /**
     * Open the status GUI for the plot the player is currently standing in.
     */
    public void open(Player player) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }

        String rawTitle = plugin.msg().get(player, "plot_status_title"); // optional key
        String title = GUIManager.safeText(rawTitle, "§dPlot Boons & Blessings");
        Inventory inv = Bukkit.createInventory(new PlotStatusHolder(plot), 54, title);

        // Filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        int maxLevel = plugin.cfg().getMaxLevel();
        int currentLevel = plot.getLevel();

        // ---------------------------------------------------------------------
        // 1. Header: Plot summary (center top)
        // ---------------------------------------------------------------------
        List<String> headerLore = new ArrayList<>();
        headerLore.add("§7Owner: §f" + plot.getOwnerName());
        headerLore.add("§7World: §f" + plot.getWorld());
        headerLore.add("");
        headerLore.add("§7Current Level: §b" + currentLevel + "§7 / §f" + maxLevel);

        String biome = plot.getCustomBiome();
        if (biome == null) biome = "Natural";
        biome = biome.toLowerCase(Locale.ROOT).replace("_", " ");
        if (!biome.isEmpty()) {
            biome = biome.substring(0, 1).toUpperCase(Locale.ROOT) + biome.substring(1);
        }
        headerLore.add("§7Biome: §a" + biome);

        inv.setItem(4, GUIManager.createItem(
                Material.NETHER_STAR,
                "§bDominion Overview",
                headerLore
        ));

        // ---------------------------------------------------------------------
        // 2. Level & progression summary (left panel)
        // ---------------------------------------------------------------------
        List<String> levelLore = new ArrayList<>();
        levelLore.add("§7Your dominion grows in power");
        levelLore.add("§7as its level rises.");
        levelLore.add("");
        levelLore.add("§7Current Level: §b" + currentLevel);

        if (currentLevel >= maxLevel) {
            levelLore.add("§aYou have reached max level.");
        } else {
            int next = currentLevel + 1;
            double nextCost = calculateCost(next);
            String costStr = plugin.eco().format(nextCost, plugin.cfg().getLevelCostType());
            levelLore.add("");
            levelLore.add("§7Next Tier: §b" + next);
            levelLore.add("§7Upgrade Cost: §e" + costStr);
        }

        inv.setItem(20, GUIManager.createItem(
                Material.EXPERIENCE_BOTTLE,
                "§aAscension Summary",
                levelLore
        ));

        // ---------------------------------------------------------------------
        // 3. All unlocked buffs/perks (middle panel)
        // ---------------------------------------------------------------------
        List<String> buffLines = buildBuffLines(currentLevel);
        if (buffLines.isEmpty()) {
            buffLines.add("§8- None yet. Level up to");
            buffLines.add("§8  unlock dominion boons.");
        }

        inv.setItem(22, GUIManager.createItem(
                Material.ENCHANTED_BOOK,
                "§6Unlocked Blessings",
                buffLines
        ));

        // ---------------------------------------------------------------------
        // 4. Flags & utility perks (right panel)
        // ---------------------------------------------------------------------
        List<String> utilityLines = buildUtilityLines(currentLevel);
        if (utilityLines.isEmpty()) {
            utilityLines.add("§8- None yet.");
        }

        inv.setItem(24, GUIManager.createItem(
                Material.FEATHER,
                "§bDomain Perks",
                utilityLines
        ));

        // ---------------------------------------------------------------------
        // 5. Back button
        // ---------------------------------------------------------------------
        inv.setItem(49, GUIManager.createItem(
                Material.ARROW,
                plugin.msg().get(player, "button_back"),
                plugin.msg().getList(player, "back_lore")
        ));

        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    /**
     * Handles clicks inside this GUI.
     */
    public void handleClick(Player player, InventoryClickEvent e, PlotStatusHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();

        // Back to main Aegis menu
        if (slot == 49) {
            plugin.gui().openMain(player);
            return;
        }

        // Other clicks are just informational, no actions.
        GUIManager.playClick(player);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a combined list of all EFFECT-based buffs the plot has unlocked
     * across all levels up to currentLevel.
     */
    private List<String> buildBuffLines(int currentLevel) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int level = 1; level <= currentLevel; level++) {
            List<String> rewards = plugin.cfg().getLevelRewards(level);
            if (rewards == null) continue;

            for (String reward : rewards) {
                if (!reward.startsWith("EFFECT:")) continue;

                try {
                    String[] parts = reward.split(":");
                    if (parts.length < 3) continue;

                    String type = parts[1].toLowerCase(Locale.ROOT).replace("_", " ");
                    type = capitalize(type);
                    int amp = Integer.parseInt(parts[2]);
                    String roman = toRoman(amp);

                    String key = "EFFECT:" + type + ":" + roman;
                    if (seen.add(key)) {
                        result.add("§b✦ " + type + " " + roman);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (!result.isEmpty()) {
            // Add subtle header
            result.add(0, "§7These boons are active");
            result.add(1, "§7while inside this dominion:");
            result.add(2, "");
        }

        return result;
    }

    /**
     * Builds lines for non-effect perks like FLAGS, MEMBERS, FLIGHT.
     */
    private List<String> buildUtilityLines(int currentLevel) {
        int totalMemberSlots = 0;
        boolean hasFlyFlag = false;
        boolean hasFlightPower = false;

        for (int level = 1; level <= currentLevel; level++) {
            List<String> rewards = plugin.cfg().getLevelRewards(level);
            if (rewards == null) continue;

            for (String reward : rewards) {
                if (reward.startsWith("MEMBERS:")) {
                    try {
                        String[] parts = reward.split(":");
                        int add = Integer.parseInt(parts[1]);
                        totalMemberSlots += add;
                    } catch (Exception ignored) {
                    }
                } else if (reward.startsWith("FLAG:")) {
                    String flagName = reward.substring("FLAG:".length()).toLowerCase(Locale.ROOT);
                    if (flagName.equals("fly")) {
                        hasFlyFlag = true;
                    }
                } else if (reward.equalsIgnoreCase("FLIGHT")) {
                    hasFlightPower = true;
                }
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("§7Non-combat perks and");
        lines.add("§7quality-of-life boons:");
        lines.add("");

        if (totalMemberSlots > 0) {
            lines.add("§a✦ Extra member capacity: §f+" + totalMemberSlots);
        }

        if (hasFlyFlag) {
            lines.add("§b✦ Flight flag unlocked inside this dominion");
        }

        if (hasFlightPower) {
            lines.add("§d✦ Ascended: Plot grants FLIGHT");
        }

        if (totalMemberSlots == 0 && !hasFlyFlag && !hasFlightPower) {
            lines.add("§8- None yet.");
        }

        return lines;
    }

    private double calculateCost(int level) {
        double base = plugin.cfg().getLevelBaseCost();
        double mult = plugin.cfg().getLevelCostMultiplier();
        return base * (level * mult);
    }

    private String capitalize(String in) {
        if (in == null || in.isEmpty()) return in;
        return in.substring(0, 1).toUpperCase(Locale.ROOT) + in.substring(1);
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
}
