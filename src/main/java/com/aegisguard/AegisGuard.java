package com.aegisguard;

import com.aegisguard.admin.AdminCommand;
import com.aegisguard.claimblocks.ClaimBlockExchangeService;
import com.aegisguard.claimblocks.ClaimBlockManager;
import com.aegisguard.claimblocks.ClaimBlockTask;
import com.aegisguard.commands.AegisCommand;
import com.aegisguard.config.AGConfig;
import com.aegisguard.data.IDataStore;
import com.aegisguard.data.SQLDataStore;
import com.aegisguard.data.YMLDataStore;
import com.aegisguard.economy.ClaimPricingCalculator;
import com.aegisguard.economy.EconomyManager;
import com.aegisguard.economy.VaultHook;
import com.aegisguard.expansions.ExpansionRequestManager;
import com.aegisguard.gui.GUIListener;
import com.aegisguard.gui.GUIManager;
import com.aegisguard.hooks.AegisPAPIExpansion;
import com.aegisguard.hooks.DiscordWebhook;
import com.aegisguard.hooks.MapHookManager;
import com.aegisguard.hooks.MobBarrierTask;
import com.aegisguard.hooks.WildernessRevertTask;
import com.aegisguard.hooks.protection.ProtectionHookManager;
import com.aegisguard.language.CodexEngine;
import com.aegisguard.listeners.BannedPlayerListener;
import com.aegisguard.listeners.LevelingListener;
import com.aegisguard.listeners.PlotGreetingListener;
import com.aegisguard.migration.MigrationManager;  // ✅ NEW: Migration Manager (1.2.5+)
import com.aegisguard.protection.ProtectionManager;
import com.aegisguard.protection.BlockProtectionListener;
import com.aegisguard.selection.SelectionService;
import com.aegisguard.selection.WandSafetyListener;
import com.aegisguard.snapshots.SnapshotManager;
import com.aegisguard.util.EffectUtil;
import com.aegisguard.util.MessagesUtil;
import com.aegisguard.visualization.WandEquipListener;
import com.aegisguard.world.WorldRulesManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // Compatibility layer for other protection plugins (WorldGuard, etc.)
    private ProtectionHookManager protectionHooks;

    // Aegis Codex language engine (1.2.4+)
    private CodexEngine codex;

    // Claim Block Manager (1.2.4+)
    private ClaimBlockManager claimBlockManager;

    // Claim Block Exchange Service (1.2.5+)
    private ClaimBlockExchangeService claimBlockExchange;

    // Snapshot Manager (1.2.5+) - Rollback system for claims
    private SnapshotManager snapshotManager;

    // Fair Pricing Calculator (1.2.6+)
    private ClaimPricingCalculator pricingCalculator;

    // ✅ NEW: Migration Manager (1.2.6+) - Import claims from other plugins
    private MigrationManager migrationManager;

    /**
     * MessagesUtil now acts as:
     * - playerdata.yml prefs (language choice)
     * - legacy msg().get(...) compatibility
     *
     * IMPORTANT: This class must NOT depend on messages.yml anymore.
     */
    private MessagesUtil messages;

    private WorldRulesManager worldRules;
    private EffectUtil effectUtil;
    private ExpansionRequestManager expansionManager;

    // --- HOOKS ---
    private MapHookManager mapHookManager;
    private DiscordWebhook discord;

    private boolean isFolia = false;

    // --- 1.2.6 QoL: runtime bypass toggle ("Master Key Mode") ---
    private final Set<UUID> bypassMode = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        instance = this;

        // --- 1. ROBUST FOLIA DETECTION (1.21+ Supported) ---
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("Folia detected! Enabling Region Scheduler compatibility.");
        } catch (ClassNotFoundException e) {
            isFolia = false;
            getLogger().info("Standard Bukkit/Spigot/Paper detected.");
        }

        saveDefaultConfig();

        // --- CONFIG + MESSAGES ---
        configMgr = new AGConfig(this);
        messages = new MessagesUtil(this);

        // --- DATA STORE ---
        String backend = getConfig().getString("storage.backend", "yml").toLowerCase();
        if (backend.equals("sql")) {
            plotStore = new SQLDataStore(this);
        } else {
            plotStore = new YMLDataStore(this);
        }
        plotStore.load();

        // --- MANAGERS ---
        gui = new GUIManager(this);
        protection = new ProtectionManager(this);
        selection = new SelectionService(this);
        worldRules = new WorldRulesManager(this);
        effectUtil = new EffectUtil(this);
        expansionManager = new ExpansionRequestManager(this);
        snapshotManager = new SnapshotManager(this);
        pricingCalculator = new ClaimPricingCalculator(this);
        migrationManager = new MigrationManager(this);

        // ClaimBlocks (1.2.4+)
        claimBlockManager = new ClaimBlockManager(this);
        claimBlockExchange = new ClaimBlockExchangeService(this);

        // --- HOOKS ---
        protectionHooks = new ProtectionHookManager(this);
        mapHookManager = new MapHookManager(this);
        discord = new DiscordWebhook(this);

        // Vault (optional)
        vault = new VaultHook(this);
        ecoManager = new EconomyManager(this);

        // --- COMMANDS ---
        PluginCommand cmd = getCommand("aegis");
        if (cmd != null) {
            AegisCommand aegisCmd = new AegisCommand(this);
            cmd.setExecutor(aegisCmd);
            cmd.setTabCompleter(aegisCmd);
        }

        PluginCommand admin = getCommand("aegisadmin");
        if (admin != null) {
            AdminCommand adminCmd = new AdminCommand(this);
            admin.setExecutor(adminCmd);
            admin.setTabCompleter(adminCmd);
        }

        // --- LISTENERS ---
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(protection, this);
        Bukkit.getPluginManager().registerEvents(selection, this);
        Bukkit.getPluginManager().registerEvents(new BlockProtectionListener(this), this);

        Bukkit.getPluginManager().registerEvents(new PlotGreetingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WandSafetyListener(this), this);

        Bukkit.getPluginManager().registerEvents(new LevelingListener(this), this);

        Bukkit.getPluginManager().registerEvents(new WandEquipListener(this), this);

        Bukkit.getPluginManager().registerEvents(new BannedPlayerListener(this), this);

        // --- TASKS ---
        new ClaimBlockTask(this).start();
        new MobBarrierTask(this).start();
        new WildernessRevertTask(this).start();

        // PlaceholderAPI (optional)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new AegisPAPIExpansion(this).register();
        }

        getLogger().info("AegisGuard enabled.");
    }

    @Override
    public void onDisable() {
        if (plotStore != null) plotStore.save();
        getLogger().info("AegisGuard disabled.");
    }

    public AGConfig cfg() {
        return configMgr;
    }

    public MessagesUtil msg() {
        return messages;
    }

    public IDataStore store() {
        return plotStore;
    }

    public GUIManager gui() {
        return gui;
    }

    public ProtectionManager protection() {
        return protection;
    }

    public SelectionService selection() {
        return selection;
    }

    public VaultHook vault() {
        return vault;
    }

    public EconomyManager eco() {
        return ecoManager;
    }

    public ProtectionHookManager protectionHooks() {
        return protectionHooks;
    }

    public WorldRulesManager worldRules() {
        return worldRules;
    }

    public EffectUtil effects() {
        return effectUtil;
    }

    public ExpansionRequestManager expansions() {
        return expansionManager;
    }

    public SnapshotManager snapshots() {
        return snapshotManager;
    }

    public ClaimPricingCalculator pricing() {
        return pricingCalculator;
    }

    public MigrationManager migration() {
        return migrationManager;
    }

    public boolean isAdmin(Player player) {
        if (player == null) return false;
        boolean trustOps = getConfig().getBoolean("admin.trust_operators", true);
        if (!trustOps) {
            return player.hasPermission("aegis.admin");
        }
        return player.isOp() || player.hasPermission("aegis.admin");
    }

    /**
     * 1.2.6 QoL: Runtime bypass toggle.
     * This is separate from the static "aegis.bypass" permission.
     */
    public boolean isBypassing(Player player) {
        if (player == null) return false;
        return bypassMode.contains(player.getUniqueId());
    }

    /**
     * Toggle runtime bypass mode for this player.
     * Requires permissions (checked by the caller command).
     */
    public boolean toggleBypass(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        if (bypassMode.contains(uuid)) {
            bypassMode.remove(uuid);
            return false;
        }
        bypassMode.add(uuid);
        return true;
    }

    // ---------------------------------------------------------------------
    // Schedulers (Folia 1.21+ compatible)
    // ---------------------------------------------------------------------

    public boolean isFolia() {
        return isFolia;
    }

    public void runSync(Runnable task) {
        if (!isFolia) {
            Bukkit.getScheduler().runTask(this, task);
            return;
        }

        // Folia-compatible scheduling
        try {
            Method getGlobalRegionScheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
            Object scheduler = getGlobalRegionScheduler.invoke(Bukkit.getServer());

            Method runMethod = scheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
            runMethod.invoke(scheduler, this, (Consumer<Object>) scheduledTask -> task.run());
        } catch (Throwable t) {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    public BukkitTask runLater(long ticks, Runnable task) {
        if (!isFolia) {
            return Bukkit.getScheduler().runTaskLater(this, task, ticks);
        }

        try {
            Method getGlobalRegionScheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
            Object scheduler = getGlobalRegionScheduler.invoke(Bukkit.getServer());

            Method runDelayed = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            return (BukkitTask) runDelayed.invoke(scheduler, this, (Consumer<Object>) scheduledTask -> task.run(), ticks);
        } catch (Throwable t) {
            return Bukkit.getScheduler().runTaskLater(this, task, ticks);
        }
    }

    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(this, task);
    }
}
