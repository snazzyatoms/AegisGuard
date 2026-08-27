package com.aegisguard.beacon;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BeaconGUI {

    private final AegisGuard plugin;

    public BeaconGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class ManagerHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public static class SetupHolder implements InventoryHolder {
        private final UUID beaconId;
        SetupHolder(UUID beaconId) { this.beaconId = beaconId; }
        public UUID beaconId() { return beaconId; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class EditHolder implements InventoryHolder {
        private final UUID beaconId;
        EditHolder(UUID beaconId) { this.beaconId = beaconId; }
        public UUID beaconId() { return beaconId; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class LinkHolder implements InventoryHolder {
        private final UUID beaconId;
        LinkHolder(UUID beaconId) { this.beaconId = beaconId; }
        public UUID beaconId() { return beaconId; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class ConfirmHolder implements InventoryHolder {
        private final UUID originId;
        private final UUID destId;
        private final boolean listingArrival;
        ConfirmHolder(UUID originId, UUID destId, boolean listingArrival) {
            this.originId = originId;
            this.destId = destId;
            this.listingArrival = listingArrival;
        }
        @Override public Inventory getInventory() { return null; }
    }

    private BeaconService svc() { return plugin.beacons(); }

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    public void openManager(Player player) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        String title = plugin.gui().title(player, "beacon_manager_title", "&bTeleport Beacons");
        Inventory inv = Bukkit.createInventory(new ManagerHolder(), 54, title);
        fill(inv);
        inv.setItem(4, GUIManager.createItem(Material.END_PORTAL_FRAME,
                t(player, "beacon_manager_guide_name", "&bHow beacons work"),
                tl(player, "beacon_manager_guide_lore", List.of(
                        "&7Place a lodestone (or listed pad).",
                        "&7Sneak-click it to create a beacon.",
                        "&71. Pick a preset  2. Link another pad",
                        "&73. Stand on it to travel."))));
        if (plot == null) {
            inv.setItem(22, GUIManager.createItem(Material.BARRIER,
                    t(player, "beacon_need_plot", "&cStand in a claim"),
                    List.of(t(player, "beacon_need_plot_lore", "&7Beacons belong to the plot you are in."))));
        } else {
            List<TeleportBeacon> pads = svc().store().forPlot(plot.getPlotId());
            int slot = 19;
            for (TeleportBeacon beacon : pads) {
                if (slot > 25 && slot < 28) slot = 28;
                if (slot > 34) break;
                ItemStack item = padIcon(player, beacon);
                plugin.gui().tagAction(item, "open:" + beacon.getId());
                inv.setItem(slot++, item);
            }
        }
        inv.setItem(40, GUIManager.createItem(svc() == null ? Material.LODESTONE : svc().starterPadMaterial(),
                t(player, "beacon_give_button", "&bGet pad blocks"),
                tl(player, "beacon_give_button_lore", List.of(
                        "&7Gives lodestones (or the server's pad).",
                        "&7Place them, then sneak-right-click to bind.",
                        "&7You can also use any allowed pad you already have."))));
        plugin.gui().tagAction(inv.getItem(40), "give");
        inv.setItem(45, back(player));
        inv.setItem(49, GUIManager.createItem(Material.BARRIER, t(player, "button_exit", "&c✖ Close"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void openSetup(Player player, TeleportBeacon beacon) {
        String title = plugin.gui().title(player, "beacon_setup_title", "&bCreate Beacon");
        Inventory inv = Bukkit.createInventory(new SetupHolder(beacon.getId()), 27, title);
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
        inv.setItem(26, GUIManager.createItem(Material.COMPARATOR,
                t(player, "beacon_advanced_button", "&7Advanced rules"),
                tl(player, "beacon_advanced_lore", List.of("&7Optional toggles for cost, confirm, and access."))));
        plugin.gui().tagAction(inv.getItem(26), "edit");
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void openEdit(Player player, TeleportBeacon beacon) {
        String title = plugin.gui().title(player, "beacon_edit_title", "&dBeacon Rules");
        Inventory inv = Bukkit.createInventory(new EditHolder(beacon.getId()), 54, title);
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
        inv.setItem(49, GUIManager.createItem(Material.NAME_TAG,
                t(player, "beacon_rename_button", "&bRename"),
                tl(player, "beacon_rename_chat_lore", List.of("&7Type a name in chat."))));
        plugin.gui().tagAction(inv.getItem(49), "rename");
        inv.setItem(53, GUIManager.createItem(purposeIcon(beacon.getPurpose()),
                plugin.gui().tr(player, "beacon_purpose_button", "&dPurpose: &f{PURPOSE}",
                        Map.of("PURPOSE", pretty(player, beacon.getPurpose()))),
                tl(player, "beacon_purpose_cycle_lore", List.of("&eClick to cycle."))));
        plugin.gui().tagAction(inv.getItem(53), "purpose");
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void openLink(Player player, TeleportBeacon origin) {
        String title = plugin.gui().title(player, "beacon_link_title", "&aLink Beacon");
        Inventory inv = Bukkit.createInventory(new LinkHolder(origin.getId()), 54, title);
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
        inv.setItem(45, back(player));
        player.openInventory(inv);
        GUIManager.playClick(player);
    }

    public void openConfirm(Player player, TeleportBeacon origin, TeleportBeacon dest, boolean listingArrival) {
        String destName = dest.getName();
        String title = plugin.gui().title(player, "beacon_confirm_title", "&eConfirm teleport");
        Inventory inv = Bukkit.createInventory(
                new ConfirmHolder(origin == null ? null : origin.getId(), dest.getId(), listingArrival),
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

        if (holder instanceof ManagerHolder) {
            if (event.getSlot() == 45 || event.getSlot() == 49) {
                if (event.getSlot() == 45) plugin.gui().openMain(player);
                else player.closeInventory();
                return;
            }
            if ("give".equals(action)) {
                service.giveStarterPads(player);
                return;
            }
            if (action != null && action.startsWith("open:")) {
                TeleportBeacon beacon = service.store().get(parseUuid(action.substring(5)));
                if (beacon != null && service.canManage(player, beacon)) openEdit(player, beacon);
            }
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
            applyEditToggle(player, event, beacon, action);
            return;
        }

        if (holder instanceof LinkHolder link) {
            TeleportBeacon origin = service.store().get(link.beaconId());
            if (origin == null || !service.canManage(player, origin)) return;
            if (event.getSlot() == 45) { openSetup(player, origin); return; }
            if (action != null && action.startsWith("dest:")) {
                UUID destId = parseUuid(action.substring(5));
                if (destId != null && service.link(origin, destId)) {
                    TeleportBeacon dest = service.store().get(destId);
                    service.send(player, "beacon_linked",
                            "&aLinked to &f{NAME}&a. Stand here to travel.",
                            Map.of("NAME", dest == null ? "beacon" : dest.getName()));
                    openSetup(player, origin);
                } else {
                    service.send(player, "beacon_not_linked", "&eThis beacon is not linked yet.");
                }
            }
            return;
        }

        if (holder instanceof ConfirmHolder confirm) {
            if ("stop".equals(action) || event.getSlot() == 15) {
                player.closeInventory();
                return;
            }
            if ("go".equals(action) || event.getSlot() == 11) {
                TeleportBeacon origin = confirm.originId == null ? null : service.store().get(confirm.originId);
                TeleportBeacon dest = service.store().get(confirm.destId);
                if (dest == null) {
                    player.closeInventory();
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
                else beacon.setVaultCost(svc().charges().clampVault(beacon.getVaultCost() + (shift ? 10 : 1)));
            }
            case "blocks" -> {
                if (!svc().charges().canEditClaimBlockFees()) return;
                if (right) beacon.setClaimBlockCost(0);
                else beacon.setClaimBlockCost(svc().charges().clampClaimBlocks(beacon.getClaimBlockCost() + (shift ? 10 : 1)));
            }
            case "cool" -> {
                if (right) beacon.setExtraCooldownSeconds(0);
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
                            "&7Shift-click +10, click +1, right-click to clear."))));
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
                            "&7Shift-click +10, click +1, right-click to clear."))));
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

    private ItemStack padIcon(Player player, TeleportBeacon beacon) {
        Material mat = beacon.getPadMaterial() == null ? Material.LODESTONE : beacon.getPadMaterial();
        if (!mat.isItem()) mat = Material.LODESTONE;
        List<String> lore = new ArrayList<>();
        lore.add(plugin.gui().tr(player, "beacon_icon_purpose", "&7Purpose: &f{PURPOSE}",
                Map.of("PURPOSE", pretty(player, beacon.getPurpose()))));
        lore.add(beacon.isLinked()
                ? t(player, "beacon_icon_linked", "&aLinked")
                : t(player, "beacon_icon_unlinked", "&cNot linked yet"));
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

    /** Same-plot pads first, then other pads this player can manage (cross-claim links). */
    private List<TeleportBeacon> linkablePads(Player player, TeleportBeacon origin) {
        List<TeleportBeacon> same = new ArrayList<>();
        List<TeleportBeacon> other = new ArrayList<>();
        UUID originPlot = origin.getPlotId();
        for (TeleportBeacon candidate : svc().store().all()) {
            if (candidate == null || candidate.getId().equals(origin.getId())) continue;
            if (!candidate.isEnabled()) continue;
            boolean samePlot = originPlot != null && originPlot.equals(candidate.getPlotId());
            if (!samePlot && !svc().canManage(player, candidate)) continue;
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
