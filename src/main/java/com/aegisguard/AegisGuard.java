package com.aegisguard;

import com.aegisguard.admin.AdminCommand;
import com.aegisguard.commands.AegisCommand; // FIX: Correct package name
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
    
    // Use generic Object to hold tasks (Handles both BukkitTask and ScheduledTask)
    private Object autoSaveTask;
    private Object upkeepTask;
    private Object wildernessRevertTask;

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
    }
    
    /**
     * Initializes all plugin hooks (Dynmap, PAPI) safely.
     */
    private void initializeHooks() {
        // Init Dynmap
        try {
             this.dynmapHook = new DynmapHook(this);
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().warning("Dynmap not found or failed to hook.");
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
        // Cancel tasks safely
        cancelTask(autoSaveTask);
        cancelTask(upkeepTask);
        cancelTask(wildernessRevertTask);

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
    
    // Helper to cancel generic task objects (Folia/Bukkit compatibility)
    private void cancelTask(Object task) {
        if (task == null) return;
        if (task instanceof BukkitTask) {
            ((BukkitTask) task).cancel();
        } else if (isFolia) {
            // Reflective cancellation for Folia tasks to avoid import errors
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Starts the asynchronous auto-saver task.
     */
    private void startAutoSaver() {
        long intervalTicks = 20L * 60 * 5; // 5 minutes

        Runnable autoSaveLogic = () -> {
            if (plotStore != null && plotStore.isDirty()) {
                getLogger().info("Auto-saving plot data...");
                plotStore.save();
            }
            if (expansionManager != null && expansionManager.isDirty()) {
                expansionManager.save();
            }
            if (messages != null && messages.isPlayerDataDirty()) {
                messages.savePlayerData();
            }
        };

        if (isFolia) {
            autoSaveTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, (task) -> autoSaveLogic.run(), intervalTicks, intervalTicks);
        } else {
            autoSaveTask = new BukkitRunnable() {
                @Override public void run() { autoSaveLogic.run(); }
            }.runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
        }
    }

    
    /**
     * Starts the asynchronous plot upkeep/rent task.
     */
    private void startUpkeepTask() {
        long intervalTicks = (long) (20L * 60 * 60 * cfg().getUpkeepCheckHours());
        if (intervalTicks <= 0) return;
        
        double cost = cfg().getUpkeepCost();
        long gracePeriodMillis = TimeUnit.DAYS.toMillis(cfg().getUpkeepGraceDays());
        long auctionDurationMillis = TimeUnit.DAYS.toMillis(cfg().raw().getLong("auction.duration_days", 3));

        Runnable upkeepLogic = () -> {
            getLogger().info("[Upkeep] Running plot upkeep check...");
            long currentTime = System.currentTimeMillis();
            long checkIntervalMillis = TimeUnit.HOURS.toMillis(cfg().getUpkeepCheckHours());

            for (Plot plot : new ArrayList<>(store().getAllPlots())) {
                if (plot.isServerZone()) continue;
                
                long timeSinceLastPayment = currentTime - plot.getLastUpkeepPayment();
                OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());

                if (plot.getPlotStatus().equals("ACTIVE")) {
                    if (timeSinceLastPayment < checkIntervalMillis) continue;

                    if (vault().charge(owner, cost)) {
                        plot.setLastUpkeepPayment(currentTime);
                        store().setDirty(true);
                        if (owner.isOnline()) {
                            runMain(owner.getPlayer(), () -> msg().send(owner.getPlayer(), "upkeep_paid", Map.of("AMOUNT", vault().format(cost))));
                        }
                    } else {
                        plot.setPlotStatus("EXPIRED");
                        plot.setLastUpkeepPayment(currentTime);
                        store().setDirty(true);
                        if (owner.isOnline()) {
                            runMain(owner.getPlayer(), () -> msg().send(owner.getPlayer(), "upkeep_failed_warning"));
                        }
                    }
                }
                else if (plot.getPlotStatus().equals("EXPIRED")) {
                    long timeInGrace = currentTime - plot.getLastUpkeepPayment();

                    if (vault().charge(owner, cost)) {
                         plot.setPlotStatus("ACTIVE");
                         plot.setLastUpkeepPayment(currentTime);
                         store().setDirty(true);
                         if (owner.isOnline()) {
                             runMain(owner.getPlayer(), () -> msg().send(owner.getPlayer(), "upkeep_paid"));
                         }
                    } else if (timeInGrace > gracePeriodMillis) {
                        plot.setPlotStatus("AUCTION");
                        plot.setLastUpkeepPayment(currentTime);
                        plot.setCurrentBid(0, null);
                        store().setDirty(true);
                        if (owner.isOnline()) {
                            runMain(owner.getPlayer(), () -> msg().send(owner.getPlayer(), "plot-now-auctioned"));
                        }
                    }
                }
                else if (plot.getPlotStatus().equals("AUCTION")) {
                    long timeInAuction = currentTime - plot.getLastUpkeepPayment();
                    
                    if (timeInAuction > auctionDurationMillis) {
                        UUID winnerUUID = plot.getCurrentBidder();
                        if (winnerUUID != null) {
                            OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerUUID);
                            double finalBid = plot.getCurrentBid();
                            
                            double ownerCutPercent = cfg().raw().getDouble("auction.owner_cut_percent", 50);
                            double ownerCut = finalBid * (ownerCutPercent / 100.0);
                            vault().give(owner, ownerCut);
                            
                            store().changePlotOwner(plot, winnerUUID, winner.getName());
                            
                            if (owner.isOnline()) {
                                runMain(owner.getPlayer(), () -> msg().send(owner.getPlayer(), "auction-sold", Map.of("PLAYER", winner.getName(), "AMOUNT", vault().format(ownerCut))));
                            }
                            if (winner.isOnline()) {
                                runMain(winner.getPlayer(), () -> msg().send(winner.getPlayer(), "auction-won", Map.of("PLOT_OWNER", owner.getName(), "AMOUNT", vault().format(finalBid))));
                            }
                        } else {
                            store().removePlot(plot.getOwner(), plot.getPlotId());
                            if (owner.isOnline()) {
                                runMain(owner.getPlayer(), () -> msg().send(owner.getPlayer(), "plot_expired_upkeep"));
                            }
                        }
                    }
                }
            }
        };

        if (isFolia) {
            upkeepTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, (task) -> upkeepLogic.run(), intervalTicks, intervalTicks);
        } else {
            upkeepTask = new BukkitRunnable() {
                @Override public void run() { upkeepLogic.run(); }
            }.runTaskTimerAsynchronously(this, 20L * 60, intervalTicks);
        }
    }
    
    private void startWildernessRevertTask() {
        long intervalTicks = 20L * 60 * cfg().raw().getLong("wilderness_revert.check_interval_minutes", 10);
        if (intervalTicks <= 0) intervalTicks = 20L * 60 * 10;
        
        WildernessRevertTask task = new WildernessRevertTask(this, plotStore);
        
        if (isFolia) {
             // For Folia, we schedule slightly differently, but for basic async tasks this works
             wildernessRevertTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, (t) -> task.run(), intervalTicks, intervalTicks);
        } else {
             wildernessRevertTask = task.runTaskTimerAsynchronously(this, 20L * 60, intervalTicks);
        }
    }

    public boolean isSoundEnabled(Player player) {
        if (!cfg().globalSoundsEnabled()) return false;
        String key = "sounds.players." + player.getUniqueId();
        return getConfig().getBoolean(key, true);
    }

    // --- Folia-safe Schedulers ---

    public void runGlobalAsync(Runnable task) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(this, (scheduledTask) -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(this, task);
        }
    }
    
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
    
    public void runMainGlobal(Runnable task) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(this, (scheduledTask) -> task.run());
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }
}
