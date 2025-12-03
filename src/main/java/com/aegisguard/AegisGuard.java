package com.aegisguard;

import com.aegisguard.admin.AdminCommand;
import com.aegisguard.commands.AegisCommand;
import com.aegisguard.commands.CommandHandler; // v1.3.0 Command Router
import com.aegisguard.config.AGConfig;
import com.aegisguard.data.IDataStore;
import com.aegisguard.data.SQLDataStore;
import com.aegisguard.data.YMLDataStore;
import com.aegisguard.economy.EconomyManager;
import com.aegisguard.economy.VaultHook;
import com.aegisguard.expansions.ExpansionRequestManager;
import com.aegisguard.gui.GUIListener;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.gui.SidebarManager; 
import com.aegisguard.hooks.AegisPAPIExpansion;
import com.aegisguard.hooks.DiscordWebhook;
import com.aegisguard.hooks.MapHookManager;
import com.aegisguard.hooks.MobBarrierTask;
import com.aegisguard.hooks.WildernessRevertTask;
import com.aegisguard.listeners.BannedPlayerListener;
import com.aegisguard.listeners.ChatInputListener; // v1.3.0 NEW
import com.aegisguard.listeners.LevelingListener;
import com.aegisguard.listeners.MigrationListener; // v1.3.0
import com.aegisguard.managers.*; // v1.3.0 New Managers
import com.aegisguard.protection.ProtectionManager;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.util.EffectUtil;
import com.aegisguard.util.MessagesUtil;
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

    // --- SINGLETON PATTERN ---
    private static AegisGuard instance;

    public static AegisGuard getInstance() {
        return instance;
    }
    
    // --- EXISTING MANAGERS ---
    private AGConfig configMgr;
    private IDataStore plotStore; // Legacy support
    private GUIManager gui;
    private ProtectionManager protection;
    private SelectionService selection;
    private VaultHook vault;
    private EconomyManager ecoManager;
    private MessagesUtil messages;
    private WorldRulesManager worldRules;
    private EffectUtil effectUtil;
    private ExpansionRequestManager expansionManager;
    private SidebarManager sidebarManager; 
    
    // --- v1.3.0 NEW MANAGERS ---
    private LanguageManager languageManager;
    private RoleManager roleManager;
    private EstateManager estateManager;
    private AllianceManager allianceManager;
    
    // --- HOOKS ---
    private MapHookManager mapHookManager;
    private DiscordWebhook discord;

    private boolean isFolia = false;
    
    // Task Objects
    private Object autoSaveTask;
    private Object upkeepTask;
    private Object wildernessRevertTask;
    private Object mobBarrierTask;

    // --- GETTERS ---
    public AGConfig cfg() { return configMgr; }
    public IDataStore store() { return plotStore; }
    public GUIManager gui() { return gui; }
    public ProtectionManager protection() { return protection; }
    public SelectionService selection() { return selection; }
    public VaultHook vault() { return vault; }
    public EconomyManager eco() { return ecoManager; }
    public MessagesUtil msg() { return messages; }
    public WorldRulesManager worldRules() { return worldRules; }
    public EffectUtil effects() { return effectUtil; }
    public ExpansionRequestManager getExpansionRequestManager() { return expansionManager; }
    public DiscordWebhook getDiscord() { return discord; }
    public MapHookManager getMapHooks() { return mapHookManager; }
    public SidebarManager getSidebar() { return sidebarManager; }
    public boolean isFolia() { return isFolia; }

    // v1.3.0 Getters
    public LanguageManager getLanguageManager() { return languageManager; }
    public RoleManager getRoleManager() { return roleManager; }
    public EstateManager getEstateManager() { return estateManager; }
    public AllianceManager getAllianceManager() { return allianceManager; }

    @Override
    public void onEnable() {
        // Initialize Singleton
        instance = this;

        // Detect Server Software
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("Folia detected. Enabling region scheduler hooks.");
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }

        // Load Configs
        saveDefaultConfig();
        
        // Fix: Ensure messages.yml exists (legacy support)
        if (!new File(getDataFolder(), "messages.yml").exists()) {
            saveResource("messages.yml", false);
        }

        this.configMgr = new AGConfig(this);
        
        // --- v1.3.0 INITIALIZATION START ---
        // 1. Load Languages First (So other managers can use messages)
        this.languageManager = new LanguageManager(this);
        
        // 2. Load Roles (Permission Brain)
        this.roleManager = new RoleManager(this);
        
        // 3. Load Estates (Land Data)
        this.estateManager = new EstateManager(this);
        
        // 4. Load Guilds (Team Data)
        this.allianceManager = new AllianceManager(this);
        
        // 5. Run Legacy Migration Check (Converts old plots to Estates)
        new DataConverter(this, estateManager).runMigration();
        // --- v1.3.0 INITIALIZATION END ---

        // Initialize Legacy Data Store (Keep for safe transition)
        String storageType = cfg().raw().getString("storage.type", "yml").toLowerCase();
        if (storageType.contains("sql")) {
            this.plotStore = new SQLDataStore(this);
        } else {
            this.plotStore = new YMLDataStore(this);
        }
        
        // Initialize Core Managers
        this.selection = new SelectionService(this);
        this.messages = new MessagesUtil(this);
        this.gui = new GUIManager(this); 
        this.vault = new VaultHook(this);
        this.ecoManager = new EconomyManager(this);
        this.worldRules = new WorldRulesManager(this);
        this.effectUtil = new EffectUtil(this);
        this.expansionManager = new ExpansionRequestManager(this);
        this.discord = new DiscordWebhook(this);
        
        // Initialize Sidebar & Protection
        this.sidebarManager = new SidebarManager(this); 
        this.protection = new ProtectionManager(this);

        // Load Legacy Data (if needed)
        this.plotStore.load();
        
        runGlobalAsync(() -> {
            messages.loadPlayerPreferences();
            expansionManager.load();
        });

        // --- REGISTER EVENTS ---
        // v1.3.0 GUI Listener (Handles Codex/Dashboard clicks)
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        
        // v1.3.0 Chat Input Listener (Handles Renaming/Creation via Chat)
        Bukkit.getPluginManager().registerEvents(new ChatInputListener(this), this);
        
        // v1.3.0 Migration Notification (Welcome Message)
        Bukkit.getPluginManager().registerEvents(new MigrationListener(this), this);

        Bukkit.getPluginManager().registerEvents(protection, this); 
        Bukkit.getPluginManager().registerEvents(selection, this);
        
        // Register Leveling Listener
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
        // v1.3.0 Command Router (/ag)
        PluginCommand aegis = getCommand("aegis");
        if (aegis != null) {
            // Note: We are switching to the new CommandHandler for v1.3.0
            CommandHandler mainExecutor = new CommandHandler(this); 
            aegis.setExecutor(mainExecutor);
            // aegis.setTabCompleter(mainExecutor); // Implement TabCompleter later
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
    }

    @Override
    public void onDisable() {
        cancelTaskReflectively(autoSaveTask);
        cancelTaskReflectively(upkeepTask);
        cancelTaskReflectively(wildernessRevertTask);
        cancelTaskReflectively(mobBarrierTask);

        if (plotStore != null) plotStore.saveSync();
        if (expansionManager != null) expansionManager.saveSync();
        if (messages != null) messages.savePlayerData();
        
        instance = null; 
        getLogger().info("AegisGuard disabled.");
    }
    
    // ... rest of the utility methods remain unchanged ...
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

    public void runGlobalAsync(Runnable task) {
        if (isFolia) {
            try {
                Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                Method runMethod = scheduler.getClass().getMethod("run", JavaPlugin.class, Consumer.class);
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
                Method runMethod = scheduler.getClass().getMethod("run", JavaPlugin.class, Consumer.class, Runnable.class);
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
                Method runMethod = scheduler.getClass().getMethod("run", JavaPlugin.class, Consumer.class);
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
                Method runMethod = scheduler.getClass().getMethod("runAtFixedRate", JavaPlugin.class, Consumer.class, long.class, long.class);
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
            if (expansionManager != null && expansionManager.isDirty()) expansionManager.save();
            if (messages != null && messages.isPlayerDataDirty()) messages.savePlayerData();
        };
        autoSaveTask = scheduleAsyncRepeating(logic, interval);
    }

    private void startUpkeepTask() {
        long interval = (long) (20L * 60 * 60 * cfg().getUpkeepCheckHours());
        if (interval <= 0) return;
        Runnable logic = () -> {
            // Placeholder for upkeep logic
        };
        upkeepTask = scheduleAsyncRepeating(logic, interval);
    }
    
    private void startWildernessRevertTask() {
        if (!cfg().raw().getBoolean("wilderness_revert.enabled", false)) return;
        String storage = cfg().raw().getString("storage.type", "yml");
        if (!storage.equalsIgnoreCase("sql") && !storage.equalsIgnoreCase("mysql") && !storage.equalsIgnoreCase("mariadb")) {
            getLogger().warning("Wilderness Revert enabled but storage is not SQL. Feature disabled.");
            return; 
        }
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
