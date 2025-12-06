package com.aegisguard;

import com.aegisguard.admin.AdminCommand;
import com.aegisguard.commands.CommandHandler;
import com.aegisguard.config.AGConfig;
import com.aegisguard.data.IDataStore;
import com.aegisguard.data.SQLDataStore;
import com.aegisguard.data.YMLDataStore;
import com.aegisguard.economy.VaultHook; 
// FIXED: EconomyManager is in managers package
import com.aegisguard.managers.EconomyManager; 
import com.aegisguard.gui.GUIManager;

import com.aegisguard.hooks.AegisPAPIExpansion;
import com.aegisguard.hooks.DiscordWebhook;
import com.aegisguard.hooks.JobsRebornHook;
import com.aegisguard.hooks.MapHookManager;
import com.aegisguard.hooks.McMMOHook;
import com.aegisguard.hooks.MobBarrierTask;
import com.aegisguard.hooks.WildernessRevertTask;
import com.aegisguard.listeners.BannedPlayerListener;
import com.aegisguard.listeners.ChatInputListener;
// FIXED: GUIListener is in listeners package
import com.aegisguard.listeners.GUIListener; 
import com.aegisguard.listeners.LevelingListener;
import com.aegisguard.listeners.MigrationListener;
import com.aegisguard.listeners.ProtectionListener;
import com.aegisguard.managers.*; 
import com.aegisguard.protection.ProtectionManager;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.util.EffectUtil;
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
    private LanguageManager languageManager; // Replaces MessagesUtil
    private WorldRulesManager worldRules;
    private EffectUtil effectUtil;
    
    // New v1.2.2 Managers (Replaces Expansion/Sidebar)
    private PetitionManager petitionManager;
    private LandGrantManager landGrantManager;
    private AllianceManager allianceManager;
    private EstateManager estateManager;
    private RoleManager roleManager;
    private ProgressionManager progressionManager;
    
    // --- HOOKS ---
    private MapHookManager mapHookManager;
    private DiscordWebhook discord;
    private McMMOHook mcmmoHook;
    private JobsRebornHook jobsHook;

    private boolean isFolia = false;
    
    // Task Objects
    private Object autoSaveTask;
    private Object upkeepTask;
    private Object wildernessRevertTask;
    private Object mobBarrierTask;

    // --- GETTERS ---
    public AGConfig cfg() { return configMgr; }
    public IDataStore store() { return plotStore; }
    public IDataStore getDataStore() { return plotStore; } // Alias
    public GUIManager gui() { return gui; }
    public GUIManager getGuiManager() { return gui; } // Alias
    public ProtectionManager protection() { return protection; }
    public SelectionService selection() { return selection; }
    public SelectionService getSelection() { return selection; } // Alias
    public VaultHook vault() { return vault; }
    public EconomyManager eco() { return ecoManager; }
    public EconomyManager getEconomy() { return ecoManager; } // Alias
    public LanguageManager getLanguageManager() { return languageManager; }
    public WorldRulesManager worldRules() { return worldRules; }
    public WorldRulesManager getWorldRules() { return worldRules; } // Alias
    public EffectUtil effects() { return effectUtil; }
    public DiscordWebhook getDiscord() { return discord; }
    public MapHookManager getMapHooks() { return mapHookManager; }
    public boolean isFolia() { return isFolia; }
    
    // v1.2.2 Getters
    public PetitionManager getPetitionManager() { return petitionManager; }
    public LandGrantManager getLandGrantManager() { return landGrantManager; }
    public AllianceManager getAllianceManager() { return allianceManager; }
    public EstateManager getEstateManager() { return estateManager; }
    public RoleManager getRoleManager() { return roleManager; }
    public ProgressionManager getProgressionManager() { return progressionManager; }
    
    public McMMOHook getMcMMO() { return mcmmoHook; }
    public JobsRebornHook getJobs() { return jobsHook; }

    @Override
    public void onEnable() {
        instance = this;

        // --- 1. ROBUST FOLIA DETECTION ---
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("✅ Folia detected! Enabling Region Scheduler compatibility.");
        } catch (ClassNotFoundException e) {
            isFolia = false;
            getLogger().info("Standard Bukkit/Spigot detected (Not Folia).");
        }

        saveDefaultConfig();
        
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }

        this.configMgr = new AGConfig(this);
        
        // --- 2. INITIALIZE MANAGERS ---
        this.languageManager = new LanguageManager(this);
        this.roleManager = new RoleManager(this);
        this.estateManager = new EstateManager(this);
        this.allianceManager = new AllianceManager(this);
        
        // Initialize Data Store
        String storageType = cfg().raw().getString("storage.type", "yml").toLowerCase();
        if (storageType.contains("mysql") || storageType.contains("mariadb") || storageType.contains("sql")) {
            getLogger().info("Initializing SQL DataStore...");
            this.plotStore = new SQLDataStore(this);
        } else {
            getLogger().info("Initializing YML DataStore...");
            this.plotStore = new YMLDataStore(this);
        }
        
        // Core Managers
        this.selection = new SelectionService(this);
        this.gui = new GUIManager(this);
        this.vault = new VaultHook(this);
        this.ecoManager = new EconomyManager(this);
        this.worldRules = new WorldRulesManager(this);
        this.effectUtil = new EffectUtil(this);
        this.discord = new DiscordWebhook(this);
        
        // New Managers
        this.progressionManager = new ProgressionManager(this);
        this.petitionManager = new PetitionManager(this);
        this.landGrantManager = new LandGrantManager(this);
        this.protection = new ProtectionManager(this);

        // Load Data
        this.plotStore.load();
        
        // Run Migration if needed
        new DataConverter(this, estateManager).runMigration();

        // Register Events
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MigrationListener(this), this);
        Bukkit.getPluginManager().registerEvents(protection, this); 
        Bukkit.getPluginManager().registerEvents(selection, this);
        
        if (cfg().isLevelingEnabled()) {
             try {
                 Class.forName("com.aegisguard.listeners.LevelingListener");
                 Bukkit.getPluginManager().registerEvents(new LevelingListener(this), this);
             } catch (ClassNotFoundException ignored) {}
        }
        
        if (cfg().raw().getBoolean("visuals.particles.border_enabled", true)) {
            Bukkit.getPluginManager().registerEvents(new WandEquipListener(this), this);
        }
        
        if (cfg().autoRemoveBannedPlots()) {
             try {
                 Class.forName("com.aegisguard.listeners.BannedPlayerListener");
                 Bukkit.getPluginManager().registerEvents(new BannedPlayerListener(this), this);
             } catch (ClassNotFoundException ignored) {}
        }

        // Register Commands
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

        // Start Tasks
        startAutoSaver();
        if (cfg().isUpkeepEnabled()) startUpkeepTask();
        startWildernessRevertTask(); 
        startMobBarrierTask();
        
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
        if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")) {
            this.mcmmoHook = new McMMOHook(this);
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

        if (plotStore != null) plotStore.saveSync();
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
                Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
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
                Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
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
                Method runMethod = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
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
            if (plotStore != null && plotStore.isDirty()) plotStore.save();
            if (petitionManager != null && petitionManager.isDirty()) petitionManager.save();
        };
        autoSaveTask = scheduleAsyncRepeating(logic, interval);
    }

    private void startUpkeepTask() {
        long interval = (long) (20L * 60 * 60 * cfg().getUpkeepCheckHours());
        if (interval <= 0) return;
        Runnable logic = () -> {
            // Upkeep logic
            for (com.aegisguard.objects.Estate e : estateManager.getAllEstates()) {
                 double cost = ecoManager.calculateDailyUpkeep(e);
                 if (!e.withdraw(cost)) { } 
            }
        };
        upkeepTask = scheduleAsyncRepeating(logic, interval);
    }
    
    private void startWildernessRevertTask() {
        if (!cfg().raw().getBoolean("wilderness_revert.enabled", false)) return;
        if (plotStore instanceof YMLDataStore) return; // Only runs on SQL
        
        long interval = 20L * 60 * cfg().raw().getLong("wilderness_revert.check_interval_minutes", 10);
        WildernessRevertTask task = new WildernessRevertTask(this, plotStore);
        wildernessRevertTask = scheduleAsyncRepeating(task::run, interval);
    }
    
    private void startMobBarrierTask() {
        long interval = cfg().raw().getLong("mob_barrier.check_interval_ticks", 60);
        MobBarrierTask task = new MobBarrierTask(this);
        mobBarrierTask = scheduleAsyncRepeating(task::run, interval);
    }
}
