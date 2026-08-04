package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.protection.ProtectionPreset;
import com.aegisguard.snapshots.ClaimSnapshot;
import com.aegisguard.util.TeleportUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Staff flow: convert a personal plot into a server zone, with optional travel
 * category / arena preset applied after ownership handoff.
 */
public class ConvertToServerGUI {

    private final AegisGuard plugin;

    public ConvertToServerGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /** Destination flavor after ownership becomes the server. */
    public enum ConvertTarget {
        PLAIN(Material.STRUCTURE_BLOCK, "convert_type_plain_name", "&bServer Zone",
                "convert_type_plain_lore", List.of(
                        "&7What: transfer ownership to the server.",
                        "&7No travel category or flag preset.",
                        "&7Use this for generic staff land.")),
        SPAWN(Material.RESPAWN_ANCHOR, "convert_type_spawn_name", "&aPublic Spawn",
                "convert_type_spawn_lore", List.of(
                        "&7What: server zone + public Spawn travel point.",
                        "&7Sets warp category SPAWN at your feet.",
                        "&7Same outcome as Set Spawn after convert.")),
        HUB(Material.ENDER_EYE, "convert_type_hub_name", "&dHub",
                "convert_type_hub_lore", List.of(
                        "&7What: server zone tagged as travel HUB.")),
        TOWN(Material.BELL, "convert_type_town_name", "&eTown",
                "convert_type_town_lore", List.of(
                        "&7What: server zone tagged as travel TOWN.")),
        EVENT(Material.FIREWORK_ROCKET, "convert_type_event_name", "&6Event",
                "convert_type_event_lore", List.of(
                        "&7What: server zone tagged as travel EVENT.")),
        SHOP(Material.EMERALD, "convert_type_shop_name", "&aShop",
                "convert_type_shop_lore", List.of(
                        "&7What: server zone tagged as travel SHOP.")),
        ARENA(Material.DIAMOND_SWORD, "convert_type_arena_name", "&cArena",
                "convert_type_arena_lore", List.of(
                        "&7What: server zone + Arena protection preset.",
                        "&7Applies the existing ARENA flag bundle",
                        "&7(public entry, PvP allowed, build locked)."));

        private final Material material;
        private final String nameKey;
        private final String nameFallback;
        private final String loreKey;
        private final List<String> loreFallback;

        ConvertTarget(Material material, String nameKey, String nameFallback,
                      String loreKey, List<String> loreFallback) {
            this.material = material;
            this.nameKey = nameKey;
            this.nameFallback = nameFallback;
            this.loreKey = loreKey;
            this.loreFallback = loreFallback;
        }

        public String actionKey() {
            return "convert_pick_" + name().toLowerCase(Locale.ROOT);
        }

