package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.travel.SafeTravelService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Plot kick and ban controls for managers. */
public class ModerationGUI {
    private final AegisGuard plugin;

    public ModerationGUI(AegisGuard plugin) { this.plugin = plugin; }

    public static final class ModerationHolder implements InventoryHolder {
        private final Plot plot;
        private final List<UUID> targets;
        public ModerationHolder(Plot plot, List<UUID> targets) { this.plot = plot; this.targets = targets; }
        public Plot getPlot() { return plot; }
        public List<UUID> getTargets() { return targets; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        if (player == null || plot == null || !plot.canManage(player, plugin)) {
            plugin.effects().playError(player);
            return;
        }
        List<UUID> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId()) && plot.isInside(online.getLocation())) targets.add(online.getUniqueId());
        }
        targets.sort(Comparator.comparing(id -> safeName(Bukkit.getOfflinePlayer(id)), String.CASE_INSENSITIVE_ORDER));
        Inventory inv = Bukkit.createInventory(new ModerationHolder(plot, targets), 54,
                plugin.gui().title(player, "moderation_title", "&8Plot Moderation"));
        for (int i = 45; i < 54; i++) inv.setItem(i, GUIManager.getFiller());
        int slot = 0;
        for (UUID id : targets) {
            Player target = Bukkit.getPlayer(id);
            if (target == null) continue;
            inv.setItem(slot++, GUIManager.createItem(Material.PLAYER_HEAD, "&e" + target.getName(),
                    trList(player, "moderation_target_lore", List.of("&7Online in this plot.", "&eLeft-click: kick", "&cRight-click: ban"))));
        }
        int banSlot = 27;
        for (UUID id : plot.getBannedPlayers()) {
            if (banSlot >= 45) break;
            inv.setItem(banSlot++, GUIManager.createItem(Material.RED_DYE, "&c" + safeName(Bukkit.getOfflinePlayer(id)),
                    trList(player, "moderation_banned_lore", List.of("&7Banned from this plot.", "&aClick to unban"))));
        }
        inv.setItem(45, GUIManager.createItem(Material.BOOK, tr(player, "moderation_guide_name", "&eModeration Guide"),
                trList(player, "moderation_guide_lore", List.of("&7Select a player in the plot to kick or ban.", "&7Banned players appear on the lower row."))));
        inv.setItem(48, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to roles."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, ModerationHolder holder) {
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        Plot plot = holder.getPlot();
        if (plot == null || !plot.canManage(player, plugin)) { plugin.effects().playError(player); return; }
        int slot = e.getRawSlot();
        if (slot == 48) { plugin.gui().roles().openRolesMenu(player, plot); return; }
        if (slot == 50) { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
        if (slot >= 0 && slot < holder.getTargets().size()) {
            Player target = Bukkit.getPlayer(holder.getTargets().get(slot));
            if (target == null) { open(player, plot); return; }
            if (e.getClick().isRightClick()) ban(player, plot, target);
            else kick(player, plot, target);
            open(player, plot);
            return;
        }
        if (slot >= 27 && slot < 45) {
            int index = slot - 27;
            if (index < plot.getBannedPlayers().size()) {
                plot.removeBan(plot.getBannedPlayers().get(index));
                plugin.store().savePlotSync(plot);
                plugin.effects().playConfirm(player);
                open(player, plot);
            }
        }
    }

    private void kick(Player actor, Plot plot, Player target) {
        if (target.equals(actor) || plot.isOwner(target.getUniqueId()) || target.hasPermission("aegis.admin.bypass") || target.isOp()) {
            plugin.effects().playError(actor); return;
        }
        plugin.safeTravel().travel(target, target.getWorld().getSpawnLocation(), SafeTravelService.Kind.SPAWN, false);
        target.sendMessage(GUIManager.color(tr(target, "kicked_target", "&cYou were kicked from this plot.")));
        actor.sendMessage(GUIManager.color(tr(actor, "kicked_sender", "&eKicked {PLAYER}").replace("{PLAYER}", target.getName())));
        plugin.effects().playConfirm(actor);
    }

    private void ban(Player actor, Plot plot, OfflinePlayer target) {
        UUID id = target.getUniqueId();
        if (id == null || id.equals(actor.getUniqueId()) || plot.isOwner(id)) { plugin.effects().playError(actor); return; }
        plot.addBan(id);
        plugin.store().savePlotSync(plot);
        if (target.isOnline() && target.getPlayer() != null && plot.isInside(target.getPlayer().getLocation())) {
            plugin.safeTravel().travel(target.getPlayer(), target.getPlayer().getWorld().getSpawnLocation(), SafeTravelService.Kind.SPAWN, false);
        }
        plugin.effects().playConfirm(actor);
    }

    private String safeName(OfflinePlayer player) { return player == null || player.getName() == null ? "Unknown" : player.getName(); }
    private String tr(Player p, String key, String fallback) { return plugin.gui().tr(p, key, fallback); }
    private List<String> trList(Player p, String key, List<String> fallback) { return plugin.gui().trList(p, key, fallback); }
}
