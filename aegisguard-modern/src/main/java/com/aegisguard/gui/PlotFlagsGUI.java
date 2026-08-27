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
 * Claim Settings: a category hub plus focused pages.
 * Server/spawn plots hide personal-only presets and cosmetics.
 *
 * Semantics:
 *  - GREEN / glow = Protection ON
 *  - RED = Protection OFF
 */
public class PlotFlagsGUI {

    private static final int HUB_SLOT_DOCTRINE = 4;
    private static final int HUB_SLOT_PRESETS = 20;
    private static final int HUB_SLOT_COSMETICS = 22;
    private static final int HUB_SLOT_SAFETY = 29;
    private static final int HUB_SLOT_MECHANICS = 31;
    private static final int HUB_SLOT_WARDS = 33;

    public enum Page {
        HUB, SAFETY, MECHANICS, WARDS, PRESETS
    }

    private final AegisGuard plugin;

    public PlotFlagsGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class PlotFlagsHolder implements InventoryHolder {
        private final Plot plot;
        private final Page page;
        public PlotFlagsHolder(Plot plot) { this(plot, Page.HUB); }
        public PlotFlagsHolder(Plot plot, Page page) {
            this.plot = plot;
            this.page = page == null ? Page.HUB : page;
        }
        public Plot getPlot() { return plot; }
        public Page getPage() { return page; }
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
        open(player, plot, Page.HUB);
    }

