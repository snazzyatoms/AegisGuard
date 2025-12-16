package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.economy.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
 * - Fully localized via Codex.
 *
 * ✅ Title fix: translate & colors + safe fallback + clamp length
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
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            return;
        }

        // ✅ Title fix (Codex key -> colored -> clamped)
        String rawTitle = (plugin.codex() != null)
                ? plugin.codex().tr(player, "cosmetics_gui_title")
                : null;

        String title = formatTitle(rawTitle, "&d✦ Plot Cosmetics ✦");
        Inventory inv = Bukkit.createInventory(new CosmeticsHolder(plot), 54, title);

        // Fill Footer
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        ConfigurationSection section = plugin.cfg().raw().getConfigurationSection("cosmetics.border_particles");
        String currentBorder = plot.getBorderParticle();

        // Slot 0: Reset/None
        String resetName = plugin.codex().tr(player, "cosmetics_border_none");
        if (resetName == null || resetName.isEmpty()) resetName = "§cDisable Border";

        List<String> noneLore = new ArrayList<>();
        if (currentBorder == null) {
            String selected = plugin.codex().tr(player, "cosmetics_status_selected");
            if (selected == null || selected.isEmpty()) selected = "§a(Selected)";
            noneLore.add(selected);
        } else {
            String disable = plugin.codex().tr(player, "cosmetics_click_disable");
            if (disable == null || disable.isEmpty()) disable = "§7Click to disable.";
            noneLore.add(disable);
        }
        inv.setItem(0, GUIManager.createItem(Material.BARRIER, resetName, noneLore));

        if (section != null) {
            int slot = 1;
            for (String key : section.getKeys(false)) {
                if (slot >= 45) break;

                String matName = section.getString(key + ".material", "BLAZE_POWDER");
                String particleName = section.getString(key + ".particle", "FLAME");
                String rawDisplay = section.getString(key + ".display-name");
                String displayName = GUIManager.safeText(rawDisplay, "Particle");
                double price = section.getDouble(key + ".price", 0.0);

                Material material = Material.matchMaterial(matName);
                if (material == null) material = Material.BLAZE_POWDER;

                List<String> lore = new ArrayList<>();

                // Effect line via Codex
                String effectLine = plugin.codex().tr(
                        player,
                        "cosmetics_effect_line",
                        Map.of("EFFECT", particleName)
                );
                if (effectLine == null || effectLine.isEmpty()) {
                    effectLine = "§7Effect: " + particleName;
                }
                lore.add(effectLine);
                lore.add(" ");

                CurrencyType type = CurrencyType.VAULT;
                boolean isSelected = particleName.equalsIgnoreCase(currentBorder);

                if (isSelected) {
                    String selected = plugin.codex().tr(player, "cosmetics_status_selected");
                    if (selected == null || selected.isEmpty()) selected = "§a(Selected)";
                    lore.add(selected);
                } else if (price > 0 && !plugin.isAdmin(player)) {
                    String costLine = plugin.codex().tr(
                            player,
                            "cosmetics_cost_line",
                            Map.of("AMOUNT", plugin.eco().format(price, type))
                    );
                    if (costLine == null || costLine.isEmpty()) {
                        costLine = "§7Cost: §e" + plugin.eco().format(price, type);
                    }
                    lore.add(costLine);

                    String clickBuy = plugin.codex().tr(player, "cosmetics_click_buy");
                    if (clickBuy == null || clickBuy.isEmpty()) clickBuy = "§eLeft-Click: Buy";
                    lore.add(clickBuy);
                } else {
                    String free = plugin.codex().tr(player, "cosmetics_status_free");
                    if (free == null || free.isEmpty()) free = "§aFree!";
                    lore.add(free);

                    String apply = plugin.codex().tr(player, "cosmetics_click_apply");
                    if (apply == null || apply.isEmpty()) apply = "§eLeft-Click: Apply";
                    lore.add(apply);
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
        String backName = plugin.codex().tr(player, "button_back");
        if (backName == null || backName.isEmpty()) backName = "§fBack";
        List<String> backLore = plugin.codex().list(player, "back_lore");
        if (backLore == null) backLore = List.of();
        inv.setItem(48, GUIManager.createItem(Material.ARROW, backName, backLore));

        String exitName = plugin.codex().tr(player, "button_exit");
        if (exitName == null || exitName.isEmpty()) exitName = "§cClose";
        List<String> exitLore = plugin.codex().list(player, "exit_lore");
        if (exitLore == null) exitLore = List.of();
        inv.setItem(49, GUIManager.createItem(Material.BARRIER, exitName, exitLore));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, CosmeticsHolder holder) {
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (plot == null) {
            player.closeInventory();
            return;
        }

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
                plugin.msg().send(player, "cosmetics_removed");
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

            if (particleName != null && particleName.equalsIgnoreCase(plot.getBorderParticle())) {
                player.sendMessage(plugin.msg().get(player, "cosmetics_already_active"));
                return;
            }

            double price = section.getDouble("price", 0.0);

            if (price > 0 && !plugin.isAdmin(player)) {
                if (!plugin.eco().withdraw(player, price, CurrencyType.VAULT)) {
                    plugin.msg().send(
                            player,
                            "need_vault",
                            Map.of("AMOUNT", plugin.eco().format(price, CurrencyType.VAULT))
                    );
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

    // ✅ Central title cleanup for THIS GUI
    private String formatTitle(String raw, String fallback) {
        String t = GUIManager.safeText(raw, fallback);
        t = ChatColor.translateAlternateColorCodes('&', t);

        if (t.length() > 32) t = t.substring(0, 32);
        if (t.endsWith("§")) t = t.substring(0, t.length() - 1);

        return t;
    }
}
