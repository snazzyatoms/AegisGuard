package com.aegisguard.beacon;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.gui.GuiClicks;
import com.aegisguard.gui.HubOriginHolder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BeaconGUI {

    private final AegisGuard plugin;

    public BeaconGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    private abstract static class HubAwareHolder implements HubOriginHolder {
        private boolean fromHub;
        @Override public boolean isFromHub() { return fromHub; }
        @Override public void setFromHub(boolean fromHub) { this.fromHub = fromHub; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class SetupHolder extends HubAwareHolder {
        private final UUID beaconId;
        SetupHolder(UUID beaconId) { this.beaconId = beaconId; }
        public UUID beaconId() { return beaconId; }
    }

    public static class EditHolder extends HubAwareHolder {
        private final UUID beaconId;
        EditHolder(UUID beaconId) { this.beaconId = beaconId; }
        public UUID beaconId() { return beaconId; }
    }

    public static class LinkHolder extends HubAwareHolder {
        private final UUID beaconId;
        private boolean bothWays;
        /** Remote public pads offered for cross-server links (id → shared row). */
        private Map<UUID, com.aegisguard.network.NetworkStore.RemoteBeacon> remote = Map.of();
        LinkHolder(UUID beaconId) { this.beaconId = beaconId; }
        public UUID beaconId() { return beaconId; }
        public boolean bothWays() { return bothWays; }
        public void setBothWays(boolean bothWays) { this.bothWays = bothWays; }
        public Map<UUID, com.aegisguard.network.NetworkStore.RemoteBeacon> remote() { return remote; }
        public void setRemote(Map<UUID, com.aegisguard.network.NetworkStore.RemoteBeacon> remote) {
            this.remote = remote == null ? Map.of() : remote;
        }
    }

    public static class UnbindHolder extends HubAwareHolder {
        private final UUID beaconId;
        UnbindHolder(UUID beaconId) { this.beaconId = beaconId; }
        public UUID beaconId() { return beaconId; }
    }

    public static class ConfirmHolder extends HubAwareHolder {
        private final UUID originId;
        private final UUID destId;
        private final boolean listingArrival;
        /** Set when the destination pad lives on another backend. */
        private com.aegisguard.network.NetworkStore.RemoteBeacon remote;
        ConfirmHolder(UUID originId, UUID destId, boolean listingArrival) {
            this.originId = originId;
            this.destId = destId;
            this.listingArrival = listingArrival;
        }
        public com.aegisguard.network.NetworkStore.RemoteBeacon remote() { return remote; }
        public void setRemote(com.aegisguard.network.NetworkStore.RemoteBeacon remote) { this.remote = remote; }
    }

    /** Top-level beacon list — every pad the player manages plus reachable public pads. */
    public static class ListHolder extends HubAwareHolder {
        private final int page;
        ListHolder(int page) { this.page = page; }
        public int page() { return page; }
    }

    /** Arrival rules screen: per-plot landing mode, traveler override, and personal preference. */
    public static class ArrivalHolder extends HubAwareHolder { }

    private BeaconService svc() { return plugin.beacons(); }

    /** Marks a holder with the travel-hub origin flag so sub-screens remember where they came from. */
    private <T extends HubAwareHolder> T withOrigin(T holder, Player player) {
        holder.setFromHub(GUIManager.hubOriginActive(player));
        return holder;
    }

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private String t(Player p, String key, String fallback, Map<String, String> vars) {
        return plugin.gui().tr(p, key, fallback, vars);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    /** Pad grid slots: three rows of seven. */
    private static final int[] PAD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    /** Standalone "My Beacons" menu — every pad the player manages, current claim first. */
    public void openManager(Player player) {
        openList(player, 0);
    }

    public void openList(Player player, int page) {
        String title = plugin.gui().title(player, "beacon_list_title", "&bTeleport Beacons");
        Inventory inv = Bukkit.createInventory(withOrigin(new ListHolder(Math.max(0, page)), player), 54, title);
        fill(inv);

        BeaconService beacons = svc();
        Plot here = plugin.store().getPlotAt(player.getLocation());
        UUID hereId = here == null ? null : here.getPlotId();
        List<TeleportBeacon> own = beacons == null
                ? new ArrayList<>()
                : new ArrayList<>(beacons.manageableBy(player));
        own.sort((a, b) -> {
            boolean ah = hereId != null && hereId.equals(a.getPlotId());
            boolean bh = hereId != null && hereId.equals(b.getPlotId());
            if (ah != bh) return ah ? -1 : 1;
            return Long.compare(a.getCreatedAt(), b.getCreatedAt());
        });
        // Foreign public pads follow your own when gui_travel=public (canGuiTravel gates this).
        List<TeleportBeacon> foreign = new ArrayList<>();
        if (beacons != null) {
            for (TeleportBeacon b : beacons.store().all()) {
                if (b == null || beacons.canManage(player, b)) continue;
                if (beacons.canGuiTravel(player, b)) foreign.add(b);
            }
            foreign.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        }
        List<TeleportBeacon> pads = new ArrayList<>(own.size() + foreign.size());
        pads.addAll(own);
        pads.addAll(foreign);

        long plots = own.stream().map(TeleportBeacon::getPlotId)
                .filter(java.util.Objects::nonNull).distinct().count();
        List<String> guideLore = new ArrayList<>(tl(player, "beacon_manager_guide_lore", List.of(
                "&7Place a lodestone (or listed pad).",
                "&7Sneak-click it to create a beacon.",
                "&71. Pick a preset  2. Link another pad",
                "&73. Stand on it to travel.")));
        guideLore.add(t(player, "beacon_count_lore",
                "&7You manage &f{COUNT}&7 pad(s) across &f{PLOTS}&7 claim(s).",
                Map.of("COUNT", String.valueOf(own.size()), "PLOTS", String.valueOf(plots))));
        if (here != null && beacons != null && here.canManage(player, plugin)) {
            guideLore.add(t(player, "beacon_cap_lore", "&7This claim: &f{USED}&7/&f{MAX}&7 pads.",
                    Map.of("USED", String.valueOf(beacons.store().forPlot(hereId).size()),
                            "MAX", String.valueOf(beacons.maxFor(here)))));
        }
        inv.setItem(4, GUIManager.createItem(Material.END_PORTAL_FRAME,
                t(player, "beacon_manager_guide_name", "&bHow beacons work"), guideLore));

        if (pads.isEmpty()) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER,
                    t(player, "beacon_none_yet", "&cNo beacons yet"),
                    tl(player, "beacon_none_yet_lore", List.of(
                            "&7Stand in a claim you manage, place a",
                            "&7pad block, then sneak-right-click it."))));
        } else {
            int start = page * PAD_SLOTS.length;
            for (int i = 0; i < PAD_SLOTS.length; i++) {
                int idx = start + i;
                if (idx >= pads.size()) break;
                TeleportBeacon beacon = pads.get(idx);
                ItemStack item = padIcon(player, beacon);
                appendClickLegend(player, item);
                plugin.gui().tagAction(item, "open:" + beacon.getId());
                inv.setItem(PAD_SLOTS[i], item);
            }
            if (page > 0) {
                ItemStack prev = GUIManager.createItem(Material.ARROW,
                        t(player, "button_prev_page", "&fPrevious Page"),
                        tl(player, "prev_page_lore", List.of("&7Go to the previous page.")));
                plugin.gui().tagAction(prev, "list_prev");
                inv.setItem(45, prev);
            }
            if (start + PAD_SLOTS.length < pads.size()) {
                ItemStack next = GUIManager.createItem(Material.ARROW,
                        t(player, "button_next_page", "&fNext Page"),
                        tl(player, "next_page_lore", List.of("&7Go to the next page.")));
                plugin.gui().tagAction(next, "list_next");
                inv.setItem(53, next);
            }
        }

        // Arrival rules — per-plot landing mode + traveler override live with the pads they use.
        inv.setItem(40, GUIManager.createItem(Material.ENDER_EYE,
                t(player, "beacon_list_arrival", "&dArrival rules"),
                tl(player, "beacon_list_arrival_lore", List.of(
                        "&7Choose classic spawn or a public pad",
                        "&7for visitors, plus traveler override."))));
        plugin.gui().tagAction(inv.getItem(40), "arrival");

        ItemStack give = GUIManager.createItem(
                beacons == null ? Material.LODESTONE : beacons.starterPadMaterial(),
                t(player, "beacon_give_button", "&bGet pad blocks"),
                tl(player, "beacon_give_button_lore", List.of(
                        "&7Gives lodestones (or the server's pad).",
                        "&7Place them, then sneak-right-click to bind.",
                        "&7You can also use any allowed pad you already have.")));
        plugin.gui().tagAction(give, "give");
        inv.setItem(43, give);

        ListHolder listHolder = (ListHolder) inv.getHolder();
        if (listHolder != null && listHolder.isFromHub()) {
            inv.setItem(50, hubReturn(player));
        }
        ItemStack back = GUIManager.createItem(Material.NETHER_STAR,
                t(player, "button_back_menu", "&fReturn to Menu"),
                tl(player, "back_menu_lore", List.of("&7Go back to the main menu.")));
        plugin.gui().tagAction(back, "back_menu");
        inv.setItem(51, back);
        ItemStack close = GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu.")));
        plugin.gui().tagAction(close, "close");
        inv.setItem(52, close);

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    /** Arrival rules: how visitors land on the plot you stand in, plus your own preference. */
    public void openArrival(Player player) {
        String title = plugin.gui().title(player, "beacon_arrival_title", "&dArrival Rules");
        Inventory inv = Bukkit.createInventory(withOrigin(new ArrivalHolder(), player), 27, title);
        fillSmall(inv);

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        boolean manage = plot != null && plot.canManage(player, plugin);
        if (plot == null) {
            inv.setItem(13, GUIManager.createItem(Material.BARRIER,
                    t(player, "beacon_need_plot", "&cStand in a claim"),
                    List.of(t(player, "atlas_arrival_need_plot_lore",
                            "&7Stand in a plot you manage to set arrival."))));
        } else {
            boolean beacon = plot.requiresBeaconArrival();
            List<String> classicLore = new ArrayList<>(tl(player, "atlas_arrival_classic_lore", List.of(
                    "&7Visitors land at this plot's spawn.")));
            classicLore.add(manage
                    ? t(player, "atlas_arrival_click_classic", "&eClick to use classic arrival.")
                    : t(player, "atlas_arrival_manage_only", "&7Only managers can change this."));
            ItemStack classic = GUIManager.createItem(
                    !beacon && manage ? Material.LIME_DYE : Material.COMPASS,
                    t(player, "atlas_arrival_classic_name", "&aClassic spawn"),
                    classicLore);
            plugin.gui().tagAction(classic, "arrival_classic");
            inv.setItem(10, classic);
            List<String> padLore = new ArrayList<>(tl(player, "atlas_arrival_beacon_lore", List.of(
                    "&7Visitors must land on a public pad.",
                    "&7Fails closed if no public pad exists.")));
            padLore.add(manage
                    ? t(player, "atlas_arrival_click_beacon", "&eClick to require beacon arrival.")
                    : t(player, "atlas_arrival_manage_only", "&7Only managers can change this."));
            ItemStack pad = GUIManager.createItem(
                    beacon && manage ? Material.LIME_DYE : Material.END_PORTAL_FRAME,
                    t(player, "atlas_arrival_beacon_name", "&bBeacon pad"),
                    padLore);
            plugin.gui().tagAction(pad, "arrival_beacon");
            inv.setItem(12, pad);
            boolean allow = plot.isAllowTravelerOverride();
            List<String> overrideLore = new ArrayList<>(tl(player, "atlas_allow_override_lore", List.of(
                    "&7When allowed, visitors may pick classic",
                    "&7or beacon if that mode is available.")));
            overrideLore.add(manage
                    ? t(player, "atlas_arrival_click_override", "&eClick to toggle.")
                    : t(player, "atlas_arrival_manage_only", "&7Only managers can change this."));
            ItemStack override = GUIManager.createItem(
                    allow ? Material.LIME_DYE : Material.GRAY_DYE,
                    t(player, allow ? "atlas_allow_override_on" : "atlas_allow_override_off",
                            allow ? "&aTraveler override allowed" : "&7Traveler override locked"),
                    overrideLore);
            plugin.gui().tagAction(override, "arrival_override");
            inv.setItem(14, override);
        }
        var pref = plugin.notifications() == null
                ? com.aegisguard.notify.PlayerNotificationSettings.ArrivalPreference.OWNER_DEFAULT
                : plugin.notifications().getSettings(player.getUniqueId()).getPreferredArrival();
        String prefLabel = switch (pref) {
            case CLASSIC -> t(player, "atlas_pref_classic", "&aClassic spawn");
            case BEACON -> t(player, "atlas_pref_beacon", "&bBeacon pad");
            default -> t(player, "atlas_pref_owner", "&7Owner default");
        };
        ItemStack traveler = GUIManager.createItem(Material.NAME_TAG,
                t(player, "atlas_traveler_pref_name", "&eMy arrival preference"),
                List.of(prefLabel,
                        t(player, "atlas_traveler_pref_lore",
                                "&7Used when the destination allows overrides."),
                        t(player, "atlas_traveler_pref_click", "&eClick to cycle.")));
        plugin.gui().tagAction(traveler, "traveler_pref");
        inv.setItem(16, traveler);

        inv.setItem(18, back(player));
        ArrivalHolder arrivalHolder = (ArrivalHolder) inv.getHolder();
        if (arrivalHolder != null && arrivalHolder.isFromHub()) inv.setItem(19, hubReturn(player));
        inv.setItem(26, GUIManager.createItem(Material.BARRIER, t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));
        plugin.gui().tagAction(inv.getItem(26), "close");
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    private void setPlotArrival(Player player, Plot.ArrivalMode mode) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.canManage(player, plugin)) {
            plugin.effects().playError(player);
            return;
        }
        plot.setArrivalMode(mode);
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
        sendSystem(player, "arrival_set",
                "&a✔ Public arrival set to &f" + mode.name().toLowerCase(java.util.Locale.ROOT) + "&a for this plot.");
        plugin.effects().playConfirm(player);
        openArrival(player);
    }

    private void togglePlotTravelerOverride(Player player) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.canManage(player, plugin)) {
            plugin.effects().playError(player);
            return;
        }
        plot.setAllowTravelerOverride(!plot.isAllowTravelerOverride());
        plugin.store().savePlot(plot);
        plugin.store().setDirty(true);
        plugin.effects().playConfirm(player);
        openArrival(player);
    }

    private void cycleTravelerPreference(Player player) {
        if (plugin.notifications() == null) {
            plugin.effects().playError(player);
            return;
        }
        plugin.notifications().cyclePreferredArrival(player.getUniqueId());
        plugin.effects().playConfirm(player);
        openArrival(player);
    }

    private void sendSystem(Player player, String key, String fallback) {
        String msg = t(player, key, fallback);
        if (msg == null || msg.isBlank()) return;
        player.sendMessage(GUIManager.color(msg));
    }

    /** Adds the "left: manage · right: travel" legend to a pad icon in the list. */
    private void appendClickLegend(Player player, ItemStack item) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.addAll(tl(player, "beacon_click_legend", List.of(
                "&eLeft-click &7manage · &eRight-click &7travel")));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public void openSetup(Player player, TeleportBeacon beacon) {
        String title = plugin.gui().title(player, "beacon_setup_title", "&bBeacon Setup");
        Inventory inv = Bukkit.createInventory(withOrigin(new SetupHolder(beacon.getId()), player), 27, title);
        fillSmall(inv);
        inv.setItem(4, padIcon(player, beacon));
        inv.setItem(10, preset(player, TeleportBeacon.Preset.PRIVATE, Material.IRON_DOOR, "&7Private",
                List.of("&7Owners only.")));
        inv.setItem(11, preset(player, TeleportBeacon.Preset.MEMBERS, Material.PLAYER_HEAD, "&eMembers",
                List.of("&7Owners, members, and trusted.")));
        inv.setItem(12, preset(player, TeleportBeacon.Preset.ALLIANCE, Material.SHIELD, "&6Alliance",
                List.of("&7Alliance only. Not listed publicly.")));
        inv.setItem(13, preset(player, TeleportBeacon.Preset.PUBLIC, Material.ENDER_EYE, "&aPublic",
                List.of("&7Anyone. Required for Visit listings.")));
        inv.setItem(15, GUIManager.createItem(Material.NAME_TAG,
                t(player, "beacon_rename_button", "&bRename"),
                plugin.gui().trList(player, "beacon_rename_lore",
                        List.of("&7Currently: &f{NAME}", "&eClick, then type a name in chat."),
                        Map.of("NAME", beacon.getName()))));
        plugin.gui().tagAction(inv.getItem(15), "rename");
        inv.setItem(16, GUIManager.createItem(purposeIcon(beacon.getPurpose()),
                plugin.gui().tr(player, "beacon_purpose_button", "&dPurpose: &f{PURPOSE}",
                        Map.of("PURPOSE", pretty(player, beacon.getPurpose()))),
                tl(player, "beacon_purpose_lore", List.of("&7Used by market, auction, and visit listings.", "&eClick to cycle."))));
        plugin.gui().tagAction(inv.getItem(16), "purpose");
        inv.setItem(22, GUIManager.createItem(Material.ENDER_PEARL,
                t(player, "beacon_link_button", "&aLink destination"),
                tl(player, "beacon_link_choose_lore", List.of("&7Choose the pad this one sends you to."))));
        plugin.gui().tagAction(inv.getItem(22), "link");
        inv.setItem(18, back(player));
        if (((SetupHolder) inv.getHolder()).isFromHub()) inv.setItem(19, hubReturn(player));
        inv.setItem(26, GUIManager.createItem(Material.COMPARATOR,
                t(player, "beacon_advanced_button", "&7Advanced rules"),
                tl(player, "beacon_advanced_lore", List.of("&7Optional toggles for cost, confirm, and access."))));
        plugin.gui().tagAction(inv.getItem(26), "edit");
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void openEdit(Player player, TeleportBeacon beacon) {
        String title = plugin.gui().title(player, "beacon_edit_title", "&dBeacon Rules");
        Inventory inv = Bukkit.createInventory(withOrigin(new EditHolder(beacon.getId()), player), 54, title);
        fill(inv);
        inv.setItem(4, padIcon(player, beacon));
        toggle(inv, player, 19, Material.IRON_DOOR, "owners", beacon.isOwners(), "&7Owners");
        toggle(inv, player, 20, Material.PLAYER_HEAD, "members", beacon.isMembers(), "&eMembers");
        toggle(inv, player, 21, Material.GOLDEN_HELMET, "trusted", beacon.isTrusted(), "&6Trusted");
        toggle(inv, player, 22, Material.NAME_TAG, "guests", beacon.isGuests(), "&dGuests");
        toggle(inv, player, 23, Material.SHIELD, "alliance", beacon.isAlliance(), "&6Alliance");
        toggle(inv, player, 24, Material.ENDER_EYE, "public", beacon.isPublicAccess(), "&aPublic");
        toggle(inv, player, 25, Material.NETHER_STAR, "staff", beacon.isStaffOnly(), "&bStaff only");
        toggle(inv, player, 28, Material.LIME_DYE, "enabled", beacon.isEnabled(), "&aEnabled");
        toggle(inv, player, 29, Material.MAP, "confirm", beacon.isRequireConfirm(), "&eRequire confirm GUI");
        toggle(inv, player, 30, Material.IRON_SWORD, "combat", beacon.isAllowCombat(), "&cAllow in combat");
        placeFeeButtons(inv, player, beacon);
        inv.setItem(34, GUIManager.createItem(Material.CLOCK,
                plugin.gui().tr(player, "beacon_cooldown", "&eExtra cooldown: &f{SECONDS}s",
                        Map.of("SECONDS", String.valueOf(beacon.getExtraCooldownSeconds()))),
                tl(player, "beacon_cooldown_lore", List.of("&7Click +5s, right-click to clear."))));
        plugin.gui().tagAction(inv.getItem(34), "cool");
        inv.setItem(40, GUIManager.createItem(Material.ENDER_PEARL,
                t(player, "beacon_link_button", "&aLink destination"),
                List.of(beacon.isLinked()
                        ? t(player, "beacon_link_status_linked", "&7Linked. Click to change.")
                        : t(player, "beacon_link_status_unlinked", "&cNot linked yet."))));
        plugin.gui().tagAction(inv.getItem(40), "link");
        inv.setItem(45, back(player));
        if (((EditHolder) inv.getHolder()).isFromHub()) inv.setItem(46, hubReturn(player));
        if (svc().canGuiTravel(player, beacon)) {
            inv.setItem(47, GUIManager.createItem(Material.ENDER_EYE,
                    t(player, "beacon_travel_button", "&dTravel here"),
                    tl(player, "beacon_travel_lore", List.of("&7Teleport straight to this pad."))));
            plugin.gui().tagAction(inv.getItem(47), "travel");
        }
        inv.setItem(49, GUIManager.createItem(Material.NAME_TAG,
                t(player, "beacon_rename_button", "&bRename"),
                tl(player, "beacon_rename_chat_lore", List.of("&7Type a name in chat."))));
        plugin.gui().tagAction(inv.getItem(49), "rename");
        inv.setItem(51, GUIManager.createItem(Material.TNT,
                t(player, "beacon_unbind", "&cUnbind beacon"),
                tl(player, "beacon_unbind_lore", List.of(
                        "&7Removes the beacon record.", "&7The pad block stays in the world."))));
        plugin.gui().tagAction(inv.getItem(51), "unbind");
        inv.setItem(53, GUIManager.createItem(purposeIcon(beacon.getPurpose()),
                plugin.gui().tr(player, "beacon_purpose_button", "&dPurpose: &f{PURPOSE}",
                        Map.of("PURPOSE", pretty(player, beacon.getPurpose()))),
                tl(player, "beacon_purpose_cycle_lore", List.of("&eClick to cycle."))));
        plugin.gui().tagAction(inv.getItem(53), "purpose");
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    /** Destructive unbind confirm — warns how many pads currently link here. */
    public void openUnbind(Player player, TeleportBeacon beacon) {
        String title = plugin.gui().title(player, "beacon_unbind_title", "&cUnbind beacon?");
        Inventory inv = Bukkit.createInventory(withOrigin(new UnbindHolder(beacon.getId()), player), 27, title);
        fillSmall(inv);
        inv.setItem(4, padIcon(player, beacon));
        int inbound = svc().inboundLinks(beacon).size();
        List<String> warnLore = new ArrayList<>(tl(player, "beacon_unbind_warn_lore",
                List.of("&7Linked pads pointing here", "&7will be unlinked.")));
        if (inbound > 0) {
            warnLore.add(plugin.gui().tr(player, "beacon_unbind_inbound",
                    "&c{COUNT} pad(s) link here.", Map.of("COUNT", String.valueOf(inbound))));
        }
        ItemStack yes = GUIManager.createItem(Material.LIME_STAINED_GLASS_PANE,
                t(player, "beacon_unbind_confirm", "&cUnbind this beacon"), warnLore);
        plugin.gui().tagAction(yes, "unbind_yes");
        inv.setItem(11, yes);
        ItemStack no = GUIManager.createItem(Material.RED_STAINED_GLASS_PANE,
                t(player, "beacon_confirm_cancel", "&cCancel"),
                tl(player, "beacon_confirm_cancel_lore", List.of("&7Stay where you are.")));
        plugin.gui().tagAction(no, "stop");
        inv.setItem(15, no);
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    /** Travel to a pad straight from the GUI. Foreign pads go through the public-arrival rules. */
    public void openGuiTravel(Player player, TeleportBeacon dest) {
        if (!svc().canGuiTravel(player, dest)) return;
        boolean listing = !svc().canManage(player, dest);
        if (dest.isRequireConfirm() || listing) {
            openConfirm(player, null, dest, listing);
        } else {
            svc().executeTrip(player, null, dest, false);
        }
    }

    public void openLink(Player player, TeleportBeacon origin) {
        openLink(player, origin, false);
    }

    public void openLink(Player player, TeleportBeacon origin, boolean bothWays) {
        // Remote public pads live in shared SQL — fetch them off the entity
        // thread, then build the GUI back on it. Non-networked installs skip
        // straight to the local list.
        var net = plugin.network();
        if (net == null || !net.isNetworked() || net.store() == null) {
            openLinkInventory(player, origin, bothWays, List.of());
            return;
        }
        plugin.scheduler().runAsync(() -> {
            List<com.aegisguard.network.NetworkStore.RemoteBeacon> remote =
                    net.store().remotePublicBeacons(net.serverName());
            plugin.scheduler().runEntity(player,
                    () -> openLinkInventory(player, origin, bothWays, remote), null);
        });
    }

    private void openLinkInventory(Player player, TeleportBeacon origin, boolean bothWays,
                                   List<com.aegisguard.network.NetworkStore.RemoteBeacon> remotePads) {
        // Origin may have been unbound while the remote list was loading.
        if (origin == null || svc().store().get(origin.getId()) == null) return;
        String title = plugin.gui().title(player, "beacon_link_title", "&aLink Beacon");
        LinkHolder linkHolder = withOrigin(new LinkHolder(origin.getId()), player);
        linkHolder.setBothWays(bothWays);
        Inventory inv = Bukkit.createInventory(linkHolder, 54, title);
        fill(inv);
        List<TeleportBeacon> pads = linkablePads(player, origin);
        int slot = 10;
        for (TeleportBeacon other : pads) {
            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
            if (slot > 34) break;
            ItemStack item = padIcon(player, other);
            plugin.gui().tagAction(item, "dest:" + other.getId());
            inv.setItem(slot++, item);
        }
        // Remote pads (other backends, public only) — tagged rdest: so the
        // click handler knows the destination is a shared row, not local.
        if (remotePads != null && !remotePads.isEmpty()) {
            Map<UUID, com.aegisguard.network.NetworkStore.RemoteBeacon> remote = new HashMap<>();
            for (var rb : remotePads) {
                if (slot == 17) slot = 19;
                if (slot == 26) slot = 28;
                if (slot > 34) break;
                if (rb == null || rb.beaconId() == null) continue;
                remote.put(rb.beaconId(), rb);
                ItemStack item = remotePadIcon(player, rb);
                plugin.gui().tagAction(item, "rdest:" + rb.beaconId());
                inv.setItem(slot++, item);
            }
            linkHolder.setRemote(remote);
        }
        inv.setItem(45, back(player));
        if (linkHolder.isFromHub()) inv.setItem(47, hubReturn(player));
        inv.setItem(48, GUIManager.createItem(
                bothWays ? Material.SLIME_BALL : Material.GRAY_DYE,
                plugin.gui().tr(player, "beacon_link_both" + (bothWays ? "_on" : "_off"),
                        bothWays ? "&aRound trip: on" : "&7Round trip: off"),
                tl(player, "beacon_link_both_lore", List.of(
                        "&7Also link the destination back here",
                        "&7when you manage it and it is free."))));
        plugin.gui().tagAction(inv.getItem(48), "both_ways");
        inv.setItem(49, GUIManager.createItem(Material.BARRIER, t(player, "button_exit", "&c✖ Close"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));
        plugin.gui().tagAction(inv.getItem(49), "close");
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void openConfirm(Player player, TeleportBeacon origin, TeleportBeacon dest, boolean listingArrival) {
        String destName = dest.getName();
        String title = plugin.gui().title(player, "beacon_confirm_title", "&eConfirm teleport");
        Inventory inv = Bukkit.createInventory(
                withOrigin(new ConfirmHolder(origin == null ? null : origin.getId(), dest.getId(), listingArrival), player),
                27, title);
        fillSmall(inv);
        inv.setItem(13, padIcon(player, dest));
        ItemStack go = GUIManager.createItem(Material.LIME_STAINED_GLASS_PANE,
                plugin.gui().tr(player, "beacon_confirm_go", "&aConfirm teleport to &f{NAME}",
                        Map.of("NAME", destName)),
                confirmLore(player, origin, dest));
        plugin.gui().tagAction(go, "go");
        inv.setItem(11, go);
        ItemStack no = GUIManager.createItem(Material.RED_STAINED_GLASS_PANE,
                t(player, "beacon_confirm_cancel", "&cCancel"),
                tl(player, "beacon_confirm_cancel_lore", List.of("&7Stay where you are.")));
        plugin.gui().tagAction(no, "stop");
        inv.setItem(15, no);
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void handleClick(Player player, InventoryClickEvent event) {
        event.setCancelled(true);
        if (GUIManager.isFiller(event.getCurrentItem())) return;
        InventoryHolder holder = event.getInventory().getHolder();
        String action = plugin.gui().getAction(event.getCurrentItem());
        BeaconService service = svc();
        if (service == null) return;

        if ("hub_return".equals(action) && holder instanceof HubAwareHolder hub && hub.isFromHub()) {
            player.closeInventory();
            plugin.gui().travelHub().open(player);
            return;
        }

        if (holder instanceof ListHolder list) {
            if ("back_menu".equals(action)) { plugin.gui().openMain(player); return; }
            if ("close".equals(action) || "close_menu".equals(action)) { player.closeInventory(); return; }
            if ("arrival".equals(action)) { openArrival(player); return; }
            if ("give".equals(action)) { service.giveStarterPads(player); return; }
            if ("list_prev".equals(action)) { openList(player, list.page() - 1); return; }
            if ("list_next".equals(action)) { openList(player, list.page() + 1); return; }
            if (action != null && action.startsWith("open:")) {
                UUID id = parseUuid(action.substring(5));
                TeleportBeacon beacon = id == null ? null : service.store().get(id);
                if (beacon == null) return;
                if (GuiClicks.alternate(event)) {
                    if (service.canGuiTravel(player, beacon)) {
                        openGuiTravel(player, beacon);
                    } else {
                        plugin.effects().playError(player);
                    }
                } else if (service.canManage(player, beacon)) {
                    openSetup(player, beacon);
                } else if (service.canGuiTravel(player, beacon)) {
                    openGuiTravel(player, beacon);
                }
            }
            return;
        }

        if (holder instanceof ArrivalHolder) {
            if (event.getSlot() == 18 || "back".equals(action)) { openManager(player); return; }
            if ("close".equals(action) || event.getSlot() == 26) { player.closeInventory(); return; }
            if ("arrival_classic".equals(action)) { setPlotArrival(player, Plot.ArrivalMode.CLASSIC); return; }
            if ("arrival_beacon".equals(action)) { setPlotArrival(player, Plot.ArrivalMode.BEACON); return; }
            if ("arrival_override".equals(action)) { togglePlotTravelerOverride(player); return; }
            if ("traveler_pref".equals(action)) { cycleTravelerPreference(player); return; }
            return;
        }

        if (holder instanceof SetupHolder setup) {
            TeleportBeacon beacon = service.store().get(setup.beaconId());
            if (beacon == null || !service.canManage(player, beacon)) return;
            if (event.getSlot() == 18) { openManager(player); return; }
            if ("rename".equals(action)) { player.closeInventory(); service.beginRename(player, beacon); return; }
            if ("purpose".equals(action)) { service.cyclePurpose(beacon); openSetup(player, beacon); return; }
            if ("link".equals(action)) { openLink(player, beacon); return; }
            if ("edit".equals(action)) { openEdit(player, beacon); return; }
            if (action != null && action.startsWith("preset:")) {
                try {
                    beacon.applyPreset(TeleportBeacon.Preset.valueOf(action.substring(7).toUpperCase()));
                    service.store().put(beacon);
                    openSetup(player, beacon);
                } catch (IllegalArgumentException ignored) {}
            }
            return;
        }

        if (holder instanceof EditHolder edit) {
            TeleportBeacon beacon = service.store().get(edit.beaconId());
            if (beacon == null || !service.canManage(player, beacon)) return;
            if (event.getSlot() == 45) { openSetup(player, beacon); return; }
            if ("rename".equals(action)) { player.closeInventory(); service.beginRename(player, beacon); return; }
            if ("purpose".equals(action)) { service.cyclePurpose(beacon); openEdit(player, beacon); return; }
            if ("link".equals(action)) { openLink(player, beacon); return; }
            if ("travel".equals(action)) { openGuiTravel(player, beacon); return; }
            if ("unbind".equals(action)) { openUnbind(player, beacon); return; }
            applyEditToggle(player, event, beacon, action);
            return;
        }

        if (holder instanceof LinkHolder link) {
            TeleportBeacon origin = service.store().get(link.beaconId());
            if (origin == null || !service.canManage(player, origin)) return;
            if (event.getSlot() == 45) { openSetup(player, origin); return; }
            if ("close".equals(action) || event.getSlot() == 49) { player.closeInventory(); return; }
            if ("both_ways".equals(action)) { openLink(player, origin, !link.bothWays()); return; }
            if (action != null && action.startsWith("rdest:")) {
                UUID destId = parseUuid(action.substring(6));
                var remote = destId == null ? null : link.remote().get(destId);
                if (remote == null || !service.linkRemote(player, origin, remote)) {
                    service.send(player, "beacon_denied", "&cYou are not allowed to link to that beacon.");
                } else {
                    // Remote rows are read-only here — link is always one-way.
                    service.send(player, "beacon_linked_remote",
                            "&aLinked to &f{NAME}&a on &b{SERVER}&a — one-way link. Stand here to travel.",
                            Map.of("NAME", remote.name() == null ? "pad" : remote.name(),
                                    "SERVER", remote.server() == null ? "?" : remote.server()));
                }
                openSetup(player, origin);
                return;
            }
            if (action != null && action.startsWith("dest:")) {
                UUID destId = parseUuid(action.substring(5));
                TeleportBeacon dest = destId == null ? null : service.store().get(destId);
                if (dest != null && !service.canLinkTo(player, origin, dest)) {
                    // Social/alliance/inbound rules: never link into a stranger's private pad.
                    service.send(player, "beacon_denied", "&cYou are not allowed to link to that beacon.");
                } else if (dest != null) {
                    BeaconService.LinkResult result = service.linkBothWays(player, origin, dest, link.bothWays());
                    switch (result) {
                        case LINKED_BOTH -> service.send(player, "beacon_link_both_done",
                                "&aRound trip linked: &f{NAME}&a ↔ here.", Map.of("NAME", dest.getName()));
                        case DEST_LINKED_ELSEWHERE -> service.send(player, "beacon_link_both_denied",
                                "&eLinked to &f{NAME}&e, but it already links elsewhere — one-way only.",
                                Map.of("NAME", dest.getName()));
                        case NO_DEST_PERMS -> service.send(player, "beacon_link_both_no_perms",
                                "&eLinked to &f{NAME}&e. You do not manage it, so only one-way was applied.",
                                Map.of("NAME", dest.getName()));
                        default -> service.send(player, "beacon_linked",
                                "&aLinked to &f{NAME}&a. Stand here to travel.",
                                Map.of("NAME", dest.getName()));
                    }
                    openSetup(player, origin);
                } else {
                    service.send(player, "beacon_not_linked", "&eThis beacon is not linked yet.");
                }
            }
            return;
        }

        if (holder instanceof UnbindHolder unbind) {
            if ("stop".equals(action) || event.getSlot() == 15) {
                TeleportBeacon beacon = service.store().get(unbind.beaconId());
                if (beacon != null && service.canManage(player, beacon)) openEdit(player, beacon);
                else openManager(player);
                return;
            }
            if ("unbind_yes".equals(action) || event.getSlot() == 11) {
                TeleportBeacon beacon = service.store().get(unbind.beaconId());
                if (beacon == null || !service.canManage(player, beacon)) { openManager(player); return; }
                service.unbind(beacon.getId());
                service.send(player, "beacon_unbound",
                        "&eBeacon unbound. Pads that linked here were cleared; break the block to reclaim it.");
                if (plugin.effects() != null) plugin.effects().playConfirm(player);
                openManager(player);
            }
            return;
        }

        if (holder instanceof ConfirmHolder confirm) {
            if ("stop".equals(action) || event.getSlot() == 15) {
                player.closeInventory();
                return;
            }
            if ("go".equals(action) || event.getSlot() == 11) {
                player.closeInventory();
                TeleportBeacon origin = confirm.originId == null ? null : service.store().get(confirm.originId);
                if (confirm.remote() != null) {
                    // Remote pad hop — needs a valid origin pad (player departed from it).
                    if (origin == null) {
                        service.send(player, "beacon_pad_gone", "&cThe destination pad is missing or broken.");
                        return;
                    }
                    service.executeRemoteTrip(player, origin, confirm.remote());
                    return;
                }
                TeleportBeacon dest = service.store().get(confirm.destId);
                if (dest == null) {
                    service.send(player, "beacon_dest_missing", "&cThe destination plot is gone.");
                    return;
                }
                service.executeTrip(player, origin, dest, confirm.listingArrival);
            }
        }
    }

    private void applyEditToggle(Player player, InventoryClickEvent event, TeleportBeacon beacon, String action) {
        if (action == null) return;
        boolean right = event.isRightClick();
        boolean shift = event.isShiftClick();
        switch (action) {
            case "owners" -> beacon.setOwners(!beacon.isOwners());
            case "members" -> beacon.setMembers(!beacon.isMembers());
            case "trusted" -> beacon.setTrusted(!beacon.isTrusted());
            case "guests" -> beacon.setGuests(!beacon.isGuests());
            case "alliance" -> beacon.setAlliance(!beacon.isAlliance());
            case "public" -> beacon.setPublicAccess(!beacon.isPublicAccess());
            case "staff" -> beacon.setStaffOnly(!beacon.isStaffOnly());
            case "enabled" -> beacon.setEnabled(!beacon.isEnabled());
            case "confirm" -> beacon.setRequireConfirm(!beacon.isRequireConfirm());
            case "combat" -> beacon.setAllowCombat(!beacon.isAllowCombat());
            case "vault" -> {
                if (!svc().charges().canEditVaultFees()) return;
                if (right) beacon.setVaultCost(0);
                else if (shift) beacon.setVaultCost(svc().charges().clampVault(beacon.getVaultCost() - 1));
                else beacon.setVaultCost(svc().charges().clampVault(beacon.getVaultCost() + 1));
            }
            case "blocks" -> {
                if (!svc().charges().canEditClaimBlockFees()) return;
                if (right) beacon.setClaimBlockCost(0);
                else if (shift) beacon.setClaimBlockCost(svc().charges().clampClaimBlocks(beacon.getClaimBlockCost() - 1));
                else beacon.setClaimBlockCost(svc().charges().clampClaimBlocks(beacon.getClaimBlockCost() + 1));
            }
            case "cool" -> {
                if (right) beacon.setExtraCooldownSeconds(0);
                else if (shift) beacon.setExtraCooldownSeconds(Math.max(0, beacon.getExtraCooldownSeconds() - 5));
                else beacon.setExtraCooldownSeconds(beacon.getExtraCooldownSeconds() + 5);
            }
            default -> { return; }
        }
        plugin.beacons().store().put(beacon);
        openEdit(player, beacon);
    }

    private ItemStack preset(Player player, TeleportBeacon.Preset preset, Material icon, String name, List<String> loreFallback) {
        List<String> lore = new ArrayList<>(tl(player, "beacon_preset_" + preset.name().toLowerCase() + "_lore", loreFallback));
        lore.add(t(player, "beacon_preset_click", "&eClick to apply."));
        ItemStack item = GUIManager.createItem(icon, t(player, "beacon_preset_" + preset.name().toLowerCase(), name), lore);
        plugin.gui().tagAction(item, "preset:" + preset.name());
        return item;
    }

    private void placeFeeButtons(Inventory inv, Player player, TeleportBeacon beacon) {
        BeaconCharges policy = svc().charges();
        BeaconCharges.TripCost listed = policy.listedFee(beacon);
        if (policy.mode() == BeaconCharges.Mode.OFF) {
            inv.setItem(32, GUIManager.createItem(Material.BARRIER,
                    t(player, "beacon_charges_off", "&7Fees disabled by the server."),
                    tl(player, "beacon_charges_off_lore", List.of("&7This server does not charge for beacon travel."))));
            inv.setItem(33, GUIManager.createItem(Material.BARRIER,
                    t(player, "beacon_charges_off", "&7Fees disabled by the server."),
                    tl(player, "beacon_charges_off_lore", List.of("&7This server does not charge for beacon travel."))));
            return;
        }
        if (policy.mode() == BeaconCharges.Mode.ALWAYS) {
            inv.setItem(32, GUIManager.createItem(Material.GOLD_INGOT,
                    plugin.gui().tr(player, "beacon_charges_always_vault", "&6Server vault fee: &f{VAULT}",
                            Map.of("VAULT", policy.vaultLabel(listed.vault()))),
                    tl(player, "beacon_charges_always_lore", List.of("&7Set in config.yml — players cannot change this."))));
            inv.setItem(33, GUIManager.createItem(Material.EMERALD,
                    plugin.gui().tr(player, "beacon_charges_always_blocks", "&aServer ClaimBlocks: &f{BLOCKS}",
                            Map.of("BLOCKS", String.valueOf(listed.claimBlocks()))),
                    tl(player, "beacon_charges_always_lore", List.of("&7Set in config.yml — players cannot change this."))));
            return;
        }
        if (policy.canEditVaultFees()) {
            inv.setItem(32, GUIManager.createItem(Material.GOLD_INGOT,
                    plugin.gui().tr(player, "beacon_vault_cost", "&6Vault maintenance: &f{VAULT}",
                            Map.of("VAULT", policy.vaultLabel(beacon.getVaultCost()))),
                    tl(player, "beacon_vault_cost_lore", List.of(
                            "&7Charge travelers a maintenance fee.",
                            "&7Paid to the plot owner when they arrive.",
                            "&7Left-click +1, shift-left -1, right-click to clear."))));
            plugin.gui().tagAction(inv.getItem(32), "vault");
        } else {
            inv.setItem(32, GUIManager.createItem(Material.BARRIER,
                    t(player, "beacon_vault_locked", "&7Vault fees are off on this server."),
                    List.of()));
        }
        if (policy.canEditClaimBlockFees()) {
            inv.setItem(33, GUIManager.createItem(Material.EMERALD,
                    plugin.gui().tr(player, "beacon_cb_cost", "&aClaimBlock fee: &f{BLOCKS}",
                            Map.of("BLOCKS", String.valueOf(beacon.getClaimBlockCost()))),
                    tl(player, "beacon_cb_cost_lore", List.of(
                            "&7Optional ClaimBlock maintenance fee.",
                            "&7Left-click +1, shift-left -1, right-click to clear."))));
            plugin.gui().tagAction(inv.getItem(33), "blocks");
        } else {
            inv.setItem(33, GUIManager.createItem(Material.BARRIER,
                    t(player, "beacon_cb_locked", "&7ClaimBlock fees are off on this server."),
                    List.of()));
        }
    }

    private List<String> confirmLore(Player player, TeleportBeacon origin, TeleportBeacon dest) {
        List<String> lore = new ArrayList<>(tl(player, "beacon_confirm_go_lore",
                List.of("&7You will arrive at the linked pad.")));
        TeleportBeacon billed = origin != null ? origin : dest;
        BeaconCharges.TripCost cost = svc().charges().resolve(player, billed);
        if (!cost.isFree()) {
            lore.add(plugin.gui().tr(player, "beacon_confirm_fee",
                    "&6Fee: &f{VAULT} &7/ &a{BLOCKS} ClaimBlocks",
                    Map.of("VAULT", svc().charges().vaultLabel(cost.vault()),
                            "BLOCKS", String.valueOf(cost.claimBlocks()))));
        } else {
            lore.add(t(player, "beacon_confirm_free", "&aThis trip is free."));
        }
        return lore;
    }

    private void toggle(Inventory inv, Player player, int slot, Material mat, String action, boolean on, String label) {
        ItemStack item = GUIManager.createItem(mat, (on ? "&a✔ " : "&c✖ ") + t(player, "beacon_toggle_" + action, label),
                List.of(on ? t(player, "beacon_toggle_on", "&aOn") : t(player, "beacon_toggle_off", "&cOff"),
                        t(player, "beacon_toggle_click", "&eClick to toggle.")));
        if (on) glow(item);
        plugin.gui().tagAction(item, action);
        inv.setItem(slot, item);
    }

    public ItemStack padIcon(Player player, TeleportBeacon beacon) {
        Material mat = beacon.getPadMaterial() == null ? Material.LODESTONE : beacon.getPadMaterial();
        if (!mat.isItem()) mat = Material.LODESTONE;
        List<String> lore = new ArrayList<>();
        lore.add(plugin.gui().tr(player, "beacon_icon_purpose", "&7Purpose: &f{PURPOSE}",
                Map.of("PURPOSE", pretty(player, beacon.getPurpose()))));
        TeleportBeacon linked = beacon.getLinkedBeaconId() == null ? null
                : svc().store().get(beacon.getLinkedBeaconId());
        lore.add(linked != null
                ? plugin.gui().tr(player, "beacon_linked_to", "&aLinked &7→ &f{NAME}",
                        Map.of("NAME", linked.getName()))
                : t(player, "beacon_icon_unlinked", "&cNot linked yet"));
        int inbound = svc().inboundLinks(beacon).size();
        if (inbound > 0) {
            lore.add(plugin.gui().tr(player, "beacon_inbound", "&7Inbound: &f{COUNT} pad(s)",
                    Map.of("COUNT", String.valueOf(inbound))));
        }
        lore.add(beacon.isPublicAccess()
                ? t(player, "beacon_icon_public", "&aPublic")
                : t(player, "beacon_icon_not_public", "&7Not public"));
        lore.add("&8" + beacon.getX() + ", " + beacon.getY() + ", " + beacon.getZ());
        BeaconCharges.TripCost listed = svc().charges().listedFee(beacon);
        if (!listed.isFree()) {
            lore.add(plugin.gui().tr(player, "beacon_icon_fee", "&6Fee: &f{VAULT} &7/ &a{BLOCKS} CB",
                    Map.of("VAULT", svc().charges().vaultLabel(listed.vault()),
                            "BLOCKS", String.valueOf(listed.claimBlocks()))));
        }
        Plot plot = plugin.store().getPlotById(beacon.getPlotId());
        if (plot != null && plot.getPlotName() != null && !plot.getPlotName().isBlank()) {
            lore.add(plugin.gui().tr(player, "beacon_icon_plot", "&7Plot: &f{PLOT}",
                    Map.of("PLOT", plot.getPlotName())));
        }
        return GUIManager.createItem(mat, "&b" + beacon.getName(), lore);
    }

    /** Icon for a pad hosted by another backend — shows its server badge. */
    private ItemStack remotePadIcon(Player player, com.aegisguard.network.NetworkStore.RemoteBeacon rb) {
        List<String> lore = new ArrayList<>();
        lore.add(plugin.gui().tr(player, "visit_remote_line", "&7Server: &b{SERVER}",
                Map.of("SERVER", rb.server() == null ? "?" : rb.server())));
        if (rb.world() != null) {
            lore.add("&8" + rb.world() + " " + (int) rb.x() + ", " + (int) rb.y() + ", " + (int) rb.z());
        }
        lore.add(t(player, "beacon_icon_public", "&aPublic"));
        lore.add(t(player, "beacon_remote_link_hint", "&7Remote pads link one-way only."));
        return GUIManager.createItem(Material.ENDER_PEARL, "&b" + (rb.name() == null ? "Remote pad" : rb.name()), lore);
    }

    /** Confirm screen for a pad on another backend. */
    public void openRemoteConfirm(Player player, TeleportBeacon origin,
                                  com.aegisguard.network.NetworkStore.RemoteBeacon remote) {
        String destName = remote.name() == null ? "Remote pad" : remote.name();
        String title = plugin.gui().title(player, "beacon_confirm_title", "&eConfirm teleport");
        ConfirmHolder holder = withOrigin(new ConfirmHolder(
                origin == null ? null : origin.getId(), remote.beaconId(), false), player);
        holder.setRemote(remote);
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        fillSmall(inv);
        inv.setItem(13, remotePadIcon(player, remote));
        List<String> goLore = new ArrayList<>(tl(player, "beacon_confirm_go_lore",
                List.of("&7You will arrive at the linked pad.")));
        goLore.add(plugin.gui().tr(player, "visit_remote_line", "&7Server: &b{SERVER}",
                Map.of("SERVER", remote.server() == null ? "?" : remote.server())));
        TeleportBeacon billed = origin != null ? origin : null;
        if (billed != null) {
            BeaconCharges.TripCost cost = svc().charges().resolve(player, billed);
            if (!cost.isFree()) {
                goLore.add(plugin.gui().tr(player, "beacon_confirm_fee",
                        "&6Fee: &f{VAULT} &7/ &a{BLOCKS} ClaimBlocks",
                        Map.of("VAULT", svc().charges().vaultLabel(cost.vault()),
                                "BLOCKS", String.valueOf(cost.claimBlocks()))));
            } else {
                goLore.add(t(player, "beacon_confirm_free", "&aThis trip is free."));
            }
        }
        ItemStack go = GUIManager.createItem(Material.LIME_STAINED_GLASS_PANE,
                plugin.gui().tr(player, "beacon_confirm_go", "&aConfirm teleport to &f{NAME}",
                        Map.of("NAME", destName)),
                goLore);
        plugin.gui().tagAction(go, "go");
        inv.setItem(11, go);
        ItemStack no = GUIManager.createItem(Material.RED_STAINED_GLASS_PANE,
                t(player, "beacon_confirm_cancel", "&cCancel"),
                tl(player, "beacon_confirm_cancel_lore", List.of("&7Stay where you are.")));
        plugin.gui().tagAction(no, "stop");
        inv.setItem(15, no);
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    /** Same-plot pads first, then other pads this player can manage (cross-claim links). */
    private List<TeleportBeacon> linkablePads(Player player, TeleportBeacon origin) {
        List<TeleportBeacon> same = new ArrayList<>();
        List<TeleportBeacon> other = new ArrayList<>();
        UUID originPlot = origin.getPlotId();
        for (TeleportBeacon candidate : svc().store().all()) {
            if (candidate == null || candidate.getId().equals(origin.getId())) continue;
            if (!candidate.isEnabled()) continue;
            boolean samePlot = originPlot != null && originPlot.equals(candidate.getPlotId());
            // Own pads plus public / alliance / inbound-allowed friend pads (see canLinkTo).
            boolean linkable = samePlot
                    || svc().canManage(player, candidate)
                    || svc().canLinkTo(player, origin, candidate);
            if (!linkable) continue;
            if (samePlot) same.add(candidate);
            else other.add(candidate);
        }
        List<TeleportBeacon> out = new ArrayList<>(same.size() + other.size());
        out.addAll(same);
        out.addAll(other);
        return out;
    }

    private ItemStack back(Player player) {
        ItemStack item = GUIManager.createItem(Material.ARROW, t(player, "button_back", "&e⟵ Back"),
                tl(player, "back_lore", List.of("&7Return to the previous page.")));
        plugin.gui().tagAction(item, "back");
        return item;
    }

    private ItemStack hubReturn(Player player) {
        ItemStack item = GUIManager.createItem(Material.COMPASS,
                t(player, "button_back_hub", "&bBack to Travel Hub"),
                tl(player, "back_hub_lore", List.of("&7Return to the travel hub.")));
        plugin.gui().tagAction(item, "hub_return");
        return item;
    }

    private void fill(Inventory inv) {
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private void fillSmall(Inventory inv) { fill(inv); }

    private Material purposeIcon(TeleportBeacon.Purpose purpose) {
        return switch (purpose) {
            case SHOP -> Material.CHEST;
            case MARKET -> Material.GOLD_INGOT;
            case AUCTION -> Material.HOPPER;
            case SPAWN -> Material.COMPASS;
            case ALLIANCE -> Material.SHIELD;
            case ARENA -> Material.DIAMOND_SWORD;
            case DUNGEON -> Material.IRON_BARS;
            case SERVER -> Material.NETHER_STAR;
            default -> Material.ENDER_PEARL;
        };
    }

    private String pretty(Player player, TeleportBeacon.Purpose purpose) {
        String key = "beacon_purpose_" + purpose.name().toLowerCase();
        String fallback = Character.toUpperCase(purpose.name().charAt(0))
                + purpose.name().substring(1).toLowerCase();
        return org.bukkit.ChatColor.stripColor(t(player, key, fallback));
    }

    private void glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        try {
            Enchantment ench = Enchantment.getByName("UNBREAKING");
            if (ench == null) ench = Enchantment.getByName("DURABILITY");
            if (ench != null) meta.addEnchant(ench, 1, true);
        } catch (Throwable ignored) {}
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private UUID parseUuid(String raw) {
        try { return UUID.fromString(raw); } catch (Exception ignored) { return null; }
    }
}
