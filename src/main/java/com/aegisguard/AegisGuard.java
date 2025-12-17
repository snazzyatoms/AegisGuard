package com.aegisguard;

import com.aegisguard.admin.AdminCommand;
import com.aegisguard.claimblocks.ClaimBlockManager; // ✅ NEW: Claim Block Manager
import com.aegisguard.claimblocks.ClaimBlockTask;    // ✅ NEW: Earning Task
import com.aegisguard.commands.AegisCommand;
// import com.aegisguard.commands.CommandHandler;
import com.aegisguard.config.AGConfig;
import com.aegisguard.data.IDataStore;
import com.aegisguard.data.SQLDataStore;
import com.aegisguard.data.YMLDataStore;
import com.aegisguard.economy.EconomyManager; // ✅ CORRECT: Economy package for 1.2.x
import com.aegisguard.economy.VaultHook;
import com.aegisguard.expansions.ExpansionRequestManager;
import com.aegisguard.gui.GUIListener;    // ✅ CORRECT: GUI package for 1.2.x
import com.aegisguard.gui.GUIManager;
import com.aegisguard.hooks.AegisPAPIExpansion;
import com.aegisguard.hooks.DiscordWebhook;
// import com.aegisguard.hooks.JobsRebornHook;
// import com.aegisguard.hooks.McMMOHook;
import com.aegisguard.hooks.MapHookManager;
import com.aegisguard.hooks.MobBarrierTask;
import com.aegisguard.hooks.WildernessRevertTask;
import com.aegisguard.hooks.protection.ProtectionHookManager; // ✅ NEW: Protection hook manager (compatibility layer)
import com.aegisguard.language.CodexEngine;       // ✅ NEW: Language Engine
import com.aegisguard.listeners.BannedPlayerListener;
import com.aegisguard.listeners.LevelingListener;
import com.aegisguard.listeners.PlotGreetingListener; // ✅ NEW: Greeting Listener
import com.aegisguard.protection.ProtectionManager;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.selection.WandSafetyListener;   // ✅ NEW: wand anti-dupe / safety listener
import com.aegisguard.util.EffectUtil;
import com.aegisguard.util.MessagesUtil;
import com.aegisguard.visualization.WandEquipListener;
import com.aegisguard.world.WorldRulesManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class AegisGuard extends JavaPlugin {

    // --- SINGLETON PATTERN ---
    private static AegisGuard instance;

    /**
     * Get the main instance of the AegisGuard plugin.
     */
    public static AegisGuard getInstance() {
        return instance;
    }

    // --- MANAGERS (v1.2.2 Strict Structure) ---
    private AGConfig configMgr;
    private IDataStore plotStore;
    private GUIManager gui;
    private ProtectionManager protection;
    private SelectionService selection;
    private VaultHook vault;
    private EconomyManager ecoManager;

    /** ✅ NEW: Compatibility layer for other protection plugins (WorldGuard, etc.) */
    private ProtectionHookManager protectionHooks;

    /** 🔤 NEW: Aegis Codex language engine (1.2.4+) */
    private CodexEngine codex;

    /** 🧱 NEW: Claim Block Manager (1.2.4+) */
    private ClaimBlockManager claimBlockManager;

    /**
     * 📜 LEGACY: MessagesUtil (player prefs + legacy lookups).
     * NOTE: AegisGuard.java no longer auto-creates messages.yml.
     * We'll fully remove this after migrating prefs to the new system.
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
    private Object claimBlockTask; // ✅ NEW

    // --- GETTERS ---
    public AGConfig cfg() { return configMgr; }
    public IDataStore store() { return plotStore; }
    public IDataStore getDataStore() { return plotStore; } // Alias
    public GUIManager gui() { return gui; }
    public GUIManager getGuiManager() { return gui; } // Alias
    public ProtectionManager protection() { return protection; }
    public ProtectionManager getProtectionManager() { return protection; }
    public SelectionService selection() { return selection; }
    public SelectionService getSelection() { return selection; }
    public VaultHook vault() { return vault; }
    public EconomyManager eco() { return ecoManager; }
    public EconomyManager getEconomy() { return ecoManager; }

    /** ✅ NEW: Protection plugin compatibility entrypoint */
    public ProtectionHookManager protectionHooks() { return protectionHooks; }

    /**
     * 🌐 New language engine entrypoint.
     * Prefer this for all NEW message lookups in 1.2.4+.
     */
    public CodexEngine codex() { return codex; }

    /**
     * 🧱 New Claim Block Manager entrypoint.
     */
    public ClaimBlockManager getClaimBlockManager() { return claimBlockManager; }

    /**
     * 📜 Legacy access.
     * Marked deprecated so we can hunt usages and migrate to codex().
     */
    @Deprecated
    public MessagesUtil msg() { return messages; }

    public WorldRulesManager worldRules() { return worldRules; }
    public EffectUtil effects() { return effectUtil; }
    public ExpansionRequestManager getExpansionRequestManager() { return expansionManager; }
    public DiscordWebhook getDiscord() { return discord; }
    public MapHookManager getMapHooks() { return mapHookManager; }
    public boolean isFolia() { return isFolia; }

    // Legacy Alias
    public AGConfig getConfigManager() { return configMgr; }

    @Override
    public void onEnable() {
        instance = this;

        // --- 1. ROBUST FOLIA DETECTION (1.21+ Supported) ---
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("✅ Folia detected! Enabling Region Scheduler compatibility.");
        } catch (ClassNotFoundException e) {
            isFolia = false;
            getLogger().info("Standard Bukkit/Spigot detected (Not Folia).");
        }

        saveDefaultConfig();

        // --- 1.a NEW: Ensure language bundle files exist (new split structure) ---
        ensureLocalizationFiles();

        // --- 1.b LEGACY (optional): DO NOT auto-create messages.yml anymore ---
        // If you still need it temporarily, set: localization.install_legacy_messages_yml: true
        if (getConfig().getBoolean("localization.install_legacy_messages_yml", false)) {
            File legacy = new File(getDataFolder(), "messages.yml");
            if (!legacy.exists()) {
                try {
                    saveResource("messages.yml", false);
                    getLogger().info("[AegisGuard] Installed legacy messages.yml (compat mode enabled).");
                } catch (IllegalArgumentException ex) {
                    getLogger().warning("[AegisGuard] messages.yml not bundled in jar (fine): " + ex.getMessage());
                }
            }
        }

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

        // --- 3. INIT MANAGERS (v1.2.2 Structure) ---

        // 3.a NEW: Language Engine (Codex)
        try {
            this.codex = new CodexEngine(this);
            getLogger().info("✅ Aegis Codex language engine initialized.");
        } catch (Throwable t) {
            getLogger().severe("❌ Failed to initialize CodexEngine! Language system may not function correctly: " + t.getMessage());
            this.codex = null;
        }

        this.selection = new SelectionService(this);

        // 3.b LEGACY MessagesUtil (player prefs + remaining legacy lookups)
        //     Wrapped so messages.yml removal won't hard-crash the whole plugin.
        try {
            this.messages = new MessagesUtil(this);
        } catch (Throwable t) {
            this.messages = null;
            getLogger().warning("⚠ MessagesUtil failed to initialize (legacy mode may be unavailable): " + t.getMessage());
        }

        // 3.c NEW: Claim Block Manager (Bank) — only if enabled
        if (cfg().raw().getBoolean("claim_blocks.enabled", true)) {
            this.claimBlockManager = new ClaimBlockManager(this);
        } else {
            this.claimBlockManager = null;
        }

        this.gui = new GUIManager(this);
        this.vault = new VaultHook(this);
        this.ecoManager = new EconomyManager(this);
        this.worldRules = new WorldRulesManager(this);
        this.effectUtil = new EffectUtil(this);
        this.expansionManager = new ExpansionRequestManager(this);
        this.discord = new DiscordWebhook(this);
        this.protection = new ProtectionManager(this);

        // Load Data
        this.plotStore.load();

        runGlobalAsync(() -> {
            // While migrating, MessagesUtil may still hold player prefs
            if (messages != null) messages.loadPlayerPreferences();
            if (expansionManager != null) expansionManager.load();
        });

        // Register Events
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(protection, this);
        Bukkit.getPluginManager().registerEvents(selection, this);

        // ✅ NEW: Greeting listener (welcome/farewell on plot enter/leave)
        Bukkit.getPluginManager().registerEvents(new PlotGreetingListener(this), this);

        // ✅ NEW: Wand safety (no dupes, no chest-moving, drop = vanish)
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
        startClaimBlockTask(); // ✅ NEW

        // ✅ Hooks (includes protection compatibility layer)
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

        // ✅ NEW: protection hook compatibility layer (WorldGuard, GP, Towny, etc.)
        // Respect config toggle: hooks.protection_compat.enabled
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
        cancelTaskReflectively(claimBlockTask); // ✅ NEW

        // Force Save on Disable
        if (plotStore != null) {
            getLogger().info("Saving plot data...");
            plotStore.saveSync();
        }

        if (expansionManager != null) expansionManager.saveSync();
        if (claimBlockManager != null) claimBlockManager.shutdown();

        // Legacy player prefs (until migrated)
        if (messages != null) messages.savePlayerData();

        // ✅ NEW: tidy release
        protectionHooks = null;

        instance = null;
        getLogger().info("AegisGuard disabled.");
    }

    // --- NEW: LANGUAGE BUNDLE BOOTSTRAP (Split Files) ---

    /**
     * Ensures the new split language structure exists (per-language folders + bundles):
     *
     *   /plugins/AegisGuard/<folder>/<language>/guis.yml
     *   /plugins/AegisGuard/<folder>/<language>/system.yml
     *   /plugins/AegisGuard/<folder>/<language>/upgrades.yml
     *   /plugins/AegisGuard/<folder>/<language>/expansions.yml
     *
     * Default folder is "codex" so it matches your existing 1.2.4 naming,
     * but you can change it in config.yml via localization.folder.
     */
    private void ensureLocalizationFiles() {
        if (!getConfig().getBoolean("localization.extract_defaults", true)) {
            return;
        }

        String folder = getConfig().getString("localization.folder", "codex");
        File baseDir = new File(getDataFolder(), folder);

        if (!baseDir.exists() && !baseDir.mkdirs()) {
            getLogger().warning("[AegisGuard] Failed to create localization folder: " + baseDir.getPath());
            return;
        }

        // Optional: keep installing legacy Codex root files for now (until CodexEngine is updated by you/me next)
        List<String> rootFiles = getConfig().getStringList("localization.root_files");
        if (rootFiles == null || rootFiles.isEmpty()) {
            rootFiles = Arrays.asList(
                    "codex.yml",
                    "core.yml",
                    "old_english.yml",
                    "hybrid_english.yml",
                    "modern_english.yml",
                    "spanish_mx.yml",
                    "spanish_ar.yml"
            );
        }

        for (String root : rootFiles) {
            saveBundledResourceIfMissing(folder + "/" + root);
        }

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
                saveBundledResourceIfMissing(folder + "/" + lang + "/" + bundle);
            }
        }
    }

    /**
     * Saves a resource from the jar into the data folder if missing.
     * @param jarRelativePath Example: "codex/old_english/guis.yml"
     */
    private void saveBundledResourceIfMissing(String jarRelativePath) {
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
        } catch (IllegalArgumentException ex) {
            // Not bundled yet – warn but do not crash
            getLogger().warning("[AegisGuard] Missing bundled resource: " + jarRelativePath + " (" + ex.getMessage() + ")");
        }
    }

    // --- UTILITY METHODS (Fixed) ---

    public boolean isSoundEnabled(Player player) {
        // Safe check if config is null
        if (configMgr == null || !cfg().globalSoundsEnabled()) return false;
        String key = "sounds.players." + player.getUniqueId();
        return getConfig().getBoolean(key, true);
    }

    public boolean isAdmin(Player player) {
        if (player == null) return false;
        // Check config for OP trust
        boolean trustOps = getConfig().getBoolean("admin.trust_operators", true);
        if (!trustOps) {
            return player.hasPermission("aegis.admin");
        }
        return player.isOp() || player.hasPermission("aegis.admin");
    }

    // --- SCHEDULERS (FOLIA 1.21+ COMPATIBLE) ---

    public void runGlobalAsync(Runnable task) {
        if (isFolia) {
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                // 1.21+ Fix: Use Plugin.class, not JavaPlugin.class
                Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
                runMethod.invoke(scheduler, this, (Consumer<Object>) t -> task.run());
            } catch (Exception e) {
                // Fallback
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
                // 1.21+ Fix: Use Plugin.class
                Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
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
                // 1.21+ Fix: Use Plugin.class
                Method runMethod = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
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

    // --- TASKS ---

    private void startAutoSaver() {
        long interval = 20L * 60 * 5;
        Runnable logic = () -> {
            if (plotStore != null && plotStore.isDirty()) plotStore.save();
            if (expansionManager != null && expansionManager.isDirty()) expansionManager.save();
            if (claimBlockManager != null) claimBlockManager.save(); // ✅ NEW
            if (messages != null && messages.isPlayerDataDirty()) messages.savePlayerData();
            // Future: if (codex != null && codex.isProfileDirty()) codex.saveProfiles();
        };
        autoSaveTask = scheduleAsyncRepeating(logic, interval);
    }

    private void startUpkeepTask() {
        long interval = (long) (20L * 60 * 60 * cfg().getUpkeepCheckHours());
        if (interval <= 0) return;
        Runnable logic = () -> {
            if (plotStore == null) return;
            for (com.aegisguard.data.Plot plot : plotStore.getAllPlots()) {
                // v1.2.1 Logic: use ecoManager to check/charge upkeep
                // implementation depends on EconomyManager details
            }
        };
        upkeepTask = scheduleAsyncRepeating(logic, interval);
    }

    private void startWildernessRevertTask() {
        if (!cfg().raw().getBoolean("wilderness_revert.enabled", false)) return;
        String storage = cfg().raw().getString("storage.type", "yml");
        // Only run if SQL is active
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
        // Feature toggle: claim_blocks.enabled
        if (!cfg().raw().getBoolean("claim_blocks.enabled", true)) return;

        // Safe check if playtime earning disabled in config
        if (!cfg().raw().getBoolean("claim_blocks.earn.playtime.enabled", true)) return;

        long earnIntervalMinutes = cfg().raw().getLong("claim_blocks.earn.playtime.interval_minutes", 10);
        long intervalTicks = earnIntervalMinutes * 60 * 20; // Convert minutes to ticks

        ClaimBlockTask task = new ClaimBlockTask(this);

        // ✅ FIX: schedule the task logic consistently (avoids Runnable/type mismatch)
        claimBlockTask = scheduleAsyncRepeating(task::run, intervalTicks);
    }
}
