package com.aegisguard.horizons;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.gui.GUIManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HorizonService implements Listener {
    private static final long COMBAT_LOCK_MILLIS = 15_000L;
    private static final long HEART_COOLDOWN_MILLIS = 30L * 60L * 1000L;

    private final AegisGuard plugin;
    private final NamespacedKey sigilKey;
    private final NamespacedKey plotKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey rankKey;
    private final File rewardFile;
    private final Map<String, Long> visitRewards = new ConcurrentHashMap<>();
    private final Set<String> likeRewards = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> recentCombat = new ConcurrentHashMap<>();
    private final Map<UUID, Long> heartCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pulseCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> appliedClimate = new ConcurrentHashMap<>();

    public HorizonService(AegisGuard plugin) {
        this.plugin = plugin;
        this.sigilKey = new NamespacedKey(plugin, "horizon_sigil");
        this.plotKey = new NamespacedKey(plugin, "horizon_plot");
        this.ownerKey = new NamespacedKey(plugin, "horizon_owner");
        this.rankKey = new NamespacedKey(plugin, "horizon_rank");
        this.rewardFile = new File(plugin.getDataFolder(), "horizon-rewards.yml");
        loadRewardLedger();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("expansions.horizons.enabled", true);
    }

    public int unlockLevel() {
        return Math.max(1, plugin.getConfig().getInt("expansions.horizons.unlock_level", 30));
    }

    public long requiredRenown(HorizonRank rank) {
        if (rank == null) return Long.MAX_VALUE;
        return Math.max(1L, plugin.getConfig().getLong(
                "expansions.horizons.ranks." + rank.index() + ".required_renown", rank.defaultRenown()));
    }

    public int radiusGain(HorizonRank rank) {
        if (rank == null) return 0;
        return Math.max(1, plugin.getConfig().getInt(
                "expansions.horizons.ranks." + rank.index() + ".radius_gain", rank.defaultRadiusGain()));
    }

    public int maximumRadius() {
        return Math.max(1, plugin.getConfig().getInt("expansions.horizons.max_radius_global", 750));
    }

    public boolean isNextHorizonExpansion(Plot plot, int oldRadius, int newRadius) {
        if (plot == null || plot.getHorizonExpansionRank() >= plot.getHorizonRank()) return false;
        HorizonRank next = HorizonRank.byIndex(plot.getHorizonExpansionRank() + 1);
        return next != null && newRadius - oldRadius == radiusGain(next);
    }

    public Material material(HorizonRank rank) {
        if (rank == null) return Material.BARRIER;
        String configured = plugin.getConfig().getString(
                "expansions.horizons.ranks." + rank.index() + ".material", rank.defaultMaterial().name());
        Material material = Material.matchMaterial(configured == null ? "" : configured);
        return material == null || material.isAir() ? rank.defaultMaterial() : material;
    }

    public HorizonRank nextRank(Plot plot) {
        return plot == null ? null : HorizonRank.byIndex(plot.getHorizonRank() + 1);
    }

    public boolean canClaimSigil(Plot plot, HorizonRank rank) {
        return enabled() && plot != null && rank != null
                && plot.getLevel() >= unlockLevel()
                && rank.index() == plot.getHorizonRank() + 1
                && plot.getHorizonRenown() >= requiredRenown(rank);
    }

    public boolean issueSigil(Player player, Plot plot, HorizonRank rank) {
        if (player == null || plot == null || !player.getUniqueId().equals(plot.getOwner())
                || !canClaimSigil(plot, rank) || !isExpansionAvailable(player, plot, rank)) {
            if (player != null) {
                send(player, "horizon_sigil_not_ready", "&cThis territory is not ready to awaken that Horizon Rank.", Map.of());
                plugin.effects().playError(player);
            }
            return false;
        }
        if (findSigil(player, plot.getPlotId(), rank.index()) != null) {
            send(player, "horizon_sigil_already_held", "&eYou already carry this plot's next Horizon Sigil.", Map.of());
            plugin.effects().playError(player);
            return false;
        }
        if (player.getInventory().firstEmpty() < 0) {
            send(player, "horizon_sigil_inventory_full", "&cMake one empty inventory slot before claiming this Sigil.", Map.of());
            plugin.effects().playError(player);
            return false;
        }

        ItemStack sigil = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = sigil.getItemMeta();
        if (meta == null) return false;
        String rankName = rankName(player, rank);
        meta.setDisplayName(color(tr(player, "horizon_sigil_name", "&d&lHorizon Sigil: &f{RANK}")
                .replace("{RANK}", rankName)));
        meta.setLore(color(List.of(
                tr(player, "horizon_sigil_lore_1", "&7Bound to: &f{PLOT}").replace("{PLOT}", displayName(plot)),
                tr(player, "horizon_sigil_lore_2", "&7Bearer: &f{PLAYER}").replace("{PLAYER}", player.getName()),
                " ",
                tr(player, "horizon_sigil_lore_3", "&dRight-click inside the bound plot"),
                tr(player, "horizon_sigil_lore_4", "&dto awaken its next Horizon Rank."),
                " ",
                tr(player, "horizon_sigil_lore_bound", "&8Soulbound • Cannot be traded")
        )));
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(sigilKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(plotKey, PersistentDataType.STRING, plot.getPlotId().toString());
        pdc.set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(rankKey, PersistentDataType.INTEGER, rank.index());
        sigil.setItemMeta(meta);
        player.getInventory().addItem(sigil);
        send(player, "horizon_sigil_granted", "&dA {RANK} Sigil has answered your territory's call. Activate it within your plot.",
                Map.of("RANK", rankName));
        plugin.effects().playConfirm(player);
        return true;
    }

    public String rankName(Player player, HorizonRank rank) {
        if (rank == null) return tr(player, "horizon_rank_unawakened", "Unawakened");
        return tr(player, "horizon_rank_" + rank.key(), switch (rank) {
            case DAWNREACH -> "Dawnreach";
            case SKYBOUND -> "Skybound";
            case REALMFORGE -> "Realmforge";
            case STARWARD -> "Starward Dominion";
            case ETERNAL_AEGIS -> "Eternal Aegis";
        });
    }

    public void recordExpansion(Plot plot, long addedBlocks) {
        if (!isEligiblePlot(plot) || addedBlocks <= 0L) return;
        double rate = Math.max(0.0D, plugin.getConfig().getDouble("expansions.horizons.renown.expansion_per_block", 0.05D));
        long cap = Math.max(0L, plugin.getConfig().getLong("expansions.horizons.renown.expansion_cap", 1_500L));
        addRenown(plot, Math.min(cap, Math.max(1L, Math.round(addedBlocks * rate))));
    }

    public void recordVisit(Plot plot, UUID visitorId) {
        if (!isEligiblePlot(plot) || visitorId == null || plot.getOwner().equals(visitorId)
                || plot.getPlayerRoles().containsKey(visitorId)) return;
        long cooldownDays = Math.max(1L, plugin.getConfig().getLong("expansions.horizons.renown.unique_visit_cooldown_days", 7L));
        String key = plot.getPlotId() + ":" + visitorId;
        long now = System.currentTimeMillis();
        if (now - visitRewards.getOrDefault(key, 0L) < cooldownDays * 86_400_000L) return;
        visitRewards.put(key, now);
        addRenown(plot, Math.max(0L, plugin.getConfig().getLong("expansions.horizons.renown.unique_visit", 15L)));
        saveRewardLedger();
    }

    public void recordLike(Plot plot, UUID playerId) {
        if (!isEligiblePlot(plot) || playerId == null || plot.getOwner().equals(playerId)) return;
        String key = plot.getPlotId() + ":" + playerId;
        if (!likeRewards.add(key)) return;
        addRenown(plot, Math.max(0L, plugin.getConfig().getLong("expansions.horizons.renown.unique_like", 75L)));
        saveRewardLedger();
    }

    public String cycleClimate(Player player, Plot plot) {
        if (plot == null || plot.getHorizonRank() < 2 || !plot.getOwner().equals(player.getUniqueId())) return "NATURAL";
        List<String> climates = List.of("NATURAL", "CLEAR", "RAIN", "SUNRISE", "SUNSET", "NIGHT");
        int current = climates.indexOf(plot.getHorizonClimate());
        String next = climates.get((Math.max(0, current) + 1) % climates.size());
        plot.setHorizonClimate(next);
        plugin.store().savePlot(plot);
        applyClimate(player, plot);
        return next;
    }

    public void territoryPulse(Player player, Plot plot) {
        if (player == null || plot == null || plot.getHorizonRank() < 4 || !plot.getOwner().equals(player.getUniqueId())) return;
        long cooldown = Math.max(1L, plugin.getConfig().getLong("expansions.horizons.pulse_cooldown_seconds", 300L)) * 1000L;
        long now = System.currentTimeMillis();
        long remaining = pulseCooldowns.getOrDefault(player.getUniqueId(), 0L) - now;
        if (remaining > 0L) {
            send(player, "horizon_pulse_cooldown", "&eThe Starward Pulse will answer again in {SECONDS} seconds.",
                    Map.of("SECONDS", String.valueOf((remaining + 999L) / 1000L)));
            plugin.effects().playError(player);
            return;
        }
        pulseCooldowns.put(player.getUniqueId(), now + cooldown);
        ceremony(player, plot, HorizonRank.STARWARD, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSigilUse(PlayerInteractEvent event) {
        org.bukkit.event.block.Action action = event.getAction();
        if (action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                && action != org.bukkit.event.block.Action.LEFT_CLICK_AIR
                && action != org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        SigilData data = readSigil(item);
        if (data == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!data.ownerId().equals(player.getUniqueId())) {
            send(player, "horizon_sigil_wrong_bearer", "&cThis Sigil does not answer to you.", Map.of());
            plugin.effects().playError(player);
            return;
        }
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        HorizonRank rank = HorizonRank.byIndex(data.rank());
        if (plot == null || !plot.getPlotId().equals(data.plotId()) || !plot.getOwner().equals(player.getUniqueId())) {
            send(player, "horizon_sigil_wrong_plot", "&cThis Sigil must be awakened inside its bound plot.", Map.of());
            plugin.effects().playError(player);
            return;
        }
        if (!canClaimSigil(plot, rank) || !isExpansionAvailable(player, plot, rank)) {
            send(player, "horizon_sigil_not_ready", "&cThis territory is not ready to awaken that Horizon Rank.", Map.of());
            plugin.effects().playError(player);
            return;
        }

        plot.setHorizonRank(rank.index());
        unlockRankDefaults(plot, rank);
        plugin.store().savePlotSync(plot);
        consumeOne(item, player);
        ceremony(player, plot, rank, true);
        requestRankExpansion(player, plot, rank);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (readSigil(event.getItemDrop().getItemStack()) == null) return;
        event.setCancelled(true);
        send(event.getPlayer(), "horizon_sigil_soulbound", "&cA bound Horizon Sigil cannot be dropped or traded.", Map.of());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(item -> readSigil(item) != null);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        boolean currentSigil = readSigil(event.getCurrentItem()) != null;
        boolean cursorSigil = readSigil(event.getCursor()) != null;
        if (!currentSigil && !cursorSigil) return;
        boolean outsidePlayerInventory = event.getClickedInventory() != null
                && !event.getClickedInventory().equals(player.getInventory());
        if (outsidePlayerInventory || event.isShiftClick()) {
            event.setCancelled(true);
            send(player, "horizon_sigil_soulbound", "&cA bound Horizon Sigil cannot be dropped or traded.", Map.of());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || readSigil(event.getOldCursor()) == null) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
            send(player, "horizon_sigil_soulbound", "&cA bound Horizon Sigil cannot be dropped or traded.", Map.of());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && event.getCause() != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return;
        Plot destination = plugin.store().getPlotAt(event.getTo());
        if (destination == null || destination.getHorizonRank() < 3
                || !destination.getFlag("horizon-ender-seal", true)) return;
        if (!destination.hasPermission(event.getPlayer().getUniqueId(), "INTERACT", plugin)) {
            event.setCancelled(true);
            send(event.getPlayer(), "horizon_ender_seal_denied", "&cAn Ender Seal rejects entry into this territory.", Map.of());
            plugin.effects().playError(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onProjectileDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target) || !(event.getDamager() instanceof Projectile projectile)) return;
        Plot plot = plugin.store().getPlotAt(target.getLocation());
        if (plot == null || plot.getHorizonRank() < 3 || !plot.getFlag("horizon-projectile-veil", true)) return;
        ProjectileSource source = projectile.getShooter();
        if (source instanceof Player shooter && plot.hasPermission(shooter.getUniqueId(), "INTERACT", plugin)) return;
        event.setCancelled(true);
        projectile.remove();
        target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1, 0), 18, 0.5, 0.6, 0.5, 0.05);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPhantomTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Phantom) || !(event.getTarget() instanceof Player player)) return;
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot != null && plot.getHorizonRank() >= 3 && plot.getFlag("horizon-phantom-ward", true)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player target) recentCombat.put(target.getUniqueId(), System.currentTimeMillis());
        Player attacker = attackingPlayer(event.getDamager());
        if (attacker != null) recentCombat.put(attacker.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) return;
        Plot plot = plugin.store().getPlotAt(player.getLocation());
        if (plot == null || plot.getHorizonRank() < 1 || !plot.getFlag("horizon-safe-landing", true)) return;
        if (!plot.hasPermission(player.getUniqueId(), "INTERACT", plugin) || !plot.getFlag("pvp", true)) return;
        if (isCombatLocked(player)) return;
        event.setCancelled(true);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 14, 0.35, 0.05, 0.35, 0.02);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ())) return;
        Player player = event.getPlayer();
        Plot plot = plugin.store().getPlotAt(event.getTo());
        UUID previous = appliedClimate.get(player.getUniqueId());
        if (plot != null && plot.getHorizonRank() >= 2 && plot.hasPermission(player.getUniqueId(), "INTERACT", plugin)) {
            if (!plot.getPlotId().equals(previous)) {
                appliedClimate.put(player.getUniqueId(), plot.getPlotId());
                applyClimate(player, plot);
                applyHeartBlessing(player, plot);
            }
        } else if (previous != null) {
            appliedClimate.remove(player.getUniqueId());
            resetClimate(player);
        }
    }

    public void save() {
        saveRewardLedger();
    }

    public void completeMatchingExpansion(Plot plot, int oldRadius, int newRadius) {
        if (plot == null || plot.getHorizonExpansionRank() >= plot.getHorizonRank()) return;
        HorizonRank next = HorizonRank.byIndex(plot.getHorizonExpansionRank() + 1);
        if (next == null || newRadius - oldRadius != radiusGain(next)) return;
        plot.setHorizonExpansionRank(next.index());
        plugin.store().savePlot(plot);
    }

    public boolean requestNextExpansion(Player player, Plot plot) {
        if (player == null || plot == null || !plot.getOwner().equals(player.getUniqueId())) return false;
        HorizonRank next = HorizonRank.byIndex(plot.getHorizonExpansionRank() + 1);
        if (next == null || next.index() > plot.getHorizonRank()) return false;
        return requestRankExpansion(player, plot, next);
    }

    private void unlockRankDefaults(Plot plot, HorizonRank rank) {
        if (rank.index() >= 1) plot.setFlag("horizon-safe-landing", true);
        if (rank.index() >= 3) {
            plot.setFlag("horizon-projectile-veil", true);
            plot.setFlag("horizon-ender-seal", true);
            plot.setFlag("horizon-phantom-ward", true);
        }
    }

    private boolean requestRankExpansion(Player player, Plot plot, HorizonRank rank) {
        if (plugin.expansions() == null || rank == null || plot.getHorizonExpansionRank() >= rank.index()) return false;
        int width = Math.max(1, plot.getX2() - plot.getX1() + 1);
        int depth = Math.max(1, plot.getZ2() - plot.getZ1() + 1);
        int currentRadius = Math.max(width, depth) / 2;
        return plugin.expansions().createRequest(player, plot, currentRadius + radiusGain(rank));
    }

    private boolean isExpansionAvailable(Player player, Plot plot, HorizonRank rank) {
        if (plugin.expansions() != null && plugin.expansions().hasPendingRequest(player.getUniqueId())) return false;
        int width = Math.max(1, plot.getX2() - plot.getX1() + 1);
        int depth = Math.max(1, plot.getZ2() - plot.getZ1() + 1);
        int targetRadius = Math.max(width, depth) / 2 + radiusGain(rank);
        int limit = maximumRadius();
        return targetRadius <= limit || player.hasPermission("aegis.admin.bypass-limits");
    }

    private void ceremony(Player player, Plot plot, HorizonRank rank, boolean advancement) {
        Location center = player.getLocation().clone().add(0, 1, 0);
        player.closeInventory();
        player.getWorld().spawnParticle(Particle.CLOUD, center, 45, 1.4, 0.45, 1.4, 0.12);
        player.getWorld().spawnParticle(Particle.PORTAL, center, 90, 1.3, 1.2, 1.3, 0.15);
        player.getWorld().spawnParticle(Particle.END_ROD, center, 60, 1.8, 1.0, 1.8, 0.08);
        plugin.effects().playSound(player, center, Sound.BLOCK_BEACON_ACTIVATE, 1.2F, 0.7F + rank.index() * 0.1F);
        plugin.effects().playSound(player, center, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 0.8F);
        if (!advancement) return;
        String rankName = rankName(player, rank);
        player.sendTitle(color(tr(player, "horizon_awakened_title", "&d&lHORIZON AWAKENED")),
                color(tr(player, "horizon_awakened_subtitle", "&f{RANK}").replace("{RANK}", rankName)), 10, 70, 20);
        send(player, "horizon_awakened_message",
                "&dYour territory has awakened &f{RANK}&d. Its new powers now answer to you.", Map.of("RANK", rankName));
    }

    private void applyHeartBlessing(Player player, Plot plot) {
        if (plot.getHorizonRank() < 5 || !plot.getFlag("pvp", true) || isCombatLocked(player)) return;
        long now = System.currentTimeMillis();
        if (now < heartCooldowns.getOrDefault(player.getUniqueId(), 0L)) return;
        heartCooldowns.put(player.getUniqueId(), now + HEART_COOLDOWN_MILLIS);
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 4.0D));
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 8, 0.5, 0.6, 0.5, 0.02);
        send(player, "horizon_heart_blessing", "&dThe Eternal Aegis welcomes you home with a protective blessing.", Map.of());
    }

    private void applyClimate(Player player, Plot plot) {
        switch (plot.getHorizonClimate()) {
            case "CLEAR" -> { player.resetPlayerTime(); player.setPlayerWeather(WeatherType.CLEAR); }
            case "RAIN" -> { player.resetPlayerTime(); player.setPlayerWeather(WeatherType.DOWNFALL); }
            case "SUNRISE" -> { player.setPlayerWeather(WeatherType.CLEAR); player.setPlayerTime(23_000L, false); }
            case "SUNSET" -> { player.setPlayerWeather(WeatherType.CLEAR); player.setPlayerTime(12_000L, false); }
            case "NIGHT" -> { player.setPlayerWeather(WeatherType.CLEAR); player.setPlayerTime(18_000L, false); }
            default -> resetClimate(player);
        }
    }

    private void resetClimate(Player player) {
        player.resetPlayerWeather();
        player.resetPlayerTime();
    }

    private void addRenown(Plot plot, long amount) {
        if (amount <= 0L) return;
        plot.addHorizonRenown(amount);
        plugin.store().savePlot(plot);
    }

    private boolean isEligiblePlot(Plot plot) {
        return enabled() && plot != null && plot.getLevel() >= unlockLevel() && plot.getHorizonRank() < 5;
    }

    private boolean isCombatLocked(Player player) {
        return System.currentTimeMillis() - recentCombat.getOrDefault(player.getUniqueId(), 0L) < COMBAT_LOCK_MILLIS;
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private ItemStack findSigil(Player player, UUID plotId, int rank) {
        for (ItemStack item : player.getInventory().getContents()) {
            SigilData data = readSigil(item);
            if (data != null && data.plotId().equals(plotId) && data.rank() == rank) return item;
        }
        return null;
    }

    private SigilData readSigil(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(sigilKey, PersistentDataType.BYTE)) return null;
        String plot = pdc.get(plotKey, PersistentDataType.STRING);
        String owner = pdc.get(ownerKey, PersistentDataType.STRING);
        Integer rank = pdc.get(rankKey, PersistentDataType.INTEGER);
        try {
            return plot == null || owner == null || rank == null ? null
                    : new SigilData(UUID.fromString(plot), UUID.fromString(owner), rank);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void consumeOne(ItemStack item, Player player) {
        if (item.getAmount() <= 1) player.getInventory().removeItem(item);
        else item.setAmount(item.getAmount() - 1);
    }

    private String displayName(Plot plot) {
        return plot.getPlotName() == null || plot.getPlotName().isBlank()
                ? (plot.getOwnerName() == null ? "Plot" : plot.getOwnerName() + "'s Plot") : plot.getPlotName();
    }

    private String tr(Player player, String key, String fallback) {
        return plugin.gui() == null ? fallback : plugin.gui().tr(player, key, fallback);
    }

    private void send(Player player, String key, String fallback, Map<String, String> values) {
        String message = tr(player, key, fallback);
        for (Map.Entry<String, String> entry : values.entrySet()) message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        player.sendMessage(color(message));
    }

    private String color(String value) { return GUIManager.color(value); }
    private List<String> color(List<String> values) { return values.stream().map(GUIManager::color).toList(); }

    private synchronized void loadRewardLedger() {
        visitRewards.clear();
        likeRewards.clear();
        if (!rewardFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(rewardFile);
        ConfigurationSection visits = yaml.getConfigurationSection("visit-rewards");
        if (visits != null) for (String key : visits.getKeys(false)) visitRewards.put(key, visits.getLong(key));
        likeRewards.addAll(yaml.getStringList("like-rewards"));
    }

    private synchronized void saveRewardLedger() {
        YamlConfiguration yaml = new YamlConfiguration();
        visitRewards.forEach((key, value) -> yaml.set("visit-rewards." + key, value));
        yaml.set("like-rewards", new HashSet<>(likeRewards).stream().sorted().toList());
        File temporary = new File(rewardFile.getParentFile(), rewardFile.getName() + ".tmp");
        try {
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), rewardFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), rewardFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            plugin.getLogger().warning("Could not save Horizon reward ledger: " + error.getMessage());
        } finally {
            if (temporary.exists() && !temporary.delete()) temporary.deleteOnExit();
        }
    }

    private record SigilData(UUID plotId, UUID ownerId, int rank) {}
}
