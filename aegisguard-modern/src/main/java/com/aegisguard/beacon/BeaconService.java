package com.aegisguard.beacon;

import com.aegisguard.AegisGuard;
import com.aegisguard.config.Modules;
import com.aegisguard.data.Plot;
import com.aegisguard.travel.SafeTravelResult;
import com.aegisguard.travel.SafeTravelService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BeaconService {

    private final AegisGuard plugin;
    private final BeaconStore store;
    private final BeaconCharges charges;
    private final Map<UUID, Long> lastPromptAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUseAt = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingRename = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPadGiveAt = new ConcurrentHashMap<>();

    public BeaconService(AegisGuard plugin) {
        this.plugin = plugin;
        this.store = new BeaconStore(plugin);
        this.charges = new BeaconCharges(plugin);
    }

    public BeaconStore store() { return store; }
    public BeaconCharges charges() { return charges; }

    public boolean isEnabled() {
        try {
            return plugin.modules().on(Modules.Id.TELEPORT_BEACONS);
        } catch (Throwable ignored) {
            return plugin.getConfig().getBoolean("teleport_beacons.enabled", true);
        }
    }

    public void load() { store.load(); }
    public void save() { store.save(); }
    public boolean isDirty() { return store.isDirty(); }

    public int maxFor(Plot plot) {
        if (plot != null && plot.isServerZone()) {
            return Math.max(1, plugin.getConfig().getInt("teleport_beacons.max_per_server_zone", 8));
        }
        return Math.max(1, plugin.getConfig().getInt("teleport_beacons.max_per_plot", 3));
    }

    public double standRadius() {
        return Math.max(1.0D, plugin.getConfig().getDouble("teleport_beacons.stand_radius", 1.6D));
    }

    public boolean isPadMaterial(Material material) {
        if (material == null || !material.isBlock()) return false;
        List<String> allowed = plugin.getConfig().getStringList("teleport_beacons.pad_materials");
        if (allowed == null || allowed.isEmpty()) {
            return material == Material.LODESTONE
                    || material == Material.END_PORTAL_FRAME
                    || material == Material.BEACON
                    || material == Material.CRYING_OBSIDIAN
                    || material == Material.SEA_LANTERN
                    || material == Material.GOLD_BLOCK;
        }
        String name = material.name();
        for (String raw : allowed) {
            if (raw != null && name.equalsIgnoreCase(raw.trim())) return true;
        }
        return false;
    }

    public Material starterPadMaterial() {
        Material mat = Material.matchMaterial(plugin.getConfig().getString("teleport_beacons.starter_pad_material", "LODESTONE"));
        if (mat == null || !mat.isItem() || !isPadMaterial(mat)) return Material.LODESTONE;
        return mat;
    }

    /**
     * Hands the player placeable pad blocks. They can also use any allowed pad
     * they already have — this is just a convenient starter kit.
     */
    public boolean giveStarterPads(Player player) {
        if (player == null || !isEnabled()) return false;
        if (!plugin.getConfig().getBoolean("teleport_beacons.give_starter_pads", true)) {
            send(player, "beacon_give_disabled", "&cStarter pads are disabled on this server.");
            return false;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || !plot.canManage(player, plugin)) {
            send(player, "beacon_give_need_plot", "&cStand in a claim you manage to get starter pads.");
            if (plugin.effects() != null) plugin.effects().playError(player);
            return false;
        }
        int cooldown = Math.max(0, plugin.getConfig().getInt("teleport_beacons.give_cooldown_seconds", 120));
        if (cooldown > 0) {
            Long last = lastPadGiveAt.get(player.getUniqueId());
            if (last != null) {
                long wait = (cooldown * 1000L) - (System.currentTimeMillis() - last);
                if (wait > 0) {
                    send(player, "beacon_give_cooldown",
                            "&eYou can request more pads in &f{SECONDS}&e second(s).",
                            Map.of("SECONDS", String.valueOf(Math.max(1L, wait / 1000L))));
                    return false;
                }
            }
        }
        int remaining = Math.max(0, maxFor(plot) - store.forPlot(plot.getPlotId()).size());
        if (remaining <= 0) {
            send(player, "beacon_at_cap", "&cThis plot already has the maximum number of beacons.");
            return false;
        }
        int amount = Math.max(1, plugin.getConfig().getInt("teleport_beacons.starter_pad_amount", 2));
        amount = Math.min(amount, remaining);
        Material mat = starterPadMaterial();
        ItemStack stack = new ItemStack(mat, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = "&bAegis Beacon Pad";
            List<String> lore = List.of("&7Place this on your claim,", "&7then sneak-right-click to bind.");
            try {
                if (plugin.gui() != null) {
                    name = plugin.gui().tr(player, "beacon_item_name", name);
                    lore = plugin.gui().trList(player, "beacon_item_lore", lore);
                }
            } catch (Throwable ignored) {}
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
            List<String> colored = new ArrayList<>();
            for (String line : lore) colored.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
            meta.setLore(colored);
            stack.setItemMeta(meta);
        }
        var leftover = player.getInventory().addItem(stack);
        leftover.values().forEach(drop -> player.getWorld().dropItemNaturally(player.getLocation(), drop));
        lastPadGiveAt.put(player.getUniqueId(), System.currentTimeMillis());
        send(player, "beacon_give_ok",
                "&aReceived &f{COUNT}&a pad(s). Place them, then sneak-right-click to bind. Any allowed pad block also works.",
                Map.of("COUNT", String.valueOf(amount)));
        if (plugin.effects() != null) plugin.effects().playConfirm(player);
        return true;
    }

    public TeleportBeacon getAt(Location loc) {
        if (loc == null) return null;
        return store.atBlock(loc.getWorld() == null ? null : loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public TeleportBeacon nearest(Location loc) {
        if (loc == null) return null;
        TeleportBeacon best = null;
        double bestDist = standRadius();
        for (TeleportBeacon beacon : store.all()) {
            if (beacon != null && beacon.isNear(loc, bestDist)) {
                best = beacon;
                Location stand = beacon.toStandLocation();
                if (stand != null) bestDist = Math.min(bestDist, loc.distance(stand));
            }
        }
        return best;
    }

    public TeleportBeacon create(Player player, Plot plot, Block block) {
        if (player == null || plot == null || block == null) return null;
        if (store.forPlot(plot.getPlotId()).size() >= maxFor(plot)) return null;
        TeleportBeacon beacon = new TeleportBeacon(UUID.randomUUID());
        beacon.setPlotId(plot.getPlotId());
        beacon.setBlock(block.getLocation().add(0.5, 0, 0.5));
        beacon.setCoordinates(block.getX(), block.getY(), block.getZ(), player.getLocation().getYaw(), 0f);
        beacon.setPadMaterial(block.getType());
        beacon.setName(player.getName() + " Beacon");
        beacon.applyPreset(TeleportBeacon.Preset.PRIVATE);
        store.put(beacon);
        return beacon;
    }

    public boolean link(TeleportBeacon origin, UUID destId) {
        if (origin == null || destId == null || destId.equals(origin.getId())) return false;
        TeleportBeacon dest = store.get(destId);
        if (dest == null || !dest.isEnabled()) return false;
        origin.setLinkedBeaconId(destId);
        store.put(origin);
        return true;
    }

    public void removeForPlot(UUID plotId) {
        if (plotId == null) return;
        for (TeleportBeacon beacon : store.forPlot(plotId)) {
            if (beacon != null) store.remove(beacon.getId());
        }
    }

    public boolean destinationReady(TeleportBeacon dest) {
        if (dest == null || !dest.isEnabled()) return false;
        Location blockLoc = dest.toBlockLocation();
        if (blockLoc == null || blockLoc.getWorld() == null) return false;
        try {
            Material type = blockLoc.getBlock().getType();
            if (type.isAir()) return false;
            return isPadMaterial(type) || type == dest.getPadMaterial();
        } catch (Throwable foliaRegion) {
            // Distant regions on Folia cannot be read here; Safe Travel still
            // resolves a standable spot at teleport time.
            return true;
        }
    }

    /** Suppresses stand-to-travel prompts right after a successful hop. */
    public boolean recentlyTraveled(Player player) {
        if (player == null) return false;
        Long last = lastUseAt.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last < 4000L;
    }

    public boolean canManage(Player player, TeleportBeacon beacon) {
        if (player == null || beacon == null) return false;
        if (plugin.isAdmin(player)) return true;
        Plot plot = plugin.store().getPlotById(beacon.getPlotId());
        return plot != null && plot.canManage(player, plugin);
    }

    public boolean canDepart(Player player, TeleportBeacon origin) {
        if (player == null || origin == null || !origin.isEnabled()) return false;
        if (plugin.isAdmin(player) || plugin.isBypassing(player)) return true;
        if (origin.isStaffOnly()) return plugin.isAdmin(player);
        Plot plot = plugin.store().getPlotById(origin.getPlotId());
        if (plot == null) return false;
        UUID id = player.getUniqueId();
        if (plot.isBanned(id)) return false;
        if (origin.isOwners() && plot.isOwner(id)) return true;
        if (origin.isMembers() && hasMemberRole(plot, id)) return true;
        if (origin.isTrusted() && plot.isTrusted(player)) return true;
        if (origin.isGuests() && plot.getActiveGuestPass(id) != null) return true;
        if (origin.isAlliance() && plot.allowsAllianceEntry(id, plugin)) return true;
        return origin.isPublicAccess();
    }

    private boolean hasMemberRole(Plot plot, UUID id) {
        if (plot.isOwner(id)) return true;
        String role = plot.getRole(id);
        return role != null && !role.isBlank() && !role.equalsIgnoreCase("visitor");
    }

    public @Nullable TeleportBeacon resolvePublicArrival(Plot plot, TeleportBeacon.Purpose preferred) {
        if (plot == null) return null;
        List<TeleportBeacon> publicPads = new ArrayList<>();
        for (TeleportBeacon beacon : store.forPlot(plot.getPlotId())) {
            if (beacon != null && beacon.isEnabled() && beacon.isPublicAccess() && !beacon.isStaffOnly()) {
                publicPads.add(beacon);
            }
        }
        if (publicPads.isEmpty()) return null;
        if (preferred != null) {
            for (TeleportBeacon beacon : publicPads) {
                if (beacon.getPurpose() == preferred) return beacon;
            }
            if (preferred == TeleportBeacon.Purpose.MARKET) {
                for (TeleportBeacon beacon : publicPads) {
                    if (beacon.getPurpose() == TeleportBeacon.Purpose.SHOP) return beacon;
                }
            }
        }
        for (TeleportBeacon beacon : publicPads) {
            if (beacon.getPurpose() == TeleportBeacon.Purpose.SPAWN) return beacon;
        }
        return publicPads.get(0);
    }

    public boolean shouldPrompt(Player player) {
        if (player == null) return false;
        long now = System.currentTimeMillis();
        Long last = lastPromptAt.get(player.getUniqueId());
        if (last != null && now - last < 2500L) return false;
        lastPromptAt.put(player.getUniqueId(), now);
        return true;
    }

    public boolean hasPendingRename(Player player) {
        return player != null && pendingRename.containsKey(player.getUniqueId());
    }

    public void beginRename(Player player, TeleportBeacon beacon) {
        if (player == null || beacon == null) return;
        pendingRename.put(player.getUniqueId(), beacon.getId());
        send(player, "beacon_rename_prompt", "&eType a new name in chat, or type &fcancel&e.");
    }

    public boolean handleRenameChat(Player player, String message) {
        UUID beaconId = pendingRename.remove(player.getUniqueId());
        if (beaconId == null) return false;
        TeleportBeacon beacon = store.get(beaconId);
        if (beacon == null || !canManage(player, beacon)) return true;
        if (message == null || message.isBlank() || message.equalsIgnoreCase("cancel")) {
            send(player, "beacon_rename_cancelled", "&eRename cancelled.");
            return true;
        }
        String cleaned = sanitizeName(message);
        if (cleaned.isBlank()) {
            send(player, "beacon_rename_cancelled", "&eRename cancelled.");
            return true;
        }
        beacon.setName(cleaned);
        store.put(beacon);
        send(player, "beacon_renamed", "&aBeacon renamed to &f{NAME}&a.", Map.of("NAME", beacon.getName()));
        return true;
    }

    private String sanitizeName(String raw) {
        String colored = org.bukkit.ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
        String plain = org.bukkit.ChatColor.stripColor(colored).replace('\n', ' ').replace('\r', ' ').trim();
        if (plain.length() > 32) plain = plain.substring(0, 32);
        return plain;
    }

    /**
     * Listing / Visit landing: teleport onto the public arrival pad itself.
     * @return true if the modern path handled the request (success or refusal).
     */
    public boolean handlePublicListingTravel(Player player, Plot plot, TeleportBeacon.Purpose preferred) {
        if (!isEnabled() || player == null || plot == null) return false;
        TeleportBeacon arrival = resolvePublicArrival(plot, preferred);
        if (arrival == null) {
            send(player, "beacon_no_public_arrival",
                    "&cThis place has no public arrival beacon. Travel was cancelled.");
            if (plugin.effects() != null) plugin.effects().playError(player);
            return true;
        }
        openArrivalConfirm(player, arrival);
        return true;
    }

    public void openPadConfirm(Player player, TeleportBeacon origin) {
        if (player == null || origin == null) return;
        TeleportBeacon dest = origin.getLinkedBeaconId() == null ? null : store.get(origin.getLinkedBeaconId());
        if (dest == null || !dest.isEnabled() || !destinationReady(dest)) {
            send(player, "beacon_not_linked", "&eThis beacon is not linked yet.");
            if (plugin.effects() != null) plugin.effects().playError(player);
            return;
        }
        if (!canDepart(player, origin)) {
            send(player, "beacon_denied", "&cYou are not allowed to use this beacon.");
            if (plugin.effects() != null) plugin.effects().playError(player);
            return;
        }
        if (origin.isRequireConfirm() && plugin.gui() != null && plugin.gui().beacons() != null) {
            plugin.gui().beacons().openConfirm(player, origin, dest, false);
            return;
        }
        executeTrip(player, origin, dest, false);
    }

    public void openArrivalConfirm(Player player, TeleportBeacon arrival) {
        if (player == null || arrival == null) return;
        if (arrival.isRequireConfirm() && plugin.gui() != null && plugin.gui().beacons() != null) {
            plugin.gui().beacons().openConfirm(player, null, arrival, true);
            return;
        }
        executeTrip(player, null, arrival, true);
    }

    public void executeTrip(Player player, @Nullable TeleportBeacon origin, TeleportBeacon dest, boolean listingArrival) {
        if (player == null || dest == null) return;
        if (!dest.isEnabled() || !destinationReady(dest)) {
            send(player, "beacon_pad_gone", "&cThe destination pad is missing or broken.");
            if (plugin.effects() != null) plugin.effects().playError(player);
            return;
        }
        if (origin != null && origin.getId().equals(dest.getId())) {
            send(player, "beacon_not_linked", "&eThis beacon is not linked yet.");
            return;
        }
        Plot destPlot = plugin.store().getPlotById(dest.getPlotId());
        if (destPlot == null) {
            send(player, "beacon_dest_missing", "&cThe destination plot is gone.");
            return;
        }
        if (destPlot.isLockdownActive() && !plugin.isAdmin(player) && !destPlot.canManage(player, plugin)) {
            send(player, "beacon_lockdown", "&cThat plot is in lockdown.");
            if (plugin.effects() != null) plugin.effects().playError(player);
            return;
        }
        if (plugin.protection() != null && !plugin.protection().canEnterPlot(player, destPlot)) {
            send(player, "beacon_cannot_enter", "&cYou cannot enter that plot.");
            if (plugin.effects() != null) plugin.effects().playError(player);
            return;
        }
        if (origin != null && !canDepart(player, origin)) {
            send(player, "beacon_denied", "&cYou are not allowed to use this beacon.");
            return;
        }
        if (listingArrival && !dest.isPublicAccess() && !plugin.isAdmin(player)) {
            send(player, "beacon_denied", "&cYou are not allowed to use this beacon.");
            return;
        }
        if (origin != null && !origin.isAllowCombat() && plugin.safeTravel() != null && plugin.safeTravel().isInCombat(player.getUniqueId())) {
            send(player, "travel_fail_combat", "&cYou cannot travel while in combat.");
            return;
        }
        int extra = origin != null ? origin.getExtraCooldownSeconds() : dest.getExtraCooldownSeconds();
        if (extra > 0) {
            Long last = lastUseAt.get(player.getUniqueId());
            if (last != null) {
                long wait = (extra * 1000L) - (System.currentTimeMillis() - last);
                if (wait > 0) {
                    send(player, "travel_fail_cooldown",
                            "&cTravel is cooling down. Try again in &e{SECONDS}&c second(s).",
                            Map.of("SECONDS", String.valueOf(Math.max(1L, wait / 1000L))));
                    return;
                }
            }
        }
        TeleportBeacon billed = origin != null ? origin : dest;
        BeaconCharges.TripCost cost = charges.resolve(player, billed);
        if (!charges.charge(player, cost)) {
            send(player, "beacon_cannot_pay", "&cYou cannot afford this beacon trip.");
            if (plugin.effects() != null) plugin.effects().playError(player);
            return;
        }

        Location destLoc = dest.toStandLocation();
        if (destLoc == null || destLoc.getWorld() == null) {
            charges.refund(player, cost);
            send(player, "beacon_dest_unloaded", "&cThe destination world is not loaded.");
            return;
        }
        if (plugin.safeTravel() == null) {
            charges.refund(player, cost);
            send(player, "beacon_unavailable", "&cTeleport Beacons are unavailable.");
            return;
        }

        player.closeInventory();
        SafeTravelResult result = plugin.safeTravel().travel(player, destLoc, SafeTravelService.Kind.BEACON);
        if (!result.isSuccess()) {
            charges.refund(player, cost);
            return;
        }
        lastUseAt.put(player.getUniqueId(), System.currentTimeMillis());
        result.teleportFuture().whenComplete((ok, error) -> {
            Runnable finish = () -> {
                if (error != null || !Boolean.TRUE.equals(ok)) {
                    charges.refund(player, cost);
                    send(player, "beacon_travel_failed", "&cTeleport failed. You were not charged.");
                    if (plugin.effects() != null) plugin.effects().playError(player);
                    return;
                }
                charges.payoutOwner(cost);
                plugin.safeTravel().recordRecentDestination(player.getUniqueId(), destPlot.getPlotId());
                flash(player, destLoc);
                send(player, "beacon_arrived", "&aArrived at &f{NAME}&a.", Map.of("NAME", dest.getName()));
                if (!cost.isFree() && cost.payOwner() != null) {
                    send(player, "beacon_paid_owner",
                            "&7Maintenance fee paid: &f{VAULT} &7/ &a{BLOCKS} ClaimBlocks.",
                            Map.of("VAULT", charges.vaultLabel(cost.vault()),
                                    "BLOCKS", String.valueOf(cost.claimBlocks())));
                }
                if (plugin.effects() != null) plugin.effects().playConfirm(player);
            };
            if (plugin.scheduler() != null) {
                plugin.scheduler().runEntity(player, finish, finish);
            } else {
                finish.run();
            }
        });
    }

    private void flash(Player player, Location dest) {
        try {
            Location here = player.getLocation();
            Runnable originFx = () -> {
                if (here.getWorld() != null) {
                    here.getWorld().spawnParticle(Particle.PORTAL, here.clone().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.4);
                }
            };
            Runnable destFx = () -> {
                if (dest.getWorld() != null) {
                    dest.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 50, 0.5, 0.7, 0.5, 0.5);
                }
            };
            if (plugin.scheduler() != null) {
                plugin.scheduler().runAt(here, originFx);
                plugin.scheduler().runAt(dest, destFx);
            } else {
                originFx.run();
                destFx.run();
            }
        } catch (Throwable ignored) {}
    }

    public void send(Player player, String key, String fallback) {
        send(player, key, fallback, Map.of());
    }

    public void send(Player player, String key, String fallback, Map<String, String> vars) {
        if (player == null) return;
        String msg = null;
        try {
            if (plugin.msg() != null) {
                msg = plugin.msg().get(player, key, vars);
            }
        } catch (Throwable ignored) {}
        if (msg == null || msg.isBlank() || msg.equalsIgnoreCase(key) || msg.contains("[Missing")) {
            msg = fallback;
            for (var e : vars.entrySet()) msg = msg.replace("{" + e.getKey() + "}", e.getValue());
            msg = org.bukkit.ChatColor.translateAlternateColorCodes('&', msg);
            String prefix = "&8[&bAegisGuard&8]&r ";
            try {
                if (plugin.msg() != null) {
                    String px = plugin.msg().get(player, "prefix");
                    if (px != null && !px.isBlank() && !px.equalsIgnoreCase("prefix")) prefix = px;
                }
            } catch (Throwable ignored) {}
            player.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix) + msg);
            return;
        }
        try {
            plugin.msg().send(player, key, vars);
        } catch (Throwable ignored) {
            player.sendMessage(msg);
        }
    }

    public void cyclePurpose(TeleportBeacon beacon) {
        TeleportBeacon.Purpose[] all = TeleportBeacon.Purpose.values();
        int idx = 0;
        for (int i = 0; i < all.length; i++) {
            if (all[i] == beacon.getPurpose()) { idx = i; break; }
        }
        beacon.setPurpose(all[(idx + 1) % all.length]);
        store.put(beacon);
    }
}
