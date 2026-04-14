package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
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

    // -----------------------
    // Codex helpers (safe)
    // -----------------------

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private String onOffFallback(Player p, boolean on, String baseLabel) {
        // Optional keys if you want them later:
        // label_on: "ON" / "ACTIVO" etc
        // label_off: "OFF" / "INACTIVO" etc
        String onTxt = t(p, "label_on", "ON");
        String offTxt = t(p, "label_off", "OFF");
        return (on ? "§a" : "§c") + baseLabel + ": " + (on ? onTxt : offTxt);
    }

    public void open(Player player, Plot plot) {
        if (plot == null) {
            // Chat feedback stays on MessagesUtil
            plugin.msg().send(player, "no_plot_here");
            return;
        }

        // ✅ Title: translate + colors + safe fallback + clamp (centralized)
        String title = plugin.gui().title(
                player,
                "plot_flags_title",
                "&9Plot Flags"
        );

        Inventory inv = Bukkit.createInventory(new PlotFlagsHolder(plot), 54, title);

        // --- 1. BORDER ---
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

        // --- 2. DANGER / ACCESS FLAGS ---
        addProtectionFlagButton(player, inv, plot, 10, "pvp",         Material.IRON_SWORD,      "button_pvp",   "pvp_toggle_lore",   "PvP");
        addProtectionFlagButton(player, inv, plot, 11, "tnt-damage",  Material.TNT,             "button_tnt",   "tnt_toggle_lore",   "TNT Damage");
        addProtectionFlagButton(player, inv, plot, 12, "fire-spread", Material.FLINT_AND_STEEL, "button_fire",  "fire_toggle_lore",  "Fire Spread");
        addProtectionFlagButton(player, inv, plot, 14, "mobs",        Material.ZOMBIE_HEAD,     "button_mobs",  "mob_toggle_lore",   "Mob Damage");
        addProtectionFlagButton(player, inv, plot, 15, "entry",       Material.OAK_FENCE_GATE,  "button_entry", "entry_toggle_lore", "Entry");

        // Safe Zone: structural / environment umbrella, admin-only toggle
        boolean safeOn = plugin.protection().isSafeZoneEnabled(plot);
        String safeLabelKey = "button_safe" + (safeOn ? "_on" : "_off");
        String safeName = t(player, safeLabelKey, onOffFallback(player, safeOn, "Safe Zone"));
        List<String> safeLore = tl(player, "safe_toggle_lore", List.of());

        ItemStack safeItem = GUIManager.createItem(Material.SHIELD, safeName, safeLore);
        if (safeOn) glow(safeItem);
        inv.setItem(16, safeItem);

        // --- 3. MECHANICS / INTERACTION ---
        addProtectionFlagButton(player, inv, plot, 19, "containers", Material.CHEST,         "button_containers", "container_toggle_lore", "Containers");
        addProtectionFlagButton(player, inv, plot, 20, "piston-use", Material.PISTON,        "button_piston",     "piston_toggle_lore",    "Pistons");
        addProtectionFlagButton(player, inv, plot, 21, "farm",       Material.WHEAT,         "button_farm",       "farm_toggle_lore",      "Farming");
        addProtectionFlagButton(player, inv, plot, 22, "animals",    Material.COW_SPAWN_EGG, "button_animals",    "animals_toggle_lore",   "Animals");
        addProtectionFlagButton(player, inv, plot, 23, "redstone",   Material.REDSTONE,      "button_redstone",   "redstone_toggle_lore",  "Redstone");
        addProtectionFlagButton(player, inv, plot, 24, "vehicles",   Material.OAK_BOAT,      "button_vehicles",   "vehicles_toggle_lore",  "Vehicles");

        // --- 4. SHOP INTERACT (Paid) ---
        double shopCost = plugin.cfg().getShopInteractCost();
        String free = t(player, "label_free", "Free");
        String shopCostStr = (shopCost > 0 && !plugin.isAdmin(player))
                ? plugin.eco().format(shopCost, CurrencyType.VAULT)
                : free;

        addPaidFlagButton(player, inv, plot, 25, "shop-interact", Material.EMERALD,
                "button_shop", "shop_toggle_lore", shopCostStr,
                "Shop Interact");

        // --- 5. ADMIN: FLY / PREMIUM + COSMETICS ---
        if (plugin.isAdmin(player)) {
            boolean canFly = plot.getFlag("fly", false);
            double flyCost = plugin.cfg().getFlightCost();
            String flyCostStr = (flyCost > 0 && !plugin.isAdmin(player))
                    ? plugin.eco().format(flyCost, CurrencyType.VAULT)
                    : free;

            List<String> flyLore = tl(player, "fly_toggle_lore", List.of());
            flyLore = replace(flyLore, "{COST}", flyCostStr);

            String flyKey = canFly ? "button_fly_on" : "button_fly_off";
            String flyName = t(player, flyKey, onOffFallback(player, canFly, "Flight"));

            ItemStack flyIcon = GUIManager.createItem(Material.FEATHER, flyName, flyLore);
            if (canFly) glow(flyIcon);
            inv.setItem(30, flyIcon);
        }

        // Cosmetics
        String cosName = t(player, "button_cosmetics", "&bCosmetics");
        List<String> cosLore = tl(player, "cosmetics_lore", List.of());
        inv.setItem(31, GUIManager.createItem(Material.NETHER_STAR, cosName, cosLore));

        // --- NAV ---
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

    public void handleClick(Player player, InventoryClickEvent e, PlotFlagsHolder holder) {
        // Always cancel to prevent item pickup/move
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        if (!(e.getInventory().getHolder() instanceof PlotFlagsHolder)) return;

        // Ignore clicks coming from the player's own inventory
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

            case 16 -> { // Safe zone (admin only)
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
            case 23 -> { toggleFlag(player, plot, "redstone"); refresh = true; }
            case 24 -> { toggleFlag(player, plot, "vehicles"); refresh = true; }

            case 25 -> { togglePaid(player, plot, "shop-interact", plugin.cfg().getShopInteractCost()); refresh = true; }
            case 30 -> {
                if (plugin.isAdmin(player)) {
                    togglePaid(player, plot, "fly", plugin.cfg().getFlightCost());
                    refresh = true;
                } else {
                    plugin.effects().playError(player);
                }
            }

            case 31 -> {
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

    // ---------------- HELPERS ----------------

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
