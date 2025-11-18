package com.aegisguard;

import com.aegisguard.admin.AdminCommand;
import com.aegisguard.config.AGConfig;
import com.aegisguard.data.IDataStore;
import com.aegisguard.data.Plot;
import com.aegisguard.data.SQLDataStore;
import com.aegisguard.data.YMLDataStore;
import com.aegisguard.economy.VaultHook;
import com.aegisguard.expansions.ExpansionRequestManager;
import com.aegisguard.gui.GUIListener;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.protection.ProtectionManager;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.util.EffectUtil;
import com.aegisguard.util.MessagesUtil;
import com.aegisguard.visualization.WandEquipListener;
import com.aegisguard.world.WorldRulesManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AegisGuard – main entrypoint
 *
 * --- FINAL VERSION ---
 * - Now uses IDataStore interface to switch between YML and SQL.
 * - Fully Folia-compatible with safe schedulers.
 * - All systems (Roles, Resize, Upkeep, Admin GUI, Protections) are complete.
 */
public class AegisGuard extends JavaPlugin {

    // --- (fields) ---
    private AGConfig configMgr;
    private IDataStore plotStore; // Uses the interface
    private GUIManager gui;
    private ProtectionManager protection;
    private SelectionService selection;
    private VaultHook vault;
    private MessagesUtil messages;
    private WorldRulesManager worldRules;
    private EffectUtil effectUtil;
    private ExpansionRequestManager expansionManager;

    private boolean isFolia = false;
    private BukkitTask autoSaveTask;
    private BukkitTask upkeepTask;

    // --- (getters) ---
    public AGConfig cfg()           { return configMgr; }
    public IDataStore store()       { return plotStore; }
    public GUIManager gui()         { return gui; }
    public ProtectionManager protection(){ return protection; }
    public SelectionService selection()  { return selection; }
    public VaultHook vault()         { return vault; }
    public MessagesUtil msg()       { return messages; }
    public WorldRulesManager worldRules(){ return worldRules; }
    public EffectUtil effects()     { return effectUtil; }
    public ExpansionRequestManager getExpansionRequestManager() { return expansionManager; }
    public boolean isFolia()        { return isFolia; }


    @Override
    public void onEnable() {
        // --- Folia Check ---
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("Folia detected. Enabling Folia-compatible schedulers.");
        } catch (ClassNotFoundException e) {
            isFolia = false;
            getLogger().info("Folia not found. Using standard Bukkit schedulers.");
        }

        // Bundle defaults
        saveDefaultConfig();
        saveResource("messages.yml", false);

        // Core systems
        this.configMgr  = new AGConfig(this);
        
        // --- Database Loader ---
        String storageType = cfg().raw().getString("storage.type", "yml").toLowerCase();
        if (storageType.equals("sql") || storageType.equals("mysql") || storageType.equals("sqlite")) {
            getLogger().info("Using SQL database for plot storage.");
            this.plotStore = new SQLDataStore(this);
        } else {
            getLogger().info("Using YML file for plot storage.");
            this.plotStore = new YMLDataStore(this);
        }
        
        this.selection  = new SelectionService(this);
        this.messages   = new MessagesUtil(this);
        this.gui        = new GUIManager(this); 
        this.vault      = new VaultHook(this);
        this.worldRules = new WorldRulesManager(this);
        this.protection = new ProtectionManager(this);
        this.effectUtil = new EffectUtil(this);
        this.expansionManager = new ExpansionRequestManager(this);

        // --- Folia-safe Async Load ---
        runGlobalAsync(() -> {
            messages.loadPlayerPreferences();
            plotStore.load(); // Load plots from YML or SQL
        });

        // Listeners
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(protection, this); // --- TYPO FIX ---
        Bukkit.getPluginManager().registerEvents(selection, this);
        if (cfg().raw().getBoolean("visualization.enabled", true)) {
            Bukkit.getPluginManager().registerEvents(new WandEquipListener(this), this);
        }

