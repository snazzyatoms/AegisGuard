package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.territory.TerritoryLifeService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Landlord view of active full-plot and zone tenants. */
public class MyTenantsGUI {
    private final AegisGuard plugin;
    public MyTenantsGUI(AegisGuard plugin) { this.plugin = plugin; }
    public record Entry(UUID plotId, String zoneName) {}
    public static final class MyTenantsHolder implements InventoryHolder {
        private final List<Entry> entries;
        public MyTenantsHolder(List<Entry> entries) { this.entries = entries; }
        public List<Entry> getEntries() { return entries; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        List<Entry> entries = collect(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(new MyTenantsHolder(entries), 54,
                plugin.gui().title(player, "my_tenants_title", "&6My Tenants"));
        for (int i = 45; i < 54; i++) inv.setItem(i, GUIManager.getFiller());
        if (entries.isEmpty()) inv.setItem(22, GUIManager.createItem(Material.GRAY_DYE,
                tr(player, "my_tenants_empty_name", "&7No Active Tenants"),
                trList(player, "my_tenants_empty_lore", List.of("&7Your plots and zones have no active tenants."))));
        for (int i = 0; i < entries.size() && i < 45; i++) {
            Entry entry = entries.get(i);
            Plot plot = find(entry.plotId());
            if (plot == null) continue;
            if (entry.zoneName() == null) {
                TerritoryLifeService.RentalContract contract = plugin.territoryLife().contract(plot.getPlotId());
                OfflinePlayer tenant = contract == null ? null : Bukkit.getOfflinePlayer(contract.renterId());
                inv.setItem(i, GUIManager.createItem(Material.GOLDEN_HOE, "&6" + plotName(plot),
                        trList(player, "my_tenants_plot_lore", List.of("&7Full-plot tenant: &f" + name(tenant),
                                "&eClick: view status", "&cShift-right: cancel contract"))));
            } else {
                Zone zone = plot.getZone(entry.zoneName());
                OfflinePlayer tenant = zone == null ? null : Bukkit.getOfflinePlayer(zone.getRenter());
                inv.setItem(i, GUIManager.createItem(Material.OAK_DOOR, "&a" + entry.zoneName(),
                        trList(player, "my_tenants_zone_lore", List.of("&7Plot: &f" + plotName(plot), "&7Tenant: &f" + name(tenant),
                                "&eClick: open tenant controls"))));
            }
        }
        inv.setItem(48, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to the main menu."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv); plugin.effects().playMenuOpen(player);
    }
    public void handleClick(Player player, InventoryClickEvent e, MyTenantsHolder holder) {
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getRawSlot() == 48) { plugin.gui().openMain(player); return; }
        if (e.getRawSlot() == 50) { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
        if (e.getRawSlot() < 0 || e.getRawSlot() >= holder.getEntries().size()) return;
        Entry entry = holder.getEntries().get(e.getRawSlot()); Plot plot = find(entry.plotId());
        if (plot == null || !plot.isOwner(player.getUniqueId())) { plugin.effects().playError(player); return; }
        if (entry.zoneName() != null) {
            Zone zone = plot.getZone(entry.zoneName());
            if (zone != null) plugin.gui().zoneTenant().open(player, plot, zone);
        } else if (e.getClick().isShiftClick() && e.getClick().isRightClick()
                && plugin.getConfig().getBoolean("full_plot_renting.allow_owner_early_cancel", false)) {
            TerritoryLifeService.RentalContract contract = plugin.territoryLife().contract(plot.getPlotId());
            if (contract != null) plugin.gui().rentConfirm().openPlotCancel(player, plot, contract.deposit(), "my_tenants");
        } else player.sendMessage(GUIManager.color(tr(player, "my_tenants_plot_status", "&7Tenant status is active.")));
    }
    private List<Entry> collect(UUID owner) {
        List<Entry> out = new ArrayList<>();
        for (TerritoryLifeService.RentalContract contract : plugin.territoryLife().contracts()) if (owner.equals(contract.ownerId())) out.add(new Entry(contract.plotId(), null));
        for (Plot plot : plugin.store().getPlots(owner)) for (Zone zone : plot.getZones()) if (zone != null && zone.isRented()) out.add(new Entry(plot.getPlotId(), zone.getName()));
        return out;
    }
    private Plot find(UUID id) { return plugin.store().getAllPlots().stream().filter(p -> p != null && id.equals(p.getPlotId())).findFirst().orElse(null); }
    private String plotName(Plot p) { return p.getPlotName() == null || p.getPlotName().isBlank() ? "Plot" : p.getPlotName(); }
    private String name(OfflinePlayer p) { return p == null || p.getName() == null ? "Unknown" : p.getName(); }
    private String tr(Player p, String k, String f) { return plugin.gui().tr(p, k, f); }
    private List<String> trList(Player p, String k, List<String> f) { return plugin.gui().trList(p, k, f); }
}
