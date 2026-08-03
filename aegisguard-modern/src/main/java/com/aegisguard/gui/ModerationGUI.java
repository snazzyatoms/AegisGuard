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
    private static final int ONLINE_SLOTS = 27; // 0-26
    private static final int BAN_START = 27;
    private static final int BAN_END = 45; // exclusive

    private final AegisGuard plugin;

    public ModerationGUI(AegisGuard plugin) { this.plugin = plugin; }

    public static final class ModerationHolder implements InventoryHolder {
        private final Plot plot;
        private final List<UUID> targets;
        private final List<UUID> banned;
        public ModerationHolder(Plot plot, List<UUID> targets, List<UUID> banned) {
            this.plot = plot;
            this.targets = targets;
            this.banned = banned;
        }
        public Plot getPlot() { return plot; }
        public List<UUID> getTargets() { return targets; }
        public List<UUID> getBanned() { return banned; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot) {
        if (player == null || plot == null || !plot.canManage(player, plugin)) {
            if (player != null) plugin.effects().playError(player);
            return;
        }
        List<UUID> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId()) && plot.isInside(online.getLocation())) {
                targets.add(online.getUniqueId());
            }
        }
        targets.sort(Comparator.comparing(id -> safeName(Bukkit.getOfflinePlayer(id)), String.CASE_INSENSITIVE_ORDER));
        if (targets.size() > ONLINE_SLOTS) {
            targets = new ArrayList<>(targets.subList(0, ONLINE_SLOTS));
        }

        List<UUID> banned = new ArrayList<>(plot.getBannedPlayers());
        int banCapacity = BAN_END - BAN_START;
        if (banned.size() > banCapacity) {
            banned = new ArrayList<>(banned.subList(0, banCapacity));
        }

        Inventory inv = Bukkit.createInventory(new ModerationHolder(plot, targets, banned), 54,
                plugin.gui().title(player, "moderation_title", "&8Plot Moderation"));
        for (int i = 0; i < 54; i++) inv.setItem(i, GUIManager.getFiller());

        for (int i = 0; i < targets.size(); i++) {
            Player target = Bukkit.getPlayer(targets.get(i));
            if (target == null) continue;
            inv.setItem(i, GUIManager.createItem(Material.PLAYER_HEAD, "&e" + target.getName(),
                    trList(player, "moderation_target_lore",
                            List.of("&7Online in this plot.", "&eLeft-click: kick", "&cRight-click: ban"))));
        }
        for (int i = 0; i < banned.size(); i++) {
            inv.setItem(BAN_START + i, GUIManager.createItem(Material.RED_DYE,
                    "&c" + safeName(Bukkit.getOfflinePlayer(banned.get(i))),
                    trList(player, "moderation_banned_lore",
                            List.of("&7Banned from this plot.", "&aClick to unban"))));
        }
        inv.setItem(45, GUIManager.createItem(Material.BOOK, tr(player, "moderation_guide_name", "&eModeration Guide"),
                trList(player, "moderation_guide_lore",
                        List.of("&7Select a player in the plot to kick or ban.",
                                "&7Banned players appear on the lower row."))));
        inv.setItem(48, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to roles."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, ModerationHolder holder) {
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (e.getCurrentItem() == null || GUIManager.isFiller(e.getCurrentItem())) return;
        Plot plot = holder.getPlot();
        if (plot == null || !plot.canManage(player, plugin)) { plugin.effects().playError(player); return; }
        int slot = e.getRawSlot();
        if (slot == 48) { plugin.gui().roles().openRolesMenu(player, plot); return; }
        if (slot == 50) { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
        if (slot == 45) return; // guide
        if (slot >= 0 && slot < holder.getTargets().size() && slot < ONLINE_SLOTS) {
            Player target = Bukkit.getPlayer(holder.getTargets().get(slot));
            if (target == null) { open(player, plot); return; }
            if (e.getClick().isRightClick()) ban(player, plot, target);
            else kick(player, plot, target);
            open(player, plot);
            return;
        }
        if (slot >= BAN_START && slot < BAN_END) {
            int index = slot - BAN_START;
            if (index < holder.getBanned().size()) {
                UUID bannedId = holder.getBanned().get(index);
                plot.removeBan(bannedId);
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
