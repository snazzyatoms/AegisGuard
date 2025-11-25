package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.Map;

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
        String title = GUIManager.safeText(plugin.msg().get(player, "biome_gui_title"), "§2Change Biome");
        Inventory inv = Bukkit.createInventory(new BiomeHolder(plot), 45, title);

        // Fill background
        for (int i = 36; i < 45; i++) inv.setItem(i, GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null));

        List<String> allowedBiomes = plugin.cfg().getAllowedBiomes();
        double cost = plugin.cfg().getBiomeChangeCost();
        CurrencyType type = plugin.cfg().getCurrencyFor("biomes");
        String costStr = (cost > 0 && !plugin.isAdmin(player)) ? plugin.eco().format(cost, type) : "Free";

        int slot = 0;
        for (String biomeName : allowedBiomes) {
            if (slot >= 36) break;
            
            try {
                Biome biome = Biome.valueOf(biomeName.toUpperCase());
                Material icon = getBiomeIcon(biome);
                
                List<String> lore = new ArrayList<>(plugin.msg().getList(player, "biome_select_lore"));
                // Replace placeholders in lore
                lore.replaceAll(line -> line.replace("{BIOME}", formatName(biome.name()))
                                            .replace("{COST}", costStr));

                inv.setItem(slot++, GUIManager.icon(icon, "§a" + formatName(biome.name()), lore));
                
            } catch (IllegalArgumentException ignored) {
                // Skip invalid config biomes
            }
        }

        // Back Button
        inv.setItem(40, GUIManager.icon(Material.ARROW, GUIManager.safeText(plugin.msg().get(player, "button_back"), "Back"), null));
        
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, BiomeHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        Plot plot = holder.getPlot();

        if (e.getSlot() == 40) { // Back
            plugin.gui().flags().open(player, plot);
            plugin.effects().playMenuFlip(player);
            return;
        }

        if (e.getSlot() < 36 && e.getCurrentItem().getType() != Material.AIR) {
            String name = e.getCurrentItem().getItemMeta().getDisplayName();
            String rawBiome = name.replace("§a", "").replace(" ", "_").toUpperCase();
            
            try {
                Biome newBiome = Biome.valueOf(rawBiome);
                double cost = plugin.cfg().getBiomeChangeCost();
                CurrencyType type = plugin.cfg().getCurrencyFor("biomes");

                // Transaction
                if (cost > 0 && !plugin.isAdmin(player)) {
                    if (!plugin.eco().withdraw(player, cost, type)) {
                        plugin.msg().send(player, "need_vault", Map.of("AMOUNT", plugin.eco().format(cost, type)));
                        plugin.effects().playError(player);
                        return;
                    }
                    plugin.msg().send(player, "biome_cost_paid", Map.of("AMOUNT", plugin.eco().format(cost, type)));
                }

                // Apply Biome Change
                applyBiomeChange(plot, newBiome);
                
                plugin.msg().send(player, "biome_changed", Map.of("BIOME", formatName(newBiome.name())));
                plugin.effects().playConfirm(player);
                player.closeInventory();

            } catch (Exception ex) {
                player.sendMessage("§cError applying biome: " + ex.getMessage());
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

        // Set biome block by block (Correct API for 1.16+)
        for (int x = minX; x <= maxX; x += 4) { // Optimization: Biomes are stored every 4 blocks
            for (int z = minZ; z <= maxZ; z += 4) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y += 4) {
                    world.setBiome(x, y, z, biome);
                }
            }
        }
        
        // Send update packets
        // Note: In 1.20+, clients might need a relog or chunk reload to see color changes immediately.
        // We can force it by re-sending chunks, but that's heavy. 
        // Usually just walking away and back works.
    }

    private Material getBiomeIcon(Biome biome) {
        String name = biome.name();
        if (name.contains("DESERT")) return Material.SAND;
        if (name.contains("FOREST")) return Material.OAK_SAPLING;
        if (name.contains("JUNGLE")) return Material.JUNGLE_SAPLING;
        if (name.contains("TAIGA")) return Material.SPRUCE_SAPLING;
        if (name.contains("SWAMP")) return Material.LILY_PAD;
        if (name.contains("PLAINS")) return Material.GRASS_BLOCK;
        if (name.contains("BADLANDS") || name.contains("MESA")) return Material.TERRACOTTA;
        if (name.contains("MUSHROOM")) return Material.RED_MUSHROOM;
        if (name.contains("CHERRY")) return Material.CHERRY_SAPLING;
        return Material.GRASS_BLOCK;
    }
    
    private String formatName(String input) {
        String[] words = input.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            sb.append(w.charAt(0)).append(w.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
}
