package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.groups.PlotGroup;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Simple dashboard for group membership and invitations. */
public class GroupPlotsGUI {
    private final AegisGuard plugin;
    public GroupPlotsGUI(AegisGuard plugin) { this.plugin = plugin; }
    public static final class GroupPlotsHolder implements InventoryHolder {
        private final List<UUID> inviteGroups;
        public GroupPlotsHolder(List<UUID> inviteGroups) { this.inviteGroups = inviteGroups; }
        public List<UUID> getInviteGroups() { return inviteGroups; }
        @Override public Inventory getInventory() { return null; }
    }
    public void open(Player player) {
        PlotGroup group = plugin.groups().getGroupForPlayer(player.getUniqueId());
        List<UUID> invites = new ArrayList<>();
        for (PlotGroup candidate : plugin.groups().getAllGroups()) if (candidate.hasInvite(player.getUniqueId())) invites.add(candidate.getId());
        Inventory inv = Bukkit.createInventory(new GroupPlotsHolder(invites), 54,
                plugin.gui().title(player, "group_plots_title", "&6Group Plots"));
        for (int i = 45; i < 54; i++) inv.setItem(i, GUIManager.getFiller());
        if (group == null) {
            inv.setItem(22, GUIManager.createItem(Material.BOOK, tr(player, "group_plots_none_name", "&eNo Group"),
                    trList(player, "group_plots_none_lore", List.of("&7Create a group with &f/ag group create <name>&7.", "&7You can accept an invitation below."))));
        } else {
            inv.setItem(4, GUIManager.createItem(Material.NAME_TAG, "&6" + group.getName(),
                    trList(player, "group_plots_info_lore", List.of("&7Members: &f" + group.size(), "&7Leader: &f" + plugin.groups().getMemberName(group.getLeader())))));
            int slot = 9;
            for (UUID member : group.getMemberIds()) {
                if (slot >= 36) break;
                inv.setItem(slot++, GUIManager.createItem(Material.PLAYER_HEAD, "&e" + plugin.groups().getMemberName(member),
                        trList(player, "group_plots_member_lore", List.of("&7Group member."))));
            }
            Plot linked = group.getLinkedPlotId() == null ? null : findPlot(group.getLinkedPlotId());
            inv.setItem(40, GUIManager.createItem(Material.GRASS_BLOCK, tr(player, "group_plots_linked_name", "&aLinked Plot"),
                    trList(player, "group_plots_linked_lore", List.of("&7" + (linked == null ? "None" : plotName(linked))))));
            if (!group.getLeader().equals(player.getUniqueId())) inv.setItem(45, GUIManager.createItem(Material.OAK_DOOR,
                    tr(player, "group_plots_leave_name", "&cLeave Group"), trList(player, "group_plots_leave_lore", List.of("&7Leave this group."))));
        }
        int slot = 36;
        for (UUID groupId : invites) {
            if (slot >= 45) break;
            PlotGroup invite = plugin.groups().getGroup(groupId);
            if (invite != null) inv.setItem(slot++, GUIManager.createItem(Material.LIME_DYE, "&aJoin " + invite.getName(),
                    trList(player, "group_plots_invite_lore", List.of("&eClick to accept invitation."))));
        }
        inv.setItem(48, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"), trList(player, "back_lore", List.of("&7Return to menu."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"), trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv); plugin.effects().playMenuOpen(player);
    }
    public void handleClick(Player player, InventoryClickEvent e, GroupPlotsHolder holder) {
        e.setCancelled(true); if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (slot == 48) { plugin.gui().openMain(player); return; }
        if (slot == 50) { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
        if (slot == 45) {
            PlotGroup group = plugin.groups().getGroupForPlayer(player.getUniqueId());
            if (group != null && plugin.groups().leaveGroup(group, player.getUniqueId())) { plugin.groups().save(); plugin.effects().playConfirm(player); open(player); }
            return;
        }
        if (slot >= 36 && slot < 45) {
            int index = slot - 36;
            if (index < holder.getInviteGroups().size()) {
                PlotGroup group = plugin.groups().getGroup(holder.getInviteGroups().get(index));
                if (plugin.groups().acceptInvite(group, player.getUniqueId())) { plugin.groups().save(); plugin.effects().playConfirm(player); open(player); }
            }
        }
    }
    private Plot findPlot(UUID id) { return plugin.store().getAllPlots().stream().filter(p -> p != null && id.equals(p.getPlotId())).findFirst().orElse(null); }
    private String plotName(Plot p) { return p.getPlotName() == null ? "Plot" : p.getPlotName(); }
    private String tr(Player p, String k, String f) { return plugin.gui().tr(p, k, f); }
    private List<String> trList(Player p, String k, List<String> f) { return plugin.gui().trList(p, k, f); }
}
