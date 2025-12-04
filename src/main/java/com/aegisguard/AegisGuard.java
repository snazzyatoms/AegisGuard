package com.aegisguard;

import com.aegisguard.admin.AdminCommand;
import com.aegisguard.commands.CommandHandler;
import com.aegisguard.config.AGConfig;
import com.aegisguard.data.IDataStore;
import com.aegisguard.data.SQLDataStore;
import com.aegisguard.data.YMLDataStore;
import com.aegisguard.economy.VaultHook; // VaultHook IS in economy package, keep this.
import com.aegisguard.gui.GUIManager;

// --- FIXED IMPORTS ---
import com.aegisguard.listeners.GUIListener; // Moved from .gui to .listeners
// REMOVED: import com.aegisguard.gui.SidebarManager; (Deleted)
// REMOVED: import com.aegisguard.economy.EconomyManager; (Moved to managers)

import com.aegisguard.hooks.AegisPAPIExpansion;
import com.aegisguard.hooks.CoreProtectHook;
import com.aegisguard.hooks.DiscordWebhook;
import com.aegisguard.hooks.JobsRebornHook;
import com.aegisguard.hooks.MapHookManager;
import com.aegisguard.hooks.McMMOHook;
import com.aegisguard.hooks.MobBarrierTask;
import com.aegisguard.hooks.WildernessRevertTask;
import com.aegisguard.listeners.BannedPlayerListener;
import com.aegisguard.listeners.ChatInputListener;
import com.aegisguard.listeners.LevelingListener;
import com.aegisguard.listeners.MigrationListener;
import com.aegisguard.listeners.ProtectionListener;
import com.aegisguard.managers.*; // This imports EconomyManager correctly now
import com.aegisguard.protection.ProtectionManager;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.util.EffectUtil;
import com.aegisguard.visualization.WandEquipListener;
import com.aegisguard.world.WorldRulesManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class AegisGuard extends JavaPlugin {

    private static AegisGuard instance;

    public static AegisGuard getInstance() {
        return instance;
    }
    
    // --- v1.3.0 MANAGERS ---
    private AGConfig configMgr;
    private IDataStore dataStore;
    private LanguageManager languageManager;
    private RoleManager roleManager;
    private EstateManager estateManager;
    private AllianceManager allianceManager;
    private EconomyManager economyManager;
    private PetitionManager petitionManager;
    private LandGrantManager landGrantManager;
    private ProgressionManager progressionManager;
    private GUIManager guiManager;
    
    // --- LEGACY / UTILS ---
    private SelectionService selection;
    private WorldRulesManager worldRules;
    private EffectUtil effectUtil;
    private VaultHook vault;
    
    // --- HOOKS ---
    private MapHookManager mapHookManager;
    private DiscordWebhook discord;
    private ProtectionManager protectionManager;
    private McMMOHook mcmmoHook;
    private CoreProtectHook coreProtectHook;
    private JobsRebornHook jobsHook;

    private boolean isFolia = false;
    
    // Task Objects
    private Object autoSaveTask;
    private Object upkeepTask;
    private Object wildernessRevertTask;
    private Object mobBarrierTask;

    // --- GETTERS ---
    public AGConfig cfg() { return configMgr; }
    public IDataStore getDataStore() { return dataStore; }
    
    public LanguageManager getLanguageManager() { return languageManager; }
    public RoleManager getRoleManager() { return roleManager; }
    public EstateManager getEstateManager() { return estateManager; }
    public AllianceManager getAllianceManager() { return allianceManager; }
    public EconomyManager getEconomy() { return economyManager; }
    public PetitionManager getPetitionManager() { return petitionManager; }
    public LandGrantManager getLandGrantManager() { return landGrantManager; }
    public ProgressionManager getProgressionManager() { return progressionManager; }
    public GUIManager getGuiManager() { return guiManager; }
    
    public ProtectionManager getProtectionManager() { return protectionManager; }
    public SelectionService getSelection() { return selection; }
    public WorldRulesManager getWorldRules() { return worldRules; }
    public EffectUtil getEffects() { return effectUtil; }
    public DiscordWebhook getDiscord() { return discord; }
    public MapHookManager getMapHooks() { return mapHookManager; }
    public boolean isFolia() { return isFolia; }
    public VaultHook getVault() { return vault; }
    
    public McMMOHook getMcMMO() { return mcmmoHook; }
    public CoreProtectHook getCoreProtect() { return coreProtectHook; }
    public JobsRebornHook getJobs() { return jobsHook; }

    // Legacy Aliases
    public AGConfig getConfigManager() { return configMgr; }
    public IDataStore store() { return dataStore; }
    public GUIManager gui() { return guiManager; }
    public EconomyManager eco() { return economyManager; }
    public ProtectionManager protection() { return protectionManager; }
    public SelectionService selection() { return selection; }
    public EffectUtil effects() { return effectUtil; }
    public VaultHook vault() { return vault; }

    @Override
    public void onEnable() {
        instance = this;

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("Folia detected. Enabling region scheduler hooks.");
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }

        saveDefaultConfig();
        
        // Messages.yml legacy check removed

        this.configMgr = new AGConfig(this);
        
        // 1. Initialize Core Managers
        this.languageManager = new LanguageManager(this);
        this.roleManager = new RoleManager(this);
        this.estateManager = new EstateManager(this);
        this.allianceManager = new AllianceManager(this);
        
        // 2. Initialize Data Store
        String storageType = cfg().raw().getString("storage.type", "yml").toLowerCase();
        if (storageType.contains("sql")) {
            this.dataStore = new SQLDataStore(this);
        } else {
            this.dataStore = new YMLDataStore(this);
        }
        this.dataStore.load();

        // 3. Initialize Economy & Gameplay
        this.vault = new VaultHook(this);
        this.economyManager = new EconomyManager(this);
        this.progressionManager = new ProgressionManager(this);
        this.petitionManager = new PetitionManager(this);
        this.landGrantManager = new LandGrantManager(this);
        
        // 4. Initialize Utils & Visuals
        this.effectUtil = new EffectUtil(this);
        this.worldRules = new WorldRulesManager(this);
        this.selection = new SelectionService(this);
        this.guiManager = new GUIManager(this);
        this.discord = new DiscordWebhook(this);
        this.protectionManager = new ProtectionManager(this);

        // 5. Run Migration
        new DataConverter(this, estateManager).runMigration();
        
        // --- REGISTER EVENTS ---
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MigrationListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(protectionManager, this);
        Bukkit.getPluginManager().registerEvents(selection, this);
        
        if (cfg().isProgressionEnabled()) {
             try {
                 Class.forName("com.aegisguard.listeners.LevelingListener");
                 Bukkit.getPluginManager().registerEvents(new LevelingListener(this), this);
             } catch (ClassNotFoundException ignored) {}
        }
        
        if (cfg().raw().getBoolean("visuals.particles.border_enabled", true)) {
            Bukkit.getPluginManager().registerEvents(new WandEquipListener(this), this);
        }
        
        if (cfg().autoRemoveBannedPlots()) {
             Bukkit.getPluginManager().registerEvents(new BannedPlayerListener(this), this);
        }

        // --- COMMANDS ---
        CommandHandler cmdHandler = new CommandHandler(this);
        PluginCommand aegis = getCommand("aegis");
        if (aegis != null) {
            aegis.setExecutor(cmdHandler);
        }

        PluginCommand admin = getCommand("aegisadmin");
        if (admin != null) {
            AdminCommand adminExecutor = new AdminCommand(this);
            admin.setExecutor(adminExecutor);
            admin.setTabCompleter(adminExecutor);
        }

        // --- TASKS ---
        startAutoSaver();
        if (cfg().isUpkeepEnabled()) startUpkeepTask();
        startWildernessRevertTask(); 
        startMobBarrierTask();
        
        initializeHooks();
        getLogger().info("AegisGuard v1.3.0 enabled successfully.");
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
        
        if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")) {
            this.mcmmoHook = new McMMOHook(this);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("CoreProtect")) {
            this.coreProtectHook = new CoreProtectHook(this);
        }
        
        if (Bukkit.getPluginManager().isPluginEnabled("Jobs")) {
            this.jobsHook = new JobsRebornHook(this);
        }
    }

    @Override
    public void onDisable() {
        cancelTaskReflectively(autoSaveTask);
        cancelTaskReflectively(upkeepTask);
        cancelTaskReflectively(wildernessRevertTask);
        cancelTaskReflectively(mobBarrierTask);

        if (dataStore != null) dataStore.saveSync();
        if (petitionManager != null) petitionManager.saveSync();
        
        instance = null;
        getLogger().info("AegisGuard disabled.");
    }
    
    // --- UTILITY METHODS ---
    public boolean isSoundEnabled(Player player) {
        if (!cfg().globalSoundsEnabled()) return false;
        String key = "sounds.players." + player.getUniqueId();
        return getConfig().getBoolean(key, true);
    }
    
    public boolean isAdmin(Player player) {
        if (!cfg().raw().getBoolean("admin.trust_operators", true)) {
            return player.hasPermission("aegis.admin");
        }
        return player.isOp() || player.hasPermission("aegis.admin");
    }

    // --- SCHEDULERS ---
    public void runGlobalAsync(Runnable task) {
        if (isFolia) {
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                Method runMethod = scheduler.getClass().getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);
                runMethod.invoke(scheduler, this, (Consumer<Object>) t -> task.run());
            } catch (Exception e) { e.printStackTrace(); }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(this, task);
        }
    }
    
    public void runMain(Player player, Runnable task) {
        if (player == null) { runMainGlobal(task); return; }
        if (isFolia) {
            try {
                Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
                Method runMethod = scheduler.getClass().getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class, Runnable.class);
                runMethod.invoke(scheduler, this, (Consumer<Object>) t -> task.run(), null);
            } catch (Exception e) {}
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }
    
    public void runMainGlobal(Runnable task) {
        if (isFolia) {
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                Method runMethod = scheduler.getClass().getMethod("run", org.bukkit.plugin.Plugin.class, Consumer.class);
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
                Method runMethod = scheduler.getClass().getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class, Consumer.class, long.class, long.class);
                return runMethod.invoke(scheduler, this, (Consumer<Object>) t -> task.run(), intervalTicks, intervalTicks);
            } catch (Exception e) { return null; }
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

    private void startAutoSaver() {
        long interval = 20L * 60 * 5; 
        Runnable logic = () -> {
            if (dataStore != null && dataStore.isDirty()) dataStore.save();
            if (petitionManager != null && petitionManager.isDirty()) petitionManager.save();
        };
        autoSaveTask = scheduleAsyncRepeating(logic, interval);
    }

    private void startUpkeepTask() {
        long interval = (long) (20L * 60 * 60 * cfg().getUpkeepCheckHours());
        if (interval <= 0) return;
        Runnable logic = () -> {
            for (com.aegisguard.objects.Estate e : estateManager.getAllEstates()) {
                 double cost = economyManager.calculateDailyUpkeep(e);
                 if (!e.withdraw(cost)) {
                     // Handle bankruptcy
                 }
            }
        };
        upkeepTask = scheduleAsyncRepeating(logic, interval);
    }
    
    private void startWildernessRevertTask() {
        if (!cfg().raw().getBoolean("wilderness_revert.enabled", false)) return;
        String storage = cfg().raw().getString("storage.type", "yml");
        if (!storage.equalsIgnoreCase("sql") && !storage.equalsIgnoreCase("mysql") && !storage.equalsIgnoreCase("mariadb")) return;
        long interval = 20L * 60 * cfg().raw().getLong("wilderness_revert.check_interval_minutes", 10);
        WildernessRevertTask task = new WildernessRevertTask(this, dataStore);
        wildernessRevertTask = scheduleAsyncRepeating(task::run, interval);
    }
    
    private void startMobBarrierTask() {
        long interval = cfg().raw().getLong("mob_barrier.check_interval_ticks", 60);
        MobBarrierTask task = new MobBarrierTask(this);
        mobBarrierTask = scheduleAsyncRepeating(task::run, interval);
    }
}
