package com.aegisguard.succession;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Access-page roster for co-owners, heir, transfer, and succession assume/rollback. */
public final class StewardshipGUI {

    private final AegisGuard plugin;

    public StewardshipGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static final class Holder implements InventoryHolder {
        private final UUID plotId;
        public Holder(UUID plotId) { this.plotId = plotId; }
        public UUID plotId() { return plotId; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        if (player == null || plot == null) return;
        Inventory inv = Bukkit.createInventory(new Holder(plot.getPlotId()), 27,
                plugin.gui().title(player, "stewardship_title", "&6Stewardship"));
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(10, GUIManager.createItem(Material.NETHERITE_HELMET,
                t(player, "stewardship_owner_name", "&cOwner: &f{PLAYER}",
                        Map.of("PLAYER", safeName(plot.getOwner(), plot.getOwnerName()))),
                List.of(t(player, "stewardship_owner_lore", "&7The current plot owner."))));

        List<String> coOwners = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : plot.getPlayerRoles().entrySet()) {
            if (plot.isCoOwnerOrSteward(entry.getKey())) {
                coOwners.add(safeName(entry.getKey(), null) + " &8(" + entry.getValue() + "&8)");
            }
        }
        inv.setItem(12, GUIManager.createItem(Material.GOLDEN_HELMET,
                t(player, "stewardship_coowners_name", "&6Co-Owners & Stewards"),
                coOwners.isEmpty()
                        ? List.of(t(player, "stewardship_coowners_empty", "&7None yet. Grant the co_owner role."))
                        : coOwners));

        UUID heir = plot.getHeir();
        inv.setItem(14, GUIManager.createItem(Material.ENCHANTED_BOOK,
                t(player, "stewardship_heir_name", "&dHeir: &f{PLAYER}",
                        Map.of("PLAYER", heir == null ? t(player, "stewardship_heir_none", "None")
                                : safeName(heir, null))),
                List.of(t(player, "stewardship_heir_lore",
                        "&7Use &e/ag heir <player>&7 while standing here."))));

        boolean owner = plot.isOwner(player.getUniqueId());
        inv.setItem(16, GUIManager.createItem(owner ? Material.WRITABLE_BOOK : Material.GRAY_DYE,
                t(player, "stewardship_transfer_name", "&eTransfer ownership"),
                List.of(t(player, "stewardship_transfer_lore",
                        "&7Use &e/ag transfer <player>&7 with confirm."))));

        SuccessionService svc = plugin.succession();
        if (svc != null && svc.canAssume(player, plot)) {
            inv.setItem(13, GUIManager.createItem(Material.TOTEM_OF_UNDYING,
                    t(player, "stewardship_assume_name", "&aAssume stewardship"),
                    List.of(t(player, "stewardship_assume_lore",
                            "&7The owner has been inactive. Click to take over."))));
            plugin.gui().tagAction(inv.getItem(13), "assume");
        }
        if (svc != null && svc.pendingRollback(plot.getPlotId()) != null
                && (plot.isOwner(player.getUniqueId()) || plugin.isAdmin(player)
                || player.getUniqueId().equals(svc.pendingRollback(plot.getPlotId()).previousOwner()))) {
            inv.setItem(22, GUIManager.createItem(Material.CLOCK,
                    t(player, "stewardship_rollback_name", "&eRollback transfer"),
                    List.of(t(player, "stewardship_rollback_lore",
                            "&7Undo the recent ownership transfer."))));
            plugin.gui().tagAction(inv.getItem(22), "rollback");
        }

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&e⟵ Back"),
                plugin.gui().trList(player, "back_lore", List.of("&7Return to the previous page."))));
        plugin.gui().tagAction(inv.getItem(18), "back");
        inv.setItem(26, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                plugin.gui().trList(player, "exit_lore", List.of("&7Close this menu."))));
        plugin.gui().tagAction(inv.getItem(26), "close");
        player.openInventory(inv);
        if (plugin.effects() != null) plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent event, Holder holder) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        Plot plot = plugin.store().getPlotById(holder.plotId());
        if (plot == null) {
            player.closeInventory();
            return;
        }
        String action = plugin.gui().getAction(event.getCurrentItem());
        if ("back".equals(action) || event.getSlot() == 18) {
            plugin.gui().player().open(player, com.aegisguard.gui.PlayerGUI.Page.ACCESS);
            return;
        }
        if ("close".equals(action) || event.getSlot() == 26) {
            player.closeInventory();
            return;
        }
        if ("assume".equals(action) && plugin.succession() != null && plugin.succession().assume(player, plot)) {
            player.sendMessage(GUIManager.color(t(player, "stewardship_assume_ok",
                    "&aYou are now the owner of this plot.")));
            open(player, plot);
            return;
        }
        if ("rollback".equals(action) && plugin.succession() != null && plugin.succession().rollback(player, plot)) {
            player.sendMessage(GUIManager.color(t(player, "stewardship_rollback_ok",
                    "&aOwnership transfer was rolled back.")));
            open(player, plugin.store().getPlotById(holder.plotId()));
        }
    }

    private String t(Player player, String key, String fallback) {
        return plugin.gui().tr(player, key, fallback);
    }

    private String t(Player player, String key, String fallback, Map<String, String> vars) {
        return plugin.gui().tr(player, key, fallback, vars);
    }

    private String safeName(UUID id, String fallback) {
        if (fallback != null && !fallback.isBlank()) return fallback;
        if (id == null) return "Unknown";
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        return offline.getName() == null ? id.toString() : offline.getName();
    }
}
