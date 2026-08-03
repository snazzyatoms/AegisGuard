package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.economy.CurrencyType;
import com.aegisguard.territory.TerritoryLifeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/** Pending payment inbox for a player (or staff Doctor view). */
public class SettlementsInboxGUI {
    private final AegisGuard plugin;
    public SettlementsInboxGUI(AegisGuard plugin) { this.plugin = plugin; }
    public static final class SettlementsHolder implements InventoryHolder {
        private final boolean adminView;
        public SettlementsHolder() { this(false); }
        public SettlementsHolder(boolean adminView) { this.adminView = adminView; }
        public boolean isAdminView() { return adminView; }
        @Override public Inventory getInventory() { return null; }
    }
    public void open(Player player) {
        open(player, false);
    }
    public void openAdmin(Player player) {
        if (!player.hasPermission("aegis.admin") && !player.hasPermission("aegis.admin.rentals")
                && !player.hasPermission("aegis.admin.doctor.repair")) {
            return;
        }
        open(player, true);
    }
    private void open(Player player, boolean adminView) {
        List<TerritoryLifeService.PendingSettlement> entries = adminView
                ? plugin.territoryLife().settlements()
                : plugin.territoryLife().settlementsFor(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(new SettlementsHolder(adminView), 54,
                plugin.gui().title(player, "settlements_inbox_title", "&6Pending Payments"));
        for (int i = 45; i < 54; i++) inv.setItem(i, GUIManager.getFiller());
        if (entries.isEmpty()) inv.setItem(22, GUIManager.createItem(Material.GRAY_DYE,
                tr(player, "settlements_empty_name", "&7No Pending Payments"),
                trList(player, "settlements_empty_lore", List.of("&7All payments have been delivered."))));
        for (int i = 0; i < entries.size() && i < 45; i++) {
            TerritoryLifeService.PendingSettlement s = entries.get(i);
            long age = Math.max(0L, (System.currentTimeMillis() - s.createdAt()) / 60_000L);
            List<String> lore = trList(player, "settlements_entry_lore",
                    List.of("&7Reason: &f{REASON}", "&7Age: &f{AGE} minutes"));
            lore.replaceAll(line -> line
                    .replace("{REASON}", s.reason() == null ? "-" : s.reason())
                    .replace("{AGE}", String.valueOf(age)));
            if (adminView) {
                String name = Bukkit.getOfflinePlayer(s.playerId()).getName();
                lore.add(0, GUIManager.color(tr(player, "settlements_entry_player", "&7Player: &f{PLAYER}")
                        .replace("{PLAYER}", name == null ? s.playerId().toString() : name)));
            }
            inv.setItem(i, GUIManager.createItem(Material.GOLD_INGOT,
                    "&6" + plugin.eco().format(s.amount(), CurrencyType.VAULT), lore));
        }
        inv.setItem(45, GUIManager.createItem(Material.EMERALD,
                tr(player, adminView ? "settlements_retry_all_name" : "settlements_retry_name",
                        adminView ? "&aRetry All Deliveries" : "&aRetry My Delivery"),
                trList(player, adminView ? "settlements_retry_all_lore" : "settlements_retry_lore",
                        adminView
                                ? List.of("&7Retry every pending Vault delivery.", "&cStaff only.")
                                : List.of("&7Retry your pending Vault deliveries."))));
        inv.setItem(48, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to previous menu."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv); plugin.effects().playMenuOpen(player);
    }
    public void handleClick(Player player, InventoryClickEvent e, SettlementsHolder holder) {
        e.setCancelled(true); if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getRawSlot() == 45) {
            int delivered;
            if (holder.isAdminView()) {
                if (!player.hasPermission("aegis.admin") && !player.hasPermission("aegis.admin.rentals")
                        && !player.hasPermission("aegis.admin.doctor.repair")) {
                    player.sendMessage(GUIManager.color(tr(player, "no_perm", "&cYou do not have permission.")));
                    plugin.effects().playError(player);
                    return;
                }
                delivered = plugin.territoryLife().retrySettlements();
            } else {
                delivered = plugin.territoryLife().retrySettlementsFor(player.getUniqueId());
            }
            player.sendMessage(GUIManager.color(tr(player, "settlements_retry_result",
                    "&aRetried delivery. Delivered: &f{COUNT}").replace("{COUNT}", String.valueOf(delivered))));
            open(player, holder.isAdminView());
        } else if (e.getRawSlot() == 48) {
            if (holder.isAdminView()) plugin.gui().doctor().open(player);
            else plugin.gui().settings().open(player);
        } else if (e.getRawSlot() == 50) {
            player.closeInventory();
            plugin.effects().playMenuClose(player);
        }
    }
    private String tr(Player p, String k, String f) { return plugin.gui().tr(p, k, f); }
    private List<String> trList(Player p, String k, List<String> f) { return plugin.gui().trList(p, k, f); }
}
