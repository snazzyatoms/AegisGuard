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
import java.util.UUID;

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

        // Title (Codex → fallback)
        String baseTitle = plugin.codex().tr(player, "plot_status_gui_title");
        String title = GUIManager.safeText(baseTitle, "§8Plot Status Codex");

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

        String headerTitleBase = plugin.codex().tr(player, "plot_status_header_title");
        String headerTitle = GUIManager.safeText(headerTitleBase, "§6Plot Information");
        inv.setItem(4, GUIManager.createItem(
                Material.NETHER_STAR,
                headerTitle,
                headerLore
        ));

        // 2. Protections & Risks (live from ProtectionManager / flags)
        List<String> protectionLore = buildProtectionLore(plot);
        String protTitleBase = plugin.codex().tr(player, "plot_status_protection_title");
        String protTitle = GUIManager.safeText(protTitleBase, "§cProtections & Risks");

        inv.setItem(20, GUIManager.createItem(
                Material.SHIELD,
                protTitle,
                protectionLore
        ));

        // 3. Blessings (fancy + deduped)
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

        String blessingsTitleBase = plugin.codex().tr(player, "plot_status_blessings_title");
        String blessingsTitle = GUIManager.safeText(blessingsTitleBase, "§dActive Blessings");
        inv.setItem(22, GUIManager.createItem(
                Material.ENCHANTED_BOOK,
                blessingsTitle,
                buffsLore
        ));

        // 4. Territory card (unchanged, but title via Codex)
        List<String> territoryLore = new ArrayList<>();
        territoryLore.add("§7Territory Rules:");
        territoryLore.add("§bAegis Menu §7→ §b" + plugin.msg().get(player, "button_expand"));

        String territoryTitleBase = plugin.codex().tr(player, "plot_status_territory_title");
        String territoryTitle = GUIManager.safeText(territoryTitleBase, "§aTerritory & Growth");
        inv.setItem(24, GUIManager.createItem(
                Material.GRASS_BLOCK,
                territoryTitle,
                territoryLore
        ));

        // -------------------------------------------------------------
        // ✅ NEW: DOMAIN REGISTRY (Claim Block Budget)
        // -------------------------------------------------------------
        UUID plotOwnerUUID = plot.getOwner();
        long totalBlocks = plugin.getClaimBlockManager().getTotalBlocks(plotOwnerUUID);
        long usedBlocks = plugin.getClaimBlockManager().getUsedBlocks(plotOwnerUUID);
        long availBlocks = plugin.getClaimBlockManager().getAvailableBlocks(plotOwnerUUID);

        List<String> budgetLore = new ArrayList<>();
        budgetLore.add("§7Available: §a" + availBlocks);
        budgetLore.add("§7Used: §c" + usedBlocks);
        budgetLore.add("§7Total Capacity: §e" + totalBlocks);
        budgetLore.add("");
        budgetLore.add("§8This budget applies to all plots");
        budgetLore.add("§8owned by §f" + owner + "§8.");

        inv.setItem(26, GUIManager.createItem(
                Material.PAPER,
                "§6📜 Domain Registry",
                budgetLore
        ));

        // 5. Back
        String backName = plugin.codex().tr(player, "button_back");
        if (backName == null || backName.isEmpty()) backName = "§fBack";

        List<String> backLore = plugin.codex().list(player, "back_lore");
        if (backLore == null) backLore = plugin.msg().getList(player, "back_lore");

        inv.setItem(49, GUIManager.createItem(
                Material.ARROW,
                backName,
                backLore
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

    // --------------------------------------------------
    // PROTECTION OVERVIEW (live from ProtectionManager)
    // --------------------------------------------------

    private List<String> buildProtectionLore(Plot plot) {
        List<String> lore = new ArrayList<>();

        boolean pvpProtected        = plugin.protection().isFlagEnabled(plot, "pvp");
        boolean mobProtected        = plugin.protection().isMobProtectionEnabled(plot);
        boolean animalsProtected    = plugin.protection().isFlagEnabled(plot, "animals");
        boolean containersProtected = plugin.protection().isFlagEnabled(plot, "containers");
        boolean redstoneProtected   = plugin.protection().isFlagEnabled(plot, "redstone");
        boolean vehiclesProtected   = plugin.protection().isFlagEnabled(plot, "vehicles");
        boolean safeZone            = plugin.protection().isSafeZoneEnabled(plot);

        boolean shopEnabled         = plot.getFlag("shop-interact", false);
        boolean flyEnabled          = plot.getFlag("fly", false);

        // Entry: true = OPEN, false = CLOSED for non-trusted
        boolean entryOpen           = plot.getFlag("entry", true);

        lore.add("§7Combat & Hostiles:");
        lore.add(formatProtectionLine("PvP", pvpProtected));
        lore.add(formatProtectionLine("Hostile mobs", mobProtected));
        lore.add(formatProtectionLine("Animals & pets", animalsProtected));
        lore.add("");

        lore.add("§7Environment & Access:");
        lore.add(formatSafeZoneLine(safeZone));
        lore.add(formatEntryLine(entryOpen));
        lore.add(formatProtectionLine("Containers", containersProtected));
        lore.add(formatProtectionLine("Redstone & doors", redstoneProtected));
        lore.add(formatProtectionLine("Vehicles", vehiclesProtected));
        lore.add("");

        lore.add("§7Perks & Services:");
        lore.add(formatSimpleToggleLine("Market shop interact", shopEnabled));
        lore.add(formatSimpleToggleLine("Flight inside this plot", flyEnabled));

        return lore;
    }

    private String formatProtectionLine(String label, boolean protectedOn) {
        // true = protection ON (safe), false = vulnerable
        String state = protectedOn ? "§aProtected" : "§cVulnerable";
        return "§f- " + label + ": " + state;
    }

    private String formatSafeZoneLine(boolean safeZone) {
        String state = safeZone ? "§aEnabled" : "§7Disabled";
        return "§f- Safe zone: " + state;
    }

    private String formatEntryLine(boolean open) {
        String state = open ? "§aOpen" : "§cClosed";
        return "§f- Entry gate: " + state;
    }

    private String formatSimpleToggleLine(String label, boolean enabled) {
        String state = enabled ? "§aEnabled" : "§7Inactive";
        return "§f- " + label + ": " + state;
    }

    // --------------------------------------------------
    // BLESSINGS (unchanged, just used by leveling/buffs)
    // --------------------------------------------------

    private List<String> buildBuffList(int level) {
        List<String> result = new ArrayList<>();
        if (level <= 0) return result;

        Map<String, Integer> highestTier = new LinkedHashMap<>();
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
                    key = (parts[0] + ":" + parts[1]).toUpperCase();
                    tier = Integer.parseInt(parts[2]);
                } else if (parts.length == 2 && isInteger(parts[1])) {
                    key = parts[0].toUpperCase();
                    tier = Integer.parseInt(parts[1]);
                } else {
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

            if (parts.length == 2 && isInteger(parts[1]) && parts[0].equalsIgnoreCase("MEMBERS")) {
                int amount = Integer.parseInt(parts[1]);
                result.add("§a✦ §fTrusted member slots: §b+" + amount);
                continue;
            }

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
