package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
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
 * BiomeGUI
 * - Allows changing the biome of a plot.
 * - Uses language engine helpers with safe fallbacks.
 */
public class BiomeGUI {

    private final AegisGuard plugin;

    public BiomeGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class BiomeHolder implements InventoryHolder {
        private final Plot plot;
        public BiomeHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        // ✅ Title fix: translate & colors + safe fallback + clamp length (via GUIManager.title helper)
        String title = plugin.gui().title(
                player,
                "biome_gui_title",
                "&2✦ Biome Shaper ✦"
        );

        Inventory inv = Bukkit.createInventory(new BiomeHolder(plot), 45, title);

        // Background Filler (bottom row only, main grid will be overridden)
        ItemStack filler = GUIManager.getFiller();
        for (int i = 36; i < 45; i++) inv.setItem(i, filler);

        List<String> allowedBiomes = plugin.cfg().getAllowedBiomes();
        double cost = plugin.cfg().getBiomeChangeCost();
        CurrencyType type = plugin.cfg().getCurrencyFor("biomes");
        String costStr = (cost > 0 && !plugin.isAdmin(player))
                ? plugin.eco().format(cost, type)
                : "Free";

        String currentBiomeStr = plot.getCustomBiome();

        int slot = 0;
        for (String biomeName : allowedBiomes) {
            if (slot >= 36) break;

            try {
                Biome biome = Biome.valueOf(biomeName.toUpperCase());
                Material iconMat = getBiomeIcon(biome);
                String prettyName = formatName(biome.name());

                // Default lore if no language key is present
                List<String> defaultLore = new ArrayList<>();
                defaultLore.add("§7Cost: " + costStr);
                defaultLore.add(" ");
                defaultLore.add("§eClick to Apply");

                // Localized lore (with {BIOME} / {COST} placeholders)
                List<String> lore = new ArrayList<>(lines(player, "biome_select_lore", defaultLore));
                lore.replaceAll(line ->
                        line.replace("{BIOME}", prettyName)
                                .replace("{COST}", costStr)
                );

                ItemStack icon = GUIManager.createItem(iconMat, "§a" + prettyName, lore);

                if (currentBiomeStr != null && currentBiomeStr.equalsIgnoreCase(biome.name())) {
                    addGlow(icon);
                }

                inv.setItem(slot++, icon);

            } catch (IllegalArgumentException ignored) {
                // Skip invalid config biomes
            }
        }

        // --- NAVIGATION BUTTONS ---

        // Back Button (Returns to Flags Menu)
        inv.setItem(40, GUIManager.createItem(
                Material.ARROW,
                line(player, "biome_button_back", "§fBack to Flags", Map.of()),
                lines(player, "biome_back_lore", List.of("§7Return to Plot Settings."))
        ));

        // Exit Button (Closes entirely)
        inv.setItem(44, GUIManager.createItem(
                Material.BARRIER,
                line(player, "biome_button_exit", "§cExit Menu", Map.of()),
                lines(player, "biome_exit_lore", List.of("§7Close the Biome Changer."))
        ));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, BiomeHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        Plot plot = holder.getPlot();

        // Navigation
        if (e.getSlot() == 40) { // Back to Flags
            plugin.gui().flags().open(player, plot);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (e.getSlot() == 44) { // Exit Menu
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }

        // Selection
        if (e.getSlot() < 36 && e.getCurrentItem().getType() != Material.AIR) {
            ItemMeta meta = e.getCurrentItem().getItemMeta();
            if (meta == null || meta.getDisplayName() == null) return;

            String displayName = meta.getDisplayName();
            String rawBiome = displayName.replace("§a", "").toUpperCase().replace(" ", "_");

            try {
                Biome newBiome = Biome.valueOf(rawBiome);

                // Don't charge if already set
                if (plot.getCustomBiome() != null && plot.getCustomBiome().equals(newBiome.name())) {
                    player.sendMessage(line(
                            player,
                            "biome_already_active",
                            "§cThis biome is already active.",
                            Map.of()
                    ));
                    return;
                }

                double cost = plugin.cfg().getBiomeChangeCost();
                CurrencyType type = plugin.cfg().getCurrencyFor("biomes");

                // Transaction
                if (cost > 0 && !plugin.isAdmin(player)) {
                    if (!plugin.eco().withdraw(player, cost, type)) {
                        plugin.msg().send(player, "need_vault",
                                Map.of("AMOUNT", plugin.eco().format(cost, type)));
                        plugin.effects().playError(player);
                        return;
                    }
                    plugin.msg().send(player, "cost_deducted",
                            Map.of("AMOUNT", plugin.eco().format(cost, type)));
                }

                // Apply
                player.closeInventory();
                player.sendMessage(line(
                        player,
                        "biome_terraforming",
                        "§eTerraforming... this may take a moment.",
                        Map.of()
                ));

                applyBiomeChange(plot, newBiome);

                plot.setCustomBiome(newBiome.name());
                plugin.store().setDirty(true);

                plugin.msg().send(player, "biome_changed",
                        Map.of("BIOME", formatName(newBiome.name())));
                plugin.effects().playConfirm(player);

                // Force client update (soft hint)
                refreshChunks(player, plot);

            } catch (Exception ex) {
                player.sendMessage("§cError applying biome: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void applyBiomeChange(Plot plot, Biome biome) {
        World world = Bukkit.getWorld(plot.getWorld());
        if (world == null) return;

        int minX = plot.getX1();
        int minZ = plot.getZ1();
        int maxX = plot.getX2();
        int maxZ = plot.getZ2();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        // Optimized Loop: Minecraft stores biomes in 4x4x4 cubes.
        for (int x = minX; x <= maxX; x += 4) {
            for (int z = minZ; z <= maxZ; z += 4) {
                for (int y = minY; y < maxY; y += 4) {
                    if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                        world.setBiome(x, y, z, biome);
                    }
                }
            }
        }
    }

    private void refreshChunks(Player player, Plot plot) {
        player.sendMessage(line(
                player,
                "biome_refresh_hint",
                "§7(Note: You may need to reconnect or leave the area to see visual changes fully.)",
                Map.of()
        ));
    }

    private Material getBiomeIcon(Biome biome) {
        String name = biome.name();
        if (name.contains("DESERT")) return Material.SAND;
        if (name.contains("FOREST") || name.contains("BIRCH")) return Material.OAK_SAPLING;
        if (name.contains("JUNGLE")) return Material.JUNGLE_SAPLING;
        if (name.contains("TAIGA") || name.contains("SNOW")) return Material.SPRUCE_SAPLING;
        if (name.contains("SWAMP") || name.contains("MANGROVE")) return Material.LILY_PAD;
        if (name.contains("PLAINS")) return Material.GRASS_BLOCK;
        if (name.contains("BADLANDS") || name.contains("MESA")) return Material.TERRACOTTA;
        if (name.contains("MUSHROOM")) return Material.RED_MUSHROOM;
        if (name.contains("CHERRY")) return Material.PINK_PETALS; // 1.20+
        if (name.contains("DEEP_DARK") || name.contains("SCULK")) return Material.SCULK_SENSOR; // 1.19+
        if (name.contains("OCEAN") || name.contains("RIVER")) return Material.WATER_BUCKET;
        if (name.contains("NETHER") || name.contains("CRIMSON")) return Material.NETHERRACK;
        if (name.contains("END")) return Material.END_STONE;
        return Material.GRASS_BLOCK;
    }

    private String formatName(String input) {
        String[] words = input.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(w.charAt(0)).append(w.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }

    private void addGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
    }

    // --------------------------------------------------
    // LANGUAGE ENGINE BRIDGE
    // --------------------------------------------------

    private String line(Player player, String key, String fallback, Map<String, String> vars) {
        String raw = null;
        try {
            if (vars == null || vars.isEmpty()) {
                raw = plugin.msg().get(player, key);
            } else {
                raw = plugin.msg().get(player, key, vars);
            }
        } catch (Throwable ignored) {}
        if (raw == null || raw.isEmpty() || raw.equalsIgnoreCase(key)) {
            return fallback;
        }
        return raw;
    }

    private List<String> lines(Player player, String key, List<String> fallback) {
        List<String> raw = null;
        try {
            raw = plugin.msg().getList(player, key);
        } catch (Throwable ignored) {}
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        return raw;
    }
}
