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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * PlotCosmeticsGUI
 * - Allows players to buy and apply particle borders.
 * - Fully localized via Codex (per-player language styles).
 *
 * ✅ Title fix: uses plugin.gui().title(...) (color translate + safe fallback + clamp)
 * ✅ Uses GUIManager.color() (public) indirectly via GUIManager helpers.
 * ✅ Supports per-cosmetic localized keys:
 *    cosmetics_border_<id>_name
 *    cosmetics_border_<id>_lore
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
        if (!plugin.modules().on(com.aegisguard.config.Modules.Id.COSMETICS)) {
            plugin.msg().send(player, "module_disabled", java.util.Map.of("MODULE", "Cosmetics"));
            return;
        }

        // ✅ Title is now fully language-aware via GUIManager.title()
        String title = plugin.gui().title(
                player,
                "cosmetics_gui_title",
                "&d✦ Plot Cosmetics ✦"
        );

        Inventory inv = Bukkit.createInventory(new CosmeticsHolder(plot), 54, title);

        // Fill Footer
        ItemStack filler = GUIManager.getFiller();
        for (int i = 45; i < 54; i++) inv.setItem(i, filler);

        ConfigurationSection section = plugin.cfg().raw().getConfigurationSection("cosmetics.border_particles");
        String currentBorder = plot.getBorderParticle();

        // ---------------------------------------------
        // Slot 0: Reset/None (localized)
        // ---------------------------------------------
        String resetName = t(player, "cosmetics_border_none", "&cDisable Border");

        List<String> noneLore = new ArrayList<>();
        if (currentBorder == null) {
            noneLore.add(t(player, "cosmetics_status_selected", "&a(Selected)"));
        } else {
            noneLore.add(t(player, "cosmetics_click_disable", "&7Click to disable."));
        }
        inv.setItem(0, GUIManager.createItem(Material.BARRIER, resetName, noneLore));

        // ---------------------------------------------
        // Cosmetics list (localized per item where possible)
        // ---------------------------------------------
        if (section != null) {
            int slot = 1;
            for (String key : section.getKeys(false)) {
                if (slot >= 45) break;

                String matName = section.getString(key + ".material", "BLAZE_POWDER");
                String particleName = section.getString(key + ".particle", "FLAME");
                String rawDisplay = section.getString(key + ".display-name", "&fParticle Border");
                double price = section.getDouble(key + ".price", 0.0);

                Material material = Material.matchMaterial(matName);
                if (material == null) material = Material.BLAZE_POWDER;

                CurrencyType type = CurrencyType.VAULT;
                boolean isSelected = particleName != null && particleName.equalsIgnoreCase(currentBorder);

                // Per-cosmetic localization hooks (override config display-name if present)
                // Example keys:
                // cosmetics_border_flame_name
                // cosmetics_border_flame_lore
                String perNameKey = "cosmetics_border_" + key + "_name";
                String perLoreKey = "cosmetics_border_" + key + "_lore";

                String displayName = t(player, perNameKey, rawDisplay);

                List<String> lore = new ArrayList<>();

                // Optional per-cosmetic lore (insert at top if provided)
                List<String> perLore = tl(player, perLoreKey, Collections.emptyList());
                if (!perLore.isEmpty()) {
                    lore.addAll(perLore);
                    lore.add(" ");
                }

                // Effect line (vars)
                lore.add(tv(player,
                        "cosmetics_effect_line",
                        Map.of("EFFECT", (particleName == null ? "?" : particleName)),
                        "&7Effect: &f" + (particleName == null ? "?" : particleName)
                ));
                lore.add(" ");

                if (isSelected) {
                    lore.add(t(player, "cosmetics_status_selected", "&a(Selected)"));
                } else if (price > 0 && !plugin.isAdmin(player)) {
                    lore.add(tv(player,
                            "cosmetics_cost_line",
                            Map.of("AMOUNT", plugin.eco().format(price, type)),
                            "&7Cost: &e" + plugin.eco().format(price, type)
                    ));
                    lore.add(t(player, "cosmetics_click_buy", "&eLeft-Click: &7Buy"));
                } else {
                    lore.add(t(player, "cosmetics_status_free", "&aFree!"));
                    lore.add(t(player, "cosmetics_click_apply", "&eLeft-Click: &7Apply"));
                }

                ItemStack icon = GUIManager.createItem(material, displayName, lore);

                // Store config key in PDC
                ItemMeta meta = icon.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(KEY_PARTICLE_ID, PersistentDataType.STRING, key);
                    icon.setItemMeta(meta);
                }

                inv.setItem(slot++, icon);
            }
        }

        // ---------------------------------------------
        // Navigation (localized)
        // ---------------------------------------------
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

    public void handleClick(Player player, InventoryClickEvent e, CosmeticsHolder holder) {
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
        if (key == null || key.isBlank()) return;

        ConfigurationSection section = plugin.cfg().raw().getConfigurationSection("cosmetics.border_particles." + key);
        if (section == null) return;

        String particleName = section.getString("particle");
        if (particleName == null || particleName.isBlank()) return;

        if (particleName.equalsIgnoreCase(plot.getBorderParticle())) {
            // Language-aware message via msg() (per player language)
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

    // --------------------------------------------------
    // Language helpers (Codex-safe + fallbacks)
    // --------------------------------------------------

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

        // safeText() returns COLORIZED output and blocks "returned key" / [Missing] leaks.
        return GUIManager.safeText(key, raw, fallback);
    }
}
