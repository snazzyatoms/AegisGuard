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
 * - Fully localized.
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

    public void open(Player player, Plot plot) {
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }

        String title = GUIManager.safeText(plugin.msg().get(player, "plot_flags_title"), "§9Plot Flags");
        Inventory inv = Bukkit.createInventory(new PlotFlagsHolder(plot), 54, title);

        // --- 1. BORDER ---
        ItemStack filler = GUIManager.getFiller();
        int[] borderSlots = {0,1,2,3,4,5,6,7,8, 9,17, 18,26, 27,35, 36,44, 45,46,47,50,51,52,53};
        for (int i : borderSlots) inv.setItem(i, filler);

        // --- 2. DANGER ---
        addFlagButton(player, inv, plot, 10, "pvp", Material.IRON_SWORD, "button_pvp", "pvp_toggle_lore");
        addFlagButton(player, inv, plot, 11, "tnt-damage", Material.TNT, "button_tnt", "tnt_toggle_lore");
        addFlagButton(player, inv, plot, 12, "fire-spread", Material.FLINT_AND_STEEL, "button_fire", "fire_toggle_lore");
        addFlagButton(player, inv, plot, 14, "mobs", Material.ZOMBIE_HEAD, "button_mobs", "mob_toggle_lore");
        addFlagButton(player, inv, plot, 15, "entry", Material.OAK_FENCE_GATE, "button_entry", "entry_toggle_lore");
        addFlagButton(player, inv, plot, 16, "safe_zone", Material.SHIELD, "button_safe", "safe_toggle_lore");

        // --- 3. MECHANICS ---
        addFlagButton(player, inv, plot, 19, "containers", Material.CHEST, "button_containers", "container_toggle_lore");
        addFlagButton(player, inv, plot, 20, "piston-use", Material.PISTON, "button_piston", "piston_toggle_lore");
        addFlagButton(player, inv, plot, 21, "farm", Material.WHEAT, "button_farm", "farm_toggle_lore");

        // ---- NEW FLAGS ----
        addFlagButton(player, inv, plot, 22, "animals", Material.COW_SPAWN_EGG, "button_animals", "animals_toggle_lore");
        addFlagButton(player, inv, plot, 23, "redstone", Material.REDSTONE, "button_redstone", "redstone_toggle_lore");
        addFlagButton(player, inv, plot, 24, "vehicles", Material.OAK_BOAT, "button_vehicles", "vehicles_toggle_lore");

        // Shop Interact (Paid)
        double shopCost = plugin.cfg().getShopInteractCost();
        String shopCostStr = (shopCost > 0 && !plugin.isAdmin(player))
                ? plugin.eco().format(shopCost, CurrencyType.VAULT) : "Free";
        addPaidFlagButton(player, inv, plot, 25, "shop-interact", Material.EMERALD, "button_shop", "shop_toggle_lore", shopCostStr);

        // --- 4. PREMIUM ---
        boolean canFly = plot.getFlag("fly", false);
        double flyCost = plugin.cfg().getFlightCost();
        String costString = (flyCost > 0 && !plugin.isAdmin(player))
                ? plugin.eco().format(flyCost, CurrencyType.VAULT) : "Free";

        ItemStack flyIcon = GUIManager.createItem(
                Material.FEATHER,
                canFly ? plugin.msg().get(player, "button_fly_on") : plugin.msg().get(player, "button_fly_off"),
                replace(plugin.msg().getList(player, "fly_toggle_lore"), "{COST}", costString)
        );
        if (canFly) glow(flyIcon);
        inv.setItem(30, flyIcon);

        // Cosmetics
        inv.setItem(31, GUIManager.createItem(
                Material.NETHER_STAR,
                plugin.msg().get(player, "button_cosmetics"),
                plugin.msg().getList(player, "cosmetics_lore")
        ));

        // --- NAV ---
        inv.setItem(48, GUIManager.createItem(Material.ARROW,
                plugin.msg().get(player, "button_back"),
                plugin.msg().getList(player, "back_lore")));

        inv.setItem(49, GUIManager.createItem(Material.BARRIER,
                plugin.msg().get(player, "button_exit"),
                plugin.msg().getList(player, "exit_lore")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, PlotFlagsHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (plot == null) return;

        if (!plot.getOwner().equals(player.getUniqueId()) && !plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            return;
        }

        switch (e.getSlot()) {
            case 10 -> toggle(player, plot, "pvp");
            case 11 -> toggle(player, plot, "tnt-damage");
            case 12 -> toggle(player, plot, "fire-spread");
            case 14 -> toggle(player, plot, "mobs");
            case 15 -> toggle(player, plot, "entry");
            case 16 -> {
                // Safe zone is admin-only, and should truly toggle
                if (plugin.isAdmin(player)) {
                    boolean currently = plugin.protection().isSafeZoneEnabled(plot);
                    plugin.protection().toggleSafeZone(plot, !currently);
                    plugin.effects().playConfirm(player);
                }
            }

            case 19 -> toggle(player, plot, "containers");
            case 20 -> toggle(player, plot, "piston-use");
            case 21 -> toggle(player, plot, "farm");

            case 22 -> toggle(player, plot, "animals");
            case 23 -> toggle(player, plot, "redstone");
            case 24 -> toggle(player, plot, "vehicles");

            case 25 -> togglePaid(player, plot, "shop-interact", plugin.cfg().getShopInteractCost());
            case 30 -> togglePaid(player, plot, "fly", plugin.cfg().getFlightCost());

            case 31 -> plugin.gui().cosmetics().open(player, plot);
            case 48 -> plugin.gui().openMain(player);
            case 49 -> player.closeInventory();
        }

        open(player, plot);
    }

    // ---------------- HELPERS ----------------

    private void toggle(Player p, Plot plot, String flag) {
        plot.setFlag(flag, !plot.getFlag(flag, true));
        plugin.store().setDirty(true);
        plugin.effects().playConfirm(p);
    }

    private void togglePaid(Player p, Plot plot, String flag, double cost) {
        boolean enabled = plot.getFlag(flag, false);

        if (!enabled && cost > 0 && !plugin.isAdmin(p)) {
            if (!plugin.eco().withdraw(p, cost, CurrencyType.VAULT)) {
                plugin.msg().send(p, "need_vault",
                        Map.of("AMOUNT", plugin.eco().format(cost, CurrencyType.VAULT)));
                return;
            }
        }

        plot.setFlag(flag, !enabled);
        plugin.store().setDirty(true);
        plugin.effects().playConfirm(p);
    }

    private void addFlagButton(Player p, Inventory inv, Plot plot, int slot,
                               String flag, Material mat, String nameKey, String loreKey) {
        boolean on = plot.getFlag(flag, true);
        ItemStack item = GUIManager.createItem(
                mat,
                plugin.msg().get(p, nameKey + (on ? "_on" : "_off")),
                plugin.msg().getList(p, loreKey)
        );
        if (on) glow(item);
        inv.setItem(slot, item);
    }

    private void addPaidFlagButton(Player p, Inventory inv, Plot plot, int slot,
                                   String flag, Material mat, String nameKey, String loreKey, String cost) {
        boolean on = plot.getFlag(flag, false);
        List<String> lore = replace(plugin.msg().getList(p, loreKey), "{COST}", cost);
        ItemStack item = GUIManager.createItem(
                mat,
                plugin.msg().get(p, nameKey + (on ? "_on" : "_off")),
                lore
        );
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
