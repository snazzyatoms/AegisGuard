package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType; // --- NEW IMPORT ---
import org.bukkit.Bukkit; 
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PlotCosmeticsGUI
 * - Allows players to buy and apply cosmetic particle effects.
 * - Updated to use EconomyManager.
 */
public class PlotCosmeticsGUI {

    private final AegisGuard plugin;

    public PlotCosmeticsGUI(AegisGuard plugin) {
        this.plugin = plugin;
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

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, GUIManager.icon(Material.GRAY_STAINED_GLASS_PANE, " ", null));
        }

        ConfigurationSection borderSection = plugin.cfg().raw().getConfigurationSection("cosmetics.border_particles");
        String currentBorder = plot.getBorderParticle();
        
        // Slot 0: "None"
        inv.setItem(0, GUIManager.icon(
                Material.BARRIER,
                GUIManager.safeText(plugin.msg().get(player, "cosmetics_border_none"), "§cDefault Particles"),
                currentBorder == null ? List.of("§a(Selected)") : List.of("§7Click to select.")
        ));

        if (borderSection != null) {
            int slot = 1;
            for (String key : borderSection.getKeys(false)) {
                if (slot >= 45) break;

                String materialName = borderSection.getString(key + ".material", "BLAZE_POWDER");
                String particleName = borderSection.getString(key + ".particle", "FLAME");
                String displayName = GUIManager.safeText(borderSection.getString(key + ".display-name"), "§6Flame Border");
                double price = borderSection.getDouble(key + ".price", 0.0);

                Material material = Material.matchMaterial(materialName);
                if (material == null) material = Material.BLAZE_POWDER;

                List<String> lore = new ArrayList<>();
                lore.add("§7Applies a " + particleName + " effect to");
                lore.add("§7your plot's visualization border.");
                lore.add(" ");

                // --- NEW: Use Eco Manager formatting ---
                // Defaulting cosmetics to VAULT for now, but you could add a config option later
                CurrencyType type = CurrencyType.VAULT; 

                if (price > 0) {
                    lore.add("§7Cost: §e" + plugin.eco().format(price, type));
                    lore.add("§eLeft-Click: §7Buy & Apply");
                } else {
                    lore.add("§aFree!");
                    lore.add("§eLeft-Click: §7Apply");
                }
                
                if (particleName.equalsIgnoreCase(currentBorder)) {
                    lore.add("§a(Selected)");
                }

                inv.setItem(slot++, GUIManager.icon(material, displayName, lore));
            }
        }
        
        inv.setItem(48, GUIManager.icon(Material.ARROW, "§fBack to Flags", null));
        inv.setItem(49, GUIManager.icon(Material.BARRIER, "§cClose", null));

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

        if (slot == 48) { // Back
            plugin.gui().flags().open(player, plot);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == 49) { // Close
            player.closeInventory();
            plugin.effects().playMenuClose(player);
            return;
        }

        if (slot == 0) {
            plot.setBorderParticle(null);
            plugin.store().setDirty(true);
            plugin.effects().playMenuFlip(player);
            open(player, plot);
            return;
        }

        if (slot > 0 && slot < 45) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

            ConfigurationSection borderSection = plugin.cfg().raw().getConfigurationSection("cosmetics.border_particles");
            if (borderSection == null) return;

            for (String key : borderSection.getKeys(false)) {
                String displayName = GUIManager.safeText(borderSection.getString(key + ".display-name"), "");
                
                if (clicked.getItemMeta() != null && displayName.equals(clicked.getItemMeta().getDisplayName())) {
                    String particleName = borderSection.getString(key + ".particle", "FLAME");
                    double price = borderSection.getDouble(key + ".price", 0.0);

                    // --- NEW: Transaction Logic with EconomyManager ---
                    CurrencyType type = CurrencyType.VAULT; // Default for cosmetics

                    if (price > 0 && !plugin.isAdmin(player)) {
                        if (!plugin.eco().withdraw(player, price, type)) {
                            plugin.msg().send(player, "need_vault", Map.of("AMOUNT", plugin.eco().format(price, type)));
                            plugin.effects().playError(player);
                            return;
                        }
                        plugin.msg().send(player, "cosmetic_purchased");
                    }
                    
                    plot.setBorderParticle(particleName);
                    plugin.store().setDirty(true);
                    plugin.effects().playConfirm(player);
                    open(player, plot);
                    return;
                }
            }
        }
    }
}
