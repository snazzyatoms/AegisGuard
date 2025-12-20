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
            // ✅ Language-aware system message (fallback safe)
            sendSystem(player, "no_plot_here", "&cYou are not standing in a protected plot.");
            return;
        }

        // ✅ Title via GUIManager helper (colors + clamp + fallback)
        String title = plugin.gui().title(player, "plot_status_gui_title", "&8Plot Status Codex");
        Inventory inv = Bukkit.createInventory(new PlotStatusHolder(plot), 54, title);

        // Filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        int level = plot.getLevel();
        int maxLevel = plugin.cfg().getMaxLevel();
        String owner = plot.getOwnerName();
        String world = plot.getWorld();

        // Biome formatting
        String biomeRaw = plot.getCustomBiome();
        String natural = tr(player, "plot_status_biome_natural", "Natural");
        String biomeName = (biomeRaw == null || biomeRaw.isEmpty()) ? natural : biomeRaw;
        biomeName = biomeName.toLowerCase().replace("_", " ");
        if (!biomeName.isEmpty()) biomeName = Character.toUpperCase(biomeName.charAt(0)) + biomeName.substring(1);

        // 1) Header Info
        List<String> headerLore = new ArrayList<>();
        headerLore.add(tr(player, "plot_status_header_owner", "&7Owner: &f{OWNER}", Map.of("OWNER", owner)));
        headerLore.add(tr(player, "plot_status_header_world", "&7World: &f{WORLD}", Map.of("WORLD", world)));
        headerLore.add(tr(player, "plot_status_header_biome", "&7Biome: &a{BIOME}", Map.of("BIOME", biomeName)));
        headerLore.add("");
        headerLore.add(tr(player, "plot_status_header_level", "&7Plot Level: &b{LEVEL}&7 / &f{MAX}",
                Map.of("LEVEL", String.valueOf(level), "MAX", String.valueOf(maxLevel))));

        String headerTitle = tr(player, "plot_status_header_title", "&6Plot Information");
        inv.setItem(4, GUIManager.createItem(
                Material.NETHER_STAR,
                headerTitle,
                headerLore
        ));

        // 2) Protections & Risks
        String protTitle = tr(player, "plot_status_protection_title", "&cProtections & Risks");
        inv.setItem(20, GUIManager.createItem(
                Material.SHIELD,
                protTitle,
                buildProtectionLore(player, plot)
        ));

        // 3) Blessings
        List<String> buffsLore = new ArrayList<>();
        buffsLore.add(tr(player, "plot_status_blessings_header", "&7Active Blessings:"));
        buffsLore.add("");

        List<String> buffs = buildBuffList(player, level);
        if (buffs.isEmpty()) {
            buffsLore.add(tr(player, "plot_status_blessings_none", "&8- None unlocked yet."));
        } else {
            buffsLore.addAll(buffs);
            buffsLore.add("");
            buffsLore.add(tr(player, "plot_status_blessings_footer", "&8Only your highest tier of each blessing is shown."));
        }

        String blessingsTitle = tr(player, "plot_status_blessings_title", "&dActive Blessings");
        inv.setItem(22, GUIManager.createItem(
                Material.ENCHANTED_BOOK,
                blessingsTitle,
                buffsLore
        ));

        // 4) Territory
        String territoryTitle = tr(player, "plot_status_territory_title", "&aTerritory & Growth");
        String expandName = tr(player, "button_expand", "&bExpand");

        List<String> territoryLore = new ArrayList<>();
        territoryLore.add(tr(player, "plot_status_territory_rules", "&7Territory Rules:"));
        territoryLore.add(tr(player, "plot_status_territory_path", "&bAegis Menu &7→ &b{EXPAND}", Map.of("EXPAND", expandName)));

        inv.setItem(24, GUIManager.createItem(
                Material.GRASS_BLOCK,
                territoryTitle,
                territoryLore
        ));

        // 5) Domain Registry (Claim Blocks) - safe if feature disabled
        inv.setItem(26, GUIManager.createItem(
                Material.PAPER,
                tr(player, "ledger_title", "&6📜 Domain Registry"),
                buildLedgerLore(player, plot)
        ));

        // 6) Back
        String backName = tr(player, "button_back", "&fBack");
        List<String> backLore = plugin.gui().trList(player, "back_lore", List.of("&7Return to the main menu."));

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

    private List<String> buildLedgerLore(Player player, Plot plot) {
        List<String> lore = new ArrayList<>();

        if (plugin.getClaimBlockManager() == null) {
            lore.add(tr(player, "plot_status_ledger_disabled_1", "&8Claim blocks are disabled."));
            lore.add(tr(player, "plot_status_ledger_disabled_2", "&7Ask an admin to enable:"));
            lore.add(tr(player, "plot_status_ledger_disabled_3", "&fclaim_blocks.enabled: &atrue"));
            return lore;
        }

        UUID ownerUUID = plot.getOwner();
        String ownerName = plot.getOwnerName();

        long totalBlocks = plugin.getClaimBlockManager().getTotalBlocks(ownerUUID);
        long usedBlocks  = plugin.getClaimBlockManager().getUsedBlocks(ownerUUID);
        long availBlocks = plugin.getClaimBlockManager().getAvailableBlocks(ownerUUID);

        String availLabel = tr(player, "ledger_available", "&7Available: &a");
        String usedLabel  = tr(player, "ledger_used", "&7Used: &c");
        String totalLabel = tr(player, "ledger_total", "&7Total Capacity: &e");

        lore.add(availLabel + availBlocks);
        lore.add(usedLabel + usedBlocks);
        lore.add(totalLabel + totalBlocks);
        lore.add("");

        lore.add(tr(player, "plot_status_ledger_footer_1", "&8This budget applies to all plots"));
        lore.add(tr(player, "plot_status_ledger_footer_2", "&8owned by &f{OWNER}&8.", Map.of("OWNER", ownerName)));

        return lore;
    }

    // --------------------------------------------------
    // PROTECTION OVERVIEW (live from ProtectionManager)
    // --------------------------------------------------

    private List<String> buildProtectionLore(Player player, Plot plot) {
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

        boolean entryOpen           = plot.getFlag("entry", true);

        lore.add(tr(player, "plot_status_section_combat", "&7Combat & Hostiles:"));
        lore.add(formatProtectionLine(player, "plot_status_label_pvp", "PvP", pvpProtected));
        lore.add(formatProtectionLine(player, "plot_status_label_hostile_mobs", "Hostile mobs", mobProtected));
        lore.add(formatProtectionLine(player, "plot_status_label_animals", "Animals & pets", animalsProtected));
        lore.add("");

        lore.add(tr(player, "plot_status_section_environment", "&7Environment & Access:"));
        lore.add(formatSafeZoneLine(player, safeZone));
        lore.add(formatEntryLine(player, entryOpen));
        lore.add(formatProtectionLine(player, "plot_status_label_containers", "Containers", containersProtected));
        lore.add(formatProtectionLine(player, "plot_status_label_redstone", "Redstone & doors", redstoneProtected));
        lore.add(formatProtectionLine(player, "plot_status_label_vehicles", "Vehicles", vehiclesProtected));
        lore.add("");

        lore.add(tr(player, "plot_status_section_perks", "&7Perks & Services:"));
        lore.add(formatSimpleToggleLine(player, "plot_status_label_shop_interact", "Market shop interact", shopEnabled));
        lore.add(formatSimpleToggleLine(player, "plot_status_label_flight", "Flight inside this plot", flyEnabled));

        return lore;
    }

    private String formatProtectionLine(Player player, String labelKey, String fallbackLabel, boolean protectedOn) {
        String label = tr(player, labelKey, fallbackLabel);

        String protectedTxt = tr(player, "plot_status_state_protected", "&aProtected");
        String vulnerableTxt = tr(player, "plot_status_state_vulnerable", "&cVulnerable");

        String state = protectedOn ? protectedTxt : vulnerableTxt;
        return "&f- " + label + ": " + state;
    }

    private String formatSafeZoneLine(Player player, boolean safeZone) {
        String label = tr(player, "plot_status_label_safe_zone", "Safe zone");

        String enabledTxt = tr(player, "plot_status_state_enabled", "&aEnabled");
        String disabledTxt = tr(player, "plot_status_state_disabled", "&7Disabled");

        String state = safeZone ? enabledTxt : disabledTxt;
        return "&f- " + label + ": " + state;
    }

    private String formatEntryLine(Player player, boolean open) {
        String label = tr(player, "plot_status_label_entry_gate", "Entry gate");

        String openTxt = tr(player, "plot_status_state_open", "&aOpen");
        String closedTxt = tr(player, "plot_status_state_closed", "&cClosed");

        String state = open ? openTxt : closedTxt;
        return "&f- " + label + ": " + state;
    }

    private String formatSimpleToggleLine(Player player, String labelKey, String fallbackLabel, boolean enabled) {
        String label = tr(player, labelKey, fallbackLabel);

        String enabledTxt = tr(player, "plot_status_state_enabled_simple", "&aEnabled");
        String inactiveTxt = tr(player, "plot_status_state_inactive", "&7Inactive");

        String state = enabled ? enabledTxt : inactiveTxt;
        return "&f- " + label + ": " + state;
    }

    // --------------------------------------------------
    // BLESSINGS (now partially language-aware)
    // --------------------------------------------------

    private List<String> buildBuffList(Player player, int level) {
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
                    if (tier != null) highestTier.put(key, tier);
                }
            }
        }

        if (rewardByKey.isEmpty()) return result;

        for (Map.Entry<String, String> entry : rewardByKey.entrySet()) {
            String reward = entry.getValue();
            String[] parts = reward.split(":");

            // EFFECT:TYPE:TIER
            if (parts.length == 3 && isInteger(parts[2]) && parts[0].equalsIgnoreCase("EFFECT")) {
                String effectKey = parts[1];
                int tier = Integer.parseInt(parts[2]);
                String effectName = formatName(effectKey);
                String roman = toRoman(tier);

                String color;
                if (tier >= 4) color = "&d";
                else if (tier >= 2) color = "&b";
                else color = "&a";

                String fmt = tr(player,
                        "plot_status_buff_effect",
                        "&f{EFFECT} &7(Effect &f{TIER}&7)",
                        Map.of("EFFECT", effectName, "TIER", roman)
                );

                result.add(color + "✦ " + fmt);
                continue;
            }

            // MEMBERS:AMOUNT
            if (parts.length == 2 && isInteger(parts[1]) && parts[0].equalsIgnoreCase("MEMBERS")) {
                int amount = Integer.parseInt(parts[1]);

                String fmt = tr(player,
                        "plot_status_buff_members",
                        "&fTrusted member slots: &b+{AMOUNT}",
                        Map.of("AMOUNT", String.valueOf(amount))
                );

                result.add("&a✦ " + fmt);
                continue;
            }

            // Generic fallback (kept as-is)
            String pretty = reward.replace("EFFECT:", "")
                                  .replace("MEMBERS:", "")
                                  .replace(":", " ");
            pretty = formatName(pretty);

            result.add("&a✦ &f" + pretty);
        }

        return result;
    }

    private void sendSystem(Player player, String key, String fallback) {
        String msg = tr(player, key, fallback);
        player.sendMessage(GUIManager.color(msg));
    }

    // --------------------------------------------------
    // Language helpers
    // --------------------------------------------------

    private String tr(Player player, String key, String fallback) {
        return plugin.gui().tr(player, key, fallback);
    }

    /**
     * Codex map-aware translate with safe fallback, plus simple {VARS} replacement for fallback strings.
     */
    private String tr(Player player, String key, String fallback, Map<String, String> vars) {
        String raw = null;
        try {
            if (plugin.codex() != null) {
                if (vars == null || vars.isEmpty()) raw = plugin.codex().tr(player, key);
                else raw = plugin.codex().tr(player, key, vars);
            }
        } catch (Throwable ignored) {}

        String out;
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase(key)) out = (fallback == null ? "" : fallback);
        else out = raw;

        if (vars != null && !vars.isEmpty()) {
            for (Map.Entry<String, String> en : vars.entrySet()) {
                String k = en.getKey();
                String v = en.getValue() == null ? "" : en.getValue();

                out = out.replace("{" + k + "}", v);
                out = out.replace("{" + k.toLowerCase() + "}", v);
            }
        }

        return out;
    }

    // --------------------------------------------------
    // Small utils
    // --------------------------------------------------

    private boolean isInteger(String s) {
        try { Integer.parseInt(s); return true; }
        catch (NumberFormatException ex) { return false; }
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
