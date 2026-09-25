package com.aegisguard.gatherings;

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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Open House start/stop menu for plot managers. */
public final class GatheringGUI {

    private final AegisGuard plugin;

    public GatheringGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) {
        if (player == null) return;
        GatheringService service = plugin.gatherings();
        if (service == null || !service.isEnabled()) {
            player.sendMessage(GUIManager.color("&8[&bAegisGuard&8]&r "
                    + t(player, "gathering_disabled", "&cOpen House is disabled on this server.")));
            return;
        }
        Plot plot = plugin.store() == null ? null : plugin.store().getPlotAt(player.getLocation());
        Inventory inv = Bukkit.createInventory(new Holder(), 27,
                plugin.gui().title(player, "gathering_gui_title", "&6Open House"));
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        Gathering live = plot == null ? null : service.active(plot.getPlotId());
        boolean canManage = plot != null && plot.canManage(player, plugin);
        String plotName = plot == null ? "-" : plot.getPlotName();
        String remaining = remainingLabel(live);

        inv.setItem(4, GUIManager.createItem(Material.CAMPFIRE,
                t(player, "gathering_status_name", "&6Open House"),
                List.of(
                        t(player, "gathering_status_plot", "&7Plot: &f{PLOT}", Map.of("PLOT", plotName)),
                        t(player, "gathering_status_state", live == null ? "&7Not running." : "&aLive for &f{REMAINING}",
                                Map.of("REMAINING", remaining)),
                        t(player, "gathering_status_hint", "&7Visitors find you on Atlas Discover → Live.")
                )));

        int shortMins = service.minMinutes();
        int defaultMins = service.defaultMinutes();
        inv.setItem(11, GUIManager.createItem(Material.CLOCK,
                t(player, "gathering_start_short", "&aStart {MINUTES}m", Map.of("MINUTES", String.valueOf(shortMins))),
                List.of(t(player, "gathering_start_lore", "&eClick to open the house."))));
        inv.setItem(12, GUIManager.createItem(Material.CLOCK,
                t(player, "gathering_start_default", "&aStart {MINUTES}m", Map.of("MINUTES", String.valueOf(defaultMins))),
                List.of(t(player, "gathering_start_lore", "&eClick to open the house."))));
        inv.setItem(14, GUIManager.createItem(live != null && live.grantGuestPass() ? Material.LIME_DYE : Material.GRAY_DYE,
                t(player, live != null && live.grantGuestPass() ? "gathering_pass_on" : "gathering_pass_off",
                        live != null && live.grantGuestPass() ? "&aVisitor passes: On" : "&cVisitor passes: Off"),
                List.of(t(player, "gathering_pass_lore", "&7Issue a timed visitor Guest Pass to arrivals."))));
        inv.setItem(15, GUIManager.createItem(Material.BARRIER,
                t(player, "gathering_stop_name", "&cEnd Open House"),
                List.of(t(player, "gathering_stop_lore", "&7Close the listing and expire visitor passes."))));

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))));
        inv.setItem(26, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        if (!canManage) {
            inv.setItem(22, GUIManager.createItem(Material.STRUCTURE_VOID,
                    t(player, "gathering_need_manage", "&cStand in a plot you manage"),
                    List.of(t(player, "gathering_need_manage_lore", "&7Owners, co-owners, and stewards can host."))));
        }

        player.openInventory(inv);
        if (plugin.effects() != null) plugin.effects().playMenuFlip(player);
    }

    public void handleClick(Player player, InventoryClickEvent event, Holder holder) {
        if (player == null || event == null) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        GatheringService service = plugin.gatherings();
        if (service == null || !service.isEnabled()) return;
        int slot = event.getRawSlot();
        if (slot == 18) {
            plugin.gui().openMain(player);
            return;
        }
        if (slot == 26) {
            player.closeInventory();
            if (plugin.effects() != null) plugin.effects().playMenuClose(player);
            return;
        }
        Plot plot = plugin.store() == null ? null : plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.canManage(player, plugin)) {
            if (plugin.effects() != null) plugin.effects().playError(player);
            return;
        }
        if (slot == 11) {
            service.start(player, service.minMinutes());
            open(player);
            return;
        }
        if (slot == 12) {
            service.start(player, service.defaultMinutes());
            open(player);
            return;
        }
        if (slot == 14) {
            Gathering live = service.active(plot.getPlotId());
            if (live == null) {
                if (plugin.effects() != null) plugin.effects().playError(player);
                return;
            }
            boolean on = service.toggleGuestPass(player);
            if (plugin.msg() != null) {
                plugin.msg().send(player, on ? "gathering_pass_enabled" : "gathering_pass_disabled");
            }
            open(player);
            return;
        }
        if (slot == 15) {
            service.stop(player);
            open(player);
        }
    }

    private String remainingLabel(Gathering gathering) {
        if (gathering == null) return "-";
        long left = Math.max(0L, gathering.endsAt() - System.currentTimeMillis());
        long minutes = Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(left));
        return minutes + "m";
    }

    private String t(Player player, String key, String fallback) {
        return plugin.gui().tr(player, key, fallback);
    }

    private String t(Player player, String key, String fallback, Map<String, String> vars) {
        return plugin.gui().tr(player, key, fallback, vars);
    }

    private List<String> tl(Player player, String key, List<String> fallback) {
        return plugin.gui().trList(player, key, fallback);
    }
}
