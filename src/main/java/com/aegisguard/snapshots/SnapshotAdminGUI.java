package com.aegisguard.snapshots;

import com.aegisguard.AegisGuard;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Admin GUI for viewing and rolling back claim snapshots.
 */
public class SnapshotAdminGUI {
    
    private final AegisGuard plugin;
    private static final int SNAPSHOTS_PER_PAGE = 45; // Slots 0-44
    
    public SnapshotAdminGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }
    
    public static class SnapshotHolder implements InventoryHolder {
        private final int page;
        private final List<UUID> snapshotIds;
        
        public SnapshotHolder(List<UUID> snapshotIds, int page) {
            this.snapshotIds = snapshotIds;
            this.page = page;
        }
        
        public int getPage() { return page; }
        public List<UUID> getSnapshotIds() { return snapshotIds; }
        
        @Override
        public Inventory getInventory() { return null; }
    }
    
    public void open(Player player) {
        open(player, 0);
    }
    
    public void open(Player player, int page) {
        if (!plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            plugin.effects().playError(player);
            return;
        }
        
        List<ClaimSnapshot> snapshots = plugin.getSnapshotManager().getAllSnapshots();
        List<UUID> ids = new ArrayList<>();
        for (ClaimSnapshot s : snapshots) ids.add(s.getSnapshotId());
        
        int maxPages = (int) Math.ceil((double) ids.size() / SNAPSHOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (maxPages > 0 && page >= maxPages) page = maxPages - 1;
        
        int safePages = Math.max(1, maxPages);
        
        String title = plugin.gui().title(
                player,
                "snapshots_admin_title",
                "&c&lClaim Snapshots &7(" + (page + 1) + "/" + safePages + ")",
                Map.of("PAGE", String.valueOf(page + 1), "PAGES", String.valueOf(safePages))
        );
        
        SnapshotHolder holder = new SnapshotHolder(ids, page);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);
        
        if (ids.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(
                    Material.BARRIER,
                    plugin.gui().tr(player, "snapshots_none_title", "&cNo Snapshots"),
                    plugin.gui().trList(player, "snapshots_none_lore", List.of(
                            "&7No claim snapshots exist yet.",
                            "&7Snapshots are created before",
                            "&7expansions and merges."
                    ))
            ));
        } else {
            int startIndex = page * SNAPSHOTS_PER_PAGE;
            
            for (int slot = 0; slot < SNAPSHOTS_PER_PAGE; slot++) {
                int index = startIndex + slot;
                if (index >= ids.size()) break;
                
                ClaimSnapshot snapshot = plugin.getSnapshotManager().getSnapshot(ids.get(index));
                if (snapshot == null) continue;
                
                OfflinePlayer owner = Bukkit.getOfflinePlayer(snapshot.getOwner());
                String ownerName = owner.getName() != null ? owner.getName() : "Unknown";
                
                OfflinePlayer actor = snapshot.getTriggeredBy() != null ? 
                        Bukkit.getOfflinePlayer(snapshot.getTriggeredBy()) : null;
                String actorName = actor != null && actor.getName() != null ? actor.getName() : "Auto";
                
                String age = formatAge(snapshot.getAgeMillis());
                
                Map<String, String> vars = Map.of(
                        "OWNER", ownerName,
                        "WORLD", snapshot.getWorldName(),
                        "TYPE", snapshot.getType().name(),
                        "REASON", snapshot.getReason(),
                        "AGE", age,
                        "ACTOR", actorName,
                        "RADIUS", String.valueOf(snapshot.getRadius())
                );
                
                String itemName = plugin.gui().tr(player, "snapshot_item_name", 
                        "&e" + snapshot.getType().name() + " &7Snapshot", vars);
                
                List<String> lore = plugin.gui().trList(player, "snapshot_item_lore", List.of(
                        "&7Owner: &f{OWNER}",
                        "&7World: &f{WORLD}",
                        "&7Type: &e{TYPE}",
                        "&7Reason: &f{REASON}",
                        "&7Age: &f{AGE}",
                        "&7Triggered By: &f{ACTOR}",
                        "&7Radius: &a{RADIUS}",
                        " ",
                        "&aLeft-click: &7Rollback to this state",
                        "&cRight-click: &7Delete snapshot"
                ), vars);
                
                Material icon = snapshot.getType() == ClaimSnapshot.SnapshotType.PRE_EXPANSION ?
                        Material.SPYGLASS : Material.COMPASS;
                
                inv.setItem(slot, GUIManager.createItem(icon, itemName, lore));
            }
        }
        
        // Navigation
        if (page > 0) {
            inv.setItem(45, GUIManager.createItem(Material.ARROW,
                    plugin.gui().tr(player, "button_prev_page", "&fPrevious Page"),
                    plugin.gui().trList(player, "prev_page_lore", List.of("&7Go to the previous page."))
            ));
        }
        
        inv.setItem(49, GUIManager.createItem(Material.BARRIER,
                plugin.gui().tr(player, "button_back", "&fBack"),
                plugin.gui().trList(player, "back_lore", List.of("&7Return to admin menu."))
        ));
        
        if (page < maxPages - 1) {
            inv.setItem(53, GUIManager.createItem(Material.ARROW,
                    plugin.gui().tr(player, "button_next_page", "&fNext Page"),
                    plugin.gui().trList(player, "next_page_lore", List.of("&7Go to the next page."))
            ));
        }
        
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }
    
    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof SnapshotHolder holder)) return;
        e.setCancelled(true);
        
        if (!plugin.isAdmin(player)) {
            plugin.effects().playError(player);
            player.closeInventory();
            return;
        }
        
        if (e.getCurrentItem() == null) return;
        
        int rawSlot = e.getRawSlot();
        if (rawSlot < 0 || rawSlot >= e.getInventory().getSize()) return;
        
        int slot = e.getSlot();
        int page = holder.getPage();
        
        // Navigation
        if (slot == 45 && e.getCurrentItem().getType() == Material.ARROW) {
            open(player, page - 1);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == 53 && e.getCurrentItem().getType() == Material.ARROW) {
            open(player, page + 1);
            plugin.effects().playMenuFlip(player);
            return;
        }
        if (slot == 49) {
            plugin.gui().admin().open(player);
            plugin.effects().playMenuFlip(player);
            return;
        }
        
        // Snapshot actions
        if (slot < 0 || slot >= SNAPSHOTS_PER_PAGE) return;
        
        int index = (page * SNAPSHOTS_PER_PAGE) + slot;
        if (index >= holder.getSnapshotIds().size()) return;
        
        UUID snapshotId = holder.getSnapshotIds().get(index);
        ClaimSnapshot snapshot = plugin.getSnapshotManager().getSnapshot(snapshotId);
        
        if (snapshot == null) {
            plugin.msg().send(player, "snapshot_not_found", Map.of());
            open(player, page);
            return;
        }
        
        if (e.getClick().isLeftClick()) {
            // Rollback
            if (plugin.getSnapshotManager().rollback(snapshotId)) {
                plugin.msg().send(player, "snapshot_rollback_success", 
                        Map.of("ID", snapshot.getPlotId().toString()));
                plugin.effects().playConfirm(player);
            } else {
                plugin.msg().send(player, "snapshot_rollback_failed", Map.of());
                plugin.effects().playError(player);
            }
        } else if (e.getClick().isRightClick()) {
            // Delete
            if (plugin.getSnapshotManager().deleteSnapshot(snapshotId)) {
                plugin.msg().send(player, "snapshot_deleted", Map.of());
                plugin.effects().playUnclaim(player);
            }
        }
        
        open(player, page);
    }
    
    private String formatAge(long ms) {
        long seconds = ms / 1000L;
        long mins = seconds / 60L;
        long hrs = mins / 60L;
        long days = hrs / 24L;
        
        if (days > 0) return days + "d " + (hrs % 24) + "h";
        if (hrs > 0) return hrs + "h " + (mins % 60) + "m";
        if (mins > 0) return mins + "m";
        return seconds + "s";
    }
}
