package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.PlotBackendMigrator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Admin confirmation menu for one-time plot-backend migrations (YML ↔ SQL). */
public class StorageMigrateGUI {
    private final AegisGuard plugin;
    private final Map<UUID, Boolean> returnToDoctor = new ConcurrentHashMap<>();

    public StorageMigrateGUI(AegisGuard plugin) { this.plugin = plugin; }

    public static final class StorageMigrateHolder implements InventoryHolder {
        private final String pendingDirection;
        public StorageMigrateHolder() { this(null); }
        public StorageMigrateHolder(String pendingDirection) { this.pendingDirection = pendingDirection; }
        public String getPendingDirection() { return pendingDirection; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        open(player, false);
    }

    public void openFromDoctor(Player player) {
        open(player, true);
    }

    public void open(Player player, boolean fromDoctor) {
        if (!plugin.isAdmin(player) && !player.hasPermission("aegis.admin.migrate")) {
            plugin.effects().playError(player);
            return;
        }
        if (fromDoctor) returnToDoctor.put(player.getUniqueId(), true);
        else returnToDoctor.remove(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(new StorageMigrateHolder(), 54,
                plugin.gui().title(player, "storage_migrate_title", "&cStorage Migration"));
        fill(inv);

        inv.setItem(4, tagged(GUIManager.createItem(Material.CHEST_MINECART,
                tr(player, "storage_migrate_info_name", "&eStorage Backend Migration"),
                trList(player, "storage_migrate_info_lore", List.of(
                        "&7Copy plot data between YML and SQLite.",
                        "&7A backup is created before changes.",
                        " ",
                        "&cUse during scheduled maintenance.",
                        "&cSwitch storage.backend in config afterward",
                        "&cif you intend to keep the destination."))), "storage_info"));

        inv.setItem(20, tagged(GUIManager.createItem(Material.HOPPER,
                tr(player, "storage_migrate_yml_sql_name", "&eYML to SQLite"),
                trList(player, "storage_migrate_yml_sql_lore", List.of(
                        "&7What: copy plots.yml into SQLite.",
                        "&7When: moving a flat-file server to SQL.",
                        " ",
                        "&7A plots.yml backup is written first.",
                        "&cDestructive if destination already has plots.",
                        " ",
                        "&eClick to review confirmation."))), "dir_yml_to_sql"));

        inv.setItem(24, tagged(GUIManager.createItem(Material.WRITABLE_BOOK,
                tr(player, "storage_migrate_sql_yml_name", "&eSQLite to YML"),
                trList(player, "storage_migrate_sql_yml_lore", List.of(
                        "&7What: copy SQLite plots into plots.yml.",
                        "&7When: exporting SQL data back to flat files.",
                        " ",
                        "&7A database backup is written first.",
                        "&cDestructive if plots.yml already has plots.",
                        " ",
                        "&eClick to review confirmation."))), "dir_sql_to_yml"));

        inv.setItem(48, tagged(GUIManager.createItem(Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "storage_migrate_back_lore", List.of("&7Return to the previous staff menu."))), "back"));
        inv.setItem(50, tagged(GUIManager.createItem(Material.BARRIER,
                tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))), "close_menu"));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    private void openConfirm(Player player, String direction) {
        Inventory inv = Bukkit.createInventory(new StorageMigrateHolder(direction), 27,
                plugin.gui().title(player, "storage_migrate_confirm_title", "&cConfirm Storage Migration"));
        fill(inv);

        boolean ymlToSql = "yml-to-sql".equals(direction);
        inv.setItem(13, tagged(GUIManager.createItem(Material.RED_CONCRETE,
                tr(player, "storage_migrate_confirm_name", "&cConfirm {DIRECTION}",
                        Map.of("DIRECTION", ymlToSql ? "YML → SQLite" : "SQLite → YML")),
                trList(player, "storage_migrate_confirm_lore", List.of(
                        "&7Direction: &f{DIRECTION}",
                        "&7A backup is created before the copy runs.",
                        " ",
                        "&cThis rewrites plot storage data.",
                        "&cDo not interrupt the server mid-migration.",
                        " ",
                        "&cClick to run now."
                ), Map.of("DIRECTION", ymlToSql ? "YML → SQLite" : "SQLite → YML"))), "confirm_run"));

        inv.setItem(18, tagged(GUIManager.createItem(Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to the previous menu."))), "back_picker"));
        inv.setItem(26, tagged(GUIManager.createItem(Material.BARRIER,
                tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))), "close_menu"));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, StorageMigrateHolder holder) {
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;

        String action = plugin.gui().getAction(clicked);
        if (action == null || action.isBlank()) return;

        switch (action) {
            case "back" -> goBack(player);
            case "back_picker" -> open(player, returnToDoctor.containsKey(player.getUniqueId()));
            case "close_menu" -> {
                returnToDoctor.remove(player.getUniqueId());
                player.closeInventory();
                plugin.effects().playMenuClose(player);
            }
            case "dir_yml_to_sql" -> openConfirm(player, "yml-to-sql");
            case "dir_sql_to_yml" -> openConfirm(player, "sql-to-yml");
            case "confirm_run" -> {
                String direction = holder.getPendingDirection();
                if (direction == null) return;
                player.closeInventory();
                plugin.effects().playConfirm(player);
                plugin.runGlobalAsync(() -> {
                    String report = new PlotBackendMigrator(plugin).migrate(direction);
                    plugin.runMain(player, () -> player.sendMessage(GUIManager.color(
                            tr(player, "storage_migrate_result", "&e[Storage] &f{REPORT}",
                                    Map.of("REPORT", report == null ? "done" : report)))));
                });
            }
            default -> { }
        }
    }

    private void goBack(Player player) {
        boolean doctor = Boolean.TRUE.equals(returnToDoctor.remove(player.getUniqueId()));
        plugin.effects().playMenuFlip(player);
        if (doctor) plugin.gui().doctor().open(player);
        else plugin.gui().admin().open(player);
    }

    private void fill(Inventory inv) {
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private ItemStack tagged(ItemStack item, String action) {
        plugin.gui().tagAction(item, action);
        return item;
    }

    private String tr(Player p, String k, String f) { return plugin.gui().tr(p, k, f); }
    private String tr(Player p, String k, String f, Map<String, String> values) {
        return plugin.gui().tr(p, k, f, values);
    }
    private List<String> trList(Player p, String k, List<String> f) { return plugin.gui().trList(p, k, f); }
    private List<String> trList(Player p, String k, List<String> f, Map<String, String> values) {
        return plugin.gui().trList(p, k, f, values);
    }
}
