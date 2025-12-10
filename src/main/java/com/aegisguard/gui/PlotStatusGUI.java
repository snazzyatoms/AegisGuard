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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        // Title
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

        // 1. Header Info
        List<String> headerLore = new ArrayList<>();
        headerLore.add("§7Owner: §f" + owner);
        headerLore.add("§7World: §f" + world);
        headerLore.add("§7Biome: §a" + biomeName);
        headerLore.add("");
        headerLore.add("§7Plot Level: §b" + level + "§7 / §f" + maxLevel);

        String headerTitleKey = plugin.msg().get(player, "plot_status_header_title");
        inv.setItem(4, GUIManager.createItem(
                Material.NETHER_STAR,
                GUIManager.safeText(headerTitleKey, "§6Plot Information"),
                headerLore
        ));

        // 2. Blessings (fancy + deduped)
        List<String> buffsLore = new ArrayList<>();
        buffsLore.add("§7Active Blessings:");
        buffsLore.add("");

        List<String> buffs = buildBuffList(level);
        if (buffs.isEmpty()) {
            buffsLore.add("§8- None unlocked yet.");
        } else {
            buffsLore.addAll(buffs);
            buffsLore.add("");
            buffsLore.add("§8Only your highest tier of each blessing is shown.");
        }

        String blessingsTitleKey = plugin.msg().get(player, "plot_status_blessings_title");
        inv.setItem(22, GUIManager.createItem(
                Material.ENCHANTED_BOOK,
                GUIManager.safeText(blessingsTitleKey, "§dActive Blessings"),
                buffsLore
        ));

        // 3. Territory
        List<String> territoryLore = new ArrayList<>();
        territoryLore.add("§7Territory Rules:");
        territoryLore.add("§bAegis Menu §7→ §b" + plugin.msg().get(player, "button_expand"));

        String territoryTitleKey = plugin.msg().get(player, "plot_status_territory_title");
        inv.setItem(24, GUIManager.createItem(
                Material.GRASS_BLOCK,
                GUIManager.safeText(territoryTitleKey, "§aTerritory & Growth"),
                territoryLore
        ));

        // 4. Back
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
        if (e.getSlot() == 49) {
            GUIManager.playClick(player);
            plugin.gui().openMain(player);
        }
    }

    /**
     * Build a clean, professional blessing list:
     * - Merges rewards from level 1..level
     * - Keeps only the highest tier per blessing
     * - Formats EFFECT and MEMBERS nicely
     */
    private List<String> buildBuffList(int level) {
        List<String> result = new ArrayList<>();
        if (level <= 0) return result;

        // key -> highest numeric tier (if any)
        Map<String, Integer> highestTier = new LinkedHashMap<>();
        // key -> raw reward string (for formatting later)
        Map<String, String> rewardByKey = new LinkedHashMap<>();

        for (int i = 1; i <= level; i++) {
            List<String> rewards = plugin.cfg().getLevelRewards(i);
            if (rewards == null) continue;

            for (String reward : rewards) {
                if (reward == null) continue;
                reward = reward.trim();
                if (reward.isEmpty()) continue;

                String[] parts = reward.split(":");
                String key;
                Integer tier = null;

                if (parts.length == 3 && isInteger(parts[2])) {
                    // e.g. EFFECT:SPEED:2
                    key = (parts[0] + ":" + parts[1]).toUpperCase();
                    tier = Integer.parseInt(parts[2]);
                } else if (parts.length == 2 && isInteger(parts[1])) {
                    // e.g. MEMBERS:2
                    key = parts[0].toUpperCase();
                    tier = Integer.parseInt(parts[1]);
                } else {
                    // Non-numeric or custom reward, treat whole line as key
                    key = reward.toUpperCase();
                }

                Integer current = highestTier.get(key);
                if (current == null || (tier != null && tier > current)) {
                    rewardByKey.put(key, reward);
                    if (tier != null) {
                        highestTier.put(key, tier);
                    }
                }
            }
        }

        if (rewardByKey.isEmpty()) {
            return result;
        }

        for (Map.Entry<String, String> entry : rewardByKey.entrySet()) {
            String reward = entry.getValue();
            String[] parts = reward.split(":");

            // EFFECT:TYPE:LEVEL
            if (parts.length == 3 && isInteger(parts[2]) && parts[0].equalsIgnoreCase("EFFECT")) {
                String effectKey = parts[1];
                int tier = Integer.parseInt(parts[2]);
                String effectName = formatName(effectKey);
                String roman = toRoman(tier);

                String color;
                if (tier >= 4) {
                    color = "§d"; // epic
                } else if (tier >= 2) {
                    color = "§b"; // rare
                } else {
                    color = "§a"; // common
                }

                result.add(color + "✦ §f" + effectName + " §7(Effect §f" + roman + "§7)");
                continue;
            }

            // MEMBERS:AMOUNT
            if (parts.length == 2 && isInteger(parts[1]) && parts[0].equalsIgnoreCase("MEMBERS")) {
                int amount = Integer.parseInt(parts[1]);
                result.add("§a✦ §fTrusted member slots: §b+" + amount);
                continue;
            }

            // Fallback – pretty print anything unknown
            String pretty = reward.replace("EFFECT:", "")
                                  .replace("MEMBERS:", "")
                                  .replace(":", " ");
            pretty = formatName(pretty);

            result.add("§a✦ §f" + pretty);
        }

        return result;
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String toRoman(int n) {
        switch (n) {
            case 1:  return "I";
            case 2:  return "II";
            case 3:  return "III";
            case 4:  return "IV";
            case 5:  return "V";
            default: return String.valueOf(n);
        }
    }

    private String formatName(String key) {
        String lower = key.toLowerCase().replace("_", " ");
        String[] words = lower.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
