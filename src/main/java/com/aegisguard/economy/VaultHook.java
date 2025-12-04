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
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * VaultHook
 * - Optional economy via Vault.
 * - Auto-detects Economy providers (Essentials, CMI, etc.) dynamically.
 */
public class VaultHook implements Listener {

    private final AegisGuard plugin;
    private Economy economy;

    public VaultHook(AegisGuard plugin) {
        this.plugin = plugin;

        // v1.3.0 Config Check (New Method Name)
        if (!plugin.cfg().isEconomyEnabled()) {
            plugin.getLogger().info("Economy features disabled via config.yml (Free Mode).");
            return;
        }

        // Initial Attempt
        setupEconomy();
        
        // Listen for late-loading plugins
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            this.economy = null;
            return;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            this.economy = rsp.getProvider();
            plugin.getLogger().info("Vault hooked successfully! Provider: " + economy.getName());
        }
    }

    /**
     * PRO FIX: Detect when an Economy plugin registers itself (e.g. Essentials loads late).
     */
    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent e) {
        if (e.getProvider().getService() == Economy.class) {
            setupEconomy();
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        if (economy == null && "Vault".equalsIgnoreCase(e.getPlugin().getName())) {
            setupEconomy();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent e) {
        if (economy != null && "Vault".equalsIgnoreCase(e.getPlugin().getName())) {
            plugin.getLogger().warning("Vault disabled. Switching to Free Mode.");
            economy = null;
        }
    }

    public boolean isEnabled() {
        return economy != null;
    }

    /**
     * Nicely format currency. Falls back to plain amount if Vault is missing.
     */
    public String format(double amount) {
        if (economy == null) {
            return String.format("$%,.2f", amount); // Default fallback: $1,000.00
        }
        try {
            return economy.format(amount);
        } catch (Exception e) {
            return String.valueOf(amount);
        }
    }

    public double balance(OfflinePlayer p) {
        if (economy == null) return 0.0;
        return economy.getBalance(p);
    }

    /**
     * Checks if a player has money (without taking it).
     */
    public boolean has(OfflinePlayer p, double amount) {
        if (economy == null) return true; // Free mode = Always has enough
        if (amount <= 0) return true;
        return economy.has(p, amount);
    }

    /**
     * Charge a player. Returns true on success.
     */
    public boolean charge(OfflinePlayer p, double amount) {
        if (economy == null) return true; // Free mode
        if (amount <= 0) return true;

        // Safety: Prevent charging infinite or NaN
        if (!Double.isFinite(amount)) return false;

        if (!economy.has(p, amount)) {
            return false;
        }

        EconomyResponse res = economy.withdrawPlayer(p, amount);
        return res.transactionSuccess();
    }

    /**
     * Give money to a player.
     */
    public void give(OfflinePlayer p, double amount) {
        if (economy == null) return;
        if (amount <= 0 || !Double.isFinite(amount)) return;

        EconomyResponse res = economy.depositPlayer(p, amount);
        if (!res.transactionSuccess()) {
            plugin.getLogger().warning("[Vault] Deposit failed for " + p.getName() + ": " + res.errorMessage);
        }
    }
}
