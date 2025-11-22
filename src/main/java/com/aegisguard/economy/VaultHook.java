package com.aegisguard.economy;

import com.aegisguard.AegisGuard;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * VaultHook
 * - Optional economy via Vault. If absent, AegisGuard runs "free".
 * - Safe against late-loading Vault and failed transactions.
 */
public class VaultHook implements Listener {

    private final AegisGuard plugin;
    private Economy economy;

    public VaultHook(AegisGuard plugin) {
        this.plugin = plugin;

        // Don't bother hooking if the user disabled it in the config
        if (!plugin.cfg().useVault()) {
            plugin.getLogger().info("Economy features disabled via config.yml (free mode).");
            return;
        }

        // Try immediately (softdepend ensures order, but this keeps it resilient)
        setupEconomy();
        
        // Also watch for Vault loading/unloading later
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Re-try when Vault enables after us
     */
    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        if (economy == null && "Vault".equalsIgnoreCase(e.getPlugin().getName())) {
            if (!plugin.cfg().useVault()) return; // Check again in case of /ag reload
            setupEconomy();
        }
    }

    /**
     * Un-hook if Vault is disabled during runtime
     */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent e) {
        if (economy != null && "Vault".equalsIgnoreCase(e.getPlugin().getName())) {
            plugin.getLogger().warning("Vault has been disabled. Economy features are now offline (free mode).");
            economy = null;
        }
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            economy = null;
            return;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        
        if (rsp != null) {
            this.economy = rsp.getProvider();
            plugin.getLogger().info("Vault hooked successfully. Economy enabled.");
        } else {
            this.economy = null;
        }
    }

    public boolean isEnabled() {
        return economy != null;
    }

    /**
     * Nicely format currency for messages. Falls back to plain amount.
     */
    public String format(double amount) {
        if (economy == null) {
            return String.format("$%,.2f", amount);
        }
        return economy.format(amount);
    }

    public double balance(OfflinePlayer p) {
        if (economy == null) return 0.0;
        return economy.getBalance(p);
    }

    /**
     * Charge a player. Returns true on success.
     * Free if Vault missing or amount <= 0.
     */
    public boolean charge(OfflinePlayer p, double amount) {
        if (economy == null) return true; // Free mode
        if (amount <= 0) return true;

        if (!economy.has(p, amount)) {
            return false;
        }

        EconomyResponse res = economy.withdrawPlayer(p, amount);
        return res.transactionSuccess();
    }

    /**
     * Give money to a player (no-op if Vault missing or amount <= 0).
     */
    public void give(OfflinePlayer p, double amount) {
        if (economy == null || amount <= 0 || !Double.isFinite(amount)) return;

        EconomyResponse res = economy.depositPlayer(p, amount);
        if (!res.transactionSuccess()) {
            plugin.getLogger().warning("[Vault] deposit failed for " + p.getName() + ": " + res.errorMessage);
        }
    }

    /**
     * --- FIX: ADDED MISSING METHOD ---
     * Checks if a player has enough money without charging them.
     */
    public boolean has(OfflinePlayer p, double amount) {
        if (economy == null) return true; // Free mode = always has enough
        return economy.has(p, amount);
    }
}