        // Register banned player listener
        if (cfg().autoRemoveBannedPlots()) { 
            Bukkit.getPluginManager().registerEvents(new BannedPlayerListener(this), this);

            // Also run an async check on startup
            runGlobalAsync(() -> {
                getLogger().info("Running async check for banned player plots...");
                store().removeBannedPlots();
            });
        }

        // Commands
        PluginCommand aegis = getCommand("aegis");
        if (aegis == null) {
            getLogger().severe("Missing /aegis command in plugin.yml. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        AegisCommand aegisExecutor = new AegisCommand(this);
        aegis.setExecutor(aegisExecutor);
        aegis.setTabCompleter(aegisExecutor);

        PluginCommand admin = getCommand("aegisadmin");
        if (admin != null) {
            AdminCommand adminExecutor = new AdminCommand(this);
            admin.setExecutor(adminExecutor);
            admin.setTabCompleter(adminExecutor);
        } else {
            getLogger().warning("No /aegisadmin command defined; admin tools will be unavailable.");
        }

        // Start auto-saver
        startAutoSaver();
        
        // Start upkeep task
        if (cfg().isUpkeepEnabled()) {
            startUpkeepTask();
        }

        getLogger().info("AegisGuard v" + getDescription().getVersion() + " enabled.");
        getLogger().info("WorldRulesManager initialized for per-world protections.");
    }

    @Override
    public void onDisable() {
        // --- MODIFIED ---
        // Cancel all repeating tasks
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
        }
        if (upkeepTask != null && !upkeepTask.isCancelled()) {
            upkeepTask.cancel();
        }

        // Gracefully save all data on shutdown
        if (plotStore != null) {
            getLogger().info("Saving plots to disk...");
            plotStore.saveSync();
        }
        if (expansionManager != null) {
            getLogger().info("Saving pending expansion requests to disk...");
            expansionManager.saveSync();
        }
        if (messages != null) {
            getLogger().info("Saving player preferences to disk...");
            messages.savePlayerData();
        }
        getLogger().info("AegisGuard disabled. Data saved.");
    }

