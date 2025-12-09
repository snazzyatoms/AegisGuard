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
 * - Fully linked to messages.yml for translation support.
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

        // --- TITLE LINKED TO MESSAGES.YML ---
        // key: plot_status_gui_title
        String titleKey = plugin.msg().get(player, "plot_status_gui_title");
        String title = GUIManager.safeText(titleKey, "§8Plot Status Codex");

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

        // Biome formatting
        String biomeRaw = plot.getCustomBiome();
        String biomeName = (biomeRaw == null || biomeRaw.isEmpty()) ? "Natural" : biomeRaw;
        biomeName = biomeName.toLowerCase().replace("_", " ");
        if (!biomeName.isEmpty()) {
            biomeName = Character.toUpperCase(biomeName.charAt(0)) + biomeName.substring(1);
        }

        // 1. Header: Basic Plot Info
        List<String> headerLore = new ArrayList<>();
        headerLore.add("§7Owner: §f" + owner);
        headerLore.add("§7World: §f" + world);
        headerLore.add("§7Biome: §a" + biomeName);
        headerLore.add("");
        headerLore.add("§7Plot Level: §b" + level + "§7 / §f" + maxLevel);
        headerLore.add("");
        headerLore.add("§8These blessings apply while");
        headerLore.add("§8you stand within this dominion.");

        // --- HEADER ITEM LINKED TO MESSAGES.YML ---
        // key: plot_status_header_title
        String headerTitleKey = plugin.msg().get(player, "plot_status_header_title");
        inv.setItem(4, GUIManager.createItem(
                Material.NETHER_STAR,
                GUIManager.safeText(headerTitleKey, "§6Plot Information"),
                headerLore
        ));

        // 2. Blessings Panel
        List<String> buffsLore = new ArrayList<>();
        buffsLore.add("§7Active Blessings:");

        List<String> buffs = buildBuffList(level);
        if (buffs.isEmpty()) {
            buffsLore.add("§8- (None)");
        } else {
            buffsLore.addAll(buffs);
        }

        // --- BLESSINGS ITEM LINKED TO MESSAGES.YML ---
        // key: plot_status_blessings_title
        String blessingsTitleKey = plugin.msg().get(player, "plot_status_blessings_title");
        inv.setItem(22, GUIManager.createItem(
                Material.ENCHANTED_BOOK,
                GUIManager.safeText(blessingsTitleKey, "§dActive Blessings"),
                buffsLore
        ));

        // 3. Territory Info Panel
        List<String> territoryLore = new ArrayList<>();
        territoryLore.add("§7Territory Growth Rules:");
        territoryLore.add("§8Leveling no longer expands land.");
        territoryLore.add("§8Request more land via:");
        territoryLore.add("§bAegis Menu §7→ §b" + plugin.msg().get(player, "button_expand"));
        territoryLore.add("");
        territoryLore.add("§7This keeps plot leveling");
        territoryLore.add("§7separate from expansion requests.");

        // --- TERRITORY ITEM LINKED TO MESSAGES.YML ---
        // key: plot_status_territory_title
        String territoryTitleKey = plugin.msg().get(player, "plot_status_territory_title");
        inv.setItem(24, GUIManager.createItem(
                Material.GRASS_BLOCK,
                GUIManager.safeText(territoryTitleKey, "§aTerritory & Growth"),
                territoryLore
        ));

        // 4. Back Button
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

        // Fix: Back button logic
        if (slot == 49) {
            GUIManager.playClick(player);
            plugin.gui().openMain(player);
            return;
        }

        // Optional: Play generic click for other items
        // GUIManager.playClick(player); 
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
        return Character.toUpperCase(in.charAt(0)) + in.substring(1);
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
