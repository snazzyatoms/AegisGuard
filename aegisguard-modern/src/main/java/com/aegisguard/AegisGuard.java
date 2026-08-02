package com.aegisguard;

import com.aegisguard.api.AegisGuardAPI;
import com.aegisguard.api.internal.DefaultAegisGuardAPI;
import com.aegisguard.admin.AdminCommand;
import com.aegisguard.audit.AuditService;
import com.aegisguard.claimblocks.ClaimBlockExchangeService;
import com.aegisguard.claimblocks.ClaimBlockManager;
import com.aegisguard.claimblocks.ClaimBlockTask;
import com.aegisguard.commands.AegisCommand;
import com.aegisguard.config.AGConfig;
import com.aegisguard.config.ConfigMigrationService;
import com.aegisguard.data.IDataStore;
import com.aegisguard.data.Plot;
import com.aegisguard.data.SQLDataStore;
import com.aegisguard.data.YMLDataStore;
import com.aegisguard.economy.ClaimPricingCalculator;
import com.aegisguard.economy.EconomyManager;
import com.aegisguard.economy.VaultHook;
import com.aegisguard.expansions.ExpansionRequestManager;
import com.aegisguard.gui.GUIListener;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.groups.GroupManager;
import com.aegisguard.hooks.AegisPAPIExpansion;
import com.aegisguard.hooks.DiscordWebhook;
import com.aegisguard.hooks.MapHookManager;
import com.aegisguard.hooks.MobBarrierTask;
import com.aegisguard.hooks.WildernessRevertTask;
import com.aegisguard.hooks.market.MarketBridgeManager;
import com.aegisguard.hooks.protection.ProtectionHookManager;
import com.aegisguard.horizons.HorizonService;
import com.aegisguard.language.CodexEngine;
import com.aegisguard.language.LanguageResourceSynchronizer;
import com.aegisguard.listeners.BannedPlayerListener;
import com.aegisguard.listeners.LevelingListener;
import com.aegisguard.listeners.MarketStallListener;
import com.aegisguard.listeners.PlotGreetingListener;
import com.aegisguard.listeners.StarterKitListener;
import com.aegisguard.migration.MigrationManager;
import com.aegisguard.market.TradeStallService;
import com.aegisguard.notify.NotificationManager;
import com.aegisguard.protection.BlockProtectionListener;
import com.aegisguard.protection.ProtectionManager;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.listeners.WandSafetyListener;
import com.aegisguard.snapshots.SnapshotManager;
import com.aegisguard.territory.TerritoryLifeService;
import com.aegisguard.util.EffectUtil;
import com.aegisguard.util.MessagesUtil;
import com.aegisguard.visualization.WandEquipListener;
import com.aegisguard.world.WorldRulesManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AegisGuard extends JavaPlugin {

    // --- SINGLETON PATTERN ---
    private static AegisGuard instance;

    public static AegisGuard getInstance() {
        return instance;
    }

    // --- MANAGERS ---
    private AGConfig configMgr;
    private ConfigMigrationService configMigration;
    private IDataStore plotStore;
    private GUIManager gui;
    private ProtectionManager protection;
    private SelectionService selection;
    private VaultHook vault;
    private EconomyManager ecoManager;
    private AegisGuardAPI api;

    // Compatibility layer for other protection plugins (WorldGuard, etc.)
    private ProtectionHookManager protectionHooks;

    // Aegis Codex language engine (1.2.4+)
    private CodexEngine codex;
    private LanguageResourceSynchronizer languageSynchronizer;

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
    private GroupManager groupManager;
    private MarketBridgeManager marketBridgeManager;
    private TradeStallService tradeStallService;
    private TerritoryLifeService territoryLifeService;
    private HorizonService horizonService;
    private LevelingListener levelingListener;

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

    // Staff Audit Ledger (1.3.0+)
    private AuditService auditService;
    private com.aegisguard.guestpass.GuestPassService guestPassService;
    private com.aegisguard.lockdown.LockdownService lockdownService;

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
    private Object rentalExpiryTask;
    private Object guestPassExpiryTask;
    private ClaimBlockTask claimBlockTaskLogic;

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
    public AegisGuardAPI api() { return api; }
    public AegisGuardAPI getApi() { return api; }

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
    public GroupManager getGroupManager() { return groupManager; }
    public GroupManager groups() { return groupManager; }
    public MarketBridgeManager marketBridges() { return marketBridgeManager; }
    public MarketBridgeManager getMarketBridgeManager() { return marketBridgeManager; }
    public TradeStallService tradeStalls() { return tradeStallService; }
    public TradeStallService getTradeStallService() { return tradeStallService; }
    public TerritoryLifeService territoryLife() { return territoryLifeService; }
    public HorizonService horizons() { return horizonService; }
    public LevelingListener ascensionEffects() { return levelingListener; }
    public ConfigMigrationService configMigration() { return configMigration; }

    /**
     * Legacy access (compat bridge).
     * This must NOT read messages.yml anymore.
     */
    public MessagesUtil msg() { return messages; }

    public WorldRulesManager worldRules() { return worldRules; }
    public EffectUtil effects() { return effectUtil; }
    public ExpansionRequestManager getExpansionRequestManager() { return expansionManager; }
    public ExpansionRequestManager expansions() { return expansionManager; }
    public AuditService audit() { return auditService; }
    public com.aegisguard.guestpass.GuestPassService guestPasses() { return guestPassService; }
    public com.aegisguard.lockdown.LockdownService lockdown() { return lockdownService; }
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
        configMigration = new ConfigMigrationService(this);
        configMigration.migrate();
        languageSynchronizer = new LanguageResourceSynchronizer(this);
        ensureLocalizationFiles();

        // --- CONFIG + MESSAGES ---
        configMgr = new AGConfig(this);

        // --- LANGUAGE ENGINE ---
        try {
            codex = new CodexEngine(this);
            getLogger().info("Codex language engine initialized.");
        } catch (Throwable t) {
            codex = null;
            getLogger().warning("Codex language engine failed to initialize: " + t.getMessage());
        }

        messages = new MessagesUtil(this);

        // --- DATA STORE ---
        String backend = resolveConfiguredStorageBackend();
        if (isSqlStorageBackend(backend)) {
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
        auditService = new AuditService(this);
        guestPassService = new com.aegisguard.guestpass.GuestPassService(this);
        lockdownService = new com.aegisguard.lockdown.LockdownService(this);
        pricingCalculator = new ClaimPricingCalculator(this);
        migrationManager = new MigrationManager(this);
        groupManager = new GroupManager(this);
        marketBridgeManager = new MarketBridgeManager(this);
        tradeStallService = new TradeStallService(this);

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
        territoryLifeService = new TerritoryLifeService(this);
        horizonService = new HorizonService(this);
        api = new DefaultAegisGuardAPI(this);

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
                if (auditService != null) auditService.load();
            } catch (Throwable ignored) {}

            try {
                if (groupManager != null) groupManager.load();
            } catch (Throwable ignored) {}

            // ✅ NotificationManager loads data inside constructor + reload()
            // ❌ Do NOT call notificationManager.loadData() (it's private).
        });

        // Register Events
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(protection, this);
        registerPaperMobBoundaryListener();
        Bukkit.getPluginManager().registerEvents(selection, this);
        Bukkit.getPluginManager().registerEvents(new BlockProtectionListener(this), this);

        Bukkit.getPluginManager().registerEvents(new PlotGreetingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WandSafetyListener(this), this);
        Bukkit.getPluginManager().registerEvents(new StarterKitListener(this), this);
        levelingListener = new LevelingListener(this);
        Bukkit.getPluginManager().registerEvents(levelingListener, this);
        Bukkit.getPluginManager().registerEvents(new com.aegisguard.listeners.MigrationWandListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MarketStallListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WandEquipListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BannedPlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(horizonService, this);

        // Tasks (robust + cancelable)
        startAutoSaver();
        startUpkeepTask();
        startWildernessRevertTask();
        startMobBarrierTask();
        startClaimBlockTask();
        startRentalExpiryTask();
        startGuestPassExpiryTask();

        // PlaceholderAPI (optional)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AegisPAPIExpansion(this).register();
        }

        getLogger().info("AegisGuard enabled.");
    }

    private void registerPaperMobBoundaryListener() {
        try {
            Class.forName("io.papermc.paper.event.entity.EntityMoveEvent", false, getClassLoader());
            Class<?> listenerType = Class.forName(
                    "com.aegisguard.protection.PaperMobBoundaryListener",
                    true,
                    getClassLoader()
            );
            org.bukkit.event.Listener listener = (org.bukkit.event.Listener) listenerType
                    .getConstructor(AegisGuard.class)
                    .newInstance(this);
            Bukkit.getPluginManager().registerEvents(listener, this);
        } catch (ClassNotFoundException ignored) {
            getLogger().info("Paper entity movement API not found; using the Spigot mob-barrier fallback.");
        } catch (ReflectiveOperationException | LinkageError error) {
            getLogger().warning("Could not enable the Paper mob boundary: " + error.getMessage());
        }
    }

    @Override
    public void onDisable() {
        // Cancel tasks safely
        cancelTaskReflectively(autoSaveTask);
        cancelTaskReflectively(upkeepTask);
        cancelTaskReflectively(wildernessRevertTask);
        cancelTaskReflectively(mobBarrierTask);
        cancelTaskReflectively(claimBlockTask);
        cancelTaskReflectively(rentalExpiryTask);
        cancelTaskReflectively(guestPassExpiryTask);

        // Save plot + player data safely
        try {
            if (plotStore != null) {
                plotStore.saveSync();
                plotStore.shutdown();
            }
        } catch (Throwable t) {
            getLogger().warning("Failed to save plot store: " + t.getMessage());
        }

        try {
            if (claimBlockManager != null) claimBlockManager.save();
        } catch (Throwable t) {
            getLogger().warning("Failed to save claim blocks: " + t.getMessage());
        }

        try {
            if (claimBlockExchange != null) claimBlockExchange.shutdown();
        } catch (Throwable t) {
            getLogger().warning("Failed to shut down ClaimBlocks exchange: " + t.getMessage());
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

        try {
            if (auditService != null) auditService.save();
        } catch (Throwable t) {
            getLogger().warning("Failed to save the audit ledger: " + t.getMessage());
        }

        try {
            if (groupManager != null && groupManager.isDirty()) groupManager.save();
        } catch (Throwable t) {
            getLogger().warning("Failed to save groups: " + t.getMessage());
        }

        try {
            if (territoryLifeService != null) territoryLifeService.save();
        } catch (Throwable t) {
            getLogger().warning("Failed to save territory life data: " + t.getMessage());
        }

        try {
            if (horizonService != null) horizonService.save();
        } catch (Throwable t) {
            getLogger().warning("Failed to save Horizon reward data: " + t.getMessage());
        }

        // Save player data
        if (messages != null) messages.savePlayerData();

        // Save notification prefs
        if (notificationManager != null && notificationManager.isDirty()) notificationManager.saveData();

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
        if (task == null) return;

        if (!isFolia) {
            Bukkit.getScheduler().runTask(this, task);
            return;
        }

        Bukkit.getGlobalRegionScheduler().run(this, ignored -> task.run());
    }

    /**
     * Run a task on the entity's region (Folia) or main thread (Bukkit).
     */
    public void runMain(Player player, Runnable task) {
        if (task == null) return;
        if (player == null) {
            runSync(task);
            return;
        }

        if (!isFolia) {
            Bukkit.getScheduler().runTask(this, task);
            return;
        }

        player.getScheduler().run(this, ignored -> task.run(), null);
    }

    /** Run a task on an entity's owning region, or on the Bukkit main thread. */
    public void runEntity(Entity entity, Runnable task) {
        if (entity == null || task == null) return;
        if (!isFolia) {
            Bukkit.getScheduler().runTask(this, task);
            return;
        }
        entity.getScheduler().run(this, ignored -> task.run(), null);
    }

    /** Run a task on the region that owns a location, or on the Bukkit main thread. */
    public void runAt(Location location, Runnable task) {
        if (location == null || location.getWorld() == null || task == null) return;
        if (!isFolia) {
            Bukkit.getScheduler().runTask(this, task);
            return;
        }
        Bukkit.getRegionScheduler().run(this, location, ignored -> task.run());
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

        Bukkit.getAsyncScheduler().runNow(this, ignored -> task.run());
    }

    /**
     * Run a task asynchronously (compat wrapper).
     */
    public void runAsync(Runnable task) {
        runGlobalAsync(task);
    }

    public Object runGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        if (task == null) return null;
        long safeDelay = Math.max(1L, initialDelayTicks);
        long safePeriod = Math.max(1L, periodTicks);
        if (!isFolia) {
            return Bukkit.getScheduler().runTaskTimer(this, task, safeDelay, safePeriod);
        }
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                this,
                ignored -> task.run(),
                safeDelay,
                safePeriod
        );
    }

    public Object runAsyncRepeating(Runnable task, long initialDelaySeconds, long periodSeconds) {
        if (task == null) return null;
        long safeDelay = Math.max(1L, initialDelaySeconds);
        long safePeriod = Math.max(1L, periodSeconds);
        if (!isFolia) {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(
                    this,
                    task,
                    safeDelay * 20L,
                    safePeriod * 20L
            );
        }
        return Bukkit.getAsyncScheduler().runAtFixedRate(
                this,
                ignored -> task.run(),
                safeDelay,
                safePeriod,
                TimeUnit.SECONDS
        );
    }

    public Object runEntityRepeating(Entity entity, Runnable task, long initialDelayTicks, long periodTicks) {
        if (entity == null || task == null) return null;
        long safeDelay = Math.max(1L, initialDelayTicks);
        long safePeriod = Math.max(1L, periodTicks);
        if (!isFolia) {
            return Bukkit.getScheduler().runTaskTimer(this, task, safeDelay, safePeriod);
        }
        return entity.getScheduler().runAtFixedRate(this, ignored -> task.run(), null, safeDelay, safePeriod);
    }

    public void runEntityLater(Entity entity, Runnable task, long delayTicks) {
        if (entity == null || task == null) return;
        long safeDelay = Math.max(1L, delayTicks);
        if (!isFolia) {
            Bukkit.getScheduler().runTaskLater(this, task, safeDelay);
            return;
        }
        entity.getScheduler().runDelayed(this, ignored -> task.run(), null, safeDelay);
    }

    public void cancelScheduledTask(Object task) {
        cancelTaskReflectively(task);
    }

    /**
     * Reload config + services. 1.2.5 behavior preserved, 1.2.6 additions included.
     */
    public void reloadAegisGuard(boolean refreshGuis) {
        if (configMigration != null) configMigration.migrate();
        reloadConfig();
        ensureLocalizationFiles();

        if (configMgr != null) configMgr.reload();
        if (codex != null) codex.reload();
        if (worldRules != null) worldRules.reload();
        if (messages != null) messages.reload();
        if (pricingCalculator != null) pricingCalculator.reload();
        if (claimBlockExchange != null) claimBlockExchange.reload();
        if (groupManager != null) {
            groupManager.load();
            groupManager.cleanupMissingPlotLinks();
        }

        restartRecurringTasks();

        // ✅ Reload notification preferences safely (don’t recreate unless missing)
        try {
            if (this.notificationManager == null) {
                this.notificationManager = new NotificationManager(this);
            } else {
                this.notificationManager.reload();
            }
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
        long safeIntervalSeconds = Math.max(1L, intervalSeconds);

        autoSaveTask = runAsyncRepeating(() -> {
            try {
                if (plotStore != null) plotStore.save();
                if (claimBlockManager != null) claimBlockManager.save();
                if (claimBlockExchange != null) claimBlockExchange.save();
                if (snapshotManager != null) snapshotManager.save();
                if (expansionManager != null) expansionManager.save();
                if (auditService != null && auditService.isDirty()) auditService.save();
                if (groupManager != null && groupManager.isDirty()) groupManager.save();
                if (messages != null) messages.savePlayerData();
                if (notificationManager != null && notificationManager.isDirty()) notificationManager.saveData();
                if (territoryLifeService != null && territoryLifeService.isDirty()) territoryLifeService.save();
            } catch (Throwable t) {
                getLogger().warning("Auto-save error: " + t.getMessage());
            }
        }, safeIntervalSeconds, safeIntervalSeconds);
    }

    private void startUpkeepTask() {
        if (!getConfig().getBoolean("upkeep.enabled", false)
                && !getConfig().getBoolean("economy.upkeep.enabled", false)) {
            return;
        }

        long intervalHours = Math.max(1L, getConfig().getLong("upkeep.check_interval_hours",
                getConfig().getLong("economy.upkeep.interval_hours", 24L)));
        long intervalSeconds = intervalHours * 3600L;
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);

        upkeepTask = runGlobalRepeating(() -> {
            try {
                if (ecoManager == null) return;
                if (!ecoManager.isVaultEnabled()) return;

                plotStore.getAllPlots().forEach(plot -> {
                    try {
                        double upkeep = getConfig().getDouble("upkeep.cost_per_plot",
                                getConfig().getDouble("economy.upkeep.cost", 0.0));
                        if (upkeep <= 0) return;

                        if (plot != null && plot.getOwner() != null) {
                            boolean paid = vault() != null
                                    && vault().charge(Bukkit.getOfflinePlayer(plot.getOwner()), upkeep);

                            if (!paid) {
                                notifyUpkeepDue(plot, upkeep);

                                boolean unclaim = getConfig().getBoolean("upkeep.unclaim_on_fail",
                                        getConfig().getBoolean("economy.upkeep.unclaim_on_fail", false));
                                if (unclaim) {
                                    plotStore.removePlot(plot.getOwner(), plot.getId());
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                });
            } catch (Throwable t) {
                getLogger().warning("Upkeep task error: " + t.getMessage());
            }
        }, intervalTicks, intervalTicks);
    }

    private void notifyUpkeepDue(com.aegisguard.data.Plot plot, double upkeepAmount) {
        if (plot == null || notificationManager == null) return;
        if (!getConfig().getBoolean("upkeep.notifications.enabled", true)) return;

        String formatted = ecoManager != null && ecoManager.isVaultReady()
                ? ecoManager.format(upkeepAmount, com.aegisguard.economy.CurrencyType.VAULT)
                : String.format(Locale.US, "%.2f", upkeepAmount);

        Map<String, String> placeholders = Map.of(
                "AMOUNT", formatted,
                "CLAIM", resolveNotifyClaimName(plot)
        );

        notificationManager.notifyPlayer(
                plot.getOwner(),
                "notify_upkeep_title",
                "&cUpkeep Due",
                "notify_upkeep_due",
                "&cUpkeep is due for {CLAIM}. Required amount: &6{AMOUNT}&c.",
                placeholders
        );

        if (plot.isGroupPlot()) {
            notificationManager.notifyPlotMembers(
                    plot,
                    plot.getOwner(),
                    "notify_upkeep_title",
                    "&cUpkeep Due",
                    "notify_group_upkeep_due",
                    "&e{CLAIM} has unpaid upkeep. Required amount: &6{AMOUNT}&e.",
                    placeholders
            );
        }
    }

    private String resolveNotifyClaimName(com.aegisguard.data.Plot plot) {
        if (plot == null) return "your claim";
        if (plot.getEntryTitle() != null && !plot.getEntryTitle().isBlank()) return plot.getEntryTitle();
        if (plot.getPlotName() != null && !plot.getPlotName().isBlank()) return plot.getPlotName();
        if (plot.getGroupName() != null && !plot.getGroupName().isBlank()) return plot.getGroupName();
        if (plot.getOwnerName() != null && !plot.getOwnerName().isBlank()) return plot.getOwnerName() + "'s claim";
        return "your claim";
    }

    private void startWildernessRevertTask() {
        boolean enabled = getConfig().getBoolean("wilderness_revert.enabled", false);
        if (!enabled) return;

        if (!(plotStore instanceof SQLDataStore)) {
            getLogger().warning("Wilderness Revert is enabled, but the active storage backend is not SQL. Skipping wilderness revert startup.");
            return;
        }

        long intervalSeconds = getConfiguredWildernessRevertIntervalSeconds();
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);

        WildernessRevertTask logic = new WildernessRevertTask(this, plotStore);

        wildernessRevertTask = runGlobalRepeating(() -> {
            try {
                logic.run();
            } catch (Throwable t) {
                getLogger().warning("Wilderness revert task error: " + t.getMessage());
            }
        }, intervalTicks, intervalTicks);
    }

    private void startMobBarrierTask() {
        boolean enabled = getConfig().getBoolean("mob_barrier.enabled", false);
        if (!enabled) return;

        long intervalSeconds = getConfiguredMobBarrierIntervalSeconds();
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);

        MobBarrierTask logic = new MobBarrierTask(this);

        mobBarrierTask = runGlobalRepeating(() -> {
            try {
                logic.run();
            } catch (Throwable t) {
                getLogger().warning("Mob barrier task error: " + t.getMessage());
            }
        }, intervalTicks, intervalTicks);
    }

    private void startClaimBlockTask() {
        boolean enabled = getConfig().getBoolean("claim_blocks.enabled", true);
        if (!enabled) return;

        // Prefer the modern playtime interval path and keep the legacy seconds path as fallback.
        long intervalMinutes = getConfig().getLong("claim_blocks.earn.playtime.interval_minutes", -1L);
        long intervalSeconds = getConfig().getLong("claim_blocks.task.interval_seconds", 60L);
        long intervalTicks = intervalMinutes > 0
                ? Math.max(20L, intervalMinutes * 60L * 20L)
                : Math.max(20L, intervalSeconds * 20L);

        if (claimBlockTaskLogic == null) {
            claimBlockTaskLogic = new ClaimBlockTask(this);
        }

        claimBlockTask = runGlobalRepeating(() -> {
            try {
                claimBlockTaskLogic.run();
            } catch (Throwable t) {
                getLogger().warning("ClaimBlock task error: " + t.getMessage());
            }
        }, intervalTicks, intervalTicks);
    }

    private void restartRecurringTasks() {
        cancelTaskReflectively(autoSaveTask);
        cancelTaskReflectively(upkeepTask);
        cancelTaskReflectively(wildernessRevertTask);
        cancelTaskReflectively(mobBarrierTask);
        cancelTaskReflectively(claimBlockTask);
        cancelTaskReflectively(rentalExpiryTask);
        cancelTaskReflectively(guestPassExpiryTask);

        autoSaveTask = null;
        upkeepTask = null;
        wildernessRevertTask = null;
        mobBarrierTask = null;
        claimBlockTask = null;
        rentalExpiryTask = null;
        guestPassExpiryTask = null;

        startAutoSaver();
        startUpkeepTask();
        startWildernessRevertTask();
        startMobBarrierTask();
        startClaimBlockTask();
        startRentalExpiryTask();
        startGuestPassExpiryTask();
    }

    private void startRentalExpiryTask() {
        if (!getConfig().getBoolean("full_plot_renting.enabled", true)) return;

        rentalExpiryTask = runGlobalRepeating(() -> {
            long now = System.currentTimeMillis();
            if (territoryLifeService != null) territoryLifeService.retrySettlements();
            for (Plot plot : plotStore.getAllPlots()) {
                if (plot == null || plot.getCurrentRenter() == null) continue;

                TerritoryLifeService.RentalContract contract = territoryLifeService == null
                        ? null : territoryLifeService.contract(plot.getPlotId());
                long reminderWindow = Math.max(1L, getConfig().getLong("full_plot_renting.reminder_hours", 24L)) * 3_600_000L;
                if (contract != null && !contract.reminderSent() && contract.expiresAt() > now
                        && contract.expiresAt() - now <= reminderWindow) {
                    territoryLifeService.queueNotice(contract.renterId(), "&eYour rental expires in less than "
                            + Math.max(1L, (contract.expiresAt() - now) / 3_600_000L) + " hour(s). Use &b/ag rental renew&e.");
                    territoryLifeService.queueNotice(contract.ownerId(), "&eA plot rental expires soon. Plot: &f" + plot.getPlotId());
                    territoryLifeService.markReminderSent(plot.getPlotId());
                }
                if (plot.getRentEndTime() > now) continue;

                UUID renterId = plot.getCurrentRenter();
                UUID ownerId = plot.getOwner();
                plot.clearRenter();
                plotStore.savePlot(plot);

                if (territoryLifeService != null) {
                    TerritoryLifeService.RentalContract expired = territoryLifeService.removeContract(plot.getPlotId());
                    territoryLifeService.refundDeposit(expired, "Rental deposit refund after expiry");
                    territoryLifeService.log(plot.getPlotId(), null, "RENTAL_EXPIRED", "Rental term expired normally.");
                }

                Player renter = Bukkit.getPlayer(renterId);
                if (renter != null) {
                    runMain(renter, () -> messages.send(renter, "market-rental-expired"));
                }
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner != null) {
                    runMain(owner, () -> messages.send(owner, "market-owner-rental-expired"));
                }
            }
        }, 20L, 1_200L);
    }

    private void startGuestPassExpiryTask() {
        if (guestPassService == null || !guestPassService.isEnabled()) return;

        guestPassExpiryTask = runGlobalRepeating(() -> {
            try {
                guestPassService.runExpirySweep();
            } catch (Throwable t) {
                getLogger().warning("Guest Pass expiry sweep error: " + t.getMessage());
            }
        }, 20L, 1_200L);
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
        if (!getConfig().getBoolean("localization.extract_defaults", true)) {
            return;
        }

        String primaryFolder = getConfig().getString("localization.folder", "lang");
        String fallbackFolder = getConfig().getString("localization.fallback_folder", "codex");

        List<String> bundles = getConfig().getStringList("localization.bundles");
        if (bundles == null || bundles.isEmpty()) {
            bundles = Arrays.asList("guis.yml", "system.yml", "upgrades.yml", "expansions.yml");
        }

        List<String> languages = getConfig().getStringList("localization.available_languages");
        if (languages == null || languages.isEmpty()) {
            languages = Arrays.asList("old_english", "modern_english", "spanish_mx", "spanish_ar");
        }

        List<String> fallbackRootFiles = getConfig().getStringList("localization.fallback_root_files");
        if (fallbackRootFiles == null || fallbackRootFiles.isEmpty()) {
            fallbackRootFiles = Arrays.asList(
                    "codex.yml",
                    "core.yml",
                    "overrides.yml",
                    "old_english.yml",
                    "modern_english.yml",
                    "spanish_mx.yml",
                    "spanish_ar.yml"
            );
        }

        for (String style : languages) {
            if (style == null || style.isBlank()) continue;
            for (String bundle : bundles) {
                if (bundle == null || bundle.isBlank()) continue;
                synchronizeLanguageResource(primaryFolder + "/" + style.trim() + "/" + bundle.trim());
            }
        }

        for (String rootFile : fallbackRootFiles) {
            if (rootFile == null || rootFile.isBlank()) continue;
            String path = fallbackFolder + "/" + rootFile.trim();
            if (rootFile.trim().equalsIgnoreCase("overrides.yml")) {
                languageSynchronizer.ensure(path);
            } else {
                synchronizeLanguageResource(path);
            }
        }
    }

    private void synchronizeLanguageResource(String path) {
        if (languageSynchronizer == null) {
            languageSynchronizer = new LanguageResourceSynchronizer(this);
        }
        int additions = languageSynchronizer.synchronize(path);
        if (additions > 0) {
            getLogger().info("Added " + additions + " missing language key(s) to " + path
                    + "; the previous file was backed up.");
        }
    }

    private String resolveConfiguredStorageBackend() {
        String backend = getConfig().getString("storage.backend");
        if (backend == null || backend.isBlank()) {
            backend = getConfig().getString("storage.type", "yml");
        }
        return backend == null ? "yml" : backend.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isSqlStorageBackend(String backend) {
        if (backend == null || backend.isBlank()) return false;
        return backend.equals("sql")
                || backend.equals("sqlite")
                || backend.equals("mysql")
                || backend.equals("mariadb");
    }

    private long getConfiguredWildernessRevertIntervalSeconds() {
        if (getConfig().isSet("wilderness_revert.interval_seconds")) {
            return Math.max(1L, getConfig().getLong("wilderness_revert.interval_seconds", 300L));
        }
        long legacyMinutes = getConfig().getLong("wilderness_revert.check_interval_minutes", 5L);
        return Math.max(1L, legacyMinutes * 60L);
    }

    private long getConfiguredMobBarrierIntervalSeconds() {
        if (getConfig().isSet("mob_barrier.interval_seconds")) {
            return Math.max(1L, getConfig().getLong("mob_barrier.interval_seconds", 5L));
        }
        long legacyTicks = getConfig().getLong("mob_barrier.check_interval_ticks", 100L);
        long convertedSeconds = Math.max(1L, legacyTicks / 20L);
        return convertedSeconds;
    }

    private void ensureResource(String rel) {
        if (rel == null || rel.isBlank()) return;

        try {
            File out = new File(getDataFolder(), rel.replace("/", File.separator));
            if (out.exists()) return;

            File parent = out.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (InputStream in = getResource(rel)) {
                if (in == null) return;
                Files.copy(in, out.toPath());
            }
        } catch (Throwable t) {
            getLogger().warning("Failed to write language file: " + rel + " (" + t.getMessage() + ")");
        }
    }
}
