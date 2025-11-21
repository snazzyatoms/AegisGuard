package com.aegisguard;

import com.aegisguard.admin.AdminCommand;
import com.aegisguard.command.AegisCommand; // Ensure this import matches your actual command file location
import com.aegisguard.listeners.BannedPlayerListener; // Ensure this matches
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
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// IMPORT FOLIA IF AVAILABLE (handled dynamically via Object to avoid compilation errors on non-folia builds)
// We use Object for tasks to support both BukkitTask and ScheduledTask without import errors

public class AegisGuard extends JavaPlugin {

    public static AegisGuard plugin;
    
    // --- Fields ---
    private AGConfig configMgr;
    private IDataStore plotStore; 
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
    
    // Use generic Object to hold the task, as it could be BukkitTask OR ScheduledTask
    private Object autoSaveTask;
    private Object upkeepTask;
    private Object wildernessRevertTask;

    // --- Getters ---
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
        plugin = this; 

        // --- Folia Check ---
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("Folia detected. Enabling Folia-compatible schedulers.");
        } catch (ClassNotFoundException e) {
            isFolia = false;
            getLogger().info("Folia not found. Using standard Bukkit schedulers.");
        }

        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.configMgr = new AGConfig(this);
        
        // Database
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

        this.plotStore.load();
        
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

        if (cfg().autoRemoveBannedPlots()) {
            Bukkit.getPluginManager().registerEvents(new BannedPlayerListener(this), this);
            runGlobalAsync(() -> store().removeBannedPlots());
        }

        // Commands
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

        // Tasks
        startAutoSaver();
        if (cfg().isUpkeepEnabled()) startUpkeepTask();
        if (cfg().raw().getBoolean("wilderness_revert.enabled", false)) startWildernessRevertTask();
        
        initializeHooks();

        getLogger().info("AegisGuard enabled.");
    }
    
    private void initializeHooks() {
        try {
             this.dynmapHook = new DynmapHook(this);
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().warning("Dynmap not found or failed to hook.");
        }
        
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new AegisPAPIExpansion(this).register();
        }
    }

    @Override
    public void onDisable() {
        // Cancel tasks safely (Handle both BukkitTask and Folia ScheduledTask)
        cancelTask(autoSaveTask);
        cancelTask(upkeepTask);
        cancelTask(wildernessRevertTask);

        if (plotStore != null) plotStore.saveSync();
        if (expansionManager != null) expansionManager.saveSync();
        if (messages != null) messages.savePlayerData();
    }
    
    // Helper to cancel generic task object
    private void cancelTask(Object task) {
        if (task == null) return;
        if (task instanceof BukkitTask) {
            ((BukkitTask) task).cancel();
        } else if (isFolia) {
            // Reflective cancellation to avoid import errors on non-Folia compilation
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (Exception ignored) {}
        }
    }

    private void startAutoSaver() {
        long intervalTicks = 20L * 60 * 5;
        Runnable autoSaveLogic = () -> {
            if (plotStore != null && plotStore.isDirty()) plotStore.save();
            if (expansionManager != null && expansionManager.isDirty()) expansionManager.save();
            if (messages != null && messages.isPlayerDataDirty()) messages.savePlayerData();
        };

        if (isFolia) {
            autoSaveTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, (t) -> autoSaveLogic.run(), intervalTicks, intervalTicks);
        } else {
            autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, autoSaveLogic, intervalTicks, intervalTicks);
        }
    }

    private void startUpkeepTask() {
        long hours = cfg().getUpkeepCheckHours();
        long intervalTicks = (long) (20L * 60 * 60 * (hours > 0 ? hours : 1));
        double cost = cfg().getUpkeepCost();
        long gracePeriodMillis = TimeUnit.DAYS.toMillis(cfg().getUpkeepGraceDays());
        long auctionDurationMillis = TimeUnit.DAYS.toMillis(cfg().raw().getLong("auction.duration_days", 3));

        Runnable upkeepLogic = () -> {
            long currentTime = System.currentTimeMillis();
            long checkIntervalMillis = TimeUnit.HOURS.toMillis(hours);

            for (Plot plot : new ArrayList<>(store().getAllPlots())) {
                if (plot.isServerZone()) continue;
                long timeSinceLastPayment = currentTime - plot.getLastUpkeepPayment();
                OfflinePlayer owner = Bukkit.getOfflinePlayer(plot.getOwner());

                if (plot.getPlotStatus().equals("ACTIVE")) {
                    if (timeSinceLastPayment < checkIntervalMillis) continue;
                    if (vault().charge(owner.getPlayer(), cost)) {
                        plot.setLastUpkeepPayment(currentTime);
                        store().setDirty(true);
                    } else {
                        plot.setPlotStatus("EXPIRED");
                        plot.setLastUpkeepPayment(currentTime);
                        store().setDirty(true);
                    }
                } else if (plot.getPlotStatus().equals("EXPIRED")) {
                    long timeInGrace = currentTime - plot.getLastUpkeepPayment();
                    if (vault().charge(owner.getPlayer(), cost)) {
                         plot.setPlotStatus("ACTIVE");
                         plot.setLastUpkeepPayment(currentTime);
                         store().setDirty(true);
                    } else if (timeInGrace > gracePeriodMillis) {
                        plot.setPlotStatus("AUCTION");
                        plot.setLastUpkeepPayment(currentTime);
                        plot.setCurrentBid(0, null);
                        store().setDirty(true);
                    }
                } else if (plot.getPlotStatus().equals("AUCTION")) {
                     if ((currentTime - plot.getLastUpkeepPayment()) > auctionDurationMillis) {
                        UUID winnerUUID = plot.getCurrentBidder();
                        if (winnerUUID != null) {
                            OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerUUID);
                            store().changePlotOwner(plot, winnerUUID, winner.getName());
                        } else {
                            store().removePlot(plot.getOwner(), plot.getPlotId());
                        }
                    }
                }
            }
        };

        if (isFolia) {
            upkeepTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, (t) -> upkeepLogic.run(), intervalTicks, intervalTicks);
        } else {
            upkeepTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, upkeepLogic, 20L * 60, intervalTicks);
        }
    }
    
    private void startWildernessRevertTask() {
        long intervalTicks = 20L * 60 * cfg().raw().getLong("wilderness_revert.check_interval_minutes", 10);
        WildernessRevertTask task = new WildernessRevertTask(this, plotStore);
        
        // Assign properly based on system
        if (isFolia) {
             // Folia revert task logic is usually handled differently, 
             // but here we just start the runTaskTimerAsync equivalent
             wildernessRevertTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, (t) -> task.run(), intervalTicks, intervalTicks);
        } else {
             wildernessRevertTask = task.runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
        }
    }

    public boolean isSoundEnabled(Player player) {
        if (!cfg().globalSoundsEnabled()) return false; 
        return getConfig().getBoolean("sounds.players." + player.getUniqueId(), true);
    }

    public void runGlobalAsync(Runnable task) {
        if (isFolia) Bukkit.getGlobalRegionScheduler().run(this, (t) -> task.run());
        else Bukkit.getScheduler().runTaskAsynchronously(this, task);
    }
    
    public void runMain(Player player, Runnable task) {
        if (player == null) { runMainGlobal(task); return; }
        if (isFolia) player.getScheduler().run(this, (t) -> task.run(), null);
        else Bukkit.getScheduler().runTask(this, task);
    }
    
    public void runMainGlobal(Runnable task) {
        if (isFolia) Bukkit.getGlobalRegionScheduler().run(this, (t) -> task.run());
        else Bukkit.getScheduler().runTask(this, task);
    }
}
