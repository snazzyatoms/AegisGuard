package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.protection.ProtectionPreset;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PlotFlagsGUI
 * - Manages protection settings.
 * - Fully localized (Codex-backed), with safe readable fallbacks.
 *
 * Semantics:
 *  - GREEN  = Protection ON (safe / restricted)
 *  - RED    = Protection OFF (vulnerable / vanilla-like)
 *
 * Backed by ProtectionManager:
 *  - PvP    -> true = PvP blocked
 *  - Mobs   -> true = mob protection ON (no damage / target in-plot)
 *  - Animals, Redstone, Vehicles, etc -> true = protected / blocked
 */
public class PlotFlagsGUI {

    private final AegisGuard plugin;

    public PlotFlagsGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlotFlagsHolder implements InventoryHolder {
        private final Plot plot;
        public PlotFlagsHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class PlotFlagsPresetConfirmHolder implements InventoryHolder {
        private final Plot plot;
        private final ProtectionPreset preset;
        public PlotFlagsPresetConfirmHolder(Plot plot, ProtectionPreset preset) {
            this.plot = plot;
            this.preset = preset;
        }
        public Plot getPlot() { return plot; }
        public ProtectionPreset getPreset() { return preset; }
        @Override public Inventory getInventory() { return null; }
    }

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private String onOffFallback(Player p, boolean on, String baseLabel) {
        String onTxt = t(p, "label_on", "ON");
        String offTxt = t(p, "label_off", "OFF");
        return (on ? "§a" : "§c") + baseLabel + ": " + (on ? onTxt : offTxt);
    }

    public void open(Player player, Plot plot) {
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }

        String title = plugin.gui().title(
                player,
                "plot_flags_title",
                "&9Plot Flags"
        );

        Inventory inv = Bukkit.createInventory(new PlotFlagsHolder(plot), 54, title);

        ItemStack filler = GUIManager.getFiller();
        int[] borderSlots = {
                0, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 17,
                18, 26,
                27, 35,
                36, 44,
                45, 46, 47, 50, 51, 52, 53
        };
        for (int i : borderSlots) inv.setItem(i, filler);

        // --- 1. DANGER / ACCESS FLAGS ---
        addProtectionFlagButton(player, inv, plot, 10, "pvp",         Material.IRON_SWORD,      "button_pvp",   "pvp_toggle_lore",   "PvP");
        addProtectionFlagButton(player, inv, plot, 11, "tnt-damage",  Material.TNT,             "button_tnt",   "tnt_toggle_lore",   "TNT Damage");
        addProtectionFlagButton(player, inv, plot, 12, "fire-spread", Material.FLINT_AND_STEEL, "button_fire",  "fire_toggle_lore",  "Fire Spread");
        addProtectionFlagButton(player, inv, plot, 14, "mobs",        Material.ZOMBIE_HEAD,     "button_mobs",  "mob_toggle_lore",   "Mob Damage");
        addProtectionFlagButton(player, inv, plot, 15, "entry",       Material.OAK_FENCE_GATE,  "button_entry", "entry_toggle_lore", "Entry");

        inv.setItem(13, GUIManager.createItem(
                Material.KNOWLEDGE_BOOK,
                t(player, "claim_settings_guide_name", "&eProtection Doctrine"),
                tl(player, "claim_settings_guide_lore", List.of(
                        "&7Glowing controls are protected.",
                        "&7Disabled controls follow normal world behavior.",
                        "&7Changes save immediately to this plot."
                ))
        ));

        boolean safeOn = plugin.protection().isSafeZoneEnabled(plot);
        String safeLabelKey = "button_safe" + (safeOn ? "_on" : "_off");
        String safeName = t(player, safeLabelKey, onOffFallback(player, safeOn, "Safe Zone"));
        List<String> safeLore = tl(player, "safe_toggle_lore", List.of());

        ItemStack safeItem = GUIManager.createItem(Material.SHIELD, safeName, safeLore);
        if (safeOn) glow(safeItem);
        inv.setItem(16, safeItem);

        // --- 2. MECHANICS / INTERACTION ---
        addProtectionFlagButton(player, inv, plot, 19, "containers", Material.CHEST,         "button_containers", "container_toggle_lore", "Containers");
        addProtectionFlagButton(player, inv, plot, 20, "piston-use", Material.PISTON,        "button_piston",     "piston_toggle_lore",    "Pistons");
        addProtectionFlagButton(player, inv, plot, 21, "farm",       Material.WHEAT,         "button_farm",       "farm_toggle_lore",      "Farming");
        addProtectionFlagButton(player, inv, plot, 22, "animals",    Material.COW_SPAWN_EGG, "button_animals",    "animals_toggle_lore",   "Animals");
        addProtectionFlagButton(player, inv, plot, 23, "doors",      Material.OAK_DOOR,      "button_doors",      "doors_toggle_lore",     "Doors");
        addProtectionFlagButton(player, inv, plot, 24, "redstone",   Material.REDSTONE,      "button_redstone",   "redstone_toggle_lore",  "Redstone");
        addProtectionFlagButton(player, inv, plot, 25, "vehicles",   Material.OAK_BOAT,      "button_vehicles",   "vehicles_toggle_lore",  "Vehicles");

        // --- 3. BORDER / TELEPORT / STORM / DECOR WARDS ---
        addProtectionFlagButton(player, inv, plot, 28, "hopper-pipe",   Material.HOPPER,       "button_hopper_pipe",   "hopper_pipe_toggle_lore",   "Hopper Pipe Ward");
        addProtectionFlagButton(player, inv, plot, 29, "liquid-flow",   Material.WATER_BUCKET, "button_liquid_flow",   "liquid_flow_toggle_lore",   "Liquid Flow Ward");
        addProtectionFlagButton(player, inv, plot, 30, "teleport-ward", Material.ENDER_PEARL,  "button_teleport_ward", "teleport_ward_toggle_lore", "Teleport Ward");
        addProtectionFlagButton(player, inv, plot, 31, "storm-ward",    Material.LIGHTNING_ROD,"button_storm_ward",    "storm_ward_toggle_lore",    "Storm Ward");
        addProtectionFlagButton(player, inv, plot, 32, "decor",         Material.ARMOR_STAND,  "button_decor",         "decor_toggle_lore",         "Decor Ward");

        double shopCost = plugin.cfg().getShopInteractCost();
        String free = t(player, "label_free", "Free");
        String shopCostStr = (shopCost > 0 && !plugin.isAdmin(player))
                ? plugin.eco().format(shopCost, CurrencyType.VAULT)
                : free;

        addPaidFlagButton(player, inv, plot, 33, "shop-interact", Material.EMERALD,
                "button_shop", "shop_toggle_lore", shopCostStr,
                "Shop Interact");

        inv.setItem(34, GUIManager.createItem(
                Material.FEATHER,
                t(player, "claim_settings_flight_ascension_name", "&fFlight: Ascension Reward"),
                tl(player, "claim_settings_flight_ascension_lore", List.of(
                        "&7Flight is no longer configured here.",
                        "&7Reach Plot Ascension Level 30 to earn",
                        "&7safe flight inside the eligible plot."
                ))
        ));

        // --- 4. PROTECTION PRESETS ---
        placePresetButton(player, inv, 37, ProtectionPreset.HOME, Material.RED_BED);
        placePresetButton(player, inv, 38, ProtectionPreset.SHOP, Material.EMERALD_BLOCK);
        placePresetButton(player, inv, 39, ProtectionPreset.ARENA, Material.IRON_SWORD);
        placePresetButton(player, inv, 40, ProtectionPreset.FARM, Material.HAY_BLOCK);

        String cosName = t(player, "button_cosmetics", "&bCosmetics");
        List<String> cosLore = tl(player, "cosmetics_lore", List.of());
        inv.setItem(43, GUIManager.createItem(Material.NETHER_STAR, cosName, cosLore));

        inv.setItem(48, GUIManager.createItem(
                Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of())
        ));

