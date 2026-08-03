package com.aegisguard.lockdown;

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
import java.util.concurrent.TimeUnit;

/**
 * Milestone 3 (Emergency Plot Lockdown) player-facing GUI.
 *
 * Flow: status -> choose mode/duration (when activating) -> optional confirm -> status.
 * Never touches ownership or roles; it is purely a temporary, fully reversible access gate.
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

    public static class LockdownOptionsHolder implements InventoryHolder {
        private final Plot plot;
        private String mode = "FULL";
        private long minutes = 0L;
        public LockdownOptionsHolder(Plot plot) { this.plot = plot; }
        public Plot getPlot() { return plot; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public long getMinutes() { return minutes; }
        public void setMinutes(long minutes) { this.minutes = minutes; }
        @Override public Inventory getInventory() { return null; }
    }

    public static class LockdownConfirmHolder implements InventoryHolder {
        private final Plot plot;
        private final boolean activating;
        private final String mode;
        private final long minutes;
        public LockdownConfirmHolder(Plot plot, boolean activating) {
            this(plot, activating, "FULL", 0L);
        }
        public LockdownConfirmHolder(Plot plot, boolean activating, String mode, long minutes) {
            this.plot = plot;
            this.activating = activating;
            this.mode = mode == null ? "FULL" : mode;
            this.minutes = Math.max(0L, minutes);
        }
        public Plot getPlot() { return plot; }
        public boolean isActivating() { return activating; }
        public String getMode() { return mode; }
        public long getMinutes() { return minutes; }
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

        List<String> lore = new ArrayList<>();
        if (active) {
            long minutesAgo = Math.max(0L, (System.currentTimeMillis() - plot.getLockdownActivatedAt()) / 60_000L);
            lore.addAll(tl(player, "lockdown_status_active_lore", List.of("&7This plot is currently locked down.")));
            lore.add(GUIManager.color(t(player, "lockdown_status_activated_by",
                    Map.of("PLAYER", plot.getLockdownActivatedByName()), "&7Activated by: &f{PLAYER}")));
            lore.add(GUIManager.color(t(player, "lockdown_status_activated_ago",
                    Map.of("MINUTES", String.valueOf(minutesAgo)), "&7Active for: &f{MINUTES}m")));
            lore.add(GUIManager.color(t(player, "lockdown_status_mode_line",
                    Map.of("MODE", plot.isSoftLockdown()
                            ? t(player, "lockdown_mode_soft_label", "Soft (containers + build)")
                            : t(player, "lockdown_mode_full_label", "Full")),
                    "&7Mode: &f{MODE}")));
            if (plot.getLockdownExpiresAt() > 0L) {
                long remain = Math.max(0L, plot.getLockdownExpiresAt() - System.currentTimeMillis());
                long remainMin = TimeUnit.MILLISECONDS.toMinutes(remain);
                lore.add(GUIManager.color(t(player, "lockdown_status_expires_line",
                        Map.of("MINUTES", String.valueOf(remainMin)),
                        "&7Auto-lifts in: &f{MINUTES}m")));
            } else {
                lore.add(GUIManager.color(t(player, "lockdown_status_manual_line",
                        "&7Duration: &fUntil manually lifted")));
            }
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
                    tl(player, "activate_lockdown_lore", List.of(
                            "&7Choose soft/full mode and an optional",
                            "&7auto-expire duration next."))));
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

    private void openOptions(Player player, Plot plot) {
        openOptions(player, new LockdownOptionsHolder(plot));
    }

    private void openOptions(Player player, LockdownOptionsHolder holder) {
        Plot plot = holder.getPlot();
        String title = plugin.gui().title(player, "lockdown_options_title", "&cLockdown Options");
        Inventory inv = Bukkit.createInventory(holder, 36, title);
        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);

        boolean soft = "SOFT".equalsIgnoreCase(holder.getMode());
        inv.setItem(11, GUIManager.createItem(soft ? Material.CHEST : Material.GRAY_DYE,
                t(player, "lockdown_mode_soft_name", "&eSoft Lockdown"),
                tl(player, "lockdown_mode_soft_lore", List.of(
                        "&7Restricts containers + build/break only.",
                        "&7Roles and guest passes stay intact.",
                        soft ? "&aSelected." : "&eClick to select."))));
        inv.setItem(15, GUIManager.createItem(!soft ? Material.REDSTONE_BLOCK : Material.GRAY_DYE,
                t(player, "lockdown_mode_full_name", "&cFull Lockdown"),
                tl(player, "lockdown_mode_full_lore", List.of(
                        "&7Uses the server restricted-permission list.",
                        "&7Doors/interact still always work.",
                        !soft ? "&aSelected." : "&eClick to select."))));

        placeDuration(inv, 19, player, holder, 0L, "lockdown_duration_manual", "&fUntil Manual Lift");
        placeDuration(inv, 20, player, holder, 15L, "lockdown_duration_15m", "&f15 Minutes");
        placeDuration(inv, 21, player, holder, 60L, "lockdown_duration_1h", "&f1 Hour");
        placeDuration(inv, 22, player, holder, 360L, "lockdown_duration_6h", "&f6 Hours");
        placeDuration(inv, 23, player, holder, 1440L, "lockdown_duration_24h", "&f24 Hours");

        inv.setItem(31, GUIManager.createItem(Material.EMERALD_BLOCK,
                t(player, "lockdown_options_continue", "&aContinue"),
                tl(player, "lockdown_options_continue_lore", List.of(
                        "&7Mode: &f" + (soft ? "Soft" : "Full"),
                        "&7Duration: &f" + durationLabel(player, holder.getMinutes()),
                        "&eClick to confirm."))));

        inv.setItem(27, GUIManager.createItem(Material.ARROW,
                t(player, "button_back", "&fBack"),
                tl(player, "back_lore", List.of("&7Return to lockdown status."))));
        inv.setItem(29, GUIManager.createItem(Material.BARRIER,
                t(player, "button_exit", "&cClose"),
                tl(player, "exit_lore", List.of("&7Close this menu."))));

        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    private void placeDuration(Inventory inv, int slot, Player player, LockdownOptionsHolder holder,
                               long minutes, String nameKey, String fallback) {
        boolean selected = holder.getMinutes() == minutes;
        List<String> lore = new ArrayList<>();
        lore.add(GUIManager.color(selected
                ? t(player, "lockdown_duration_selected", "&aSelected.")
                : t(player, "lockdown_duration_click", "&eClick to select.")));
        inv.setItem(slot, GUIManager.createItem(selected ? Material.CLOCK : Material.GRAY_DYE,
                t(player, nameKey, fallback), lore));
    }

    private String durationLabel(Player player, long minutes) {
        if (minutes <= 0) return t(player, "lockdown_duration_manual_label", "Until manually lifted");
        if (minutes == 60) return t(player, "lockdown_duration_1h_label", "1 hour");
        if (minutes == 360) return t(player, "lockdown_duration_6h_label", "6 hours");
        if (minutes == 1440) return t(player, "lockdown_duration_24h_label", "24 hours");
        return minutes + "m";
    }

    private void openConfirm(Player player, Plot plot, boolean activating, String mode, long minutes) {
        String titleKey = activating ? "lockdown_confirm_activate_title" : "lockdown_confirm_deactivate_title";
        String title = plugin.gui().title(player, titleKey,
                activating ? "&cConfirm Lockdown" : "&aConfirm Unlock");
        Inventory inv = Bukkit.createInventory(new LockdownConfirmHolder(plot, activating, mode, minutes), 27, title);

        ItemStack filler = GUIManager.getFiller();
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        List<String> lore;
        if (activating) {
            lore = new ArrayList<>(tl(player, "lockdown_confirm_activate_lore", List.of(
                    "&7Everyone except you and staff will",
                    "&7instantly lose selected access on this plot.",
                    " ",
                    "&7Doors keep working, so nobody gets trapped.",
                    " ",
                    "&aClick to activate lockdown.")));
            lore.add(GUIManager.color(t(player, "lockdown_confirm_mode_line",
                    Map.of("MODE", "SOFT".equalsIgnoreCase(mode)
                            ? t(player, "lockdown_mode_soft_label", "Soft (containers + build)")
                            : t(player, "lockdown_mode_full_label", "Full")),
                    "&7Mode: &f{MODE}")));
            lore.add(GUIManager.color(t(player, "lockdown_confirm_duration_line",
                    Map.of("DURATION", durationLabel(player, minutes)),
                    "&7Duration: &f{DURATION}")));
        } else {
            lore = tl(player, "lockdown_confirm_deactivate_lore", List.of(
                    "&7This plot will immediately return to",
                    "&7its normal roles and permissions.",
                    " ",
                    "&aClick to deactivate lockdown."));
        }

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
            if (plot.isLockdownActive()) {
                if (plugin.lockdown() != null && plugin.lockdown().requiresConfirmation()) {
                    openConfirm(player, plot, false, "FULL", 0L);
                } else {
                    applyToggle(player, plot, false, "FULL", 0L);
                }
            } else {
                openOptions(player, plot);
            }
        }
    }

    public void handleOptionsClick(Player player, InventoryClickEvent e, LockdownOptionsHolder holder) {
        if (!isTopClick(e)) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;

        Plot plot = holder.getPlot();
        if (!canManagePlot(player, plot)) { plugin.effects().playError(player); openMenu(player, plot); return; }

        int slot = e.getRawSlot();
        if (slot == 27) { openMenu(player, plot); return; }
        if (slot == 29) { player.closeInventory(); return; }

        if (slot == 11) { holder.setMode("SOFT"); openOptions(player, holder); return; }
        if (slot == 15) { holder.setMode("FULL"); openOptions(player, holder); return; }
        if (slot == 19) { holder.setMinutes(0L); openOptions(player, holder); return; }
        if (slot == 20) { holder.setMinutes(15L); openOptions(player, holder); return; }
        if (slot == 21) { holder.setMinutes(60L); openOptions(player, holder); return; }
        if (slot == 22) { holder.setMinutes(360L); openOptions(player, holder); return; }
        if (slot == 23) { holder.setMinutes(1440L); openOptions(player, holder); return; }

        if (slot == 31) {
            if (plugin.lockdown() != null && plugin.lockdown().requiresConfirmation()) {
                openConfirm(player, plot, true, holder.getMode(), holder.getMinutes());
            } else {
                applyToggle(player, plot, true, holder.getMode(), holder.getMinutes());
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
        if (slot == 18) {
            if (holder.isActivating()) openOptions(player, plot);
            else openMenu(player, plot);
            return;
        }
        if (slot == 20) { player.closeInventory(); return; }

        if (slot == 13) {
            applyToggle(player, plot, holder.isActivating(), holder.getMode(), holder.getMinutes());
        }
    }

    private void applyToggle(Player player, Plot plot, boolean activating, String mode, long minutes) {
        String failureKey = activating
                ? plugin.lockdown().activate(player, plot, minutes, mode)
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
