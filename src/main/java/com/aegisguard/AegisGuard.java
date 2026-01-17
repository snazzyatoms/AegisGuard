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
import com.aegisguard.migration.MigrationManager;  // ✅ NEW: Migration Manager (1.2.5+)
import com.aegisguard.protection.ProtectionManager;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.selection.WandSafetyListener;
import com.aegisguard.snapshots.SnapshotManager;
import com.aegisguard.util.EffectUtil;
import com.aegisguard.util.MessagesUtil;
import com.aegisguard.visualization.WandEquipListener;
import com.aegisguard.world.WorldRulesManager;
import org.bukkit.Bukkit;
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
import java.util.Arrays;
import java.util.List;
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

    // ✅ NEW: Migration Manager (1.2.6+) - Import claims from other plugins
    private MigrationManager migrationManager;

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

    // Exchange service getter
    public ClaimBlockExchangeService exchange() { return claimBlockExchange; }
    public ClaimBlockExchangeService getClaimBlockExchangeService() { return claimBlockExchange; }

    // Snapshot Manager getter
    public SnapshotManager getSnapshotManager() { return snapshotManager; }

    // Fair Pricing Calculator getter (1.2.6+)
    /**
     * Get the claim pricing calculator for fair initial pricing.
     * Returns null if fair pricing is disabled or failed to initialize.
     * @return The pricing calculator instance, or null
     */
    public ClaimPricingCalculator getPricingCalculator() { return pricingCalculator; }

    // ✅ NEW: Migration Manager getter (1.2.6+)
    /**
     * Get the migration manager for importing claims from other protection plugins.
     * Supports GriefPrevention, GriefDefender, and Lands.
     * @return The migration manager instance
     */
    public MigrationManager getMigrationManager() { return migrationManager; }

    /**
     * Legacy access (compat bridge).
     * This must NOT read messages.yml anymore.
     */
    @Deprecated
    public MessagesUtil msg() { return messages; }

    public WorldRulesManager worldRules() { return worldRules; }
    public EffectUtil effects() { return effectUtil; }
    public ExpansionRequestManager getExpansionRequestManager() { return expansionManager; }
    public DiscordWebhook getDiscord() { return discord; }
    public MapHookManager getMapHooks() { return mapHookManager; }
    public boolean isFolia() { return isFolia; }

    public AGConfig getConfigManager() { return configMgr; }

    @Override
    public void onEnable() {
        instance = this;

        // --- 1. ROBUST FOLIA DETECTION (1.21+ Supported) ---
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("Folia detected! Enabling Region Scheduler compatibility.");
        } catch (ClassNotFoundException e) {
            isFolia = false;
            getLogger().info("Standard Bukkit/Spigot detected (Not Folia).");
        }

        saveDefaultConfig();

        // --- 1.a Ensure language bundle files exist (safe extraction) ---
        ensureLocalizationFiles();

        // --- 1.b HARD STOP: messages.yml is NOT used anymore ---
        warnIfLegacyMessagesYmlPresent();

        this.configMgr = new AGConfig(this);

        // --- 2. STORAGE INIT ---
        String storageType = cfg().raw().getString("storage.type", "yml").toLowerCase();

        if (storageType.contains("mysql") || storageType.contains("mariadb") || storageType.contains("sql")) {
            getLogger().info("Initializing SQL DataStore (Custom/Local)...");
            this.plotStore = new SQLDataStore(this);
        } else {
            getLogger().info("Initializing YML DataStore (File System)...");
            this.plotStore = new YMLDataStore(this);
        }

        // --- 3. INIT MANAGERS ---

        // 3.a Language Engine (Codex)
        try {
            this.codex = new CodexEngine(this);
            getLogger().info("Aegis Codex language engine initialized.");
        } catch (Throwable t) {
            getLogger().severe("Failed to initialize CodexEngine: " + t.getMessage());
            this.codex = null;
        }

        this.selection = new SelectionService(this);

        // 3.b MessagesUtil (prefs + compat)
        try {
            this.messages = new MessagesUtil(this);
        } catch (Throwable t) {
            this.messages = null;
            getLogger().warning("MessagesUtil failed to initialize: " + t.getMessage());
        }

        // 3.c Claim Block Manager (only if enabled)
        if (cfg().raw().getBoolean("claim_blocks.enabled", true)) {
            this.claimBlockManager = new ClaimBlockManager(this);
        } else {
            this.claimBlockManager = null;
        }

        // 3.d Vault FIRST (exchange + many GUIs rely on it)
        this.vault = new VaultHook(this);
        this.ecoManager = new EconomyManager(this);

        // 3.d2 Fair Pricing Calculator (after economy manager)
        try {
            this.pricingCalculator = new ClaimPricingCalculator(this);
            boolean enabled = pricingCalculator.isEnabled();
            getLogger().info("Fair Pricing Calculator initialized (enabled: " + enabled + ")");
        } catch (Throwable t) {
            this.pricingCalculator = null;
            getLogger().warning("ClaimPricingCalculator failed to initialize: " + t.getMessage());
        }

        // 3.e Claim Block Exchange Service (only meaningful if claim blocks exist)
        if (this.claimBlockManager != null) {
            try {
                this.claimBlockExchange = new ClaimBlockExchangeService(this);
            } catch (Throwable t) {
                this.claimBlockExchange = null;
                getLogger().warning("ClaimBlockExchangeService failed to initialize: " + t.getMessage());
            }
        } else {
            this.claimBlockExchange = null;
        }

        // 3.f Snapshot Manager (only if enabled in config)
        if (cfg().raw().getBoolean("snapshots.enabled", true)) {
            try {
                this.snapshotManager = new SnapshotManager(this);
                getLogger().info("Snapshot Manager initialized.");
            } catch (Throwable t) {
                this.snapshotManager = null;
                getLogger().warning("SnapshotManager failed to initialize: " + t.getMessage());
            }
        } else {
            this.snapshotManager = null;
            getLogger().info("Snapshot Manager disabled in config.");
        }

        // ✅ 3.g NEW: Migration Manager (always available for admins)
        try {
            this.migrationManager = new MigrationManager(this);
            getLogger().info("Migration Manager initialized (supports GP, GD, Lands).");
        } catch (Throwable t) {
            this.migrationManager = null;
            getLogger().warning("MigrationManager failed to initialize: " + t.getMessage());
        }

        // 3.h GUI AFTER vault/exchange exist
        this.gui = new GUIManager(this);

        this.worldRules = new WorldRulesManager(this);
        this.effectUtil = new EffectUtil(this);
        this.expansionManager = new ExpansionRequestManager(this);
        this.discord = new DiscordWebhook(this);
        this.protection = new ProtectionManager(this);

        // Load Data
        this.plotStore.load();

        runGlobalAsync(() -> {
            if (messages != null) messages.loadPlayerPreferences();
            if (expansionManager != null) expansionManager.load();
            if (snapshotManager != null) snapshotManager.load();
        });

        // Register Events
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(protection, this);
        Bukkit.getPluginManager().registerEvents(selection, this);

        Bukkit.getPluginManager().registerEvents(new PlotGreetingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WandSafetyListener(this), this);

        if (cfg().isLevelingEnabled()) {
            try {
                Class.forName("com.aegisguard.listeners.LevelingListener");
                Bukkit.getPluginManager().registerEvents(new LevelingListener(this), this);
            } catch (ClassNotFoundException ignored) {}
        }

        if (cfg().raw().getBoolean("visualization.enabled", true)) {
            Bukkit.getPluginManager().registerEvents(new WandEquipListener(this), this);
        }

        if (cfg().autoRemoveBannedPlots()) {
            try {
                Class.forName("com.aegisguard.listeners.BannedPlayerListener");
                Bukkit.getPluginManager().registerEvents(new BannedPlayerListener(this), this);
            } catch (ClassNotFoundException ignored) {}
        }

        // Register Commands
        PluginCommand aegis = getCommand("aegis");
        if (aegis != null) {
            AegisCommand aegisExecutor = new AegisCommand(this);
            aegis.setExecutor(aegisExecutor);
            aegis.setTabCompleter(aegisExecutor);
        }

        PluginCommand admin = getCommand("aegisadmin");
        if (admin != null) {
            AdminCommand adminExecutor = new AdminCommand(this);
            admin.setExecutor(adminExecutor);
            admin.setTabCompleter(adminExecutor);
        }

        // Start Tasks
        startAutoSaver();
        if (cfg().isUpkeepEnabled()) startUpkeepTask();
        startWildernessRevertTask();
        startMobBarrierTask();
        startClaimBlockTask();

        // Hooks
        initializeHooks();

        getLogger().info("AegisGuard enabled successfully.");
    }

    private void initializeHooks() {
        try {
            this.mapHookManager = new MapHookManager(this);
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().warning("Map hooks could not be initialized: " + e.getMessage());
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AegisPAPIExpansion(this).register();
        }

        boolean compatEnabled = cfg() != null && cfg().raw().getBoolean("hooks.protection_compat.enabled", true);
        if (compatEnabled) {
            try {
                this.protectionHooks = new ProtectionHookManager(this);
                this.protectionHooks.registerDefaults();
            } catch (Throwable t) {
                this.protectionHooks = null;
                getLogger().warning("ProtectionHookManager could not be initialized: " + t.getMessage());
            }
        } else {
            this.protectionHooks = null;
            getLogger().info("[AegisGuard] Protection compatibility hooks disabled in config (hooks.protection_compat.enabled=false).");
        }
    }

    @Override
    public void onDisable() {
        cancelTaskReflectively(autoSaveTask);
        cancelTaskReflectively(upkeepTask);
        cancelTaskReflectively(wildernessRevertTask);
        cancelTaskReflectively(mobBarrierTask);
        cancelTaskReflectively(claimBlockTask);

        // Exchange: best-effort flush
        try {
            if (claimBlockExchange != null) claimBlockExchange.shutdown();
        } catch (Throwable ignored) {}

        if (plotStore != null) {
            getLogger().info("Saving plot data...");
            plotStore.saveSync();
        }

        if (expansionManager != null) expansionManager.saveSync();
        if (claimBlockManager != null) claimBlockManager.shutdown();

        // Save snapshots on shutdown
        if (snapshotManager != null) {
            getLogger().info("Saving snapshot data...");
            snapshotManager.saveSync();
        }

        if (messages != null) messages.savePlayerData();

        protectionHooks = null;
        claimBlockExchange = null;
        snapshotManager = null;
        pricingCalculator = null;
        migrationManager = null;  // ✅ Cleanup migration manager
        instance = null;

        getLogger().info("AegisGuard disabled.");
    }

    // ---------------------------------------------------------------------
    // Legacy file warning
    // ---------------------------------------------------------------------

    private void warnIfLegacyMessagesYmlPresent() {
        File legacy = new File(getDataFolder(), "messages.yml");
        if (legacy.exists()) {
            getLogger().warning("Detected legacy messages.yml in plugin folder.");
            getLogger().warning("AegisGuard v1.2.4+ no longer uses messages.yml.");
            getLogger().warning("You may safely delete it (your new language bundles are in the localization folder).");
        }
    }

    // ---------------------------------------------------------------------
    // Language bundle bootstrap (Primary lang + Fallback codex, no spam)
    // ---------------------------------------------------------------------

    private void ensureLocalizationFiles() {
        if (!getConfig().getBoolean("localization.extract_defaults", true)) {
            return;
        }

        String primaryFolder = getConfig().getString("localization.folder", "lang");
        if (primaryFolder == null || primaryFolder.isBlank()) primaryFolder = "lang";
        primaryFolder = primaryFolder.trim();

        String fallbackFolder = getConfig().getString("localization.fallback_folder", "codex");
        if (fallbackFolder == null || fallbackFolder.isBlank()) fallbackFolder = "codex";
        fallbackFolder = fallbackFolder.trim();

        File primaryDir = new File(getDataFolder(), primaryFolder);
        if (!primaryDir.exists() && !primaryDir.mkdirs()) {
            getLogger().warning("[AegisGuard] Failed to create localization folder: " + primaryDir.getPath());
            return;
        }

        File fallbackDir = new File(getDataFolder(), fallbackFolder);
        if (!fallbackDir.exists() && !fallbackDir.mkdirs()) {
            getLogger().warning("[AegisGuard] Failed to create fallback localization folder: " + fallbackDir.getPath());
        }

        installSplitLanguageBundles(primaryFolder);

        if (!fallbackFolder.equalsIgnoreCase(primaryFolder)) {
            installLegacyFallbackFiles(fallbackFolder);
        }
    }

    private void installSplitLanguageBundles(String primaryFolder) {
        File baseDir = new File(getDataFolder(), primaryFolder);

        List<String> languages = getConfig().getStringList("localization.available_languages");
        if (languages == null || languages.isEmpty()) {
            languages = Arrays.asList("old_english", "hybrid_english", "modern_english", "spanish_mx", "spanish_ar");
        }

        List<String> bundles = getConfig().getStringList("localization.bundles");
        if (bundles == null || bundles.isEmpty()) {
            bundles = Arrays.asList("guis.yml", "system.yml", "upgrades.yml", "expansions.yml");
        }

        for (String lang : languages) {
            File langDir = new File(baseDir, lang);
            if (!langDir.exists() && !langDir.mkdirs()) {
                getLogger().warning("[AegisGuard] Failed to create language folder: " + langDir.getPath());
                continue;
            }

            for (String bundle : bundles) {
                saveBundledResourceIfMissing(primaryFolder + "/" + lang + "/" + bundle);
            }
        }
    }

    private void installLegacyFallbackFiles(String fallbackFolder) {
        File baseDir = new File(getDataFolder(), fallbackFolder);

        if (!baseDir.exists() && !baseDir.mkdirs()) {
            getLogger().warning("[AegisGuard] Failed to create fallback localization folder: " + baseDir.getPath());
            return;
        }

        List<String> legacyRootFiles = getConfig().getStringList("localization.fallback_root_files");
        if (legacyRootFiles == null || legacyRootFiles.isEmpty()) {
            legacyRootFiles = Arrays.asList(
                    "codex.yml",
                    "core.yml",
                    "old_english.yml",
                    "hybrid_english.yml",
                    "modern_english.yml",
                    "spanish_mx.yml",
                    "spanish_ar.yml"
            );
        }

        for (String root : legacyRootFiles) {
            saveBundledResourceIfMissing(fallbackFolder + "/" + root);
        }
    }

    private boolean hasBundledResource(String jarRelativePath) {
        if (jarRelativePath == null || jarRelativePath.isBlank()) return false;
        try (InputStream in = getResource(jarRelativePath)) {
            return in != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void saveBundledResourceIfMissing(String jarRelativePath) {
        if (jarRelativePath == null || jarRelativePath.isBlank()) return;

        if (!hasBundledResource(jarRelativePath)) {
            return;
        }

        File target = new File(getDataFolder(), jarRelativePath.replace("/", File.separator));
        if (target.exists()) return;

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            getLogger().warning("[AegisGuard] Failed to create parent folders for: " + target.getPath());
            return;
        }

        try {
            saveResource(jarRelativePath, false);
            getLogger().info("[AegisGuard] Installed language resource: " + jarRelativePath);
        } catch (Throwable ex) {
            getLogger().warning("[AegisGuard] Failed to install bundled resource: " + jarRelativePath + " (" + ex.getMessage() + ")");
        }
    }

    // ---------------------------------------------------------------------
    // Utility
    // ---------------------------------------------------------------------

    public boolean isSoundEnabled(Player player) {
        if (configMgr == null || !cfg().globalSoundsEnabled()) return false;
        String key = "sounds.players." + player.getUniqueId();
        return getConfig().getBoolean(key, true);
    }

    public boolean isAdmin(Player player) {
        if (player == null) return false;
        boolean trustOps = getConfig().getBoolean("admin.trust_operators", true);
        if (!trustOps) {
            return player.hasPermission("aegis.admin");
        }
        return player.isOp() || player.hasPermission("aegis.admin");
    }

    // ---------------------------------------------------------------------
    // Schedulers (Folia 1.21+ compatible)
    // ---------------------------------------------------------------------

    public void runGlobalAsync(Runnable task) {
        if (isFolia) {
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
                //noinspection unchecked
                runMethod.invoke(scheduler, this, (Consumer<Object>) t -> task.run());
            } catch (Exception e) {
                new Thread(task).start();
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(this, task);
        }
    }

    public void runMain(Player player, Runnable task) {
        if (player == null) { runMainGlobal(task); return; }
        if (isFolia) {
            try {
                Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
                Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
                //noinspection unchecked
                runMethod.invoke(scheduler, this, (Consumer<Object>) t -> task.run(), null);
            } catch (Exception e) { e.printStackTrace(); }
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    public void runMainGlobal(Runnable task) {
        if (isFolia) {
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
                //noinspection unchecked
                runMethod.invoke(scheduler, this, (Consumer<Object>) t -> task.run());
            } catch (Exception e) { e.printStackTrace(); }
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    private Object scheduleAsyncRepeating(Runnable task, long intervalTicks) {
        if (isFolia) {
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                Method runMethod = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
                //noinspection unchecked
                return runMethod.invoke(scheduler, this, (Consumer<Object>) t -> task.run(), intervalTicks, intervalTicks);
            } catch (Exception e) {
                return null;
            }
        } else {
            return new BukkitRunnable() {
                @Override public void run() { task.run(); }
            }.runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
        }
    }

    private void cancelTaskReflectively(Object task) {
        if (task == null) return;
        if (task instanceof BukkitTask) {
            ((BukkitTask) task).cancel();
        } else {
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (Exception ignored) {}
        }
    }

    // ---------------------------------------------------------------------
    // Tasks
    // ---------------------------------------------------------------------

    private void startAutoSaver() {
        long interval = 20L * 60 * 5;
        Runnable logic = () -> {
            if (plotStore != null && plotStore.isDirty()) plotStore.save();
            if (expansionManager != null && expansionManager.isDirty()) expansionManager.save();
            if (claimBlockManager != null) claimBlockManager.save();
            if (snapshotManager != null && snapshotManager.isDirty()) snapshotManager.save();
            if (messages != null && messages.isPlayerDataDirty()) messages.savePlayerData();
            // Exchange state auto-saves on trades; shutdown flush handles final save.
        };
        autoSaveTask = scheduleAsyncRepeating(logic, interval);
    }

    private void startUpkeepTask() {
        long interval = (long) (20L * 60 * 60 * cfg().getUpkeepCheckHours());
        if (interval <= 0) return;
        Runnable logic = () -> {
            if (plotStore == null) return;
            for (com.aegisguard.data.Plot plot : plotStore.getAllPlots()) {
                // upkeep logic (depends on EconomyManager details)
            }
        };
        upkeepTask = scheduleAsyncRepeating(logic, interval);
    }

    private void startWildernessRevertTask() {
        if (!cfg().raw().getBoolean("wilderness_revert.enabled", false)) return;
        String storage = cfg().raw().getString("storage.type", "yml");
        if (!storage.contains("sql") && !storage.contains("mysql")) return;

        long interval = 20L * 60 * cfg().raw().getLong("wilderness_revert.check_interval_minutes", 10);
        WildernessRevertTask task = new WildernessRevertTask(this, plotStore);
        wildernessRevertTask = scheduleAsyncRepeating(task::run, interval);
    }

    private void startMobBarrierTask() {
        long interval = cfg().raw().getLong("mob_barrier.check_interval_ticks", 60);
        MobBarrierTask task = new MobBarrierTask(this);
        mobBarrierTask = scheduleAsyncRepeating(task::run, interval);
    }

    private void startClaimBlockTask() {
        if (!cfg().raw().getBoolean("claim_blocks.enabled", true)) return;
        if (!cfg().raw().getBoolean("claim_blocks.earn.playtime.enabled", true)) return;

        long earnIntervalMinutes = cfg().raw().getLong("claim_blocks.earn.playtime.interval_minutes", 10);
        long intervalTicks = earnIntervalMinutes * 60 * 20;

        ClaimBlockTask task = new ClaimBlockTask(this);
        claimBlockTask = scheduleAsyncRepeating(task::run, intervalTicks);
    }

    // ---------------------------------------------------------------------
    // Central Reload Hook (Config + Codex + Bundles)
    // ---------------------------------------------------------------------

    public void reloadAegisGuard(boolean refreshGuis) {
        try {
            reloadConfig();
        } catch (Throwable t) {
            getLogger().warning("[AegisGuard] reloadConfig() failed: " + t.getMessage());
        }

        try {
            this.configMgr = new AGConfig(this);
        } catch (Throwable t) {
            getLogger().warning("[AegisGuard] AGConfig reload failed: " + t.getMessage());
        }

        try {
            ensureLocalizationFiles();
        } catch (Throwable t) {
            getLogger().warning("[AegisGuard] ensureLocalizationFiles() failed: " + t.getMessage());
        }

        try {
            if (this.codex != null) {
                this.codex.reload();
            }
        } catch (Throwable t) {
            getLogger().warning("[AegisGuard] CodexEngine reload failed: " + t.getMessage());
        }

        // Reload Fair Pricing Calculator
        try {
            if (this.pricingCalculator != null) {
                this.pricingCalculator.reload();
                getLogger().info("[AegisGuard] Fair Pricing Calculator reloaded.");
            }
        } catch (Throwable t) {
            getLogger().warning("[AegisGuard] ClaimPricingCalculator reload failed: " + t.getMessage());
        }

        runGlobalAsync(() -> {
            try {
                if (messages != null) messages.loadPlayerPreferences();
            } catch (Throwable ignored) {}
        });

        if (refreshGuis) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    Inventory top = p.getOpenInventory().getTopInventory();
                    if (top == null) continue;
                    InventoryHolder h = top.getHolder();
                    if (h == null) continue;

                    String cn = h.getClass().getName();
                    if (cn.startsWith("com.aegisguard.gui.")
                            || cn.startsWith("com.aegisguard.expansions.")
                            || cn.startsWith("com.aegisguard.snapshots.")) {
                        p.closeInventory();
                    }
                } catch (Throwable ignored) {}
            }
        }
    }
}
