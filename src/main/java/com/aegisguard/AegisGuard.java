package com.aegisguard;

import com.aegisguard.admin.AdminCommand;
import com.aegisguard.claimblocks.ClaimBlockExchangeService;
import com.aegisguard.claimblocks.ClaimBlockManager;
import com.aegisguard.claimblocks.ClaimBlockTask;
import com.aegisguard.commands.AegisCommand;
import com.aegisguard.config.AGConfig;
import com.aegisguard.data.IDataStore;
import com.aegisguard.data.SQLDataStore;
import com.aegisguard.data.YMLDataStore;
import com.aegisguard.economy.ClaimPricingCalculator;
import com.aegisguard.economy.EconomyManager;
import com.aegisguard.economy.VaultHook;
import com.aegisguard.expansions.ExpansionRequestManager;
import com.aegisguard.gui.GUIListener;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.hooks.AegisPAPIExpansion;
import com.aegisguard.hooks.DiscordWebhook;
import com.aegisguard.hooks.MapHookManager;
import com.aegisguard.hooks.MobBarrierTask;
import com.aegisguard.hooks.WildernessRevertTask;
import com.aegisguard.hooks.protection.ProtectionHookManager;
import com.aegisguard.language.CodexEngine;
import com.aegisguard.listeners.BannedPlayerListener;
import com.aegisguard.listeners.LevelingListener;
import com.aegisguard.listeners.PlotGreetingListener;
import com.aegisguard.migration.MigrationManager;
import com.aegisguard.notify.NotificationManager;
import com.aegisguard.protection.BlockProtectionListener;
import com.aegisguard.protection.ProtectionManager;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.selection.WandSafetyListener;
import com.aegisguard.snapshots.SnapshotManager;
import com.aegisguard.util.EffectUtil;
import com.aegisguard.util.MessagesUtil;
import com.aegisguard.visualization.WandEquipListener;
import com.aegisguard.world.WorldRulesManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AegisGuard extends JavaPlugin {

    // --- SINGLETON PATTERN ---
    private static AegisGuard instance;

    public static AegisGuard getInstance() {
        return instance;
    }

    // --- MANAGERS ---
    private AGConfig configMgr;
    private IDataStore plotStore;
    private GUIManager gui;
    private ProtectionManager protection;
    private SelectionService selection;
    private VaultHook vault;
    private EconomyManager ecoManager;

    // Compatibility layer for other protection plugins (WorldGuard, etc.)
    private ProtectionHookManager protectionHooks;

    // Aegis Codex language engine (1.2.4+)
    private CodexEngine codex;

    // Claim Block Manager (1.2.4+)
    private ClaimBlockManager claimBlockManager;

    // Claim Block Exchange Service (1.2.5+)
    private ClaimBlockExchangeService claimBlockExchange;

    // Snapshot Manager (1.2.5+) - Rollback system for claims
    private SnapshotManager snapshotManager;

    // Fair Pricing Calculator (1.2.6+)
    private ClaimPricingCalculator pricingCalculator;

    // Migration Manager (1.2.6+) - Import claims from other plugins
    private MigrationManager migrationManager;

    // Notification Manager (1.2.6+) - Player notification preferences
    private NotificationManager notificationManager;

    /**
     * MessagesUtil now acts as:
     * - playerdata.yml prefs (language choice)
     * - legacy msg().get(...) compatibility
     *
     * IMPORTANT: This class must NOT depend on messages.yml anymore.
     */
    private MessagesUtil messages;

    private WorldRulesManager worldRules;
    private EffectUtil effectUtil;
    private ExpansionRequestManager expansionManager;

    // --- HOOKS ---
    private MapHookManager mapHookManager;
    private DiscordWebhook discord;

    private boolean isFolia = false;

    // Task Objects
    private Object autoSaveTask;
    private Object upkeepTask;
    private Object wildernessRevertTask;
    private Object mobBarrierTask;
    private Object claimBlockTask;

    // --- 1.2.6 QoL: runtime bypass toggle ("Master Key Mode") ---
    private final Set<UUID> bypassMode = ConcurrentHashMap.newKeySet();

    // --- GETTERS ---
    public AGConfig cfg() { return configMgr; }
    public IDataStore store() { return plotStore; }
    public IDataStore getDataStore() { return plotStore; }
    public GUIManager gui() { return gui; }
    public GUIManager getGuiManager() { return gui; }
    public ProtectionManager protection() { return protection; }
    public ProtectionManager getProtectionManager() { return protection; }
    public SelectionService selection() { return selection; }
    public SelectionService getSelection() { return selection; }
    public VaultHook vault() { return vault; }
    public EconomyManager eco() { return ecoManager; }
    public EconomyManager getEconomy() { return ecoManager; }

    public ProtectionHookManager protectionHooks() { return protectionHooks; }
    public CodexEngine codex() { return codex; }

    public ClaimBlockManager getClaimBlockManager() { return claimBlockManager; }

    /**
     * Alias for getClaimBlockManager() - compatibility method
     */
    public ClaimBlockManager claimBlocks() { return claimBlockManager; }

    // Exchange service getter
    public ClaimBlockExchangeService exchange() { return claimBlockExchange; }
    public ClaimBlockExchangeService getClaimBlockExchangeService() { return claimBlockExchange; }

    // Snapshot Manager getter
    public SnapshotManager getSnapshotManager() { return snapshotManager; }
    public SnapshotManager snapshots() { return snapshotManager; }

    // Fair Pricing Calculator getter (1.2.6+)
    public ClaimPricingCalculator getPricingCalculator() { return pricingCalculator; }
    public ClaimPricingCalculator pricing() { return pricingCalculator; }

    // Migration Manager getter (1.2.6+)
    public MigrationManager getMigrationManager() { return migrationManager; }
    public MigrationManager migration() { return migrationManager; }

    // Notification Manager getter (1.2.6+)
    public NotificationManager getNotificationManager() { return notificationManager; }
    public NotificationManager notifications() { return notificationManager; }

    /**
     * Legacy access (compat bridge).
     * This must NOT read messages.yml anymore.
     */
    public MessagesUtil msg() { return messages; }

    public WorldRulesManager worldRules() { return worldRules; }
    public EffectUtil effects() { return effectUtil; }
    public ExpansionRequestManager getExpansionRequestManager() { return expansionManager; }
    public ExpansionRequestManager expansions() { return expansionManager; }
    public DiscordWebhook getDiscord() { return discord; }
    public MapHookManager getMapHooks() { return mapHookManager; }
    public boolean isFolia() { return isFolia; }

    public AGConfig getConfigManager() { return configMgr; }

    @Override
    public void onEnable() {
        instance = this;

        // --- 1) Folia detection (safe) ---
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("Folia detected! Enabling Region Scheduler compatibility.");
        } catch (ClassNotFoundException e) {
            isFolia = false;
            getLogger().info("Standard Bukkit/Spigot/Paper detected.");
        }

        saveDefaultConfig();
        ensureLocalizationFiles();

        // --- CONFIG + MESSAGES ---
        configMgr = new AGConfig(this);
        messages = new MessagesUtil(this);

        // --- DATA STORE ---
        String backend = getConfig().getString("storage.backend", "yml").toLowerCase();
        if (backend.equals("sql")) {
            plotStore = new SQLDataStore(this);
        } else {
            plotStore = new YMLDataStore(this);
        }
        plotStore.load();

        // --- MANAGERS ---
        gui = new GUIManager(this);
        protection = new ProtectionManager(this);
        selection = new SelectionService(this);
        worldRules = new WorldRulesManager(this);
        effectUtil = new EffectUtil(this);
        expansionManager = new ExpansionRequestManager(this);
        snapshotManager = new SnapshotManager(this);
        pricingCalculator = new ClaimPricingCalculator(this);
        migrationManager = new MigrationManager(this);

        // Notification Manager (1.2.6+) - per-player notification preferences
        try {
            this.notificationManager = new NotificationManager(this);
            getLogger().info("Notification Manager initialized.");
        } catch (Throwable t) {
            this.notificationManager = null;
            getLogger().warning("NotificationManager failed to initialize: " + t.getMessage());
        }

        // ClaimBlocks (1.2.4+)
        claimBlockManager = new ClaimBlockManager(this);
        claimBlockExchange = new ClaimBlockExchangeService(this);

        // --- HOOKS ---
        protectionHooks = new ProtectionHookManager(this);
        mapHookManager = new MapHookManager(this);
        discord = new DiscordWebhook(this);

        // Vault (optional)
        vault = new VaultHook(this);
        ecoManager = new EconomyManager(this);

        // --- COMMANDS ---
        PluginCommand cmd = getCommand("aegis");
        if (cmd != null) {
            AegisCommand aegisCmd = new AegisCommand(this);
            cmd.setExecutor(aegisCmd);
            cmd.setTabCompleter(aegisCmd);
        }

        PluginCommand admin = getCommand("aegisadmin");
        if (admin != null) {
            AdminCommand adminCmd = new AdminCommand(this);
            admin.setExecutor(adminCmd);
            admin.setTabCompleter(adminCmd);
        }

        // Load async data (safe)
        runGlobalAsync(() -> {
            try {
                if (messages != null) messages.loadPlayerPreferences();
            } catch (Throwable ignored) {}

            try {
                if (expansionManager != null) expansionManager.load();
            } catch (Throwable ignored) {}

            try {
                if (snapshotManager != null) snapshotManager.load();
            } catch (Throwable ignored) {}

            try {
                if (notificationManager != null) notificationManager.loadData();
            } catch (Throwable ignored) {}
        });

        // Register Events
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(protection, this);
        Bukkit.getPluginManager().registerEvents(selection, this);
        Bukkit.getPluginManager().registerEvents(new BlockProtectionListener(this), this);

        Bukkit.getPluginManager().registerEvents(new PlotGreetingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WandSafetyListener(this), this);
        Bukkit.getPluginManager().registerEvents(new LevelingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WandEquipListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BannedPlayerListener(this), this);

        // Tasks (robust + cancelable)
        startAutoSaver();
        startUpkeepTask();
        startWildernessRevertTask();
        startMobBarrierTask();
        startClaimBlockTask();

        // PlaceholderAPI (optional)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AegisPAPIExpansion(this).register();
        }

        getLogger().info("AegisGuard enabled.");
    }

    @Override
    public void onDisable() {
        // Cancel tasks safely
        cancelTaskReflectively(autoSaveTask);
        cancelTaskReflectively(upkeepTask);
        cancelTaskReflectively(wildernessRevertTask);
        cancelTaskReflectively(mobBarrierTask);
        cancelTaskReflectively(claimBlockTask);

        // Save plot + player data safely
        try {
            if (plotStore != null) plotStore.saveSync();
        } catch (Throwable t) {
            getLogger().warning("Failed to save plot store: " + t.getMessage());
        }

        try {
            if (claimBlockManager != null) claimBlockManager.save();
        } catch (Throwable t) {
            getLogger().warning("Failed to save claim blocks: " + t.getMessage());
        }

        try {
            if (snapshotManager != null) snapshotManager.save();
        } catch (Throwable t) {
            getLogger().warning("Failed to save snapshots: " + t.getMessage());
        }

        try {
            if (expansionManager != null) expansionManager.save();
        } catch (Throwable t) {
            getLogger().warning("Failed to save expansion requests: " + t.getMessage());
        }

        // Save player data
        if (messages != null) messages.savePlayerData();

        // Save notification prefs
        if (notificationManager != null) notificationManager.saveData();

        getLogger().info("AegisGuard disabled.");
    }

    public boolean isAdmin(Player player) {
        if (player == null) return false;
        boolean trustOps = getConfig().getBoolean("admin.trust_operators", true);
        if (!trustOps) {
            return player.hasPermission("aegis.admin");
        }
        return player.isOp() || player.hasPermission("aegis.admin");
    }

    /**
     * 1.2.6 QoL: Runtime bypass toggle.
     * This is separate from the static "aegis.bypass" permission.
     */
    public boolean isBypassing(Player player) {
        if (player == null) return false;
        return bypassMode.contains(player.getUniqueId());
    }

    /**
     * Toggle runtime bypass mode for this player.
     * Requires permissions (checked by the caller command).
     *
     * @return true if bypass is now enabled, false if disabled
     */
    public boolean toggleBypass(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        if (bypassMode.contains(uuid)) {
            bypassMode.remove(uuid);
            return false;
        }
        bypassMode.add(uuid);
        return true;
    }

    /**
     * Check if sounds are enabled for a player.
     * Checks global config first, then player-specific preference.
     */
    public boolean isSoundEnabled(Player player) {
        if (player == null) return false;

        // Check global setting first
        if (!cfg().globalSoundsEnabled()) {
            return false;
        }

        // Check player-specific setting (default: true)
        return getConfig().getBoolean("sounds.players." + player.getUniqueId(), true);
    }

    // ---------------------------------------------------------------------
    // Schedulers (Folia compatible) + 1.2.5 compat wrappers
    // ---------------------------------------------------------------------

    /**
     * 1.2.5 compat: run on main thread (Bukkit) or global region (Folia).
     */
    public void runMainGlobal(Runnable task) {
        runSync(task);
    }

    /**
     * Run a task on the main thread (Bukkit) or global region (Folia).
     */
    public void runSync(Runnable task) {
        if (!isFolia) {
            Bukkit.getScheduler().runTask(this, task);
            return;
        }

        // Folia-compatible scheduling
        try {
            Method getGlobalRegionScheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
            Object scheduler = getGlobalRegionScheduler.invoke(Bukkit.getServer());

            Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
            runMethod.invoke(scheduler, this, (Consumer<Object>) scheduledTask -> task.run());
        } catch (Throwable t) {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    /**
     * Run a task on the entity's region (Folia) or main thread (Bukkit).
     */
    public void runMain(Player player, Runnable task) {
        if (player == null || task == null) return;

        if (!isFolia) {
            Bukkit.getScheduler().runTask(this, task);
            return;
        }

        // Folia: use entity scheduler
        try {
            Method getScheduler = player.getClass().getMethod("getScheduler");
            Object entityScheduler = getScheduler.invoke(player);

            Method runMethod = entityScheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            runMethod.invoke(entityScheduler, this, (Consumer<Object>) scheduledTask -> task.run(), (Runnable) null);
        } catch (Throwable t) {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    /**
     * Run a task asynchronously on the global region.
     */
    public void runGlobalAsync(Runnable task) {
        if (task == null) return;

        if (!isFolia) {
            Bukkit.getScheduler().runTaskAsynchronously(this, task);
            return;
        }

        // Folia: use async scheduler
        try {
            Method getAsyncScheduler = Bukkit.getServer().getClass().getMethod("getAsyncScheduler");
            Object asyncScheduler = getAsyncScheduler.invoke(Bukkit.getServer());

            Method runNow = asyncScheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
            runNow.invoke(asyncScheduler, this, (Consumer<Object>) scheduledTask -> task.run());
        } catch (Throwable t) {
            Bukkit.getScheduler().runTaskAsynchronously(this, task);
        }
    }

    /**
     * Run a task asynchronously (compat wrapper).
     */
    public void runAsync(Runnable task) {
        runGlobalAsync(task);
    }

    /**
     * Reload config + services. 1.2.5 behavior preserved, 1.2.6 additions included.
     */
    public void reloadAegisGuard(boolean refreshGuis) {
        reloadConfig();

        if (configMgr != null) configMgr.reload();
        if (worldRules != null) worldRules.reload();
        if (messages != null) messages.reload();

        // Reload notification preferences
        try {
            this.notificationManager = new NotificationManager(this);
        } catch (Throwable t) {
            this.notificationManager = null;
        }

        if (refreshGuis) {
            runMainGlobal(this::closeAllAegisGUIs);
        }

        getLogger().info("AegisGuard reloaded successfully.");
    }

    // ---------------------------------------------------------------------
    // Task starters (stable + cancelable)
    // ---------------------------------------------------------------------

    private void startAutoSaver() {
        long intervalSeconds = getConfig().getLong("storage.autosave_seconds", 300L);
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);

        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (plotStore != null) plotStore.save();
                    if (claimBlockManager != null) claimBlockManager.save();
                    if (snapshotManager != null) snapshotManager.save();
                    if (expansionManager != null) expansionManager.save();
                    if (messages != null) messages.savePlayerData();
                    if (notificationManager != null) notificationManager.saveData();
                } catch (Throwable t) {
                    getLogger().warning("Auto-save error: " + t.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
    }

    private void startUpkeepTask() {
        long intervalSeconds = getConfig().getLong("economy.upkeep.interval_seconds", 3600L);
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);

        upkeepTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (ecoManager == null) return;
                    if (!ecoManager.isVaultEnabled()) return;

                    plotStore.getAllPlots().forEach(plot -> {
                        try {
                            double upkeep = getConfig().getDouble("economy.upkeep.cost", 0.0);
                            if (upkeep <= 0) return;

                            if (plot != null && plot.getOwner() != null) {
                                if (!ecoManager.withdraw(plot.getOwner(), upkeep)) {
                                    // If configured, unclaim plots on non-payment (optional)
                                    boolean unclaim = getConfig().getBoolean("economy.upkeep.unclaim_on_fail", false);
                                    if (unclaim) {
                                        plotStore.removePlot(plot.getId());
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    });
                } catch (Throwable t) {
                    getLogger().warning("Upkeep task error: " + t.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
    }

    private void startWildernessRevertTask() {
        boolean enabled = getConfig().getBoolean("wilderness_revert.enabled", false);
        if (!enabled) return;

        long intervalSeconds = getConfig().getLong("wilderness_revert.interval_seconds", 300L);
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);

        WildernessRevertTask logic = new WildernessRevertTask(this);

        wildernessRevertTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    logic.run();
                } catch (Throwable t) {
                    getLogger().warning("Wilderness revert task error: " + t.getMessage());
                }
            }
        }.runTaskTimer(this, intervalTicks, intervalTicks);
    }

    private void startMobBarrierTask() {
        boolean enabled = getConfig().getBoolean("mob_barrier.enabled", false);
        if (!enabled) return;

        long intervalSeconds = getConfig().getLong("mob_barrier.interval_seconds", 5L);
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);

        MobBarrierTask logic = new MobBarrierTask(this);

        mobBarrierTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    logic.run();
                } catch (Throwable t) {
                    getLogger().warning("Mob barrier task error: " + t.getMessage());
                }
            }
        }.runTaskTimer(this, intervalTicks, intervalTicks);
    }

    private void startClaimBlockTask() {
        boolean enabled = getConfig().getBoolean("claim_blocks.enabled", true);
        if (!enabled) return;

        long intervalSeconds = getConfig().getLong("claim_blocks.task.interval_seconds", 60L);
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);

        ClaimBlockTask logic = new ClaimBlockTask(this);

        claimBlockTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    logic.run();
                } catch (Throwable t) {
                    getLogger().warning("ClaimBlock task error: " + t.getMessage());
                }
            }
        }.runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
    }

    private void cancelTaskReflectively(Object task) {
        if (task == null) return;
        try {
            if (task instanceof BukkitTask bt) {
                bt.cancel();
                return;
            }
            Method cancel = task.getClass().getMethod("cancel");
            cancel.invoke(task);
        } catch (Throwable ignored) {}
    }

    private void closeAllAegisGUIs() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                Inventory top = p.getOpenInventory().getTopInventory();
                if (top == null) continue;

                InventoryHolder holder = top.getHolder();
                if (holder == null) continue;

                String hn = holder.getClass().getName();
                if (hn.startsWith("com.aegisguard.gui")) {
                    p.closeInventory();
                }
            } catch (Throwable ignored) {}
        }
    }

    private void ensureLocalizationFiles() {
        // Keep these synced with your /resources packs.
        List<String> packs = Arrays.asList(
                "language/system.yml",
                "language/gui.yml",
                "language/tasks.yml",
                "language/roles.yml"
        );

        for (String rel : packs) {
            try {
                File out = new File(getDataFolder(), rel);
                if (out.exists()) continue;

                out.getParentFile().mkdirs();

                try (InputStream in = getResource(rel)) {
                    if (in == null) continue;
                    Files.copy(in, out.toPath());
                }
            } catch (Throwable t) {
                getLogger().warning("Failed to write language file: " + rel + " (" + t.getMessage() + ")");
            }
        }
    }
}
