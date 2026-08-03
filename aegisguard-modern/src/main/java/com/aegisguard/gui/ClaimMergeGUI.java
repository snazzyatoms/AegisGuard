package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockManager;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.snapshots.ClaimSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Safe MVP merge flow: choose a base plot, then an adjacent owned plot. */
public class ClaimMergeGUI {
    private final AegisGuard plugin;
    public ClaimMergeGUI(AegisGuard plugin) { this.plugin = plugin; }
    public static final class ClaimMergeHolder implements InventoryHolder {
        private final UUID baseId; private final List<UUID> plotIds;
        public ClaimMergeHolder(UUID baseId, List<UUID> plotIds) { this.baseId = baseId; this.plotIds = plotIds; }
        public UUID getBaseId() { return baseId; } public List<UUID> getPlotIds() { return plotIds; }
        @Override public Inventory getInventory() { return null; }
    }
    public void open(Player player) { open(player, null); }
    private void open(Player player, UUID baseId) {
        List<UUID> ids = plugin.store().getPlots(player.getUniqueId()).stream().map(Plot::getPlotId).toList();
        Inventory inv = Bukkit.createInventory(new ClaimMergeHolder(baseId, ids), 54,
                plugin.gui().title(player, "claim_merge_title", "&6Merge Claims"));
        for (int i = 45; i < 54; i++) inv.setItem(i, GUIManager.getFiller());
        for (int i = 0; i < ids.size() && i < 45; i++) {
            Plot plot = find(ids.get(i)); if (plot == null) continue;
            boolean base = plot.getPlotId().equals(baseId);
            inv.setItem(i, GUIManager.createItem(base ? Material.LIME_CONCRETE : Material.GRASS_BLOCK,
                    (base ? "&aBase: " : "&e") + plotName(plot),
                    trList(player, "claim_merge_plot_lore", List.of("&7Bounds: &f" + plot.getX1() + "," + plot.getZ1() + " to " + plot.getX2() + "," + plot.getZ2(),
                            base ? "&aSelect an adjacent candidate." : "&eClick to select."))));
        }
        inv.setItem(48, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"), trList(player, "back_lore", List.of("&7Return to menu."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"), trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv); plugin.effects().playMenuOpen(player);
    }
    public void handleClick(Player player, InventoryClickEvent e, ClaimMergeHolder holder) {
        e.setCancelled(true); if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getRawSlot() == 48) { plugin.gui().openMain(player); return; }
        if (e.getRawSlot() == 50) { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
        if (!plugin.getConfig().getBoolean("claims.merging.enabled", false) || e.getRawSlot() < 0 || e.getRawSlot() >= holder.getPlotIds().size()) return;
        Plot selected = find(holder.getPlotIds().get(e.getRawSlot()));
        if (selected == null || !selected.isOwner(player.getUniqueId())) return;
        if (holder.getBaseId() == null) { open(player, selected.getPlotId()); return; }
        Plot base = find(holder.getBaseId());
        if (base == null || base.getPlotId().equals(selected.getPlotId())) { open(player, null); return; }
        if (!adjacent(base, selected)) { player.sendMessage(GUIManager.color(tr(player, "claim_merge_not_adjacent", "&cPlots must share a side."))); plugin.effects().playError(player); return; }
        merge(player, base, selected);
    }
    private void merge(Player player, Plot base, Plot other) {
        if (base.hasActiveRental() || other.hasActiveRental() || base.isForSale() || other.isForSale() || base.isForAuction() || other.isForAuction()) {
            player.sendMessage(GUIManager.color(tr(player, "claim_merge_market_blocked", "&cClear listings and rentals before merging."))); return;
        }
        long cost = Math.max(0L, plugin.getConfig().getLong("claims.merging.cost", 0L));
        ClaimBlockManager blocks = plugin.getClaimBlockManager();
        if (cost > 0 && (blocks == null || blocks.getAvailableBlocks(player.getUniqueId()) < cost)) { plugin.effects().playError(player); return; }
        if (plugin.snapshots() != null) {
            plugin.snapshots().createSnapshot(base, ClaimSnapshot.SnapshotType.PRE_MERGE, "Before player merge", player.getUniqueId());
            plugin.snapshots().createSnapshot(other, ClaimSnapshot.SnapshotType.PRE_MERGE, "Before player merge", player.getUniqueId());
        }
        List<Zone> zones = new ArrayList<>(other.getZones());
        try {
            other.getZones().clear(); base.getZones().addAll(zones);
            plugin.store().removePlot(other.getOwner(), other.getPlotId());
            plugin.store().updatePlotBounds(base, Math.min(base.getX1(), other.getX1()), Math.min(base.getZ1(), other.getZ1()),
                    Math.max(base.getX2(), other.getX2()), Math.max(base.getZ2(), other.getZ2()));
            if (cost > 0) blocks.adjustAvailableBlocks(player.getUniqueId(), -cost);
            if (plugin.getMapHooks() != null) plugin.getMapHooks().reload();
            plugin.effects().playConfirm(player); open(player);
        } catch (Throwable t) {
            base.getZones().removeAll(zones); other.getZones().addAll(zones); plugin.store().addPlot(other); plugin.store().savePlotSync(other);
            player.sendMessage(GUIManager.color("&cMerge failed: " + (t.getMessage() == null ? "unknown error" : t.getMessage())));
        }
    }
    private boolean adjacent(Plot a, Plot b) {
        if (!a.getWorld().equalsIgnoreCase(b.getWorld())) return false;
        boolean xTouch = a.getX2() + 1 == b.getX1() || b.getX2() + 1 == a.getX1();
        boolean zTouch = a.getZ2() + 1 == b.getZ1() || b.getZ2() + 1 == a.getZ1();
        boolean zOverlap = Math.max(a.getZ1(), b.getZ1()) <= Math.min(a.getZ2(), b.getZ2());
        boolean xOverlap = Math.max(a.getX1(), b.getX1()) <= Math.min(a.getX2(), b.getX2());
        return (xTouch && zOverlap) || (zTouch && xOverlap);
    }
    private Plot find(UUID id) { return plugin.store().getAllPlots().stream().filter(p -> p != null && id.equals(p.getPlotId())).findFirst().orElse(null); }
    private String plotName(Plot p) { return p.getPlotName() == null || p.getPlotName().isBlank() ? "Plot" : p.getPlotName(); }
    private String tr(Player p, String k, String f) { return plugin.gui().tr(p, k, f); }
    private List<String> trList(Player p, String k, List<String> f) { return plugin.gui().trList(p, k, f); }
}