    public void open(Player player, Plot plot, Page page) {
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }
        if (!plot.canManage(player, plugin)) {
            if (plot.isServerZone()) {
                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                        plugin.gui().tr(player, "server_zone_manage_denied",
                                "&cYou need server-zone manage permission or the Steward role to change these settings.")));
            } else {
                plugin.msg().send(player, "no_perm");
            }
            return;
        }

        Page safePage = page == null ? Page.HUB : page;
        String title = titleFor(player, plot, safePage);
        Inventory inv = Bukkit.createInventory(new PlotFlagsHolder(plot, safePage), 54, title);

        if (safePage == Page.HUB) {
            paintHubFrame(inv);
        } else {
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
        }

        switch (safePage) {
            case HUB -> buildHub(player, inv, plot);
            case SAFETY -> buildSafety(player, inv, plot);
            case MECHANICS -> buildMechanics(player, inv, plot);
            case WARDS -> buildWards(player, inv, plot);
            case PRESETS -> buildPresets(player, inv, plot);
        }

        boolean hub = safePage == Page.HUB;
        inv.setItem(48, GUIManager.createItem(
                Material.ARROW,
                t(player, hub ? "button_back" : "button_back_claim_hub", hub ? "&fBack" : "&fBack to Claim Settings"),
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

    private String titleFor(Player player, Plot plot, Page page) {
        boolean server = plot.isServerZone();
        String hubKey = server ? "plot_flags_server_title" : "plot_flags_title";
        String hubFallback = server ? "&3⚙ Server Plot Settings" : "&9Plot Flags";
        return switch (page) {
            case HUB -> plugin.gui().title(player, hubKey, hubFallback);
            case SAFETY -> plugin.gui().title(player, "plot_flags_page_safety_title", "&cSafety");
            case MECHANICS -> plugin.gui().title(player, "plot_flags_page_mechanics_title", "&eMechanics");
            case WARDS -> plugin.gui().title(player, "plot_flags_page_wards_title", "&bWards");
            case PRESETS -> plugin.gui().title(player, "plot_flags_page_presets_title", "&dPresets");
        };
    }

    private void buildHub(Player player, Inventory inv, Plot plot) {
        boolean server = plot.isServerZone();
        inv.setItem(HUB_SLOT_DOCTRINE, GUIManager.createItem(
                Material.KNOWLEDGE_BOOK,
                t(player, "claim_settings_guide_name", "&eProtection Doctrine"),
                tl(player, server ? "plot_flags_hub_server_lore" : "plot_flags_hub_personal_lore",
                        server
                                ? List.of(
                                "&7These controls apply to this",
                                "&7server or spawn plot only.",
                                "&7Home presets and cosmetics stay",
                                "&7on personal plots.")
                                : List.of(
                                "&7Open a category to change flags.",
                                "&7Glowing controls are protected.",
                                "&7Changes save immediately."))
        ));

        inv.setItem(HUB_SLOT_SAFETY, GUIManager.createItem(
                Material.SHIELD,
                t(player, "plot_flags_page_safety", "&cSafety"),
                tl(player, "plot_flags_page_safety_lore", List.of(
                        "&7PvP, TNT, fire, mobs, entry,",
                        "&7and Safe Zone."
                ))
        ));
        inv.setItem(HUB_SLOT_MECHANICS, GUIManager.createItem(
                Material.REDSTONE,
                t(player, "plot_flags_page_mechanics", "&eMechanics"),
                tl(player, "plot_flags_page_mechanics_lore", List.of(
                        "&7Containers, pistons, farming,",
                        "&7animals, doors, redstone, vehicles."
                ))
        ));
        inv.setItem(HUB_SLOT_WARDS, GUIManager.createItem(
                Material.ENDER_PEARL,
                t(player, "plot_flags_page_wards", "&bWards"),
                tl(player, "plot_flags_page_wards_lore", List.of(
                        "&7Hopper, liquid, teleport, storm,",
                        "&7decor, and shop interact."
                ))
        ));
        inv.setItem(server ? HUB_SLOT_COSMETICS : HUB_SLOT_PRESETS, GUIManager.createItem(
                Material.WRITABLE_BOOK,
                t(player, "plot_flags_page_presets", "&dPresets"),
                tl(player, "plot_flags_page_presets_lore", List.of(
                        server
                                ? "&7Shop and Arena bundles for this server plot."
                                : "&7Home, Shop, Arena, and Farm bundles."
                ))
        ));

        if (!server && plugin.modules().on(com.aegisguard.config.Modules.Id.COSMETICS)) {
            String cosName = t(player, "button_cosmetics", "&bCosmetics");
            List<String> cosLore = tl(player, "cosmetics_lore", List.of());
            inv.setItem(HUB_SLOT_COSMETICS, GUIManager.createItem(Material.NETHER_STAR, cosName, cosLore));
        }
    }

    private void paintHubFrame(Inventory inv) {
        ItemStack background = GUIManager.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        ItemStack navy = GUIManager.createItem(Material.BLUE_STAINED_GLASS_PANE, " ", List.of());
        ItemStack gold = GUIManager.createItem(Material.YELLOW_STAINED_GLASS_PANE, " ", List.of());
        ItemStack cyan = GUIManager.createItem(Material.CYAN_STAINED_GLASS_PANE, " ", List.of());

        for (int slot = 0; slot < inv.getSize(); slot++) inv.setItem(slot, background);
        for (int slot : new int[]{0,1,2,3,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,50,51,52,53}) {
            inv.setItem(slot, navy);
        }
        for (int slot : new int[]{3,5,12,13,14,19,21,23,24,28,30,32,34}) inv.setItem(slot, gold);
        for (int slot : new int[]{10,11,15,16,37,38,39,40,41,42,43}) inv.setItem(slot, cyan);
    }

    private void buildSafety(Player player, Inventory inv, Plot plot) {
        addProtectionFlagButton(player, inv, plot, 10, "pvp",         Material.IRON_SWORD,      "button_pvp",   "pvp_toggle_lore",   "PvP");
        addProtectionFlagButton(player, inv, plot, 11, "tnt-damage",  Material.TNT,             "button_tnt",   "tnt_toggle_lore",   "TNT Damage");
        addProtectionFlagButton(player, inv, plot, 12, "fire-spread", Material.FLINT_AND_STEEL, "button_fire",  "fire_toggle_lore",  "Fire Spread");
        addProtectionFlagButton(player, inv, plot, 14, "mobs",        Material.ZOMBIE_HEAD,     "button_mobs",  "mob_toggle_lore",   "Mob Damage");
        addProtectionFlagButton(player, inv, plot, 15, "entry",       Material.OAK_FENCE_GATE,  "button_entry", "entry_toggle_lore", "Entry");

        boolean safeOn = plugin.protection().isSafeZoneEnabled(plot);
        String safeLabelKey = "button_safe" + (safeOn ? "_on" : "_off");
        String safeName = t(player, safeLabelKey, onOffFallback(player, safeOn, "Safe Zone"));
        List<String> safeLore = tl(player, "safe_toggle_lore", List.of());
        ItemStack safeItem = GUIManager.createItem(Material.SHIELD, safeName, safeLore);
        if (safeOn) glow(safeItem);
        inv.setItem(16, safeItem);
    }

    private void buildMechanics(Player player, Inventory inv, Plot plot) {
        addProtectionFlagButton(player, inv, plot, 10, "containers", Material.CHEST,         "button_containers", "container_toggle_lore", "Containers");
        addProtectionFlagButton(player, inv, plot, 11, "piston-use", Material.PISTON,        "button_piston",     "piston_toggle_lore",    "Pistons");
        addProtectionFlagButton(player, inv, plot, 12, "farm",       Material.WHEAT,         "button_farm",       "farm_toggle_lore",      "Farming");
        addProtectionFlagButton(player, inv, plot, 13, "animals",    Material.COW_SPAWN_EGG, "button_animals",    "animals_toggle_lore",   "Animals");
        addProtectionFlagButton(player, inv, plot, 14, "doors",      Material.OAK_DOOR,      "button_doors",      "doors_toggle_lore",     "Doors");
        addProtectionFlagButton(player, inv, plot, 15, "redstone",   Material.REDSTONE,      "button_redstone",   "redstone_toggle_lore",  "Redstone");
        addProtectionFlagButton(player, inv, plot, 16, "vehicles",   Material.OAK_BOAT,      "button_vehicles",   "vehicles_toggle_lore",  "Vehicles");
    }

    private void buildWards(Player player, Inventory inv, Plot plot) {
        addProtectionFlagButton(player, inv, plot, 10, "hopper-pipe",   Material.HOPPER,       "button_hopper_pipe",   "hopper_pipe_toggle_lore",   "Hopper Pipe Ward");
        addProtectionFlagButton(player, inv, plot, 11, "liquid-flow",   Material.WATER_BUCKET, "button_liquid_flow",   "liquid_flow_toggle_lore",   "Liquid Flow Ward");
        addProtectionFlagButton(player, inv, plot, 12, "teleport-ward", Material.ENDER_PEARL,  "button_teleport_ward", "teleport_ward_toggle_lore", "Teleport Ward");
        addProtectionFlagButton(player, inv, plot, 13, "storm-ward",    Material.LIGHTNING_ROD,"button_storm_ward",    "storm_ward_toggle_lore",    "Storm Ward");
        addProtectionFlagButton(player, inv, plot, 14, "decor",         Material.ARMOR_STAND,  "button_decor",         "decor_toggle_lore",         "Decor Ward");

        double shopCost = plugin.cfg().getShopInteractCost();
        String free = t(player, "label_free", "Free");
        String shopCostStr = (shopCost > 0 && !plugin.isAdmin(player))
                ? plugin.eco().format(shopCost, CurrencyType.VAULT)
                : free;
        addPaidFlagButton(player, inv, plot, 15, "shop-interact", Material.EMERALD,
                "button_shop", "shop_toggle_lore", shopCostStr, "Shop Interact");

    }

    private void buildPresets(Player player, Inventory inv, Plot plot) {
        boolean server = plot.isServerZone();
        if (!server) {
            placePresetButton(player, inv, 10, ProtectionPreset.HOME, Material.RED_BED);
            placePresetButton(player, inv, 16, ProtectionPreset.FARM, Material.HAY_BLOCK);
            if (plugin.modules().on(com.aegisguard.config.Modules.Id.COSMETICS)) {
                String cosName = t(player, "button_cosmetics", "&bCosmetics");
                List<String> cosLore = tl(player, "cosmetics_lore", List.of());
                inv.setItem(22, GUIManager.createItem(Material.NETHER_STAR, cosName, cosLore));
            }
        }
        placePresetButton(player, inv, 12, ProtectionPreset.SHOP, Material.EMERALD_BLOCK);
        placePresetButton(player, inv, 14, ProtectionPreset.ARENA, Material.IRON_SWORD);
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
            if (plot.isServerZone()) {
                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                        plugin.gui().tr(player, "server_zone_manage_denied",
                                "&cYou need server-zone manage permission or the Steward role to change these settings.")));
            } else {
                plugin.msg().send(player, "no_perm");
            }
            return;
        }

        Page page = holder.getPage();
        if (rawSlot == 48) {
            plugin.effects().playMenuFlip(player);
            if (page == Page.HUB) {
                plugin.gui().openMain(player);
            } else {
                open(player, plot, Page.HUB);
            }
            return;
        }
        if (rawSlot == 49) {
            plugin.effects().playMenuClose(player);
            player.closeInventory();
            return;
        }

        boolean refresh = false;
        switch (page) {
            case HUB -> {
                switch (rawSlot) {
                    case HUB_SLOT_SAFETY -> { plugin.effects().playMenuFlip(player); open(player, plot, Page.SAFETY); return; }
                    case HUB_SLOT_MECHANICS -> { plugin.effects().playMenuFlip(player); open(player, plot, Page.MECHANICS); return; }
                    case HUB_SLOT_WARDS -> { plugin.effects().playMenuFlip(player); open(player, plot, Page.WARDS); return; }
                    case HUB_SLOT_PRESETS -> {
                        if (!plot.isServerZone()) {
                            plugin.effects().playMenuFlip(player);
                            open(player, plot, Page.PRESETS);
                            return;
                        }
                    }
                    case HUB_SLOT_COSMETICS -> {
                        if (!plot.isServerZone() && plugin.modules().on(com.aegisguard.config.Modules.Id.COSMETICS)) {
                            plugin.effects().playMenuFlip(player);
                            plugin.gui().cosmetics().open(player, plot);
                        } else if (plot.isServerZone()) {
                            plugin.effects().playMenuFlip(player);
                            open(player, plot, Page.PRESETS);
                        }
                    }
                }
            }
            case SAFETY -> {
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
                }
            }
            case MECHANICS -> {
                switch (rawSlot) {
                    case 10 -> { toggleFlag(player, plot, "containers"); refresh = true; }
                    case 11 -> { toggleFlag(player, plot, "piston-use"); refresh = true; }
                    case 12 -> { toggleFlag(player, plot, "farm"); refresh = true; }
                    case 13 -> { toggleFlag(player, plot, "animals"); refresh = true; }
                    case 14 -> { toggleFlag(player, plot, "doors"); refresh = true; }
                    case 15 -> { toggleFlag(player, plot, "redstone"); refresh = true; }
                    case 16 -> { toggleFlag(player, plot, "vehicles"); refresh = true; }
                }
            }
            case WARDS -> {
                switch (rawSlot) {
                    case 10 -> { toggleFlag(player, plot, "hopper-pipe"); refresh = true; }
                    case 11 -> { toggleFlag(player, plot, "liquid-flow"); refresh = true; }
                    case 12 -> { toggleFlag(player, plot, "teleport-ward"); refresh = true; }
                    case 13 -> { toggleFlag(player, plot, "storm-ward"); refresh = true; }
                    case 14 -> { toggleFlag(player, plot, "decor"); refresh = true; }
                    case 15 -> { togglePaid(player, plot, "shop-interact", plugin.cfg().getShopInteractCost()); refresh = true; }
                }
            }
            case PRESETS -> {
                switch (rawSlot) {
                    case 10 -> {
                        if (!plot.isServerZone()) {
                            openPresetConfirm(player, plot, ProtectionPreset.HOME);
                            return;
                        }
                    }
                    case 12 -> { openPresetConfirm(player, plot, ProtectionPreset.SHOP); return; }
                    case 14 -> { openPresetConfirm(player, plot, ProtectionPreset.ARENA); return; }
                    case 16 -> {
                        if (!plot.isServerZone()) {
                            openPresetConfirm(player, plot, ProtectionPreset.FARM);
                            return;
                        }
                    }
                    case 22 -> {
                        if (!plot.isServerZone() && plugin.modules().on(com.aegisguard.config.Modules.Id.COSMETICS)) {
                            plugin.effects().playMenuFlip(player);
                            plugin.gui().cosmetics().open(player, plot);
                        }
                    }
                }
            }
        }

        if (refresh) open(player, plot, page);
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
            if (plot.isServerZone()) {
                player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&',
                        plugin.gui().tr(player, "server_zone_manage_denied",
                                "&cYou need server-zone manage permission or the Steward role to change these settings.")));
            } else {
                plugin.msg().send(player, "no_perm");
            }
            return;
        }

        if (rawSlot == 18) {
            open(player, plot, Page.PRESETS);
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
            open(player, plot, Page.PRESETS);
        }
    }

    private void toggleFlag(Player p, Plot plot, String flag) {
        boolean currentlyOn = plugin.protection().isFlagEnabled(plot, flag);
        plot.setFlag(flag, !currentlyOn);
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
        List<String> lore = replace(tl(p, loreKey, List.of()), "{COST}", cost);
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
