package com.aegisguard.gui;

import com.yourname.aegisguard.AegisGuard;
import com.yourname.aegisguard.economy.CurrencyType;
import com.yourname.aegisguard.managers.LanguageManager;
import com.yourname.aegisguard.objects.Estate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlotCosmeticsGUI {

    private final AegisGuard plugin;
    private final NamespacedKey KEY_PARTICLE_ID;

    public PlotCosmeticsGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.KEY_PARTICLE_ID = new NamespacedKey(plugin, "cosmetic_id");
    }

    public static class CosmeticsHolder implements InventoryHolder {
        private final Estate estate;
        public CosmeticsHolder(Estate estate) { this.estate = estate; }
        public Estate getEstate() { return estate; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Estate estate) {
        LanguageManager lang = plugin.getLanguageManager();
        
        String title = lang.getGui("title_cosmetics"); 
        if (title.contains("Missing")) title = "§dEstate Cosmetics";
        
        Inventory inv = Bukkit.createInventory(new CosmeticsHolder(estate), 54, title);

        // Fill Footer
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("visuals.particles.border_particles");
        String currentBorder = estate.getBorderParticle(); // Ensure getBorderParticle() is in Estate.java
        
        // Slot 0: Reset/None
        String resetName = lang.getMsg(player, "cosmetics_border_none");
        if (resetName.contains("Missing")) resetName = "§cDisable Border";
        
        List<String> noneLore = (currentBorder == null) ? 
            List.of("§a(Active)") : 
            List.of("§7Click to disable.");
            
        inv.setItem(0, GUIManager.createItem(Material.BARRIER, resetName, noneLore));

        if (section != null) {
            int slot = 1;
            for (String key : section.getKeys(false)) {
                if (slot >= 45) break;

                String matName = section.getString(key + ".material", "BLAZE_POWDER");
                String particleName = section.getString(key + ".particle", "FLAME");
                String displayName = GUIManager.safeText(section.getString(key + ".display-name"), "Particle");
                double price = section.getDouble(key + ".price", 0.0);

                Material material = Material.matchMaterial(matName);
                if (material == null) material = Material.BLAZE_POWDER;

                List<String> lore = new ArrayList<>();
                lore.add("§7Effect: " + particleName);
                lore.add(" ");

                CurrencyType type = CurrencyType.VAULT; 
                boolean isSelected = particleName.equalsIgnoreCase(currentBorder);

                if (isSelected) {
                    lore.add("§a(Active)");
                } else if (price > 0 && !plugin.isAdmin(player)) {
                    lore.add("§7Cost: §e" + plugin.getEconomy().format(price, type));
                    lore.add("§eLeft-Click: Buy");
                } else {
                    lore.add("§aFree!");
                    lore.add("§eLeft-Click: Apply");
                }

                ItemStack icon = GUIManager.createItem(material, displayName, lore);
                
                // Store Key in NBT
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(KEY_PARTICLE_ID, PersistentDataType.STRING, key);
                    icon.setItemMeta(meta);
                }
                
                inv.setItem(slot++, icon);
            }
        }
        
        // Navigation
        inv.setItem(48, GUIManager.createItem(Material.ARROW, lang.getGui("button_back")));
        inv.setItem(49, GUIManager.createItem(Material.BARRIER, lang.getGui("button_close")));

        player.openInventory(inv);
        // plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, CosmeticsHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Estate estate = holder.getEstate();
        if (estate == null) { player.closeInventory(); return; }

        // Permission Check
        if (!estate.getOwnerId().equals(player.getUniqueId()) && !plugin.isAdmin(player)) {
            player.sendMessage(plugin.getLanguageManager().getMsg(player, "no_permission"));
            player.closeInventory();
            return;
        }

        int slot = e.getSlot();

        // Nav
        if (slot == 48) { 
            // Go back to dashboard
            plugin.getGuiManager().openGuardianCodex(player);
            return; 
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        // Reset
        if (slot == 0) {
            if (estate.getBorderParticle() != null) {
                estate.setBorderParticle(null);
                // plugin.getEstateManager().saveEstate(estate);
                player.sendMessage("§eBorder disabled.");
                open(player, estate);
            }
            return;
        }

        // Selection
        ItemStack item = e.getCurrentItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(KEY_PARTICLE_ID, PersistentDataType.STRING)) return;

        String key = meta.getPersistentDataContainer().get(KEY_PARTICLE_ID, PersistentDataType.STRING);
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("visuals.particles.border_particles." + key);
        
        if (section != null) {
            String particleName = section.getString("particle");
            
            // Check if already selected
            if (particleName != null && particleName.equalsIgnoreCase(estate.getBorderParticle())) {
                player.sendMessage("§cThis cosmetic is already active.");
                return;
            }

            double price = section.getDouble("price", 0.0);
            
            if (price > 0 && !plugin.isAdmin(player)) {
                if (!plugin.getEconomy().withdraw(player, price, CurrencyType.VAULT)) {
                    player.sendMessage("§cInsufficient Funds.");
                    return;
                }
                player.sendMessage("§aPurchased cosmetic!");
            }

            estate.setBorderParticle(particleName);
            // plugin.getEstateManager().saveEstate(estate);
            
            open(player, estate); 
        }
    }
}