        inv.setItem(49, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of())
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    private void placePresetButton(Player player, Inventory inv, int slot, ProtectionPreset preset, Material icon) {
        List<String> lore = new ArrayList<>(tl(player, "protection_preset_lore_" + preset.name(),
                fallbackPresetLore(preset)));
        lore.add(" ");
        lore.add(GUIManager.color(t(player, "protection_preset_click_lore",
                "&eClick to review and apply.")));
        inv.setItem(slot, GUIManager.createItem(icon,
                t(player, "protection_preset_name_" + preset.name(), "&b" + preset.fallbackLabel()),
                lore));
    }

    private List<String> fallbackPresetLore(ProtectionPreset preset) {
        return switch (preset) {
            case HOME -> List.of("&7Private home safety bundle.", "&7Containers, build, and border wards on.");
            case SHOP -> List.of("&7Open entry with shop interact.", "&7Containers and build stay protected.");
            case ARENA -> List.of("&7PvP allowed. TNT and fire stay safe.", "&7Build and containers stay protected.");
            case FARM -> List.of("&7Farm and animals open for helpers.", "&7Mobs and TNT stay protected.");
        };
    }

    public void openPresetConfirm(Player player, Plot plot, ProtectionPreset preset) {
        String title = plugin.gui().title(player, "protection_preset_confirm_title",
                "&eApply Preset: " + preset.fallbackLabel());
        Inventory inv = Bukkit.createInventory(new PlotFlagsPresetConfirmHolder(plot, preset), 27, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<String> lore = new ArrayList<>(tl(player, "protection_preset_confirm_lore_" + preset.name(),
                fallbackPresetLore(preset)));
        lore.add(" ");
        lore.add(GUIManager.color(t(player, "protection_preset_confirm_warning",
                "&cThis overwrites matching plot flags.")));
        lore.add(GUIManager.color(t(player, "protection_preset_confirm_hint",
                "&aClick the emerald to apply.")));

        inv.setItem(13, GUIManager.createItem(Material.EMERALD_BLOCK,
                t(player, "protection_preset_confirm_name_" + preset.name(),
                        "&aConfirm: " + preset.fallbackLabel()),
                lore));
        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to Plot Flags."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotFlagsHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (!(e.getInventory().getHolder() instanceof PlotFlagsHolder)) return;

        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        Plot plot = holder.getPlot();
        if (plot == null) return;

        if (!plot.canManage(player, plugin)) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        boolean refresh = false;

        switch (rawSlot) {
            case 10 -> { toggleFlag(player, plot, "pvp"); refresh = true; }
            case 11 -> { toggleFlag(player, plot, "tnt-damage"); refresh = true; }
            case 12 -> { toggleFlag(player, plot, "fire-spread"); refresh = true; }
            case 14 -> { toggleFlag(player, plot, "mobs"); refresh = true; }
            case 15 -> { toggleFlag(player, plot, "entry"); refresh = true; }

            case 16 -> {
                if (plugin.isAdmin(player)) {
                    boolean currently = plugin.protection().isSafeZoneEnabled(plot);
                    plugin.protection().toggleSafeZone(plot, !currently);
                    plugin.effects().playConfirm(player);
                    refresh = true;
                } else {
                    plugin.effects().playError(player);
                }
            }

            case 19 -> { toggleFlag(player, plot, "containers"); refresh = true; }
            case 20 -> { toggleFlag(player, plot, "piston-use"); refresh = true; }
            case 21 -> { toggleFlag(player, plot, "farm"); refresh = true; }
            case 22 -> { toggleFlag(player, plot, "animals"); refresh = true; }
            case 23 -> { toggleFlag(player, plot, "doors"); refresh = true; }
            case 24 -> { toggleFlag(player, plot, "redstone"); refresh = true; }
            case 25 -> { toggleFlag(player, plot, "vehicles"); refresh = true; }

            case 28 -> { toggleFlag(player, plot, "hopper-pipe"); refresh = true; }
            case 29 -> { toggleFlag(player, plot, "liquid-flow"); refresh = true; }
            case 30 -> { toggleFlag(player, plot, "teleport-ward"); refresh = true; }
            case 31 -> { toggleFlag(player, plot, "storm-ward"); refresh = true; }
            case 32 -> { toggleFlag(player, plot, "decor"); refresh = true; }
            case 33 -> { togglePaid(player, plot, "shop-interact", plugin.cfg().getShopInteractCost()); refresh = true; }

            case 37 -> { openPresetConfirm(player, plot, ProtectionPreset.HOME); return; }
            case 38 -> { openPresetConfirm(player, plot, ProtectionPreset.SHOP); return; }
            case 39 -> { openPresetConfirm(player, plot, ProtectionPreset.ARENA); return; }
            case 40 -> { openPresetConfirm(player, plot, ProtectionPreset.FARM); return; }

            case 43 -> {
                plugin.effects().playMenuFlip(player);
                plugin.gui().cosmetics().open(player, plot);
            }
            case 48 -> {
                plugin.effects().playMenuFlip(player);
                plugin.gui().openMain(player);
            }
            case 49 -> {
                plugin.effects().playMenuClose(player);
                player.closeInventory();
            }
        }

        if (refresh) open(player, plot);
    }

    public void handlePresetConfirmClick(Player player, InventoryClickEvent e, PlotFlagsPresetConfirmHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;

        Plot plot = holder.getPlot();
        ProtectionPreset preset = holder.getPreset();
        if (plot == null || preset == null) return;
        if (!plot.canManage(player, plugin)) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        if (rawSlot == 18) {
            open(player, plot);
            return;
        }
        if (rawSlot == 20) {
            player.closeInventory();
            return;
        }
        if (rawSlot == 13) {
            preset.apply(plot);
            plugin.store().savePlot(plot);
            plugin.store().setDirty(true);
            plugin.effects().playConfirm(player);
            plugin.msg().send(player, "protection_preset_applied",
                    Map.of("PRESET", preset.fallbackLabel()));
            open(player, plot);
        }
    }

    private void toggleFlag(Player p, Plot plot, String flag) {
        boolean currentlyOn = plugin.protection().isFlagEnabled(plot, flag);
        boolean newValue = !currentlyOn;

        plot.setFlag(flag, newValue);
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
        plugin.effects().playConfirm(p);
    }

    private void togglePaid(Player p, Plot plot, String flag, double cost) {
        boolean enabled = plot.getFlag(flag, false);

        if (!enabled && cost > 0 && !plugin.isAdmin(p)) {
            if (!plugin.eco().withdraw(p, cost, CurrencyType.VAULT)) {
                plugin.msg().send(p, "need_vault",
                        Map.of("AMOUNT", plugin.eco().format(cost, CurrencyType.VAULT)));
                plugin.effects().playError(p);
                return;
            }
        }

        plot.setFlag(flag, !enabled);
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
        plugin.effects().playConfirm(p);
    }

    private void addProtectionFlagButton(Player p, Inventory inv, Plot plot, int slot,
                                         String flag, Material mat,
                                         String nameKey, String loreKey,
                                         String fallbackLabel) {

        boolean on = plugin.protection().isFlagEnabled(plot, flag);

        String fullKey = nameKey + (on ? "_on" : "_off");
        String name = t(p, fullKey, onOffFallback(p, on, fallbackLabel));

        List<String> lore = tl(p, loreKey, List.of());

        ItemStack item = GUIManager.createItem(mat, name, lore);
        if (on) glow(item);
        inv.setItem(slot, item);
    }

    private void addPaidFlagButton(Player p, Inventory inv, Plot plot, int slot,
                                   String flag, Material mat,
                                   String nameKey, String loreKey,
                                   String cost,
                                   String fallbackLabel) {

        boolean on = plot.getFlag(flag, false);

        String fullKey = nameKey + (on ? "_on" : "_off");
        String name = t(p, fullKey, onOffFallback(p, on, fallbackLabel));

        List<String> rawLore = tl(p, loreKey, List.of());
        List<String> lore = replace(rawLore, "{COST}", cost);

        ItemStack item = GUIManager.createItem(mat, name, lore);
        if (on) glow(item);
        inv.setItem(slot, item);
    }

    private void glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
    }

    private List<String> replace(List<String> list, String key, String value) {
        List<String> out = new ArrayList<>();
        for (String s : list) out.add(s.replace(key, value));
        return out;
    }
}
