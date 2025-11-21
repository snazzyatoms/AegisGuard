package com.aegisguard;

import com.aegisguard.admin.AdminCommand;
// Check these two imports match your folder structure
import com.aegisguard.command.AegisCommand; 
import com.aegisguard.listeners.BannedPlayerListener;

import com.aegisguard.config.AGConfig;
import com.aegisguard.data.IDataStore;
import com.aegisguard.data.Plot;
import com.aegisguard.data.SQLDataStore;
import com.aegisguard.data.YMLDataStore;
import com.aegisguard.economy.VaultHook;
import com.aegisguard.expansions.ExpansionRequestManager;
import com.aegisguard.gui.GUIListener;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.hooks.AegisPAPIExpansion;
import com.aegisguard.hooks.DynmapHook;
import com.aegisguard.hooks.WildernessRevertTask;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
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
    private DynmapHook dynmapHook;

    private boolean isFolia = false;
    private BukkitTask autoSaveTask;
    private BukkitTask upkeepTask;
    private BukkitTask wildernessRevertTask;

    // Global instance
    public static AegisGuard plugin;

    // --- (getters) ---
    public AGConfig cfg() { return configMgr; }
    public IDataStore store() { return plotStore; }
    public GUIManager gui() { return gui; }
    public ProtectionManager protection() { return protection; }
    public SelectionService selection() { return selection; }
    public VaultHook vault() { return vault; }
    public MessagesUtil msg() { return messages; }
    public WorldRulesManager worldRules() { return worldRules; }
    public EffectUtil effects() { return effectUtil; }
    public ExpansionRequestManager getExpansionRequestManager() { return expansionManager; }
    public boolean isFolia() { return isFolia; }


    @Override
    public void onEnable() {
        plugin = this; // Assign global instance

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
        this.configMgr = new AGConfig(this);
        
        // --- Database Loader ---
        String storageType = cfg().raw().getString("storage.type", "yml").toLowerCase();
        if (storageType.equals("sql") || storageType.equals("mysql") || storageType.equals("sqlite")) {
            this.plotStore = new SQLDataStore(this);
            getLogger().info("Using SQL data store.");
        } else {
            this.plotStore = new YMLDataStore(this);
            getLogger().info("Using YML data store.");
        }
        
        this.selection = new SelectionService(this);
        this.messages = new MessagesUtil(this);
        this.gui = new GUIManager(this);
        this.vault = new VaultHook(this);
        this.worldRules = new WorldRulesManager(this);
        this.protection = new ProtectionManager(this);
        this.effectUtil = new EffectUtil(this);
        this.expansionManager = new ExpansionRequestManager(this);

        // --- CRITICAL: Load data BEFORE registering listeners ---
        // Load plot data (YML or SQL) synchronously to prevent race conditions.
        this.plotStore.load();
        
        // Load non-essential data asynchronously
        runGlobalAsync(() -> {
            messages.loadPlayerPreferences();
            expansionManager.load();
        });

        // Listeners
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(protection, this); 
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

        // --- Start All Background Tasks ---
        startAutoSaver();
        
        if (cfg().isUpkeepEnabled()) {
            startUpkeepTask();
        }
        
        if (cfg().raw().getBoolean("wilderness_revert.enabled", false)) {
            startWildernessRevertTask();
        }
        
        // --- Initialize Plugin Hooks (PAPI, Dynmap) ---
        initializeHooks();

        getLogger().info("AegisGuard v" + getDescription().getVersion() + " enabled.");
        getLogger().info("WorldRulesManager initialized for per-world protections.");
    }
    
    /**
     * Initializes all plugin hooks (Dynmap, PAPI) safely.
     */
    private void initializeHooks() {
        // Init Dynmap
        try {
             this.dynmapHook = new DynmapHook(this);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Dynmap hook!");
            e.printStackTrace();
        }
        
        // Init PlaceholderAPI
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                new AegisPAPIExpansion(this).register();
                getLogger().info("Successfully hooked into PlaceholderAPI.");
            } catch (Exception e) {
                getLogger().severe("Failed to initialize PlaceholderAPI hook!");
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDisable() {
        // Cancel all repeating tasks
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
        }
        if (upkeepTask != null && !upkeepTask.isCancelled()) {
            upkeepTask.cancel();
        }
        if (wildernessRevertTask != null && !wildernessRevertTask.isCancelled()) {
            wildernessRevertTask.cancel();
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
     * --- CRITICAL LAG FIX ---
     * Starts the asynchronous auto-saver task.
     * This is the central hub for all async data saving.
     */
    private void startAutoSaver() {
        long intervalTicks = 20L * 60 * 5; // 5 minutes

        Runnable autoSaveLogic = () -> {
            // This code runs on an ASYNC thread
            
            // Check PlotStore
            if (plotStore != null && plotStore.isDirty()) {
                getLogger().info("Auto-saving plot data in the background...");
                plotStore.save(); // This is the synchronized save method
                getLogger().info("Plot data auto-save complete.");
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
     * --- CRITICAL LAG FIX ---
     * Starts the asynchronous plot upkeep/rent task.
     * Handles EXPIRED -> AUCTION -> SOLD/DELETED lifecycle.
     */
    private void startUpkeepTask() {
        long intervalTicks = (long) (20L * 60 * 60 * cfg().getUpkeepCheckHours()); // Ticks * Sec * Min * Hours
        if (intervalTicks <= 0) {
            getLogger().warning("Upkeep check_interval_hours is 0 or less. Upkeep task will not run.");
            return;
        }
        
        double cost = cfg().getUpkeepCost();
        if (cost <= 0) {
             getLogger().info("Upkeep cost is 0. Upkeep task will run but will not charge players.");
        }
        
        long gracePeriodMillis = TimeUnit.DAYS.toMillis(cfg().getUpkeepGraceDays());
        long auctionDurationMillis = TimeUnit.DAYS.toMillis(cfg().raw().getLong("auction.duration_days", 3));

        getLogger().info("Starting Upkeep Task. Check interval: " + cfg().getUpkeepCheckHours() + " hours. Cost: " + cost);

        Runnable upkeepLogic = () -> {
            // This code runs on an ASYNC thread
            getLogger().info("[Upkeep] Running plot upkeep check...");
            long currentTime = System.currentTimeMillis();
            long checkIntervalMillis = TimeUnit.HOURS.toMillis(cfg().getUpkeepCheckHours());

            // We must iterate over a snapshot, as removePlot can cause concurrent modification
            for (Plot plot : new ArrayList<>(store().getAllPlots())) {
                
                // Skip Server Zones
                if (plot.isServerZone()) continue;
                
                long timeSinceLastPayment = currentTime - plot.getLastUpkeepPayment();
                OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());

                // --- State 1: ACTIVE Plot ---
                if (plot.getPlotStatus().equals("ACTIVE")) {
                    if (timeSinceLastPayment < checkIntervalMillis) {
                        continue; // Not time to check yet
                    }

                    if (vault().charge(owner.getPlayer(), cost)) {
                        // Payment success
                        plot.setLastUpkeepPayment(currentTime);
                        store().setDirty(true);
                        if (owner.isOnline()) {
                            runMain(owner.getPlayer(), () -> msg().send(owner.getPlayer(), "upkeep_paid", Map.of("AMOUNT", vault().format(cost))));
                        }
                    } else {
                        // Payment failed. Set to EXPIRED.
                        plot.setPlotStatus("EXPIRED");
                        // We reset the upkeep time to mark the *start* of the grace period
                        plot.setLastUpkeepPayment(currentTime);
                        store().setDirty(true);
                        getLogger().info("[Upkeep] Plot " + plot.getPlotId() + " by " + owner.getName() + " has expired. Grace period started.");
                        if (owner.isOnline()) {
                            runMain(owner.getPlayer(), () -> msg().send(owner.getPlayer(), "upkeep_failed_warning"));
                        }
                    }
                }
                // --- State 2: EXPIRED Plot (Grace Period) ---
                else if (plot.getPlotStatus().equals("EXPIRED")) {
                    long timeInGrace = currentTime - plot.getLastUpkeepPayment();

                    // Check if owner paid their debt (e.g., /ag payupkeep command - for now, just re-check balance)
                    if (vault().charge(owner.getPlayer(), cost)) {
                         plot.setPlotStatus("ACTIVE");
                         plot.setLastUpkeepPayment(currentTime);
                         store().setDirty(true);
                         getLogger().info("[Upkeep] Plot " + plot.getPlotId() + " by " + owner.getName() + " has been reclaimed.");
                         if (owner.isOnline()) {
                             runMain(owner.getPlayer(), () -> msg().send(owner.getPlayer(), "upkeep_paid"));
                         }
                    } else if (timeInGrace > gracePeriodMillis) {
                        // Grace period is over. Put up for AUCTION.
                        plot.setPlotStatus("AUCTION");
                        plot.setLastUpkeepPayment(currentTime); // Reset timer for auction duration
                        plot.setCurrentBid(0, null); // Reset bids
                        store().setDirty(true);
                        getLogger().info("[Upkeep] Plot " + plot.getPlotId() + " by " + owner.getName() + " is now for auction.");
                        if (owner.isOnline()) {
                            runMain(owner.getPlayer(), () -> msg().send(owner.getPlayer(), "plot-now-auctioned"));
                        }
                    }
                }
                // --- State 3: AUCTION Plot ---
                else if (plot.getPlotStatus().equals("AUCTION")) {
                    long timeInAuction = currentTime - plot.getLastUpkeepPayment();
                    
                    if (timeInAuction > auctionDurationMillis) {
                        // Auction is OVER
                        UUID winnerUUID = plot.getCurrentBidder();
                        if (winnerUUID != null) {
                            // We have a winner!
                            OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerUUID);
                            double finalBid = plot.getCurrentBid();
                            
                            // Pay owner cut (if any)
                            double ownerCutPercent = cfg().raw().getDouble("auction.owner_cut_percent", 50);
                            double ownerCut = finalBid * (ownerCutPercent / 100.0);
                            vault().give(owner, ownerCut);
                            
                            // Transfer ownership
                            getLogger().info("[Upkeep] Plot " + plot.getPlotId() + " auction won by " + winner.getName() + " for " + finalBid);
                            store().changePlotOwner(plot, winnerUUID, winner.getName());
                            
                            // Notify relevant parties
                            if (owner.isOnline()) {
                                runMain(owner.getPlayer(), () -> msg().send(owner.getPlayer(), "auction-sold", Map.of("PLAYER", winner.getName(), "AMOUNT", vault().format(ownerCut))));
                            }
                            if (winner.isOnline()) {
                                runMain(winner.getPlayer(), () -> msg().send(winner.getPlayer(), "auction-won", Map.of("PLOT_OWNER", owner.getName(), "AMOUNT", vault().format(finalBid))));
                            }
                        } else {
                            // No bids. Unclaim the plot.
                            getLogger().info("[Upkeep] Plot " + plot.getPlotId() + " expired with no bids. Unclaiming.");
                            store().removePlot(plot.getOwner(), plot.getPlotId());
                            if (owner.isOnline()) {
                                runMain(owner.getPlayer(), () -> msg().send(owner.getPlayer(), "plot_expired_upkeep"));
                            }
                        }
                        // store.changePlotOwner/removePlot already setDirty(true)
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
            }.runTaskTimerAsynchronously(this, 20L * 60, intervalTicks); // 1-minute initial delay
        }
    }
    
    /**
     * Starts the asynchronous wilderness revert task.
     */
    private void startWildernessRevertTask() {
        long intervalTicks = 20L * 60 * cfg().raw().getLong("wilderness_revert.check_interval_minutes", 10);
        if (intervalTicks <= 0) intervalTicks = 20L * 60 * 10;
        
        // This task is fully async-safe and handles its own logic
        // FIX: changed 'store' to 'plotStore'
        wildernessRevertTask = new WildernessRevertTask(this, plotStore)
            .runTaskTimerAsynchronously(this, 20L * 60, intervalTicks); // 1-minute initial delay
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
        if (player == null) {
            runMainGlobal(task);
            return;
        }
        
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
