package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
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

/**
 * PlotCosmeticsGUI
 * - Allows players to buy and apply particle borders.
 * - Fully localized.
 */
public class PlotCosmeticsGUI {

    private final AegisGuard plugin;
    private final NamespacedKey KEY_PARTICLE_ID;

    public PlotCosmeticsGUI(AegisGuard plugin) {
        this.plugin = plugin;
        this.KEY_PARTICLE_ID = new NamespacedKey(plugin, "cosmetic_id");
    }

    public static class CosmeticsHolder implements InventoryHolder {
        private final Plot plot;
        public CosmeticsHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        String title = GUIManager.safeText(plugin.msg().get(player, "cosmetics_gui_title"), "§dPlot Cosmetics");
        Inventory inv = Bukkit.createInventory(new CosmeticsHolder(plot), 54, title);

        // Fill Footer
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        ConfigurationSection section = plugin.cfg().raw().getConfigurationSection("cosmetics.border_particles");
        String currentBorder = plot.getBorderParticle();
        
        // Slot 0: Reset/None
        String resetName = plugin.msg().get(player, "cosmetics_border_none");
        if (resetName == null) resetName = "§cDisable Border";
        
        List<String> noneLore = currentBorder == null ? 
            List.of(plugin.msg().get(player, "cosmetics_status_selected", "§a(Selected)")) : 
            List.of(plugin.msg().get(player, "cosmetics_click_disable", "§7Click to disable."));
            
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
                    lore.add(plugin.msg().get(player, "cosmetics_status_selected", "§a(Selected)"));
                } else if (price > 0 && !plugin.isAdmin(player)) {
                    lore.add("§7Cost: §e" + plugin.eco().format(price, type));
                    lore.add(plugin.msg().get(player, "cosmetics_click_buy", "§eLeft-Click: Buy"));
                } else {
                    lore.add(plugin.msg().get(player, "cosmetics_status_free", "§aFree!"));
                    lore.add(plugin.msg().get(player, "cosmetics_click_apply", "§eLeft-Click: Apply"));
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
        inv.setItem(48, GUIManager.createItem(Material.ARROW, 
            plugin.msg().get(player, "button_back"), 
            plugin.msg().getList(player, "back_lore")));

        inv.setItem(49, GUIManager.createItem(Material.BARRIER, 
            plugin.msg().get(player, "button_exit"), 
            plugin.msg().getList(player, "exit_lore")));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, CosmeticsHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (plot == null) { player.closeInventory(); return; }

        if (!plot.getOwner().equals(player.getUniqueId()) && !plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            player.closeInventory();
            return;
        }

        int slot = e.getSlot();

        // Nav
        if (slot == 48) { 
            plugin.gui().flags().open(player, plot);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        // Reset
        if (slot == 0) {
            if (plot.getBorderParticle() != null) {
                plot.setBorderParticle(null);
                plugin.store().setDirty(true);
                plugin.msg().send(player, "cosmetics_removed"); // Need to add this key
                plugin.effects().playMenuFlip(player);
                open(player, plot);
            }
            return;
        }

        // Selection
        ItemStack item = e.getCurrentItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(KEY_PARTICLE_ID, PersistentDataType.STRING)) return;

        String key = meta.getPersistentDataContainer().get(KEY_PARTICLE_ID, PersistentDataType.STRING);
        ConfigurationSection section = plugin.cfg().raw().getConfigurationSection("cosmetics.border_particles." + key);
        
        if (section != null) {
            String particleName = section.getString("particle");
            
            // Check if already selected
            if (particleName != null && particleName.equalsIgnoreCase(plot.getBorderParticle())) {
                player.sendMessage(plugin.msg().get(player, "cosmetics_already_active")); // Add key
                return;
            }

            double price = section.getDouble("price", 0.0);
            
            if (price > 0 && !plugin.isAdmin(player)) {
                if (!plugin.eco().withdraw(player, price, CurrencyType.VAULT)) {
                    plugin.msg().send(player, "need_vault", Map.of("AMOUNT", plugin.eco().format(price, CurrencyType.VAULT)));
                    plugin.effects().playError(player);
                    return;
                }
                plugin.msg().send(player, "cosmetic_purchased");
            }

            plot.setBorderParticle(particleName);
            plugin.store().setDirty(true);
            plugin.effects().playConfirm(player);
            open(player, plot); 
        }
    }
}
