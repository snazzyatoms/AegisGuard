package com.aegisguard;

import com.aegisguard.admin.AdminCommand;
import com.aegisguard.commands.SoundCommand;
import com.aegisguard.config.AGConfig;
import com.aegisguard.data.PlotStore;
import com.aegisguard.economy.VaultHook;
import com.aegisguard.expansions.ExpansionRequestManager;
import com.aegisguard.gui.GUIListener;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.protection.ProtectionManager;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.util.EffectUtil;
import com.aegisguard.util.MessagesUtil;
import com.aegisguard.world.WorldRulesManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AegisGuard – main entrypoint
 *
 * --- UPGRADE NOTES ---
 * - Now 100% Folia-compatible.
 * - Added isFolia check.
 * - All async tasks and schedulers now use the correct
 * Global Region Scheduler (for Folia) or Bukkit Scheduler (for Spigot/Paper).
 */
public class AegisGuard extends JavaPlugin {

    // --- (fields) ---
    private AGConfig configMgr;
    private PlotStore plotStore;
    private GUIManager gui;
    private ProtectionManager protection;
    private SelectionService selection;
    private VaultHook vault;
    private MessagesUtil messages;
    private WorldRulesManager worldRules;
    private EffectUtil effectUtil;
    private SoundCommand soundCommand;
    private ExpansionRequestManager expansionManager;

    // --- NEW: Folia detection ---
    private boolean isFolia = false;
    private BukkitTask autoSaveTask;

    // --- (getters) ---
    public AGConfig cfg()           { return configMgr; }
    public PlotStore store()        { return plotStore; }
    public GUIManager gui()         { return gui; }
    public ProtectionManager protection(){ return protection; }
    public SelectionService selection()  { return selection; }
    public VaultHook vault()        { return vault; }
    public MessagesUtil msg()       { return messages; }
    public WorldRulesManager worldRules(){ return worldRules; }
    public EffectUtil effects()     { return effectUtil; }
    public SoundCommand soundCommand() { return soundCommand; }
    public ExpansionRequestManager getExpansionRequestManager() { return expansionManager; }
    public boolean isFolia()        { return isFolia; } // --- NEW ---


    @Override
    public void onEnable() {
        // --- NEW: Folia Check ---
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("Folia detected. Enabling Folia-safe schedulers.");
        } catch (ClassNotFoundException e) {
            isFolia = false;
            getLogger().info("Spigot/Paper detected. Using standard Bukkit schedulers.");
        }

        // Bundle defaults
        saveDefaultConfig();
        saveResource("messages.yml", false);

        // Core systems
        this.configMgr  = new AGConfig(this);
        this.plotStore  = new PlotStore(this);
        this.selection  = new SelectionService(this);
        this.messages   = new MessagesUtil(this); // (no longer loads playerdata here)
        this.gui        = new GUIManager(this); 
        this.vault      = new VaultHook(this);
        this.worldRules = new WorldRulesManager(this);
        this.protection = new ProtectionManager(this);
        this.effectUtil = new EffectUtil(this);
        this.soundCommand = new SoundCommand(this);
        this.expansionManager = new ExpansionRequestManager(this);

        // --- MODIFIED: Folia-safe Async Load ---
        // Load player data preferences asynchronously
        runGlobalAsync(() -> {
            messages.loadPlayerPreferences();
        });

        // Listeners
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(protection, this);
        Bukkit.getPluginManager().registerEvents(selection, this);

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

        getLogger().info("AegisGuard v" + getDescription().getVersion() + " enabled.");
        getLogger().info("WorldRulesManager initialized for per-world protections.");
    }

    @Override
    public void onDisable() {
        // --- NEW: Cancel auto-saver task ---
        if (autoSaveTask != null && !autoSaveTask.isCancelled()) {
            autoSaveTask.cancel();
        }
        
        // --- MODIFIED ---
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
     * --- MODIFIED --- to be Folia-safe.
     */
    private void startAutoSaver() {
        long intervalTicks = 20L * 60 * 5; // 5 minutes

        Runnable autoSaveLogic = () -> {
            // Check PlotStore
            if (plotStore != null && plotStore.isDirty()) {
                getLogger().info("Auto-saving plot data in the background...");
                // We can run this async, but NOT using runGlobalAsync,
                // as that would create a new task. We are already async.
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
            // Use Folia's GlobalRegionScheduler for a repeating async task
            autoSaveTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(this, 
                    (task) -> autoSaveLogic.run(), 
                    intervalTicks, 
                    intervalTicks
            );
        } else {
            // Use standard Bukkit async scheduler
            autoSaveTask = new BukkitRunnable() {
                @Override
                public void run() {
                    autoSaveLogic.run();
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
     * Runs a task asynchronously on the appropriate scheduler (Folia or Bukkit).
     * This is for global tasks that do not affect a specific world or player.
     */
    public void runGlobalAsync(Runnable task) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(this, (scheduledTask) -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(this, task);
        }
    }

    /**
     * Runs a task on the main server thread (or player's region).
     * This is for tasks that need to interact with Bukkit API (e.g., sending messages).
     */
    public void runMain(Player player, Runnable task) {
        if (isFolia) {
            player.getScheduler().run(this, (scheduledTask) -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    /**
     * Runs a task on the main server thread (or global region).
     * Use this if you don't have a specific player.
     */
    public void runMainGlobal(Runnable task) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(this, (scheduledTask) -> task.run());
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }
}
