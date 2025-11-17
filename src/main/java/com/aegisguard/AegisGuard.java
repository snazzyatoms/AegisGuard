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

import java.util.concurrent.CompletableFuture;

/**
 * AegisGuard – main entrypoint
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


    @Override
    public void onEnable() {
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

        // --- NEW ---
        // Load player data preferences asynchronously
        CompletableFuture.runAsync(() -> {
            messages.loadPlayerPreferences();
        }, getServer().getScheduler().getMainThreadExecutor(this));

        // Listeners
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(protection, this);
        Bukkit.getPluginManager().registerEvents(selection, this);

        // Register banned player listener
        if (cfg().autoRemoveBannedPlots()) { 
            Bukkit.getPluginManager().registerEvents(new BannedPlayerListener(this), this);

            // Also run an async check on startup
            CompletableFuture.runAsync(() -> {
                getLogger().info("Running async check for banned player plots...");
                store().removeBannedPlots();
            }, getServer().getScheduler().getMainThreadExecutor(this));
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
     * This is the central hub for all async data saving.
     */
    private void startAutoSaver() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Check PlotStore
                if (plotStore != null && plotStore.isDirty()) {
                    getLogger().info("Auto-saving plot data in the background...");
                    CompletableFuture.runAsync(() -> {
                        plotStore.save(); // This is the synchronized save method
                        getLogger().info("Auto-save complete.");
                    }, getServer().getScheduler().getMainThreadExecutor(AegisGuard.this));
                }

                // Check ExpansionRequestManager
                if (expansionManager != null && expansionManager.isDirty()) {
                    getLogger().info("Auto-saving expansion request data in the background...");
                    CompletableFuture.runAsync(() -> {
                        expansionManager.save(); // This is the synchronized save method
                        getLogger().info("Expansion auto-save complete.");
                    }, getServer().getScheduler().getMainThreadExecutor(AegisGuard.this));
                }

                // Check MessagesUtil for player data
                if (messages != null && messages.isPlayerDataDirty()) {
                    getLogger().info("Auto-saving player data in the background...");
                    CompletableFuture.runAsync(() -> {
                        messages.savePlayerData(); // This is the synchronized save method
                        getLogger().info("Player data auto-save complete.");
                    }, getServer().getScheduler().getMainThreadExecutor(AegisGuard.this));
                }
            }
            // Run every 5 minutes (20 ticks * 60 seconds * 5 minutes)
        }.runTaskTimer(this, 20L * 60 * 5, 20L * 60 * 5);
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
}
