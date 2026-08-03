package com.aegisguard.alliance;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Milestone 7 - Alliance membership browser plus the per-plot Alliance Access toggles.
 * The plot button is grayed out with an explanation until the plot joins an alliance.
 */
public class AllianceAccessGUI {

    private final AegisGuard plugin;

    public AllianceAccessGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class AllianceMenuHolder implements InventoryHolder {
        private final Plot plot;
        public AllianceMenuHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class AllianceConfirmHolder implements InventoryHolder {
        private final Plot plot;
        private final String action;
        public AllianceConfirmHolder(Plot plot, String action) {
            this.plot = plot;
            this.action = action;
        }
        public Plot getPlot() { return plot; }
        public String getAction() { return action; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class AllianceRosterHolder implements InventoryHolder {
        private final Plot plot;
        private final UUID allianceId;
        public AllianceRosterHolder(Plot plot, UUID allianceId) {
            this.plot = plot;
            this.allianceId = allianceId;
        }
        public Plot getPlot() { return plot; }
        public UUID getAllianceId() { return allianceId; }
        @Override public Inventory getInventory() { return null; }
    }

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private String t(Player p, String key, Map<String, String> vars, String fallback) {
        String out = plugin.gui().tr(p, key, fallback);
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }

    private boolean isTopClick(InventoryClickEvent e) {
        return e.getClickedInventory() != null && e.getClickedInventory() == e.getView().getTopInventory();
    }

    public void open(Player player) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        openMenu(player, plot);
    }

    public void openMenu(Player player, Plot plot) {
        if (!plugin.allianceService().isEnabled()) {
            plugin.msg().send(player, "alliance_disabled");
            plugin.effects().playError(player);
            return;
        }

        String title = plugin.gui().title(player, "alliance_menu_title", "&6Alliance Access");
        Inventory inv = Bukkit.createInventory(new AllianceMenuHolder(plot), 45, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        Alliance personal = plugin.alliances().getByPlayer(player.getUniqueId());
        boolean canManage = plot != null && plot.canManage(player, plugin);
        boolean joined = plot != null && plot.getAllianceId() != null;
        Alliance plotAlliance = joined ? plugin.alliances().get(plot.getAllianceId()) : null;

        // Header
        List<String> header = new ArrayList<>();
        if (personal == null) {
            header.add(GUIManager.color(t(player, "alliance_none_personal",
                    "&8You are not in an alliance.")));
            header.add(GUIManager.color(t(player, "alliance_create_hint",
                    "&8Use /ag alliance create <name>")));
        } else {
            header.add(GUIManager.color(t(player, "alliance_personal_line",
                    Map.of("NAME", personal.getName()),
                    "&7Your alliance: &f{NAME}")));
            header.add(GUIManager.color(t(player, "alliance_members_line",
                    Map.of("COUNT", String.valueOf(personal.size())),
                    "&7Members: &f{COUNT}")));
        }
        boolean hasPendingInvite = plugin.alliances().all().stream()
                .anyMatch(alliance -> alliance.isInvited(player.getUniqueId()));
        if (personal != null || hasPendingInvite) {
            inv.setItem(16, GUIManager.createItem(Material.BOOK,
                    t(player, "alliance_roster_name", "&bRoster & Invites"),
                    tl(player, "alliance_roster_lore", List.of(
                            "&7View alliance members and pending",
                            "&7invitations. Click to open."
                    ))));
        }
        inv.setItem(4, GUIManager.createItem(Material.SHIELD,
                t(player, "alliance_header_name", "&6&lAlliance Access"), header));

        // Membership actions
        if (personal == null) {
            inv.setItem(10, GUIManager.createItem(Material.WRITABLE_BOOK,
                    t(player, "alliance_create_name", "&aCreate Alliance"),
                    tl(player, "alliance_create_lore", List.of(
                            "&7Use &f/ag alliance create <name>",
                            "&7to form a new alliance."))));
            inv.setItem(12, GUIManager.createItem(Material.PAPER,
                    t(player, "alliance_accept_name", "&eAccept Invite"),
                    tl(player, "alliance_accept_lore", List.of(
                            "&7Use &f/ag alliance accept",
                            "&7if you have a pending invite."))));
        } else {
            inv.setItem(10, GUIManager.createItem(Material.PLAYER_HEAD,
                    t(player, "alliance_invite_name", "&bInvite Player"),
                    tl(player, "alliance_invite_lore", List.of(
                            "&7Use &f/ag alliance invite <player>",
                            "&7to invite someone (leader only)."))));
            inv.setItem(12, GUIManager.createItem(Material.OAK_DOOR,
                    t(player, "alliance_leave_name", "&eLeave Alliance"),
                    tl(player, "alliance_leave_lore", List.of(
                            "&7Leaders must disband instead.",
                            "&eClick for confirmation."))));
            if (personal.isLeader(player.getUniqueId())) {
                inv.setItem(14, GUIManager.createItem(Material.TNT,
                        t(player, "alliance_disband_name", "&cDisband Alliance"),
                        tl(player, "alliance_disband_lore", List.of(
                                "&cRemoves the alliance for everyone.",
                                "&cJoined plots lose alliance access.",
                                "&eClick for confirmation."))));
            }
        }

        // Plot join / toggles
        if (plot == null) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER,
                    t(player, "alliance_no_plot_name", "&7Stand in a Plot"),
                    tl(player, "alliance_no_plot_lore", List.of(
                            "&7Alliance Access toggles apply to",
                            "&7the plot you are standing in."))));
        } else if (!joined) {
            List<String> lore = new ArrayList<>(tl(player, "alliance_plot_not_joined_lore", List.of(
                    "&7This plot has not joined an alliance.",
                    "&7Alliance members gain nothing here",
                    "&7until you opt in.")));
            lore.add(" ");
            if (canManage && personal != null) {
                lore.add(GUIManager.color(t(player, "alliance_join_click",
                        "&eClick to join your alliance.")));
            } else if (!canManage) {
                lore.add(GUIManager.color(t(player, "alliance_manage_locked",
                        "&8Only the owner can change this.")));
            } else {
                lore.add(GUIManager.color(t(player, "alliance_need_membership",
                        "&8Join or create an alliance first.")));
            }
            inv.setItem(22, GUIManager.createItem(Material.GRAY_DYE,
                    t(player, "alliance_plot_not_joined_name", "&7No Alliance on This Plot"), lore));
        } else {
            String allianceName = plotAlliance == null ? "Alliance" : plotAlliance.getName();
            inv.setItem(20, GUIManager.createItem(Material.LIME_DYE,
                    t(player, "alliance_plot_joined_name", Map.of("NAME", allianceName),
                            "&aJoined: &f{NAME}"),
                    tl(player, canManage ? "alliance_leave_plot_lore" : "alliance_manage_locked_lore",
                            canManage
                                    ? List.of("&eClick to remove this plot", "&efrom the alliance.")
                                    : List.of("&8Only the owner can change this."))));

            AllianceAccess access = plot.getAllianceAccess();
            placeToggle(inv, 28, player, "enter", access.isEnter(), Material.OAK_DOOR,
                    "Allies may enter", canManage);
            placeToggle(inv, 29, player, "interact", access.isInteract(), Material.LEVER,
                    "Allies may use doors/controls", canManage);
            placeToggle(inv, 30, player, "containers", access.isContainers(), Material.CHEST,
                    "Allies may use containers", canManage);
            placeToggle(inv, 31, player, "build", access.isBuild(), Material.BRICKS,
                    "Allies may build and break", canManage);
            placeToggle(inv, 32, player, "animals", access.isAnimals(), Material.WHEAT,
                    "Allies may use animals/farms", canManage);
            placeToggle(inv, 33, player, "vehicles", access.isVehicles(), Material.MINECART,
                    "Allies may use boats/carts", canManage);
            placeToggle(inv, 34, player, "friendly_pvp", access.isFriendlyPvp(), Material.IRON_SWORD,
                    "Allies are friendly for PvP", canManage);
        }

        inv.setItem(36, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the main menu."))));
        inv.setItem(40, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    private void placeToggle(Inventory inv, int slot, Player player, String key, boolean on,
                             Material icon, String fallbackLabel, boolean canManage) {
        boolean disallowed = plugin.alliances() != null && plugin.alliances().isToggleDisallowed(key);
        List<String> lore = new ArrayList<>();
        lore.add(GUIManager.color(on
                ? t(player, "alliance_toggle_on", "&aON")
                : t(player, "alliance_toggle_off", "&cOFF")));
        lore.add(" ");
        if (disallowed && !on) {
            lore.add(GUIManager.color(t(player, "alliance_toggle_disallowed_lore",
                    "&cDisabled by server policy.")));
            lore.add(GUIManager.color(t(player, "alliance_toggle_disallowed_hint",
                    "&8Ask staff if this option should be allowed.")));
        } else if (canManage) {
            lore.add(GUIManager.color(t(player, "alliance_toggle_click", "&eClick to toggle.")));
        } else {
            lore.add(GUIManager.color(t(player, "alliance_manage_locked",
                    "&8Only the owner can change this.")));
        }
        Material display = (disallowed && !on) ? Material.BARRIER : (on ? icon : Material.GRAY_DYE);
        inv.setItem(slot, GUIManager.createItem(display,
                (on ? "&a" : "&7") + t(player, "alliance_toggle_" + key, fallbackLabel), lore));
    }

    private void openConfirm(Player player, Plot plot, String action) {
        String title = plugin.gui().title(player, "alliance_confirm_title", "&cConfirm");
        Inventory inv = Bukkit.createInventory(new AllianceConfirmHolder(plot, action), 27, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(11, GUIManager.createItem(Material.LIME_WOOL,
                t(player, "alliance_confirm_yes", "&aConfirm"),
                tl(player, "alliance_confirm_yes_lore", List.of("&eClick to confirm."))));
        inv.setItem(15, GUIManager.createItem(Material.RED_WOOL,
                t(player, "alliance_confirm_no", "&cCancel"),
                tl(player, "alliance_confirm_no_lore", List.of("&7Go back."))));

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleMenuClick(Player player, InventoryClickEvent e, AllianceMenuHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || GUIManager.isFiller(e.getCurrentItem())) return;

        Plot plot = holder.getPlot();
        int slot = e.getRawSlot();
        if (slot == 36) { plugin.gui().openMain(player); return; }
        if (slot == 40) { player.closeInventory(); return; }

        Alliance personal = plugin.alliances().getByPlayer(player.getUniqueId());
        if (slot == 16) {
            Alliance rosterAlliance = personal;
            if (rosterAlliance == null) {
                rosterAlliance = plugin.alliances().all().stream()
                        .filter(alliance -> alliance.isInvited(player.getUniqueId())).findFirst().orElse(null);
            }
            if (rosterAlliance != null) openRoster(player, holder.getPlot(), rosterAlliance.getId());
            return;
        }

        if (slot == 12 && personal != null) {
            openConfirm(player, plot, "leave_alliance");
            return;
        }
        if (slot == 14 && personal != null && personal.isLeader(player.getUniqueId())) {
            openConfirm(player, plot, "disband");
            return;
        }

        if (slot == 22 && plot != null && plot.getAllianceId() == null
                && plot.canManage(player, plugin) && personal != null) {
            openConfirm(player, plot, "join_plot");
            return;
        }

        if (slot == 20 && plot != null && plot.getAllianceId() != null && plot.canManage(player, plugin)) {
            openConfirm(player, plot, "leave_plot");
            return;
        }

        String toggleKey = switch (slot) {
            case 28 -> "enter";
            case 29 -> "interact";
            case 30 -> "containers";
            case 31 -> "build";
            case 32 -> "animals";
            case 33 -> "vehicles";
            case 34 -> "friendly_pvp";
            default -> null;
        };
        if (toggleKey != null) {
            String err = plugin.allianceService().toggle(player, plot, toggleKey);
            if (err != null) {
                plugin.msg().send(player, err);
                plugin.effects().playError(player);
                return;
            }
            plugin.effects().playMenuFlip(player);
            openMenu(player, plot);
        }
    }

    public void openRoster(Player player, Plot plot, UUID allianceId) {
        Alliance alliance = plugin.alliances().get(allianceId);
        if (alliance == null) {
            openMenu(player, plot);
            return;
        }
        Inventory inv = Bukkit.createInventory(new AllianceRosterHolder(plot, allianceId), 54,
                plugin.gui().title(player, "alliance_roster_title", "&6Alliance Roster"));
        for (int i = 0; i < 54; i++) inv.setItem(i, GUIManager.getFiller());
        inv.setItem(4, GUIManager.createItem(Material.SHIELD, "&6" + alliance.getName(),
                List.of(GUIManager.color(t(player, "alliance_roster_members_line",
                                Map.of("COUNT", String.valueOf(alliance.size())),
                                "&7Members: &f{COUNT}")),
                        GUIManager.color(t(player, "alliance_roster_invites_line",
                                Map.of("COUNT", String.valueOf(alliance.getInvites().size())),
                                "&7Pending invites: &f{COUNT}")))));
        int slot = 9;
        for (UUID memberId : alliance.getMemberIds()) {
            if (slot >= 36) break;
            org.bukkit.OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(member);
                String display = member.getName() == null
                        ? t(player, "alliance_unknown_player", "Unknown")
                        : member.getName();
                meta.setDisplayName(GUIManager.color("&a" + display));
                meta.setLore(List.of(GUIManager.color(alliance.isLeader(memberId)
                        ? t(player, "alliance_roster_leader", "&6Leader")
                        : t(player, "alliance_roster_member", "&7Member"))));
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }
        boolean leader = alliance.isLeader(player.getUniqueId());
        boolean invited = alliance.isInvited(player.getUniqueId());
        if (invited) {
            inv.setItem(39, GUIManager.createItem(Material.LIME_WOOL, t(player, "alliance_roster_accept", "&aAccept Invite"),
                    tl(player, "alliance_roster_accept_lore", List.of("&eClick to join this alliance."))));
            inv.setItem(41, GUIManager.createItem(Material.RED_WOOL, t(player, "alliance_roster_decline", "&cDecline Invite"),
                    tl(player, "alliance_roster_decline_lore", List.of("&eClick to decline this invite."))));
        }
        for (UUID inviteeId : alliance.getInvites().keySet()) {
            if (slot >= 36) break;
            org.bukkit.OfflinePlayer invitee = Bukkit.getOfflinePlayer(inviteeId);
            String inviteName = invitee.getName() == null
                    ? inviteeId.toString().substring(0, 8)
                    : invitee.getName();
            ItemStack item = GUIManager.createItem(Material.PAPER,
                    t(player, "alliance_roster_pending_name", Map.of("PLAYER", inviteName),
                            "&ePending: &f{PLAYER}"),
                    List.of(GUIManager.color(leader
                            ? t(player, "alliance_roster_pending_cancel_lore", "&eClick to cancel invitation.")
                            : t(player, "alliance_roster_pending_locked_lore", "&8Leader can cancel this invite."))));
            plugin.gui().tagAction(item, "alliance_invite:" + inviteeId);
            inv.setItem(slot++, item);
        }
        inv.setItem(45, GUIManager.createItem(Material.ARROW, t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to alliance access."))));
        inv.setItem(53, GUIManager.createItem(Material.BARRIER, t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleRosterClick(Player player, InventoryClickEvent e, AllianceRosterHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || GUIManager.isFiller(e.getCurrentItem())) return;
        int slot = e.getRawSlot();
        if (slot == 45) { openMenu(player, holder.getPlot()); return; }
        if (slot == 53) { player.closeInventory(); return; }
        Alliance alliance = plugin.alliances().get(holder.getAllianceId());
        if (alliance == null) { openMenu(player, holder.getPlot()); return; }
        String error = null;
        if (slot == 39 && alliance.isInvited(player.getUniqueId())) {
            error = plugin.alliances().accept(player.getUniqueId(), alliance.getId());
        } else if (slot == 41 && alliance.isInvited(player.getUniqueId())) {
            error = plugin.alliances().decline(player.getUniqueId(), alliance.getId());
        } else {
            String action = plugin.gui().getAction(e.getCurrentItem());
            if (action != null && action.startsWith("alliance_invite:") && alliance.isLeader(player.getUniqueId())) {
                try { error = plugin.alliances().removeInvite(player.getUniqueId(), alliance.getId(),
                        UUID.fromString(action.substring("alliance_invite:".length()))); }
                catch (IllegalArgumentException ignored) { error = "alliance_invalid"; }
            } else return;
        }
        if (error != null) {
            plugin.msg().send(player, error);
            plugin.effects().playError(player);
        } else {
            plugin.msg().send(player, "alliance_action_ok");
            plugin.effects().playConfirm(player);
        }
        openRoster(player, holder.getPlot(), holder.getAllianceId());
    }

    public void handleConfirmClick(Player player, InventoryClickEvent e, AllianceConfirmHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        int slot = e.getRawSlot();
        if (slot == 18 || slot == 15) { openMenu(player, holder.getPlot()); return; }
        if (slot == 20) { player.closeInventory(); return; }
        if (slot != 11) return;

        String action = holder.getAction();
        String err = null;
        switch (action) {
            case "join_plot" -> err = plugin.allianceService().joinPlot(player, holder.getPlot());
            case "leave_plot" -> err = plugin.allianceService().leavePlot(player, holder.getPlot());
            case "leave_alliance" -> {
                Alliance alliance = plugin.alliances().getByPlayer(player.getUniqueId());
                err = plugin.alliances().leave(player.getUniqueId());
                if (err == null && plugin.audit() != null && alliance != null) {
                    plugin.audit().record(com.aegisguard.audit.AuditCategory.ALLIANCE, player,
                            alliance.getName(), "Left alliance");
                }
            }
            case "disband" -> {
                Alliance alliance = plugin.alliances().getByPlayer(player.getUniqueId());
                UUID id = alliance == null ? null : alliance.getId();
                err = plugin.alliances().disband(player.getUniqueId());
                if (err == null && id != null) {
                    plugin.allianceService().clearPlotsForDisbandedAlliance(id, player);
                    if (plugin.audit() != null) {
                        plugin.audit().record(com.aegisguard.audit.AuditCategory.ALLIANCE, player,
                                alliance.getName(), "Disbanded alliance");
                    }
                }
            }
            default -> err = "alliance_invalid";
        }

        if (err != null) {
            plugin.msg().send(player, err);
            plugin.effects().playError(player);
            openMenu(player, holder.getPlot());
            return;
        }

        plugin.msg().send(player, "alliance_action_ok");
        plugin.effects().playConfirm(player);
        openMenu(player, holder.getPlot());
    }
}
