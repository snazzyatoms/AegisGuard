package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.managers.LanguageManager;
import com.aegisguard.objects.Estate;
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

public class BiomeGUI {

    private final AegisGuard plugin;

    public BiomeGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class BiomeHolder implements InventoryHolder {
        private final Estate estate;
        public BiomeHolder(Estate estate) { this.estate = estate; }
        public Estate getEstate() { return estate; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Estate estate) {
        LanguageManager lang = plugin.getLanguageManager();
        
        // Title: "Change Biome" (Localized)
        String title = lang.getGui("title_biome_menu"); 
        Inventory inv = Bukkit.createInventory(new BiomeHolder(estate), 45, title);

        // Background Filler
        ItemStack filler = GUIManager.getFiller();
        for (int i = 36; i < 45; i++) inv.setItem(i, filler);

        List<String> allowedBiomes = plugin.cfg().getAllowedBiomes();
        double cost = plugin.cfg().getBiomeChangeCost();
        
        // TODO: Move currency type to config (using VAULT for now)
        CurrencyType type = CurrencyType.VAULT; 
        String costStr = (cost > 0 && !plugin.isAdmin(player)) ? plugin.getEconomy().format(cost, type) : "Free";
        String currentBiomeStr = estate.getCustomBiome(); 
        
        int slot = 0;
        for (String biomeName : allowedBiomes) {
            if (slot >= 36) break;
            
            try {
                Biome biome = Biome.valueOf(biomeName.toUpperCase());
                Material iconMat = getBiomeIcon(biome);
                String prettyName = formatName(biome.name());
                
                List<String> lore = new ArrayList<>();
                // Fetch lore template from Lang file
                // If missing, use default
                List<String> template = lang.getMsgList(player, "biome_select_lore");
                if (template.isEmpty()) {
                    lore.add("§7Cost: " + costStr);
                    lore.add(" ");
                    lore.add("§eClick to Apply");
                } else {
                    for(String line : template) {
                        lore.add(line.replace("{COST}", costStr));
                    }
                }

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
        
        // Back Button
        inv.setItem(40, GUIManager.createItem(Material.ARROW, lang.getGui("button_back")));
        
        // Exit Button
        inv.setItem(44, GUIManager.createItem(Material.BARRIER, lang.getGui("button_close")));
        
        player.openInventory(inv);
        // plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, BiomeHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        Estate estate = holder.getEstate();

        // Navigation
        if (e.getSlot() == 40) { // Back
            // plugin.gui().flags().open(player, estate); 
            // Fallback to main menu for now
            plugin.getGuiManager().openGuardianCodex(player);
            return;
        }
        if (e.getSlot() == 44) { // Exit
            player.closeInventory();
            return;
        }

        // Selection
        if (e.getSlot() < 36 && e.getCurrentItem().getType() != Material.AIR) {
            String displayName = e.getCurrentItem().getItemMeta().getDisplayName();
            String rawBiome = ChatColor.stripColor(displayName).toUpperCase().replace(" ", "_");
            
            try {
                Biome newBiome = Biome.valueOf(rawBiome);
                
                // Don't charge if already set
                if (estate.getCustomBiome() != null && estate.getCustomBiome().equals(newBiome.name())) {
                    player.sendMessage("§cThis biome is already active.");
                    return;
                }

                double cost = plugin.cfg().getBiomeChangeCost();
                // Assuming Vault for simplicity in v1.3.0
                CurrencyType type = CurrencyType.VAULT; 

                // Transaction
                if (cost > 0 && !plugin.isAdmin(player)) {
                    if (!plugin.getEconomy().withdraw(player, cost)) {
                        player.sendMessage("§cInsufficient Funds. Cost: " + plugin.getEconomy().format(cost, type));
                        return;
                    }
                    player.sendMessage("§aPaid " + plugin.getEconomy().format(cost, type));
                }

                // Apply
                player.closeInventory();
                player.sendMessage("§eTerraforming... this may take a moment.");
                
                applyBiomeChange(estate, newBiome);
                
                estate.setCustomBiome(newBiome.name());
                // plugin.getEstateManager().saveEstate(estate); // Save logic
                
                player.sendMessage("§a✔ Biome changed to " + formatName(newBiome.name()));
                // plugin.effects().playConfirm(player);
                
                // Force client update message
                refreshChunks(player, estate);

            } catch (Exception ex) {
                player.sendMessage("§cError applying biome: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void applyBiomeChange(Estate estate, Biome biome) {
        World world = estate.getWorld();
        if (world == null) return;

        int minX = estate.getRegion().getLowerNE().getBlockX();
        int minZ = estate.getRegion().getLowerNE().getBlockZ();
        int maxX = estate.getRegion().getUpperSW().getBlockX();
        int maxZ = estate.getRegion().getUpperSW().getBlockZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        // Optimized Loop: Minecraft stores biomes in 4x4x4 cubes.
        for (int x = minX; x <= maxX; x += 4) { 
            for (int z = minZ; z <= maxZ; z += 4) {
                for (int y = minY; y < maxY; y += 4) {
                    // Strict bounds check before setting
                    if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                        world.setBiome(x, y, z, biome);
                    }
                }
            }
        }
    }
    
    private void refreshChunks(Player player, Estate estate) {
        // Inform user to relog/move
        player.sendMessage("§7(Note: You may need to leave the area or relog to see visual changes.)");
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
        if (name.contains("CHERRY")) return Material.PINK_PETALS; 
        if (name.contains("DEEP_DARK") || name.contains("SCULK")) return Material.SCULK_SENSOR; 
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
}
