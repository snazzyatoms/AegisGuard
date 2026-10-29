package com.aegisguard.snapshots;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Owner-facing backup browser for /ag backup: every restore point on the plot
 * you own, newest first. Click an entry once to arm the restore, again to
 * confirm — the same confirm-gated flow as /ag backup restore. Honors
 * self_backup.restore_mode (staff mode shows a "contact staff" hint instead).
 */
public final class BackupGUI {

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int SLOT_NEW = 49;
    private static final int SLOT_CLOSE = 53;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 46;
    private static final SimpleDateFormat FMT = new SimpleDateFormat("MMM d, HH:mm");

    private final AegisGuard plugin;

    public BackupGUI(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public static final class BackupHolder implements InventoryHolder {
        private final UUID plotId;
        private final List<UUID> snapshotIds;
        private final int page;
        private final UUID armed;

        public BackupHolder(UUID plotId, List<UUID> snapshotIds, int page, UUID armed) {
            this.plotId = plotId;
            this.snapshotIds = List.copyOf(snapshotIds);
            this.page = page;
            this.armed = armed;
        }

        public UUID getPlotId() { return plotId; }
        public int getPage() { return page; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player, Plot plot, int page) {
        open(player, plot, page, null);
    }

    private void open(Player player, Plot plot, int page, UUID armed) {
        if (plot == null || !plot.isOwner(player.getUniqueId()) && !plugin.isAdmin(player)) {
            send(player, "&cYou can only manage backups on a plot you own.");
            return;
        }
        List<ClaimSnapshot> snaps = plugin.snapshots().getSnapshotsForPlot(plot.getPlotId());
        List<UUID> ids = new ArrayList<>();
        for (ClaimSnapshot s : snaps) if (s != null) ids.add(s.getSnapshotId());

        int maxPage = Math.max(0, (ids.size() - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, maxPage));
        BackupHolder holder = new BackupHolder(plot.getPlotId(), ids, page, armed);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                "§6Backups — " + (plot.getPlotName() == null ? "plot" : plot.getPlotName()));

        ItemStack filler = GUIManager.getFiller();
        for (int slot : new int[]{45, 47, 48, 50, 51, 52}) inv.setItem(slot, filler);

        inv.setItem(4, GUIManager.createItem(Material.OAK_SIGN,
                "§e⚠ Read before restoring", List.of(
                        "§7Backups save your plot's data — settings,",
                        "§7roles, members, boards. Restoring rewinds",
                        "§7the plot to the point you pick, and anything",
                        "§7changed after it is gone for good.",
                        " ",
                        "§cUse restores at your own risk.",
                        "§7If something goes wrong, ask a server",
                        "§7admin — staff keep their own restore",
                        "§7points and can roll back further.")));

        int start = page * PER_PAGE;
        boolean staffMode = "staff".equalsIgnoreCase(
                plugin.getConfig().getString("self_backup.restore_mode", "self"));
        for (int i = 0; i < PER_PAGE && start + i < ids.size(); i++) {
            ClaimSnapshot s = find(snaps, ids.get(start + i));
            if (s == null) continue;
            boolean isArmed = ids.get(start + i).equals(armed);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + (s.getReason() == null ? "Snapshot" : s.getReason()));
            lore.add("§8" + FMT.format(new Date(s.getTimestamp())));
            lore.add(" ");
            if (staffMode) {
                lore.add("§7Restores go through staff —");
                lore.add("§7ask an admin to roll this back.");
            } else if (isArmed) {
                lore.add("§e⚠ Click again to restore!");
                lore.add("§7This rewinds the plot to this point.");
                lore.add("§8At your own risk — admins can help if it goes wrong.");
            } else {
                lore.add("§aClick to arm a restore,");
                lore.add("§aclick again to confirm.");
            }
            inv.setItem(i, GUIManager.createItem(
                    isArmed ? Material.TNT : Material.CLOCK,
                    (isArmed ? "§c⚠ Restore " : "§f") + FMT.format(new Date(s.getTimestamp())),
                    lore));
        }

        if (page > 0) inv.setItem(SLOT_PREV, GUIManager.createItem(Material.ARROW,
                "§e◀ Previous page", List.of("§7Page " + page)));
        if (page < maxPage) inv.setItem(SLOT_NEXT, GUIManager.createItem(Material.ARROW,
                "§eNext page ▶", List.of("§7Page " + (page + 2) + "/" + (maxPage + 1))));
        inv.setItem(SLOT_NEW, GUIManager.createItem(Material.EMERALD,
                "§a✚ New backup", List.of("§7Snapshot this plot right now.",
                        "§7Same as /ag backup.")));
        inv.setItem(SLOT_CLOSE, GUIManager.createItem(Material.BARRIER, "§cClose", List.of()));
        player.openInventory(inv);
    }

    /** GUIListener dispatch. Owner + permission re-checked on every click. */
    public void handleClick(Player player, InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof BackupHolder holder)) return;
        Plot plot = plugin.store() == null ? null : plugin.store().getPlotById(holder.getPlotId());
        if (plot == null || (!plot.isOwner(player.getUniqueId()) && !plugin.isAdmin(player))
                || (!player.hasPermission("aegis.backup") && !plugin.isAdmin(player))) {
            player.closeInventory();
            send(player, "&cYou can only manage backups on a plot you own.");
            return;
        }
        int slot = e.getRawSlot();
        if (slot == SLOT_CLOSE) { player.closeInventory(); return; }
        if (slot == SLOT_PREV && holder.getPage() > 0) {
            open(player, plot, holder.getPage() - 1);
            return;
        }
        if (slot == SLOT_NEXT) {
            open(player, plot, holder.getPage() + 1);
            return;
        }
        if (slot == SLOT_NEW) {
            player.closeInventory();
            player.performCommand("aegis backup create");
            return;
        }
        if (slot < 0 || slot >= PER_PAGE) return;
        int index = holder.getPage() * PER_PAGE + slot;
        if (index >= holder.snapshotIds.size()) return;
        UUID snapshotId = holder.snapshotIds.get(index);

        if ("staff".equalsIgnoreCase(plugin.getConfig().getString("self_backup.restore_mode", "self"))) {
            send(player, "&eRestores go through staff on this server — ask an admin.");
            return;
        }
        if (!snapshotId.equals(holder.armed)) {
            open(player, plot, holder.getPage(), snapshotId); // arm it
            return;
        }
        player.closeInventory();
        restore(player, plot, snapshotId);
    }

    /** Same scoped restore the command path runs — durable, async, messaged. */
    private void restore(Player player, Plot plot, UUID snapshotId) {
        EnumSet<RestoreScope> scopes = EnumSet.of(RestoreScope.FULL_DATA);
        if (plugin.getConfig().getBoolean("self_backup.block_copies", false)) {
            scopes.add(RestoreScope.BUILD);
        }
        send(player, "&7Rolling your plot back…");
        plugin.snapshots().restoreAsync(snapshotId, player.getUniqueId(), scopes)
                .whenComplete((result, error) -> plugin.runMain(player, () -> {
                    if (error != null || result == null || !result.dataRestored()) {
                        send(player, "&cThe restore did not complete — staff can check the Audit Ledger.");
                        return;
                    }
                    send(player, "&aRestore complete — your plot is back to how it was.");
                }));
    }

    private ClaimSnapshot find(List<ClaimSnapshot> snaps, UUID id) {
        for (ClaimSnapshot s : snaps) {
            if (s != null && id.equals(s.getSnapshotId())) return s;
        }
        return null;
    }

    private void send(Player player, String message) {
        player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', message));
    }
}
