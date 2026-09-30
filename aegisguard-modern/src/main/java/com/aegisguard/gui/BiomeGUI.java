package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-plot biome picker opened by {@code /aegis biome}.
 * Lists {@code biomes.allowed}, charges {@code biomes.cost_per_change} via Vault,
 * then hands the apply work to {@link com.aegisguard.biomes.BiomeService}.
 */
public class BiomeGUI {

    private final AegisGuard plugin;
    private final NamespacedKey KEY_BIOME;

    public BiomeGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.KEY_BIOME = new NamespacedKey(plugin, "plot_biome");
    }

    public static class BiomeHolder implements InventoryHolder {
        private final Plot plot;
        public BiomeHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }
        if (!plugin.modules().on(com.aegisguard.config.Modules.Id.BIOMES)) {
            plugin.msg().send(player, "module_disabled", Map.of("MODULE", "Biomes"));
            return;
        }

        String title = plugin.gui().title(player, "biome_gui_title", "&2Plot Biome");
        Inventory inv = Bukkit.createInventory(new BiomeHolder(plot), 54, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        String current = plot.getCustomBiome();
        double cost = plugin.biomes().costPerChange();

        int slot = 0;
        for (String biomeName : plugin.biomes().allowedBiomes()) {
            if (slot >= 45) break;
            Biome biome = plugin.biomes().resolve(biomeName);
            if (biome == null) continue;

            boolean selected = current != null && current.equalsIgnoreCase(biome.name());
            Material icon = iconFor(biome.name());

            List<String> lore = new ArrayList<>();
            lore.add(tv(player, "biome_effect_line",
                    Map.of("BIOME", biomeName),
                    "&7Sets this claim to &f" + biomeName));
            lore.add(" ");
            if (selected) {
                lore.add(t(player, "cosmetics_status_selected", "&a(Selected)"));
            } else {
                if (cost > 0 && !plugin.isAdmin(player)) {
                    lore.add(tv(player, "cosmetics_cost_line",
                            Map.of("AMOUNT", plugin.eco().format(cost, CurrencyType.VAULT)),
                            "&7Cost: &e" + plugin.eco().format(cost, CurrencyType.VAULT)));
                }
                lore.add(t(player, "biome_click_apply", "&eLeft-Click: &7Apply to claim"));
                lore.add(t(player, "biome_relog_hint", "&8Relog may be needed to see colors."));
            }

            ItemStack item = GUIManager.createItem(icon, biomeDisplay(player, biomeName), lore);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(KEY_BIOME, PersistentDataType.STRING, biome.name());
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }

        inv.setItem(48, GUIManager.createItem(
                Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))
        ));
        inv.setItem(49, GUIManager.createItem(
                Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, BiomeHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (plot == null) {
            player.closeInventory();
            return;
        }
        if (!plot.canManage(player, plugin)) {
            plugin.msg().send(player, "no_perm");
            player.closeInventory();
            return;
        }

        int slot = e.getSlot();
        if (slot == 48) {
            plugin.gui().settings().open(player, plot);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        ItemMeta meta = e.getCurrentItem().getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(KEY_BIOME, PersistentDataType.STRING)) return;
        String biomeName = meta.getPersistentDataContainer().get(KEY_BIOME, PersistentDataType.STRING);
        if (biomeName == null || biomeName.isBlank()) return;

        Biome biome = plugin.biomes().resolve(biomeName);
        if (biome == null || !plugin.biomes().isAllowed(biomeName)) return;

        String current = plot.getCustomBiome();
        if (current != null && current.equalsIgnoreCase(biome.name())) {
            player.sendMessage(t(player, "biome_already_active", "&eThat biome is already active here."));
            return;
        }

        double cost = plugin.biomes().costPerChange();
        if (cost > 0 && !plugin.isAdmin(player)) {
            if (!plugin.eco().withdraw(player, cost, CurrencyType.VAULT)) {
                plugin.msg().send(player, "need_vault",
                        Map.of("AMOUNT", plugin.eco().format(cost, CurrencyType.VAULT)));
                plugin.effects().playError(player);
                return;
            }
        }

        player.closeInventory();
        plugin.msg().send(player, "biome_applying",
                Map.of("BIOME", biomeDisplay(player, biome.name())));
        plugin.biomes().apply(plot, biome, player, () -> {
            if (!player.isOnline()) return;
            plugin.msg().send(player, "biome_applied",
                    Map.of("BIOME", biomeDisplay(player, biome.name())));
            plugin.effects().playConfirm(player);
        });
    }

    private String biomeDisplay(Player player, String biomeName) {
        String key = "biome_name_" + biomeName.toLowerCase(Locale.ROOT);
        String pretty = biomeName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return t(player, key, "&f" + pretty);
    }

    private Material iconFor(String biomeName) {
        return switch (biomeName.toUpperCase(Locale.ROOT)) {
            case "FOREST", "DARK_FOREST", "BIRCH_FOREST", "FLOWER_FOREST" -> Material.OAK_SAPLING;
            case "DESERT" -> Material.SAND;
            case "JUNGLE", "SPARSE_JUNGLE", "BAMBOO_JUNGLE" -> Material.JUNGLE_SAPLING;
            case "TAIGA", "SNOWY_TAIGA", "OLD_GROWTH_PINE_TAIGA", "OLD_GROWTH_SPRUCE_TAIGA" -> Material.SPRUCE_SAPLING;
            case "SWAMP", "MANGROVE_SWAMP" -> Material.LILY_PAD;
            case "CHERRY_GROVE" -> Material.CHERRY_SAPLING;
            case "BADLANDS", "ERODED_BADLANDS", "WOODED_BADLANDS" -> Material.RED_SAND;
            case "MUSHROOM_FIELDS" -> Material.RED_MUSHROOM;
            case "MEADOW" -> Material.GRASS_BLOCK;
            case "LUSH_CAVES" -> Material.MOSS_BLOCK;
            case "DRIPSTONE_CAVES" -> Material.DRIPSTONE_BLOCK;
            case "DEEP_DARK" -> Material.SCULK;
            case "SNOWY_PLAINS", "ICE_SPIKES" -> Material.SNOW_BLOCK;
            case "SAVANNA", "SAVANNA_PLATEAU", "WINDSWEPT_SAVANNA" -> Material.ACACIA_SAPLING;
            case "OCEAN", "DEEP_OCEAN", "COLD_OCEAN", "LUKEWARM_OCEAN", "WARM_OCEAN", "FROZEN_OCEAN" -> Material.WATER_BUCKET;
            case "RIVER", "FROZEN_RIVER" -> Material.WATER_BUCKET;
            case "BEACH", "SNOWY_BEACH" -> Material.SAND;
            case "MOUNTAINS", "WINDSWEPT_HILLS", "JAGGED_PEAKS", "FROZEN_PEAKS", "STONY_PEAKS", "SNOWY_SLOPES", "GROVE" -> Material.STONE;
            case "THE_END", "END_HIGHLANDS", "END_MIDLANDS", "END_BARRENS", "SMALL_END_ISLANDS" -> Material.END_STONE;
            case "NETHER_WASTES", "CRIMSON_FOREST", "WARPED_FOREST", "SOUL_SAND_VALLEY", "BASALT_DELTAS" -> Material.NETHERRACK;
            default -> Material.GRASS_BLOCK;
        };
    }

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private String tv(Player p, String key, Map<String, String> vars, String fallback) {
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(p, key, vars);
        } catch (Throwable ignored) {}
        return GUIManager.safeText(key, raw, fallback);
    }
}
