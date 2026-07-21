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

    public PlotStatusGUI(AegisGuard AegisGuard) {
        this.plugin = AegisGuard;
    }

    public static class PlotStatusHolder implements InventoryHolder {
        private final Plot plot;
        public PlotStatusHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        if (plot == null) {
            // ✅ IMPORTANT FIX: System keys should come from system.yml (plugin.msg()),
            // not guis.yml (plugin.gui()).
            sendSystem(player, "no_plot_here", null, "&cYou are not standing in a protected plot.");
            try { plugin.effects().playError(player); } catch (Throwable ignored) {}
            return;
        }

        // ✅ Title (language aware + safe)
        String title = plugin.gui().title(player, "plot_status_gui_title", "&8Plot Status");
        Inventory inv = Bukkit.createInventory(new PlotStatusHolder(plot), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);

        int level = plot.getLevel();
        int maxLevel = plugin.cfg().getMaxLevel();
        String owner = plot.getOwnerName();
        String world = plot.getWorld();

        // --- Header Info ---
        List<String> headerLore = new ArrayList<>();
        headerLore.add(tr(player,
                "plot_status_owner_line", "plot_status_header_owner",
                "&7Owner: &f{OWNER}",
                Map.of("OWNER", owner)
        ));
        headerLore.add(tr(player,
                "plot_status_world_line", "plot_status_header_world",
                "&7World: &f{WORLD}",
                Map.of("WORLD", world)
        ));
        headerLore.add("");
        headerLore.add(tr(player,
                "plot_status_level_line", "plot_status_header_level",
                "&7Plot Level: &b{LEVEL}&7 / &f{MAX}",
                Map.of("LEVEL", String.valueOf(level), "MAX", String.valueOf(maxLevel))
        ));

        String headerTitle = tr(player, "plot_status_header_title", null, "&6Plot Information");
        inv.setItem(4, GUIManager.createItem(Material.NETHER_STAR, headerTitle, colorList(headerLore)));

        // --- Protections & Risks ---
        String protTitle = tr(player, "plot_status_protection_title", null, "&cProtections & Risks");
        inv.setItem(20, GUIManager.createItem(Material.SHIELD, protTitle, buildProtectionLore(player, plot)));

        // --- Blessings ---
        List<String> buffsLore = new ArrayList<>();
        buffsLore.add(tr(player, "plot_status_blessings_header_line", "plot_status_blessings_header", "&7Active Blessings:"));
        buffsLore.add("");

        List<String> buffs = buildBuffList(player, level);
        if (buffs.isEmpty()) {
            buffsLore.add(tr(player, "plot_status_blessings_none_line", "plot_status_blessings_none", "&8- None unlocked yet."));
        } else {
            buffsLore.addAll(buffs);
            buffsLore.add("");
            buffsLore.add(tr(player, "plot_status_blessings_footer_line", "plot_status_blessings_footer",
                    "&8Only your highest tier of each blessing is shown."));
        }

        String blessingsTitle = tr(player, "plot_status_blessings_title", null, "&dActive Blessings");
        inv.setItem(22, GUIManager.createItem(Material.ENCHANTED_BOOK, blessingsTitle, colorList(buffsLore)));

        // --- Territory ---
        String territoryTitle = tr(player, "plot_status_territory_title", null, "&aTerritory & Growth");
        String expandName = tr(player, "button_expand", null, "&bExpand");

        // ✅ IMPORTANT: This key needs to exist in MX/AR guis.yml or you'll see "Aegis Menu" in English.
        String menuName = tr(player, "plot_status_menu_name", null, "&bAegis Menu");

        List<String> territoryLore = new ArrayList<>();
        territoryLore.add(tr(player, "plot_status_territory_rules_line", "plot_status_territory_rules", "&7Territory Rules:"));
        territoryLore.add(tr(player,
                "plot_status_territory_hint_line", "plot_status_territory_path",
                "&b{MENU} &7→ &b{EXPAND}",
                Map.of("MENU", menuName, "EXPAND", expandName)
        ));

        inv.setItem(24, GUIManager.createItem(Material.GRASS_BLOCK, territoryTitle, colorList(territoryLore)));

        // --- Domain Registry (Claim Blocks) ---
        inv.setItem(26, GUIManager.createItem(
                Material.PAPER,
                tr(player, "ledger_title", null, "&6📜 Domain Registry"),
                buildLedgerLore(player, plot)
        ));

        // --- Back ---
        String backName = tr(player, "button_back", null, "&fBack");
        List<String> backLore = plugin.gui().trList(player, "back_lore", List.of("&7Return to the main menu."));
        inv.setItem(49, GUIManager.createItem(Material.ARROW, backName, colorList(backLore)));
        inv.setItem(50, GUIManager.createItem(
                Material.BARRIER,
                tr(player, "button_exit", null, "&cClose"),
                colorList(plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu.")))
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
            return;
        }

        if (e.getSlot() == 50) {
            try { plugin.effects().playMenuClose(player); } catch (Throwable ignored) {}
            player.closeInventory();
        }
    }

    // --------------------------------------------------
    // Domain Registry Lore (language aware)
    // --------------------------------------------------

    private List<String> buildLedgerLore(Player player, Plot plot) {
        List<String> lore = new ArrayList<>();

        if (plugin.getClaimBlockManager() == null) {
            lore.add(tr(player, "ledger_disabled_1", "plot_status_ledger_disabled_1", "&8Claim blocks are disabled."));
            lore.add(tr(player, "ledger_disabled_2", "plot_status_ledger_disabled_2", "&7Ask an admin to enable:"));
            lore.add(tr(player, "ledger_disabled_3", "plot_status_ledger_disabled_3", "&fclaim_blocks.enabled: &atrue"));
            return colorList(lore);
        }

        UUID ownerUUID = plot.getOwner();
        String ownerName = plot.getOwnerName();

        long totalBlocks = plugin.getClaimBlockManager().getTotalBlocks(ownerUUID);
        long usedBlocks  = plugin.getClaimBlockManager().getUsedBlocks(ownerUUID);
        long availBlocks = plugin.getClaimBlockManager().getAvailableBlocks(ownerUUID);

        String availLabel = tr(player, "ledger_available", null, "&7Available: &a");
        String usedLabel  = tr(player, "ledger_used", null, "&7Used: &c");
        String totalLabel = tr(player, "ledger_total", null, "&7Total Capacity: &e");

        lore.add(availLabel + availBlocks);
        lore.add(usedLabel + usedBlocks);
        lore.add(totalLabel + totalBlocks);
        lore.add("");

        lore.add(tr(player, "ledger_applies_1", "plot_status_ledger_footer_1", "&8This budget applies to all plots"));
        lore.add(tr(player,
                "ledger_applies_2", "plot_status_ledger_footer_2",
                "&8owned by &f{OWNER}&8.",
                Map.of("OWNER", ownerName)
        ));

        return colorList(lore);
    }

    // --------------------------------------------------
    // Protection Overview
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
        boolean flyEnabled          = hasAscensionFlight(plot);
        boolean entryOpen           = plot.getFlag("entry", true);

        lore.add(tr(player, "plot_status_section_combat", null, "&7Combat & Hostiles:"));
        lore.add(bullet(player,
                tr(player, "plot_status_label_pvp", null, "PvP"),
                stateProtected(player, pvpProtected)
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_hostile_mobs", null, "Hostile mobs"),
                stateProtected(player, mobProtected)
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_animals_pets", "plot_status_label_animals", "Animals & pets"),
                stateProtected(player, animalsProtected)
        ));
        lore.add("");

        lore.add(tr(player, "plot_status_section_environment", null, "&7Environment & Access:"));
        lore.add(bullet(player,
                tr(player, "plot_status_label_safe_zone", null, "Safe zone"),
                safeZone
                        ? tr(player, "plot_status_state_enabled", "plot_status_state_enabled_simple", "&aEnabled")
                        : tr(player, "plot_status_state_disabled", null, "&7Disabled")
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_entry_gate", null, "Entry gate"),
                entryOpen
                        ? tr(player, "plot_status_state_open", null, "&aOpen")
                        : tr(player, "plot_status_state_closed", null, "&cClosed")
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_containers", null, "Containers"),
                stateProtected(player, containersProtected)
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_redstone_doors", "plot_status_label_redstone", "Redstone & doors"),
                stateProtected(player, redstoneProtected)
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_vehicles", null, "Vehicles"),
                stateProtected(player, vehiclesProtected)
        ));
        lore.add("");

        lore.add(tr(player, "plot_status_section_perks", null, "&7Perks & Services:"));
        lore.add(bullet(player,
                tr(player, "plot_status_label_shop_interact", null, "Market shop interact"),
                shopEnabled
                        ? tr(player, "plot_status_state_active", "plot_status_state_enabled_simple", "&aEnabled")
                        : tr(player, "plot_status_state_inactive", null, "&7Inactive")
        ));
        lore.add(bullet(player,
                tr(player, "plot_status_label_flight", null, "Flight inside this plot"),
                flyEnabled
                        ? tr(player, "plot_status_state_active", "plot_status_state_enabled_simple", "&aEnabled")
                        : tr(player, "plot_status_state_inactive", null, "&7Inactive")
        ));

        return colorList(lore);
    }

    private boolean hasAscensionFlight(Plot plot) {
        for (int level = 1; level <= plot.getLevel(); level++) {
            List<String> rewards = plugin.cfg().getLevelRewards(level);
            if (rewards != null && rewards.stream().anyMatch(reward -> reward != null
                    && (reward.equalsIgnoreCase("FLIGHT") || reward.equalsIgnoreCase("FLY")
                    || reward.equalsIgnoreCase("FLAG:fly")))) return true;
        }
        return false;
    }

    private String stateProtected(Player p, boolean isProtected) {
        return isProtected
                ? tr(p, "plot_status_state_protected", null, "&aProtected")
                : tr(p, "plot_status_state_vulnerable", null, "&cVulnerable");
    }

    private String bullet(Player p, String label, String state) {
        String fmt = tr(p, "plot_status_line_bullet", null, "&f- {LABEL}: {STATE}");
        return apply(fmt, Map.of("LABEL", label, "STATE", state));
    }

    // --------------------------------------------------
    // Blessings (formats)
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

            if (parts.length == 3 && isInteger(parts[2]) && parts[0].equalsIgnoreCase("EFFECT")) {
                String effectKey = parts[1];
                int tier = Integer.parseInt(parts[2]);
                String effectName = formatName(effectKey);
                String roman = toRoman(tier);

                String color = (tier >= 4) ? "&d" : (tier >= 2) ? "&b" : "&a";

                String fmt = tr(player,
                        "plot_status_blessing_effect_format", "plot_status_buff_effect",
                        "{COLOR}✦ &f{EFFECT} &7(Effect &f{TIER}&7)",
                        Map.of("COLOR", color, "EFFECT", effectName, "TIER", roman)
                );

                result.add(fmt);
                continue;
            }

            if (parts.length == 2 && isInteger(parts[1]) && parts[0].equalsIgnoreCase("MEMBERS")) {
                int amount = Integer.parseInt(parts[1]);

                String fmt = tr(player,
                        "plot_status_blessing_member_slots_format", "plot_status_buff_members",
                        "&a✦ &fTrusted member slots: &b+{AMOUNT}",
                        Map.of("AMOUNT", String.valueOf(amount))
                );

                result.add(fmt);
                continue;
            }

            String pretty = reward.replace("EFFECT:", "")
                    .replace("MEMBERS:", "")
                    .replace(":", " ");
            pretty = formatName(pretty);

            String gen = tr(player, "plot_status_blessing_generic_format", null, "&a✦ &f{TEXT}",
                    Map.of("TEXT", pretty));
            result.add(gen);
        }

        return result;
    }

    // --------------------------------------------------
    // Language helpers (modern + legacy keys)
    // --------------------------------------------------

    private String tr(Player player, String key, String legacyKey, String fallback) {
        return tr(player, key, legacyKey, fallback, null);
    }

    private String tr(Player player, String key, String legacyKey, String fallback, Map<String, String> vars) {
        String out = safeGuiGet(player, key);
        if (out.isBlank() && legacyKey != null) out = safeGuiGet(player, legacyKey);
        if (out.isBlank()) out = (fallback == null ? "" : fallback);

        if (vars != null && !vars.isEmpty()) out = apply(out, vars);
        return GUIManager.color(out);
    }

    private String safeGuiGet(Player player, String key) {
        if (key == null || key.isBlank()) return "";
        String out = "";
        try {
            out = plugin.gui().tr(player, key, "");
        } catch (Throwable ignored) {}

        if (out == null) return "";
        String t = out.trim();
        if (t.isEmpty()) return "";
        if (t.equalsIgnoreCase(key)) return "";
        if (t.toLowerCase().contains("missing") && t.contains(key)) return "";
        return out;
    }

    private void sendSystem(Player player, String key, String legacyKey, String fallback) {
        String msg = safeSystemGet(player, key);
        if (msg.isBlank() && legacyKey != null) msg = safeSystemGet(player, legacyKey);

        // ultra-safe fallback: if system lookup fails, try gui lookup
        if (msg.isBlank()) msg = safeGuiGet(player, key);
        if (msg.isBlank() && legacyKey != null) msg = safeGuiGet(player, legacyKey);

        if (msg.isBlank()) msg = (fallback == null ? "" : fallback);
        player.sendMessage(GUIManager.color(msg));
    }

    private String safeSystemGet(Player player, String key) {
        if (key == null || key.isBlank()) return "";
        String out = "";
        try {
            // plugin.msg() should be backed by your system.yml now
            out = plugin.msg().get(player, key);
        } catch (Throwable ignored) {}

        if (out == null) return "";
        String t = out.trim();
        if (t.isEmpty()) return "";
        if (t.equalsIgnoreCase(key)) return "";
        if (t.toLowerCase().contains("missing") && t.contains(key)) return "";
        return out;
    }

    private String apply(String s, Map<String, String> vars) {
        if (s == null) return "";
        String out = s;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            out = out.replace("{" + e.getKey() + "}", v);
            out = out.replace("{" + e.getKey().toLowerCase() + "}", v);
        }
        return out;
    }

    private List<String> colorList(List<String> in) {
        if (in == null) return List.of();
        List<String> out = new ArrayList<>(in.size());
        for (String s : in) out.add(GUIManager.color(s));
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
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
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
