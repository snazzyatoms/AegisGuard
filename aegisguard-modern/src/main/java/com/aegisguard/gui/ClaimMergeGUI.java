package com.aegisguard.gui;

import com.aegisguard.AegisGuard;
import com.aegisguard.claim.ClaimMergeMath;
import com.aegisguard.claimblocks.ClaimBlockManager;
import com.aegisguard.data.Plot;
import com.aegisguard.data.Zone;
import com.aegisguard.guestpass.GuestPass;
import com.aegisguard.snapshots.ClaimSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Safe MVP merge flow: choose a base plot, then an adjacent owned plot, then confirm. */
public class ClaimMergeGUI {
    private final AegisGuard plugin;
    public ClaimMergeGUI(AegisGuard plugin) { this.plugin = plugin; }

    public static final class ClaimMergeHolder implements InventoryHolder {
        private final UUID baseId;
        private final UUID candidateId;
        private final List<UUID> plotIds;
        private final boolean confirm;
        private final String returnTo;
        private final UUID originPlotId;
        public ClaimMergeHolder(UUID baseId, UUID candidateId, List<UUID> plotIds, boolean confirm) {
            this(baseId, candidateId, plotIds, confirm, MarketNav.MAIN, null);
        }
        public ClaimMergeHolder(UUID baseId, UUID candidateId, List<UUID> plotIds, boolean confirm,
                                String returnTo, UUID originPlotId) {
            this.baseId = baseId;
            this.candidateId = candidateId;
            this.plotIds = plotIds;
            this.confirm = confirm;
            this.returnTo = MarketNav.normalize(returnTo);
            this.originPlotId = originPlotId;
        }
        public UUID getBaseId() { return baseId; }
        public UUID getCandidateId() { return candidateId; }
        public List<UUID> getPlotIds() { return plotIds; }
        public boolean isConfirm() { return confirm; }
        public String getReturnTo() { return returnTo; }
        public UUID getOriginPlotId() { return originPlotId; }
        @Override public Inventory getInventory() { return null; }
    }

    public void open(Player player) { openFrom(player, MarketNav.MAIN, null); }

    public void openFrom(Player player, String returnTo, Plot originPlot) {
        openSelect(player, null, returnTo, originPlot == null ? null : originPlot.getPlotId());
    }

    private void openSelect(Player player, UUID baseId) {
        openSelect(player, baseId, MarketNav.MAIN, null);
    }

