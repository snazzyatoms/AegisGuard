package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

/** Explicit confirmation before a no-cost ownership transfer. */
public class TransferConfirmGUI {
    private final AegisGuard plugin;
    public TransferConfirmGUI(AegisGuard plugin) { this.plugin = plugin; }
    public static final class TransferConfirmHolder implements InventoryHolder {
        private final UUID plotId, recipientId;
        public TransferConfirmHolder(UUID plotId, UUID recipientId) { this.plotId = plotId; this.recipientId = recipientId; }
        public UUID getPlotId() { return plotId; } public UUID getRecipientId() { return recipientId; }
        @Override public Inventory getInventory() { return null; }
    }
    public void open(Player owner, Plot plot, OfflinePlayer recipient) {
        if (owner == null || plot == null || recipient == null || !canTransfer(owner, plot, recipient.getUniqueId())) { plugin.effects().playError(owner); return; }
        Inventory inv = Bukkit.createInventory(new TransferConfirmHolder(plot.getPlotId(), recipient.getUniqueId()), 27,
                plugin.gui().title(owner, "transfer_confirm_title", "&cConfirm Transfer"));
        for (int i = 0; i < 27; i++) inv.setItem(i, GUIManager.getFiller());
        inv.setItem(13, GUIManager.createItem(Material.WRITABLE_BOOK, tr(owner, "transfer_confirm_details_name", "&eTransfer Ownership"),
                trList(owner, "transfer_confirm_details_lore", List.of("&7Plot: &f" + plotName(plot), "&7Recipient: &f" + name(recipient), " ", "&cThis clears plot listings."))));
        inv.setItem(11, GUIManager.createItem(Material.EMERALD_BLOCK, tr(owner, "transfer_confirm_accept", "&aConfirm Transfer"),
                trList(owner, "transfer_confirm_accept_lore", List.of("&7Transfer this plot now."))));
        inv.setItem(15, GUIManager.createItem(Material.BARRIER, tr(owner, "transfer_confirm_cancel", "&cCancel"),
                trList(owner, "transfer_confirm_cancel_lore", List.of("&7Return without transferring."))));
        owner.openInventory(inv); plugin.effects().playMenuFlip(owner);
    }
    public void handleClick(Player player, InventoryClickEvent e, TransferConfirmHolder holder) {
        e.setCancelled(true); if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getRawSlot() == 15) { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
        if (e.getRawSlot() != 11) return;
        Plot plot = find(holder.getPlotId()); OfflinePlayer recipient = Bukkit.getOfflinePlayer(holder.getRecipientId());
        if (plot == null || !canTransfer(player, plot, recipient.getUniqueId())) { plugin.effects().playError(player); player.closeInventory(); return; }
        plot.setForSale(false, 0); plot.setForRent(false, 0); plot.setForAuction(false); plot.clearRenter();
        plugin.territoryLife().clearOffer(plot.getPlotId());
        plugin.store().changePlotOwner(plot, recipient.getUniqueId(), name(recipient));
        plugin.store().savePlotSync(plot);
        if (plugin.getMapHooks() != null) plugin.getMapHooks().reload();
        plugin.effects().playConfirm(player);
        player.sendMessage(GUIManager.color(tr(player, "transfer_confirm_success", "&aTransferred plot to &f{PLAYER}&a.").replace("{PLAYER}", name(recipient))));
        plugin.gui().openMain(player);
    }
    private boolean canTransfer(Player owner, Plot plot, UUID recipient) {
        return recipient != null && !recipient.equals(owner.getUniqueId()) && plot.isOwner(owner.getUniqueId())
                && plot.canManage(owner, plugin) && plugin.store().getPlotAt(owner.getLocation()) == plot;
    }
    private Plot find(UUID id) { return plugin.store().getAllPlots().stream().filter(p -> p != null && id.equals(p.getPlotId())).findFirst().orElse(null); }
    private String plotName(Plot p) { return p.getPlotName() == null || p.getPlotName().isBlank() ? "Plot" : p.getPlotName(); }
    private String name(OfflinePlayer p) { return p.getName() == null ? p.getUniqueId().toString() : p.getName(); }
    private String tr(Player p, String k, String f) { return plugin.gui().tr(p, k, f); }
    private List<String> trList(Player p, String k, List<String> f) { return plugin.gui().trList(p, k, f); }
}
