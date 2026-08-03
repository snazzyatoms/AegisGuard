package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotBackendMigrator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/** Admin confirmation menu for one-time plot-backend migrations. */
public class StorageMigrateGUI {
    private final AegisGuard plugin;
    public StorageMigrateGUI(AegisGuard plugin) { this.plugin = plugin; }
    public static final class StorageMigrateHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
    public void open(Player player) {
        if (!plugin.isAdmin(player) && !player.hasPermission("aegis.admin.migrate")) { plugin.effects().playError(player); return; }
        Inventory inv = Bukkit.createInventory(new StorageMigrateHolder(), 54,
                plugin.gui().title(player, "storage_migrate_title", "&cStorage Migration"));
        for (int i = 45; i < 54; i++) inv.setItem(i, GUIManager.getFiller());
        inv.setItem(20, GUIManager.createItem(Material.HOPPER, tr(player, "storage_migrate_yml_sql_name", "&eYML to SQLite"),
                trList(player, "storage_migrate_yml_sql_lore", List.of("&7Back up plots.yml, then copy its plots to SQLite.", "&cClick to run."))));
        inv.setItem(24, GUIManager.createItem(Material.WRITABLE_BOOK, tr(player, "storage_migrate_sql_yml_name", "&eSQLite to YML"),
                trList(player, "storage_migrate_sql_yml_lore", List.of("&7Back up aegisguard.db, then copy its plots to YML.", "&cClick to run."))));
        inv.setItem(48, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"), trList(player, "back_lore", List.of("&7Return to admin menu."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"), trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv); plugin.effects().playMenuOpen(player);
    }
    public void handleClick(Player player, InventoryClickEvent e, StorageMigrateHolder holder) {
        e.setCancelled(true); if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getRawSlot() == 48) { plugin.gui().admin().open(player); return; }
        if (e.getRawSlot() == 50) { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
        String direction = e.getRawSlot() == 20 ? "yml-to-sql" : e.getRawSlot() == 24 ? "sql-to-yml" : null;
        if (direction == null) return;
        player.closeInventory();
        String report = new PlotBackendMigrator(plugin).migrate(direction);
        player.sendMessage(GUIManager.color("&e[Storage] &f" + report));
    }
    private String tr(Player p, String k, String f) { return plugin.gui().tr(p, k, f); }
    private List<String> trList(Player p, String k, List<String> f) { return plugin.gui().trList(p, k, f); }
}
