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

import java.util.ArrayList;
import java.util.List;

/**
 * PlotStatusGUI
 * - Replaces the old sidebar.
 * - Shows current plot level, biome, and all unlocked blessings.
 */
public class PlotStatusGUI {

    private final AegisGuard plugin;

    public PlotStatusGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlotStatusHolder implements InventoryHolder {
        private final Plot plot;
        public PlotStatusHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }

        String title = GUIManager.safeText(
                plugin.msg().get(player, "plot_status_gui_title"),
                "§aPlot Status Codex"
        );
        Inventory inv = Bukkit.createInventory(new PlotStatusHolder(plot), 54, title);

        // Filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        int level = plot.getLevel();
        int maxLevel = plugin.cfg().getMaxLevel();
        String owner = plot.getOwnerName();
        String world = plot.getWorld();

        String biome = plot.getCustomBiome();
        if (biome == null || biome.isEmpty()) {
            biome = "Natural";
        }
        biome = biome.toLowerCase().replace("_", " ");
        if (biome.length() > 0) {
            biome = biome.substring(0, 1).toUpperCase() + biome.substring(1);
        }

        // Header: basic plot info
        List<String> headerLore = new ArrayList<>();
        headerLore.add("§7Owner: §f" + owner);
        headerLore.add("§7World: §f" + world);
        headerLore.add("§7Biome: §a" + biome);
        headerLore.add("");
        headerLore.add("§7Plot Level: §b" + level + "§7 / §f" + maxLevel);
        headerLore.add("");
        headerLore.add("§8These blessings apply while");
        headerLore.add("§8you stand within this dominion.");

        inv.setItem(4, GUIManager.createItem(
                Material.NETHER_STAR,
                plugin.msg().get(player, "plot_status_header_title"),
                headerLore
        ));

        // Blessings panel
        List<String> buffsLore = new ArrayList<>();
        buffsLore.add("§7Active Blessings:");

        List<String> buffs = buildBuffList(level);
        if (buffs.isEmpty()) {
            buffsLore.add("§8- (None)");
        } else {
            buffsLore.addAll(buffs);
        }

        inv.setItem(22, GUIManager.createItem(
                Material.ENCHANTED_BOOK,
                plugin.msg().get(player, "plot_status_blessings_title"),
                buffsLore
        ));

        // Territory info panel, to make it clear that leveling no longer grows land
        List<String> territoryLore = new ArrayList<>();
        territoryLore.add("§7Territory Growth Rules:");
        territoryLore.add("§8Leveling no longer expands land.");
        territoryLore.add("§8Request more land via:");
        territoryLore.add("§bAegis Menu §7→ §b" + plugin.msg().get(player, "button_expand"));
        territoryLore.add("");
        territoryLore.add("§7This keeps plot leveling");
        territoryLore.add("§7separate from expansion requests.");

        inv.setItem(24, GUIManager.createItem(
                Material.GRASS_BLOCK,
                plugin.msg().get(player, "plot_status_territory_title"),
                territoryLore
        ));

        // Back
        inv.setItem(49, GUIManager.createItem(
                Material.ARROW,
                plugin.msg().get(player, "button_back"),
                plugin.msg().getList(player, "back_lore")
        ));

        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotStatusHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getSlot();

        if (slot == 49) {
            plugin.gui().openMain(player);
            return;
        }

        GUIManager.playClick(player);
    }

    // --------------------------------------------------
    // Helpers
    // --------------------------------------------------

    private List<String> buildBuffList(int level) {
        List<String> result = new ArrayList<>();
        if (level <= 0) return result;

        for (int i = 1; i <= level; i++) {
            List<String> rewards = plugin.cfg().getLevelRewards(i);
            if (rewards == null) continue;

            for (String reward : rewards) {
                if (reward.startsWith("EFFECT:")) {
                    try {
                        String[] parts = reward.split(":");
                        String type = parts[1].toLowerCase().replace("_", " ");
                        type = capitalize(type);
                        int amp = Integer.parseInt(parts[2]);
                        result.add("§b✦ " + type + " " + toRoman(amp));
                    } catch (Exception ignored) {
                        result.add("§b✦ " + reward);
                    }
                } else if (reward.startsWith("FLAG:")) {
                    String flagName = reward.substring("FLAG:".length()).toLowerCase().replace("_", " ");
                    flagName = capitalize(flagName);
                    result.add("§a✦ Flag: " + flagName);
                } else if (reward.equalsIgnoreCase("FLIGHT")) {
                    result.add("§a✦ Flight");
                } else if (reward.startsWith("MEMBERS:")) {
                    String amount = reward.substring("MEMBERS:".length());
                    result.add("§a✦ Extra Members: §f+" + amount);
                } else if (reward.startsWith("RADIUS:")) {
                    // Kept in text for history, but you can later strip if you never want RADIUS lines shown
                    String blocks = reward.substring("RADIUS:".length());
                    result.add("§7✦ Former Radius Gain: §f+" + blocks);
                } else {
                    result.add("§a✦ " + reward);
                }
            }
        }

        return result;
    }

    private String capitalize(String in) {
        if (in == null || in.isEmpty()) return in;
        return in.substring(0, 1).toUpperCase() + in.substring(1);
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
