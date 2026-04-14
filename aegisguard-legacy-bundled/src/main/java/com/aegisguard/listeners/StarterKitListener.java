package com.aegisguard.listeners;

import com.aegisguard.AegisGuard;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.util.CompatMaterial;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StarterKitListener implements Listener {

    private final AegisGuard plugin;
    private final NamespacedKey starterNoteKey;
    private final NamespacedKey starterNoteIdKey;
    private final NamespacedKey wandIdKey;

    public StarterKitListener(AegisGuard plugin) {
        this.plugin = plugin;
        this.starterNoteKey = new NamespacedKey(plugin, "starter_quickstart_note");
        this.starterNoteIdKey = new NamespacedKey(plugin, "starter_quickstart_note_id");
        this.wandIdKey = new NamespacedKey(plugin, "wand_id");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) return;
        if (!isStarterKitEnabled()) return;

        plugin.runMain(player, () -> giveStarterItems(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isStarterNoteDropCleanupEnabled()) return;

        Item dropped = event.getItemDrop();
        if (!isStarterNote(dropped.getItemStack())) return;

        dropped.remove();
        try {
            plugin.effects().playToggle(event.getPlayer());
        } catch (Throwable ignored) {}
    }

    private void giveStarterItems(Player player) {
        FileConfiguration cfg = plugin.getConfig();
        PlayerInventory inv = player.getInventory();

        if (cfg.getBoolean("starter_kit.first_join.give_wand", true) && !WandSafetyListener.playerHasAnyWand(player)) {
            placeItem(inv, buildStarterWand(player), cfg.getInt("starter_kit.first_join.wand_hotbar_slot", 0));
        }

        if (cfg.getBoolean("starter_kit.first_join.give_quickstart_note", true) && !hasStarterNote(player)) {
            placeItem(inv, buildQuickstartNote(player), cfg.getInt("starter_kit.first_join.note_hotbar_slot", 1));
        }

        player.updateInventory();
    }

    private ItemStack buildStarterWand(Player player) {
        ItemStack rod = new ItemStack(CompatMaterial.resolve("LIGHTNING_ROD", "BLAZE_ROD"));
        ItemMeta meta = rod.getItemMeta();
        if (meta == null) return rod;

        meta.setDisplayName(color(tr(player, "wand_item_name", "&bAegis Scepter")));

        List<String> lore = trList(player, "wand_item_lore", List.of(
                "&7Right-click: set the first corner",
                "&7Left-click: set the second corner",
                "&7Use &b/ag claim &7to create your plot."
        ));
        meta.setLore(colorize(lore));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(SelectionService.WAND_KEY, PersistentDataType.BYTE, (byte) 1);
        pdc.set(wandIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());

        rod.setItemMeta(meta);
        return rod;
    }

    private ItemStack buildQuickstartNote(Player player) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta == null) return paper;

        meta.setDisplayName(color(tr(player, "starter_note_name", "&eSettler's Quickstart Note")));

        List<String> lore = new ArrayList<>(trList(player, "starter_note_lore", List.of(
                "&7Welcome to AegisGuard.",
                " ",
                "&eOpen the menu: &b/ag menu",
                "&eGet the wand: &b/ag wand",
                "&eRight-click: &7first corner",
                "&eLeft-click: &7second corner",
                "&eClaim land: &b/ag claim",
                " ",
                "&7For deeper help, open the Codex",
                "&7inside the main menu."
        )));
        lore.add(" ");
        lore.add(tr(player, "starter_note_discard_lore", "&8Drop this note to discard it."));

        meta.setLore(colorize(lore));

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(starterNoteKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(starterNoteIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());

        paper.setItemMeta(meta);
        return paper;
    }

    private void placeItem(PlayerInventory inventory, ItemStack item, int preferredHotbarSlot) {
        int slot = clampHotbarSlot(preferredHotbarSlot);
        ItemStack existing = inventory.getItem(slot);
        if (existing == null || existing.getType().isAir()) {
            inventory.setItem(slot, item);
            return;
        }

        int firstEmpty = inventory.firstEmpty();
        if (firstEmpty >= 0) {
            inventory.setItem(firstEmpty, item);
            return;
        }

        inventory.addItem(item);
    }

    private boolean hasStarterNote(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isStarterNote(item)) return true;
        }
        return false;
    }

    private boolean isStarterNote(ItemStack stack) {
        if (stack == null) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(starterNoteKey, PersistentDataType.BYTE);
    }

    private boolean isStarterKitEnabled() {
        return plugin.getConfig().getBoolean("starter_kit.first_join.enabled", true);
    }

    private boolean isStarterNoteDropCleanupEnabled() {
        return plugin.getConfig().getBoolean("starter_kit.first_join.note_disappears_on_drop", true);
    }

    private int clampHotbarSlot(int slot) {
        if (slot < 0) return 0;
        return Math.min(slot, 8);
    }

    private String tr(Player player, String key, String fallback) {
        try {
            return plugin.gui().tr(player, key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private List<String> trList(Player player, String key, List<String> fallback) {
        try {
            return plugin.gui().trList(player, key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private List<String> colorize(List<String> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) out.add(color(line));
        return out;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