    /**
     * Starts the asynchronous auto-saver task.
     * This is the central hub for all async data saving.
     */
    private void startAutoSaver() {
        long intervalTicks = 20L * 60 * 5; // 5 minutes

        Runnable autoSaveLogic = () -> {
            // Check PlotStore
            if (plotStore != null && plotStore.isDirty()) {
                getLogger().info("Auto-saving plot data in the background...");
                plotStore.save(); // This is the synchronized save method
                getLogger().info("Auto-save complete.");
            }

            // Check ExpansionRequestManager
            if (expansionManager != null && expansionManager.isDirty()) {
                getLogger().info("Auto-saving expansion request data in the background...");
                expansionManager.save(); // This is the synchronized save method
                getLogger().info("Expansion auto-save complete.");
            }

            // Check MessagesUtil for player data
            if (messages != null && messages.isPlayerDataDirty()) {
                getLogger().info("Auto-saving player data in the background...");
                messages.savePlayerData(); // This is the synchronized save method
                getLogger().info("Player data auto-save complete.");
            }
        };

        if (isFolia) {
            autoSaveTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, 
                    (task) -> autoSaveLogic.run(), 
                    intervalTicks, 
                    intervalTicks
            );
        } else {
            autoSaveTask = new BukkitRunnable() {
                @Override
                public void run() {
                    autoSaveLogic.run();
                }
            }.runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
        }
    }

    
    /**
     * Starts the asynchronous plot upkeep/rent task.
     */
    private void startUpkeepTask() {
        long intervalTicks = 20L * 60 * 60 * cfg().getUpkeepCheckHours(); // Ticks * Sec * Min * Hours
        if (intervalTicks <= 0) {
            getLogger().warning("Upkeep check_interval_hours is 0 or less. Upkeep task will not run.");
            return;
        }
        
        double cost = cfg().getUpkeepCost();
        if (cost <= 0) {
             getLogger().info("Upkeep cost is 0. Upkeep task will run but will not charge players.");
        }
        
        long gracePeriodMillis = TimeUnit.DAYS.toMillis(cfg().getUpkeepGraceDays());

        getLogger().info("Starting Upkeep Task. Check interval: " + cfg().getUpkeepCheckHours() + " hours. Cost: " + cost);

        Runnable upkeepLogic = () -> {
            getLogger().info("[Upkeep] Running plot upkeep check...");
            long currentTime = System.currentTimeMillis();
            long checkIntervalMillis = TimeUnit.HOURS.toMillis(cfg().getUpkeepCheckHours());

            for (Plot plot : store().getAllPlots()) {
                long timeSinceLastPayment = currentTime - plot.getLastUpkeepPayment();

                // Has it been long enough to check?
                if (timeSinceLastPayment >= checkIntervalMillis) {
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());
                    
                    // Try to charge the player
                    if (vault().charge(owner.getPlayer(), cost)) {
                        // Payment success
                        plot.setLastUpkeepPayment(currentTime);
                        store().setDirty(true); // Mark for saving
                        
                        if (owner.isOnline()) {
                            msg().send(owner.getPlayer(), "upkeep_paid", Map.of("AMOUNT", vault().format(cost)));
                        }
                    } else {
                        // Payment failed
                        long timeOverdue = currentTime - plot.getLastUpkeepPayment();
                        
                        if (timeOverdue > gracePeriodMillis) {
                            // Grace period is over. Unclaim the plot.
                            getLogger().info("[Upkeep] Plot " + plot.getPlotId() + " by " + owner.getName() + " has expired. Unclaiming.");
                            store().removePlot(plot.getOwner(), plot.getPlotId());
                            // store.removePlot already sets the dirty flag
                            
                            if (owner.isOnline()) {
                                msg().send(owner.getPlayer(), "plot_expired_upkeep");
                            }
                        } else {
                            // Still in grace period.
                            if (owner.isOnline()) {
                                msg().send(owner.getPlayer(), "upkeep_failed_warning");
                            }
                        }
                    }
                }
            }
            getLogger().info("[Upkeep] Upkeep check complete.");
        };

        if (isFolia) {
            upkeepTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, 
                    (task) -> upkeepLogic.run(), 
                    intervalTicks, 
                    intervalTicks
            );
        } else {
            upkeepTask = new BukkitRunnable() {
                @Override
                public void run() {
                    upkeepLogic.run();
                }
            }.runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
        }
    }


    /* -----------------------------
     * Utility: Sound Control
     * (This is the central check used by EffectUtil)
     * ----------------------------- */
    public boolean isSoundEnabled(Player player) {
        if (!cfg().globalSoundsEnabled()) return false; // Use getter
        String key = "sounds.players." + player.getUniqueId();
        // Return the value if set, otherwise default to true
        return getConfig().getBoolean(key, true);
    }

    /* -----------------------------
     * --- NEW: Folia-safe Schedulers ---
     * ----------------------------- */

    /**
     * Runs a task asynchronously on the global scheduler (Folia)
     * or the standard Bukkit async scheduler (Paper/Spigot).
     */
    public void runGlobalAsync(Runnable task) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(this, (scheduledTask) -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(this, task);
        }
    }
    
    /**
     * Runs a task on the main thread for a specific player (Folia)
     * or the standard Bukkit main thread (Paper/Spigot).
     */
    public void runMain(Player player, Runnable task) {
        if (isFolia) {
            player.getScheduler().run(this, (scheduledTask) -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }
    
    /**
     * Runs a task on the main server thread (Folia)
     * or the standard Bukkit main thread (Paper/Spigot).
     * Used for console/non-player tasks.
     */
    public void runMainGlobal(Runnable task) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(this, (scheduledTask) -> task.run());
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }
}