    private void openSelect(Player player, UUID baseId, String returnTo, UUID originPlotId) {
        if (!isMergeEnabled()) {
            player.sendMessage(GUIManager.color(tr(player, "claim_merge_disabled",
                    "&cClaim merging is disabled on this server.")));
            plugin.effects().playError(player);
            return;
        }
        List<UUID> ids = plugin.store().getPlots(player.getUniqueId()).stream().map(Plot::getPlotId).toList();
        Inventory inv = Bukkit.createInventory(new ClaimMergeHolder(baseId, null, ids, false, returnTo, originPlotId), 54,
                plugin.gui().title(player, "claim_merge_title", "&6Merge Claims"));
        for (int i = 45; i < 54; i++) inv.setItem(i, GUIManager.getFiller());
        for (int i = 0; i < ids.size() && i < 45; i++) {
            Plot plot = find(ids.get(i)); if (plot == null) continue;
            boolean base = plot.getPlotId().equals(baseId);
            inv.setItem(i, GUIManager.createItem(base ? Material.LIME_CONCRETE : Material.GRASS_BLOCK,
                    (base ? "&aBase: " : "&e") + plotName(plot),
                    trList(player, "claim_merge_plot_lore", List.of(
                            "&7Bounds: &f" + plot.getX1() + "," + plot.getZ1() + " to " + plot.getX2() + "," + plot.getZ2(),
                            base ? "&aSelect an adjacent candidate." : "&eClick to select."))));
        }
        inv.setItem(48, GUIManager.createItem(Material.ARROW, tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to menu."))));
        inv.setItem(50, GUIManager.createItem(Material.BARRIER, tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv);
        plugin.effects().playMenuOpen(player);
    }

    private void openConfirm(Player player, Plot base, Plot other, String returnTo, UUID originPlotId) {
        List<UUID> ids = List.of(base.getPlotId(), other.getPlotId());
        Inventory inv = Bukkit.createInventory(
                new ClaimMergeHolder(base.getPlotId(), other.getPlotId(), ids, true, returnTo, originPlotId), 27,
                plugin.gui().title(player, "claim_merge_confirm_title", "&cConfirm Merge"));
        for (int i = 0; i < 27; i++) inv.setItem(i, GUIManager.getFiller());
        long cost = mergeCost();
        List<String> lore = new ArrayList<>(trList(player, "claim_merge_confirm_lore", List.of(
                "&7Base: &f{BASE}",
                "&7Merge in: &f{OTHER}",
                "&7Cost: &e{COST} ClaimBlocks",
                " ",
                "&eMembers, nicknames, and Guest Passes",
                "&efrom the second plot carry into the base."
        )));
        lore.replaceAll(line -> line
                .replace("{BASE}", plotName(base))
                .replace("{OTHER}", plotName(other))
                .replace("{COST}", String.valueOf(cost)));
        inv.setItem(13, GUIManager.createItem(Material.ANVIL,
                tr(player, "claim_merge_confirm_details", "&eMerge Summary"), lore));
        inv.setItem(11, GUIManager.createItem(Material.EMERALD_BLOCK,
                tr(player, "claim_merge_confirm_accept", "&aConfirm Merge"),
                trList(player, "claim_merge_confirm_accept_lore", List.of("&7Merge these claims now."))));
        inv.setItem(15, GUIManager.createItem(Material.ARROW,
                tr(player, "button_back", "&fBack"),
                trList(player, "back_lore", List.of("&7Return to plot selection."))));
        inv.setItem(22, GUIManager.createItem(Material.BARRIER,
                tr(player, "button_exit", "&cClose"),
                trList(player, "exit_lore", List.of("&7Close this menu."))));
        player.openInventory(inv);
        plugin.effects().playMenuFlip(player);
    }

    public void handleClick(Player player, InventoryClickEvent e, ClaimMergeHolder holder) {
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        if (holder.isConfirm()) {
            if (e.getRawSlot() == 15) { openSelect(player, holder.getBaseId(), holder.getReturnTo(), holder.getOriginPlotId()); return; }
            if (e.getRawSlot() == 22) { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
            if (e.getRawSlot() != 11) return;
            Plot base = find(holder.getBaseId());
            Plot other = find(holder.getCandidateId());
            if (base == null || other == null || !base.isOwner(player.getUniqueId()) || !other.isOwner(player.getUniqueId())) {
                fail(player, "claim_merge_failed_generic", "&cMerge failed: plots are no longer available.");
                openSelect(player, null, holder.getReturnTo(), holder.getOriginPlotId());
                return;
            }
            MergeCheck check = validateMerge(player, base, other);
            if (!check.ok()) {
                fail(player, check.key(), check.fallback());
                openSelect(player, holder.getBaseId(), holder.getReturnTo(), holder.getOriginPlotId());
                return;
            }
            executeMerge(player, base, other, check);
            return;
        }

        if (e.getRawSlot() == 48) {
            MarketNav.back(plugin, player, holder.getReturnTo(), MarketNav.findPlot(plugin, holder.getOriginPlotId()));
            return;
        }
        if (e.getRawSlot() == 50) { player.closeInventory(); plugin.effects().playMenuClose(player); return; }
        if (!isMergeEnabled()) {
            fail(player, "claim_merge_disabled", "&cClaim merging is disabled on this server.");
            return;
        }
        if (e.getRawSlot() < 0 || e.getRawSlot() >= holder.getPlotIds().size()) return;
        Plot selected = find(holder.getPlotIds().get(e.getRawSlot()));
        if (selected == null || !selected.isOwner(player.getUniqueId())) return;
        if (holder.getBaseId() == null) { openSelect(player, selected.getPlotId(), holder.getReturnTo(), holder.getOriginPlotId()); return; }
        Plot base = find(holder.getBaseId());
        if (base == null || base.getPlotId().equals(selected.getPlotId())) { openSelect(player, null, holder.getReturnTo(), holder.getOriginPlotId()); return; }
        MergeCheck check = validateMerge(player, base, selected);
        if (!check.ok()) {
            fail(player, check.key(), check.fallback());
            return;
        }
        openConfirm(player, base, selected, holder.getReturnTo(), holder.getOriginPlotId());
    }

    private record MergeCheck(boolean ok, String key, String fallback, ClaimMergeMath.MergeBounds bounds,
                              Map<UUID, String> rolesToCarry, Map<UUID, String> nicksToCarry,
                              Map<UUID, GuestPass> passesToCarry) {
        static MergeCheck fail(String key, String fallback) {
            return new MergeCheck(false, key, fallback, null, Map.of(), Map.of(), Map.of());
        }
        static MergeCheck ok(ClaimMergeMath.MergeBounds bounds,
                             Map<UUID, String> roles, Map<UUID, String> nicks, Map<UUID, GuestPass> passes) {
            return new MergeCheck(true, null, null, bounds, roles, nicks, passes);
        }
    }

    private MergeCheck validateMerge(Player player, Plot base, Plot other) {
        if (base.hasActiveRental() || other.hasActiveRental() || base.isForSale() || other.isForSale()
                || base.isForAuction() || other.isForAuction()) {
            return MergeCheck.fail("claim_merge_market_blocked",
                    "&cClear listings and rentals before merging.");
        }
        if (!base.getWorld().equalsIgnoreCase(other.getWorld())) {
            return MergeCheck.fail("claim_merge_not_adjacent", "&cPlots must share a side.");
        }
        ClaimMergeMath.Rect a = new ClaimMergeMath.Rect(base.getX1(), base.getZ1(), base.getX2(), base.getZ2());
        ClaimMergeMath.Rect b = new ClaimMergeMath.Rect(other.getX1(), other.getZ1(), other.getX2(), other.getZ2());
        boolean requireAlignment = plugin.getConfig().getBoolean("claims.merging.require_alignment", true);
        if (!ClaimMergeMath.adjacent(a, b)) {
            return MergeCheck.fail("claim_merge_not_adjacent", "&cPlots must share a side.");
        }
        if (!ClaimMergeMath.canMerge(a, b, requireAlignment)) {
            return MergeCheck.fail("claim_merge_alignment_required",
                    "&cPlots must be fully edge-aligned. L-shapes and partial overlaps are blocked to prevent land theft.");
        }
        ClaimMergeMath.MergeBounds bounds = ClaimMergeMath.mergedBounds(a, b);
        List<ClaimMergeMath.Rect> foreign = new ArrayList<>();
        for (Plot plot : plugin.store().getAllPlots()) {
            if (plot == null) continue;
            if (plot.getPlotId().equals(base.getPlotId()) || plot.getPlotId().equals(other.getPlotId())) continue;
            if (!plot.getWorld().equalsIgnoreCase(base.getWorld())) continue;
            foreign.add(new ClaimMergeMath.Rect(plot.getX1(), plot.getZ1(), plot.getX2(), plot.getZ2()));
        }
        if (ClaimMergeMath.foreignLandInside(bounds, foreign)) {
            return MergeCheck.fail("claim_merge_foreign_land",
                    "&cMerge blocked: the combined area would cover another claim.");
        }

        Map<UUID, String> rolesToCarry = new HashMap<>();
        Map<UUID, String> nicksToCarry = new HashMap<>();
        for (Map.Entry<UUID, String> entry : other.getPlayerRoles().entrySet()) {
            UUID id = entry.getKey();
            if (id == null || base.isOwner(id)) continue;
            String incoming = entry.getValue();
            if (incoming == null || incoming.isBlank()) continue;
            String existing = base.getPlayerRoles().get(id);
            if (existing != null && !existing.equalsIgnoreCase(incoming)
                    && !existing.equalsIgnoreCase("visitor")) {
                return MergeCheck.fail("claim_merge_role_conflict",
                        "&cMerge blocked: both plots assign different roles to the same member. Resolve roles first.");
            }
            if (existing == null || existing.equalsIgnoreCase("visitor")) {
                rolesToCarry.put(id, incoming);
            }
            String nick = other.getRoleNickname(id);
            if (nick != null && base.getRoleNickname(id) == null) nicksToCarry.put(id, nick);
        }

        Map<UUID, GuestPass> passesToCarry = new HashMap<>();
        for (GuestPass pass : other.getGuestPasses().values()) {
            if (pass == null || pass.getPlayerId() == null) continue;
            if (base.getActiveGuestPass(pass.getPlayerId()) != null) {
                return MergeCheck.fail("claim_merge_guest_conflict",
                        "&cMerge blocked: both plots have Guest Passes for the same player. Revoke one first.");
            }
            passesToCarry.put(pass.getPlayerId(), pass);
        }

        int projectedMembers = base.countTrustedMembers() + rolesToCarry.size();
        if (projectedMembers > base.getMaxMembers()) {
            return MergeCheck.fail("claim_merge_member_cap",
                    "&cMerge blocked: combining members would exceed this plot's member limit.");
        }

        long cost = mergeCost();
        ClaimBlockManager blocks = plugin.getClaimBlockManager();
        if (cost > 0 && (blocks == null || blocks.getAvailableBlocks(player.getUniqueId()) < cost)) {
            return MergeCheck.fail("claim_merge_insufficient_blocks",
                    "&cYou need &e{COST} &cClaimBlocks to merge."
                            .replace("{COST}", String.valueOf(cost)));
        }
        return MergeCheck.ok(bounds, rolesToCarry, nicksToCarry, passesToCarry);
    }

    private void executeMerge(Player player, Plot base, Plot other, MergeCheck check) {
        long cost = mergeCost();
        ClaimBlockManager blocks = plugin.getClaimBlockManager();
        if (plugin.snapshots() != null) {
            plugin.snapshots().createSnapshot(base, ClaimSnapshot.SnapshotType.PRE_MERGE,
                    "Before player merge", player.getUniqueId());
            plugin.snapshots().createSnapshot(other, ClaimSnapshot.SnapshotType.PRE_MERGE,
                    "Before player merge", player.getUniqueId());
        }
        List<Zone> zones = new ArrayList<>(other.getZones());
        try {
            other.getZones().clear();
            base.getZones().addAll(zones);
            for (Map.Entry<UUID, String> entry : check.rolesToCarry().entrySet()) {
                base.setRole(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<UUID, String> entry : check.nicksToCarry().entrySet()) {
                base.setRoleNickname(entry.getKey(), entry.getValue());
            }
            for (GuestPass pass : check.passesToCarry().values()) {
                base.addGuestPass(pass);
            }
            other.getPlayerRoles().clear();
            other.getRoleNicknames().clear();
            other.getGuestPasses().clear();
            plugin.store().removePlot(other.getOwner(), other.getPlotId());
            plugin.store().updatePlotBounds(base, check.bounds().x1(), check.bounds().z1(),
                    check.bounds().x2(), check.bounds().z2());
            if (cost > 0 && blocks != null) blocks.adjustAvailableBlocks(player.getUniqueId(), -cost);
            if (plugin.getMapHooks() != null) plugin.getMapHooks().reload();
            plugin.territoryLife().logKey(base.getPlotId(), player.getUniqueId(), "CLAIM_MERGE",
                    "activity_detail_claim_merge",
                    "Merged plot " + other.getPlotId() + " into " + base.getPlotId(),
                    java.util.Map.of("OTHER", String.valueOf(other.getPlotId()), "BASE", String.valueOf(base.getPlotId())));
            player.sendMessage(GUIManager.color(tr(player, "claim_merge_success",
                    "&aClaims merged successfully.")));
            plugin.effects().playConfirm(player);
            openSelect(player, null);
        } catch (Throwable t) {
            base.getZones().removeAll(zones);
            other.getZones().addAll(zones);
            plugin.store().addPlot(other);
            plugin.store().savePlotSync(other);
            fail(player, "claim_merge_failed_rollback",
                    "&cMerge failed and was rolled back: &7{ERROR}"
                            .replace("{ERROR}", t.getMessage() == null ? "unknown error" : t.getMessage()));
        }
    }

    private void fail(Player player, String key, String fallback) {
        player.sendMessage(GUIManager.color(tr(player, key, fallback)));
        plugin.effects().playError(player);
    }

    private boolean isMergeEnabled() {
        return plugin.getConfig().getBoolean("claims.merging.enabled", false);
    }

    private long mergeCost() {
        return Math.max(0L, plugin.getConfig().getLong("claims.merging.cost",
                (long) plugin.getConfig().getDouble("claims.merging.cost", 0.0D)));
    }

    private Plot find(UUID id) {
        if (id == null) return null;
        return plugin.store().getAllPlots().stream()
                .filter(p -> p != null && id.equals(p.getPlotId())).findFirst().orElse(null);
    }

    private String plotName(Plot p) {
        return p.getPlotName() == null || p.getPlotName().isBlank() ? "Plot" : p.getPlotName();
    }

    private String tr(Player p, String k, String f) { return plugin.gui().tr(p, k, f); }
    private List<String> trList(Player p, String k, List<String> f) { return plugin.gui().trList(p, k, f); }
}
