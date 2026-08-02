package com.aegisguard.lockdown;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Milestone 3 (Emergency Plot Lockdown) player-facing GUI.
 *
 * Flow: status screen (shows current state, who/when it was activated) -> an explicit
 * confirmation screen for either activating or deactivating -> back to the status screen.
 *
 * Never touches ownership or roles; it is purely a temporary, fully reversible access gate
 * enforced in {@link Plot#canBuild}.
 */
public class LockdownGUI {

    private final AegisGuard plugin;

    public LockdownGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static class LockdownMenuHolder implements InventoryHolder {
        private final Plot plot;
        public LockdownMenuHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class LockdownConfirmHolder implements InventoryHolder {
        private final Plot plot;
        private final boolean activating;
        public LockdownConfirmHolder(Plot plot, boolean activating) { this.plot = plot; this.activating = activating; }
        public Plot getPlot() { return plot; }
        public boolean isActivating() { return activating; }
        @Override public Inventory getInventory() { return null; }
    }

    private String t(Player p, String key, String fallback) {
        return plugin.gui().tr(p, key, fallback);
    }

    private List<String> tl(Player p, String key, List<String> fallback) {
        return plugin.gui().trList(p, key, fallback);
    }

    private String t(Player p, String key, Map<String, String> vars, String fallback) {
        String raw = null;
        try {
            if (plugin.codex() != null) raw = plugin.codex().tr(p, key, vars);
        } catch (Throwable ignored) {}

        String out = (raw == null || raw.isBlank() || raw.equalsIgnoreCase(key))
                ? (fallback == null ? "" : fallback)
                : raw;

        if (vars != null && !vars.isEmpty()) {
            for (Map.Entry<String, String> en : vars.entrySet()) {
                String k = en.getKey();
                String v = en.getValue() == null ? "" : en.getValue();
                out = out.replace("{" + k + "}", v);
            }
        }
        return out;
    }

    private boolean isTopClick(InventoryClickEvent e) {
        return e.getClickedInventory() != null && e.getClickedInventory() == e.getView().getTopInventory();
    }

    private boolean canManagePlot(Player actor, Plot plot) {
        return actor != null && plot != null && plot.canManage(actor, plugin);
    }

    public void open(Player player) {
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null) {
            plugin.msg().send(player, "no_plot_here");
            plugin.effects().playError(player);
            return;
        }
        if (!canManagePlot(player, plot)) {
            plugin.msg().send(player, "not_plot_owner");
            plugin.effects().playError(player);
            return;
        }
        openMenu(player, plot);
    }

    public void openMenu(Player player, Plot plot) {
        if (plot == null) { plugin.effects().playError(player); return; }

        String title = plugin.gui().title(player, "lockdown_menu_title", "&cEmergency Lockdown");
        Inventory inv = Bukkit.createInventory(new LockdownMenuHolder(plot), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        boolean active = plot.isLockdownActive();

        List<String> lore = new java.util.ArrayList<>();
        if (active) {
            long minutesAgo = Math.max(0L, (System.currentTimeMillis() - plot.getLockdownActivatedAt()) / 60_000L);
            lore.addAll(tl(player, "lockdown_status_active_lore", List.of("&7This plot is currently locked down.")));
            lore.add(GUIManager.color(t(player, "lockdown_status_activated_by",
                    Map.of("PLAYER", plot.getLockdownActivatedByName()), "&7Activated by: &f{PLAYER}")));
            lore.add(GUIManager.color(t(player, "lockdown_status_activated_ago",
                    Map.of("MINUTES", String.valueOf(minutesAgo)), "&7Active for: &f{MINUTES}m")));
        } else {
            lore.addAll(tl(player, "lockdown_status_inactive_lore", List.of(
                    "&7This plot is operating normally.",
                    "&7Activate lockdown to instantly restrict",
                    "&7building, breaking, and containers for",
                    "&7everyone except you and staff.")));
        }

        inv.setItem(13, GUIManager.createItem(
                active ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                t(player, active ? "lockdown_status_active_name" : "lockdown_status_inactive_name",
                        active ? "&c&lLOCKDOWN ACTIVE" : "&a&lNormal Operation"),
                lore
        ));

        inv.setItem(11, GUIManager.createItem(Material.WRITTEN_BOOK,
                t(player, "lockdown_guide_name", "&eLockdown Guide"),
                tl(player, "lockdown_guide_lore", List.of(
                        "&7Lockdown instantly overrides roles and",
                        "&7Guest Passes for sensitive actions.",
                        " ",
                        "&8Doors and buttons always keep working,",
                        "&8so nobody is ever trapped inside.",
                        "&8Ownership and roles are never changed."))));

        if (active) {
            inv.setItem(15, GUIManager.createItem(Material.LIME_DYE,
                    t(player, "button_deactivate_lockdown", "&aDeactivate Lockdown"),
                    tl(player, "deactivate_lockdown_lore", List.of("&7Immediately restore normal access."))));
        } else {
            inv.setItem(15, GUIManager.createItem(Material.RED_DYE,
                    t(player, "button_activate_lockdown", "&cActivate Lockdown"),
                    tl(player, "activate_lockdown_lore", List.of("&7Immediately restrict sensitive actions."))));
        }

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the main menu."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    private void openConfirm(Player player, Plot plot, boolean activating) {
        String titleKey = activating ? "lockdown_confirm_activate_title" : "lockdown_confirm_deactivate_title";
        String title = plugin.gui().title(player, titleKey,
                activating ? "&cConfirm Lockdown" : "&aConfirm Unlock");
        Inventory inv = Bukkit.createInventory(new LockdownConfirmHolder(plot, activating), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<String> lore = activating
                ? tl(player, "lockdown_confirm_activate_lore", List.of(
                        "&7Everyone except you and staff will",
                        "&7instantly lose build, break, and",
                        "&7container access on this plot.",
                        " ",
                        "&7Doors and buttons keep working, so",
                        "&7nobody gets trapped inside.",
                        " ",
                        "&aClick to activate lockdown."))
                : tl(player, "lockdown_confirm_deactivate_lore", List.of(
                        "&7This plot will immediately return to",
                        "&7its normal roles and permissions.",
                        " ",
                        "&aClick to deactivate lockdown."));

        inv.setItem(13, GUIManager.createItem(
                activating ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK,
                t(player, activating ? "lockdown_confirm_activate_name" : "lockdown_confirm_deactivate_name",
                        activating ? "&cConfirm Activation" : "&aConfirm Deactivation"),
                lore));

        inv.setItem(18, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to the previous menu."))));
        inv.setItem(20, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleMenuClick(Player player, InventoryClickEvent e, LockdownMenuHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); return; }

        int slot = e.getRawSlot();
        if (slot == 18) { plugin.gui().openMain(player); return; }
        if (slot == 20) { player.closeInventory(); return; }

        if (slot == 15) {
            boolean activating = !plot.isLockdownActive();
            if (plugin.lockdown() != null && plugin.lockdown().requiresConfirmation()) {
                openConfirm(player, plot, activating);
            } else {
                applyToggle(player, plot, activating);
            }
        }
    }

    public void handleConfirmClick(Player player, InventoryClickEvent e, LockdownConfirmHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); openMenu(player, plot); return; }

        int slot = e.getRawSlot();
        if (slot == 18) { openMenu(player, plot); return; }
        if (slot == 20) { player.closeInventory(); return; }

        if (slot == 13) {
            applyToggle(player, plot, holder.isActivating());
        }
    }

    private void applyToggle(Player player, Plot plot, boolean activating) {
        String failureKey = activating
                ? plugin.lockdown().activate(player, plot)
                : plugin.lockdown().deactivate(player, plot);

        if (failureKey != null) {
            plugin.msg().send(player, failureKey);
            plugin.effects().playError(player);
        } else {
            plugin.msg().send(player, activating ? "lockdown_activated" : "lockdown_deactivated");
            if (activating) plugin.effects().playError(player);
            else plugin.effects().playConfirm(player);
        }
        openMenu(player, plot);
    }
}