        public static ConvertTarget fromAction(String action) {
            if (action == null || !action.startsWith("convert_pick_")) return null;
            String raw = action.substring("convert_pick_".length()).toUpperCase(Locale.ROOT);
            try {
                return ConvertTarget.valueOf(raw);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public static final class StaffWandHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public static final class ConvertSelectHolder implements InventoryHolder {
        private final UUID plotId;
        public ConvertSelectHolder(UUID plotId) { this.plotId = plotId; }
        public UUID getPlotId() { return plotId; }
        @Override public Inventory getInventory() { return null; }
    }

    public static final class ConvertConfirmHolder implements InventoryHolder {
        private final UUID plotId;
        private final ConvertTarget target;
        public ConvertConfirmHolder(UUID plotId, ConvertTarget target) {
            this.plotId = plotId;
            this.target = target == null ? ConvertTarget.PLAIN : target;
        }
        public UUID getPlotId() { return plotId; }
        public ConvertTarget getTarget() { return target; }
        @Override public Inventory getInventory() { return null; }
    }

    public boolean hasConvertPermission(Player player) {
        if (player == null) return false;
        return plugin.isAdmin(player)
                || player.hasPermission("aegis.convert")
                || player.hasPermission("aegis.admin.manage");
    }

    /** Staff may convert plots they can manage (own plot or elevated staff access). */
    public boolean canConvertPlot(Player player, Plot plot) {
        return hasConvertPermission(player) && plot != null && plot.canManage(player, plugin);
    }

    /**
     * @return localization key for the first hard blocker, or null if convertible.
     */
    public String findBlockerKey(Plot plot) {
        if (plot == null) return "convert_blocker_no_plot";
        if (plot.isServerZone()) return "convert_blocker_already_server";
        if (plot.isGroupPlot()) return "convert_blocker_group";
        if (plot.hasActiveRental()) return "convert_blocker_rental";
        if (plot.isForAuction()) return "convert_blocker_auction";
        if (plot.getZones() != null) {
            for (Zone zone : plot.getZones()) {
                if (zone != null && (zone.isRented() || zone.isListedForRent())) {
                    return "convert_blocker_zone_rent";
                }
            }
        }
        return null;
    }

    public void openFromStanding(Player player) {
        if (!hasConvertPermission(player)) {
            plugin.msg().send(player, "no_perm");
            plugin.effects().playError(player);
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            send(player, "convert_blocker_no_plot", "&cStand inside the player plot you want to convert.");
            plugin.effects().playError(player);
            return;
        }
        openSelect(player, plot);
    }

    public void openSelect(Player player, Plot plot) {
        if (!canConvertPlot(player, plot)) {
            plugin.msg().send(player, "no_perm");
            plugin.effects().playError(player);
            return;
        }

        String blocker = findBlockerKey(plot);
        String title = plugin.gui().title(player, "convert_select_title", "&cConvert → Server Plot");
        Inventory inv = Bukkit.createInventory(new ConvertSelectHolder(plot.getPlotId()), 45, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 45; i++) inv.setItem(i, filler);

        List<String> infoLore = new ArrayList<>();
        infoLore.addAll(trList(player, "convert_select_info_lore", List.of(
                "&7Plot: &f{PLOT}",
                "&7Owner: &f{OWNER}",
                " ",
                "&cConverts personal ownership to the server.",
                "&cTrusted roles and Guest Passes are cleared.",
                "&7Inactive sale/rent listings are cleared.",
                "&7A recovery snapshot is taken first."
        )));
        replacePlaceholders(infoLore, plot);
        if (blocker != null) {
            infoLore.add(" ");
            infoLore.add(GUIManager.color(tr(player, blocker, blockerMessage(blocker))));
            infoLore.addAll(trList(player, "convert_select_blocked_hint", List.of(
                    "&eResolve the issue above before converting."
            )));
        }

        inv.setItem(4, GUIManager.createItem(Material.WRITABLE_BOOK,
                tr(player, "convert_select_info_name", "&eConversion Details"),
                infoLore));

        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        ConvertTarget[] targets = ConvertTarget.values();
        for (int i = 0; i < targets.length && i < slots.length; i++) {
            ConvertTarget target = targets[i];
            List<String> lore = new ArrayList<>(trList(player, target.loreKey, target.loreFallback));
            lore.add(" ");
            if (blocker != null) {
                lore.add(GUIManager.color(tr(player, "convert_type_unavailable",
                        "&cUnavailable until blockers are cleared.")));
                ItemStack disabled = GUIManager.createItem(Material.GRAY_DYE,
                        tr(player, target.nameKey, target.nameFallback), lore);
                plugin.gui().tagAction(disabled, "convert_blocked");
                inv.setItem(slots[i], disabled);
            } else {
                lore.add(GUIManager.color(tr(player, "convert_type_click",
                        "&eClick to review and confirm.")));
                ItemStack item = GUIManager.createItem(target.material,
                        tr(player, target.nameKey, target.nameFallback), lore);
                plugin.gui().tagAction(item, target.actionKey());
                inv.setItem(slots[i], item);
            }
        }

        ItemStack back = GUIManager.createItem(Material.ARROW,
                tr(player, "button_back_admin", "&eBack to Admin"),
                trList(player, "back_admin_lore", List.of("&7Return to Staff Tools.")));
        plugin.gui().tagAction(back, "convert_back_admin");
        inv.setItem(40, back);

        ItemStack exit = GUIManager.createItem(Material.BARRIER,
                tr(player, "button_exit", "&c✖ Close"),
                trList(player, "exit_lore", List.of("&7Close this menu.")));
        plugin.gui().tagAction(exit, "convert_close");
        inv.setItem(44, exit);

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void openConfirm(Player player, Plot plot, ConvertTarget target) {
        if (!canConvertPlot(player, plot) || findBlockerKey(plot) != null || target == null) {
            plugin.effects().playError(player);
            openSelect(player, plot);
            return;
        }

        String title = plugin.gui().title(player, "convert_confirm_title", "&cConfirm Server Convert");
        Inventory inv = Bukkit.createInventory(new ConvertConfirmHolder(plot.getPlotId(), target), 27, title);
        for (int i = 0; i < 27; i++) inv.setItem(i, GUIManager.getFiller());

        List<String> details = new ArrayList<>(trList(player, "convert_confirm_details_lore", List.of(
                "&7Plot: &f{PLOT}",
                "&7Current owner: &f{OWNER}",
                "&7Becomes: &f{TARGET}",
                " ",
                "&cOwnership moves to the server.",
                "&cThis personal claim becomes a server zone.",
                "&cPlayer access (roles / Guest Passes) clears.",
                "&7Sale, rent, and auction listings clear.",
                "&7A recovery snapshot is created first.",
                " ",
                "&8This cannot be undone except by restore."
        )));
        replacePlaceholders(details, plot);
        replaceInList(details, "{TARGET}", targetLabel(player, target));

        inv.setItem(13, GUIManager.createItem(Material.WRITABLE_BOOK,
                tr(player, "convert_confirm_details_name", "&eConfirm Conversion"),
                details));

        ItemStack confirm = GUIManager.createItem(Material.EMERALD_BLOCK,
                tr(player, "convert_confirm_accept", "&aConfirm Convert"),
                trList(player, "convert_confirm_accept_lore", List.of(
                        "&7Convert this plot into a server zone now."
                )));
        plugin.gui().tagAction(confirm, "convert_confirm_yes");
        inv.setItem(11, confirm);

        ItemStack back = GUIManager.createItem(Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "convert_confirm_back_lore", List.of(
                        "&7Return to type selection."
                )));
        plugin.gui().tagAction(back, "convert_confirm_back");
        inv.setItem(15, back);

        ItemStack exit = GUIManager.createItem(Material.BARRIER,
                tr(player, "button_exit", "&c✖ Close"),
                trList(player, "exit_lore", List.of("&7Close this menu.")));
        plugin.gui().tagAction(exit, "convert_close");
        inv.setItem(22, exit);

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    /** Sneak + right-click on the server scepter opens this staff context menu. */
    public void openStaffWandMenu(Player player) {
        if (!hasConvertPermission(player) && !plugin.isAdmin(player)) {
            plugin.msg().send(player, "no_perm");
            plugin.effects().playError(player);
            return;
        }

        String title = plugin.gui().title(player, "staff_wand_menu_title", "&cStaff Scepter Options");
        Inventory inv = Bukkit.createInventory(new StaffWandHolder(), 27, title);
        for (int i = 0; i < 27; i++) inv.setItem(i, GUIManager.getFiller());

        ItemStack doctor = GUIManager.createItem(Material.SPYGLASS,
                tr(player, "staff_wand_doctor_name", "&bTerritory Doctor"),
                trList(player, "staff_wand_doctor_lore", List.of(
                        "&7What: scan, repair, and storage tools.",
                        "&eClick to open Doctor Tools."
                )));
        plugin.gui().tagAction(doctor, "wand_open_doctor");
        inv.setItem(11, doctor);

        Plot plot = plugin.store().getPlotAt(player.getLocation());
        List<String> convertLore = new ArrayList<>(trList(player, "staff_wand_convert_lore", List.of(
                "&7What: turn a personal plot into a server zone.",
                "&7Pick Spawn, Hub, Town, Event, Shop, Arena,",
                "&7or a plain server zone after a confirm step."
        )));
        convertLore.add(" ");
        String action = "wand_open_convert";
        Material convertMat = Material.STRUCTURE_BLOCK;
        if (plot == null) {
            convertLore.add(GUIManager.color(tr(player, "convert_blocker_no_plot",
                    "&cStand inside the player plot you want to convert.")));
            action = "wand_convert_blocked";
            convertMat = Material.GRAY_DYE;
        } else if (!canConvertPlot(player, plot)) {
            convertLore.add(GUIManager.color(tr(player, "convert_blocker_no_manage",
                    "&cYou cannot manage this plot as staff.")));
            action = "wand_convert_blocked";
            convertMat = Material.GRAY_DYE;
        } else {
            String blocker = findBlockerKey(plot);
            if (blocker != null) {
                convertLore.add(GUIManager.color(tr(player, blocker, blockerMessage(blocker))));
                action = "wand_convert_blocked";
                convertMat = Material.GRAY_DYE;
            } else {
                convertLore.add(GUIManager.color(tr(player, "staff_wand_convert_ready",
                        "&eClick to choose a server plot type.")));
            }
        }
        ItemStack convert = GUIManager.createItem(convertMat,
                tr(player, "staff_wand_convert_name", "&cConvert → Server Plot"),
                convertLore);
        plugin.gui().tagAction(convert, action);
        inv.setItem(13, convert);

        ItemStack staff = GUIManager.createItem(Material.COMMAND_BLOCK,
                tr(player, "staff_wand_admin_name", "&cStaff Tools"),
                trList(player, "staff_wand_admin_lore", List.of(
                        "&7Open the full Staff Command Center."
                )));
        plugin.gui().tagAction(staff, "wand_open_admin");
        inv.setItem(15, staff);

        ItemStack exit = GUIManager.createItem(Material.BARRIER,
                tr(player, "button_exit", "&c✖ Close"),
                trList(player, "exit_lore", List.of("&7Close this menu.")));
        plugin.gui().tagAction(exit, "convert_close");
        inv.setItem(22, exit);

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    public void handleStaffWandClick(Player player, InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        String action = plugin.gui().getAction(e.getCurrentItem());
        if (action == null || action.isBlank()) return;

        switch (action) {
            case "wand_open_doctor" -> {
                plugin.effects().playMenuFlip(player);
                plugin.gui().doctor().open(player);
            }
            case "wand_open_convert" -> openFromStanding(player);
            case "wand_convert_blocked" -> {
                plugin.effects().playError(player);
                Plot plot = plugin.store().getPlotAt(player.getLocation());
                String blocker = plot == null ? "convert_blocker_no_plot"
                        : (!canConvertPlot(player, plot) ? "convert_blocker_no_manage" : findBlockerKey(plot));
                if (blocker != null) send(player, blocker, blockerMessage(blocker));
            }
            case "wand_open_admin" -> {
                plugin.effects().playMenuFlip(player);
                plugin.gui().admin().open(player);
            }
            case "convert_close" -> {
                player.closeInventory();
                plugin.effects().playMenuClose(player);
            }
            default -> { }
        }
    }

    public void handleSelectClick(Player player, InventoryClickEvent e, ConvertSelectHolder holder) {
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        String action = plugin.gui().getAction(e.getCurrentItem());
        if (action == null || action.isBlank()) return;

        Plot plot = find(holder.getPlotId());
        switch (action) {
            case "convert_close" -> {
                player.closeInventory();
                plugin.effects().playMenuClose(player);
            }
            case "convert_back_admin" -> {
                plugin.effects().playMenuFlip(player);
                plugin.gui().admin().open(player);
            }
            case "convert_blocked" -> {
                plugin.effects().playError(player);
                if (plot != null) {
                    String blocker = findBlockerKey(plot);
                    if (blocker != null) send(player, blocker, blockerMessage(blocker));
                }
            }
            default -> {
                ConvertTarget target = ConvertTarget.fromAction(action);
                if (target == null || plot == null) return;
                openConfirm(player, plot, target);
            }
        }
    }

    public void handleConfirmClick(Player player, InventoryClickEvent e, ConvertConfirmHolder holder) {
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        String action = plugin.gui().getAction(e.getCurrentItem());
        if (action == null || action.isBlank()) return;

        Plot plot = find(holder.getPlotId());
        switch (action) {
            case "convert_close" -> {
                player.closeInventory();
                plugin.effects().playMenuClose(player);
            }
            case "convert_confirm_back" -> {
                if (plot != null) openSelect(player, plot);
                else plugin.gui().admin().open(player);
            }
            case "convert_confirm_yes" -> {
                if (plot == null) {
                    plugin.effects().playError(player);
                    player.closeInventory();
                    return;
                }
                boolean ok = executeConvert(player, plot, holder.getTarget());
                if (ok) {
                    plugin.effects().playConfirm(player);
                    player.closeInventory();
                    plugin.gui().admin().open(player);
                } else {
                    plugin.effects().playError(player);
                    openSelect(player, plot);
                }
            }
            default -> { }
        }
    }

    /**
     * Performs the ownership handoff and optional type setup.
     * @return true when conversion succeeded
     */
    public boolean executeConvert(Player actor, Plot plot, ConvertTarget target) {
        if (!canConvertPlot(actor, plot)) {
            plugin.msg().send(actor, "no_perm");
            return false;
        }
        String blocker = findBlockerKey(plot);
        if (blocker != null) {
            send(actor, blocker, blockerMessage(blocker));
            return false;
        }
        ConvertTarget resolved = target == null ? ConvertTarget.PLAIN : target;

        UUID previousOwner = plot.getOwner();
        if (plugin.snapshots() != null) {
            plugin.snapshots().createSnapshot(plot, ClaimSnapshot.SnapshotType.MANUAL,
                    "Before server-zone conversion by " + actor.getName()
                            + " (" + resolved.name() + ")", actor.getUniqueId());
        }

        plot.setForSale(false, 0);
        plot.setForRent(false, 0);
        plot.setForAuction(false);
        plot.clearPlayerAccess();
        if (plot.getZones() != null) {
            plot.getZones().forEach(zone -> {
                if (zone != null) zone.clearGuests();
            });
        }

        plugin.store().changePlotOwner(plot, Plot.SERVER_OWNER_UUID, "Server");
        applyTarget(actor, plot, resolved);
        plugin.store().savePlotSync(plot);
        plugin.claimBlocks().invalidateOwnerCache(previousOwner);
        if (plugin.getMapHooks() != null) plugin.getMapHooks().reload();
        plugin.territoryLife().clearOffer(plot.getPlotId());
        plugin.territoryLife().log(plot.getPlotId(), actor.getUniqueId(), "SERVER_ZONE_CONVERT",
                "Player territory converted into a server zone (" + resolved.name() + ").");

        plugin.getLogger().info("[Admin Audit] " + actor.getName() + " converted plot "
                + plot.getPlotId() + " from owner " + previousOwner + " into a server zone ("
                + resolved.name() + ").");
        if (plugin.notifications() != null) {
            plugin.notifications().notifyAdmins(
                    "aegis.admin",
                    "admin_notify_convert",
                    "&6[Admin] &e{PLAYER} &7converted plot {PLOT} into a server zone.",
                    java.util.Map.of("PLAYER", actor.getName(), "PLOT", String.valueOf(plot.getPlotId())));
        }

        send(actor, "convert_success",
                "&aPlot converted into a server zone (&f{TARGET}&a). Recovery snapshot created.",
                "{TARGET}", targetLabel(actor, resolved));
        return true;
    }

    private void applyTarget(Player actor, Plot plot, ConvertTarget target) {
        switch (target) {
            case SPAWN -> {
                var safeLocation = plugin.safeTravel() != null
                        ? plugin.safeTravel().findSafeDestination(actor.getLocation())
                        : TeleportUtil.findSafeDestination(actor.getLocation());
                if (safeLocation != null) {
                    plot.setSpawnLocation(safeLocation);
                }
                plot.setServerWarp(true, "Spawn", Material.BEACON);
                plot.setWarpCategory("SPAWN");
            }
            case HUB -> {
                plot.setServerWarp(true, "Hub", Material.ENDER_EYE);
                plot.setWarpCategory("HUB");
            }
            case TOWN -> {
                plot.setServerWarp(true, "Town", Material.BELL);
                plot.setWarpCategory("TOWN");
            }
            case EVENT -> {
                plot.setServerWarp(true, "Event", Material.FIREWORK_ROCKET);
                plot.setWarpCategory("EVENT");
            }
            case SHOP -> {
                plot.setServerWarp(true, "Shop", Material.EMERALD);
                plot.setWarpCategory("SHOP");
            }
            case ARENA -> ProtectionPreset.ARENA.apply(plot);
            case PLAIN -> { /* ownership-only */ }
        }
    }

    private Plot find(UUID id) {
        if (id == null) return null;
        return plugin.store().getAllPlots().stream()
                .filter(p -> p != null && id.equals(p.getPlotId()))
                .findFirst()
                .orElse(null);
    }

    private String targetLabel(Player player, ConvertTarget target) {
        return GUIManager.color(tr(player, target.nameKey, target.nameFallback)).replaceAll("§.", "");
    }

    private String blockerMessage(String key) {
        return switch (key) {
            case "convert_blocker_no_plot" -> "&cStand inside the player plot you want to convert.";
            case "convert_blocker_already_server" -> "&eThis plot is already a server zone.";
            case "convert_blocker_group" -> "&cGroup plots must be dissolved before conversion.";
            case "convert_blocker_rental" -> "&cActive full-plot rentals must end before conversion.";
            case "convert_blocker_auction" -> "&cAuctions must be cancelled before conversion.";
            case "convert_blocker_zone_rent" -> "&cRented or listed child zones must be resolved first.";
            case "convert_blocker_no_manage" -> "&cYou cannot manage this plot as staff.";
            default -> "&cThis plot cannot be converted right now.";
        };
    }

    private void replacePlaceholders(List<String> lines, Plot plot) {
        String plotName = plot.getPlotName() == null || plot.getPlotName().isBlank() ? "Plot" : plot.getPlotName();
        String owner = plot.getOwnerName() == null || plot.getOwnerName().isBlank()
                ? String.valueOf(plot.getOwner()) : plot.getOwnerName();
        replaceInList(lines, "{PLOT}", plotName);
        replaceInList(lines, "{OWNER}", owner);
    }

    private void replaceInList(List<String> lines, String token, String value) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line != null && line.contains(token)) {
                lines.set(i, line.replace(token, value == null ? "" : value));
            }
        }
    }

    private void send(Player player, String key, String fallback) {
        player.sendMessage(GUIManager.color(tr(player, key, fallback)));
    }

    private void send(Player player, String key, String fallback, String token, String value) {
        player.sendMessage(GUIManager.color(tr(player, key, fallback).replace(token, value == null ? "" : value)));
    }

    private String tr(Player p, String k, String f) { return plugin.gui().tr(p, k, f); }
    private List<String> trList(Player p, String k, List<String> f) { return plugin.gui().trList(p, k, f); }
}
