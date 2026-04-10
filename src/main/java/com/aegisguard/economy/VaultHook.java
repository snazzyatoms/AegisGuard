package com.aegisguard.economy;

import com.aegisguard.AegisGuard;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;
import java.util.Locale;

/**
 * VaultHook
 * - Optional economy via Vault.
 * - Auto-detects Economy providers (Essentials, CMI, etc.) dynamically.
 */
public class VaultHook implements Listener {

    private static final String COFFERS_PROVIDER = "coffers";
    private static final String COFFERS_LEGACY_PROVIDER = "cofferslegacy";

    private final AegisGuard plugin;
    private Economy economy;

    public VaultHook(AegisGuard plugin) {
        this.plugin = plugin;

        // Config Check
        if (!plugin.cfg().useVault()) {
            plugin.getLogger().info("Economy features disabled via config.yml (Free Mode).");
            return;
        }

        // Initial Attempt
        setupEconomy();

        // Listen for late-loading plugins
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void setupEconomy() {
        Economy previous = this.economy;

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            this.economy = null;
            if (previous != null) {
                plugin.getLogger().warning("Vault is no longer available. Economy features are in Free Mode.");
            }
            return;
        }

        RegisteredServiceProvider<Economy> selected = selectEconomyProvider(
                Bukkit.getServicesManager().getRegistrations(Economy.class)
        );
        if (selected == null) {
            this.economy = null;
            if (previous != null) {
                plugin.getLogger().warning("Vault is loaded, but no economy provider is currently registered.");
            }
            return;
        }

        this.economy = selected.getProvider();
        if (previous == this.economy) {
            return;
        }

        String providerName = safeProviderName(selected.getProvider());
        String sourcePlugin = selected.getPlugin() != null ? selected.getPlugin().getName() : "unknown";
        if (isPreferredCoffersProvider(selected)) {
            plugin.getLogger().info("Vault hooked successfully! Preferring Coffers economy provider: "
                    + providerName + " (plugin " + sourcePlugin + ").");
        } else {
            plugin.getLogger().info("Vault hooked successfully! Provider: " + providerName
                    + " (plugin " + sourcePlugin + ")");
        }
    }

    private RegisteredServiceProvider<Economy> selectEconomyProvider(Collection<RegisteredServiceProvider<Economy>> registrations) {
        RegisteredServiceProvider<Economy> best = null;
        int bestScore = Integer.MIN_VALUE;

        for (RegisteredServiceProvider<Economy> registration : registrations) {
            if (registration == null || registration.getProvider() == null) {
                continue;
            }

            int score = providerPreferenceScore(registration);
            if (best == null || score > bestScore) {
                best = registration;
                bestScore = score;
            }
        }

        return best;
    }

    private int providerPreferenceScore(RegisteredServiceProvider<Economy> registration) {
        String providerName = normalize(registration.getProvider().getName());
        String pluginName = registration.getPlugin() != null ? normalize(registration.getPlugin().getName()) : "";
        String className = normalize(registration.getProvider().getClass().getName());

        if (providerName.equals(COFFERS_PROVIDER) || pluginName.equals(COFFERS_PROVIDER) || className.contains(".coffers.paper.")) {
            return 300;
        }
        if (providerName.equals(COFFERS_LEGACY_PROVIDER)
                || pluginName.equals(COFFERS_LEGACY_PROVIDER)
                || className.contains(".coffers.legacy.")) {
            return 250;
        }
        if (providerName.contains("coffers") || pluginName.contains("coffers") || className.contains(".coffers.")) {
            return 200;
        }
        return 0;
    }

    private boolean isPreferredCoffersProvider(RegisteredServiceProvider<Economy> registration) {
        return providerPreferenceScore(registration) >= 250;
    }

    private String safeProviderName(Economy provider) {
        try {
            return provider.getName();
        } catch (Exception ignored) {
            return provider.getClass().getSimpleName();
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
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
    public void onServiceUnregister(ServiceUnregisterEvent e) {
        if (e.getProvider().getService() == Economy.class) {
            setupEconomy();
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        String pluginName = e.getPlugin().getName();
        if (economy == null
                && ("Vault".equalsIgnoreCase(pluginName)
                || "Coffers".equalsIgnoreCase(pluginName)
                || "CoffersLegacy".equalsIgnoreCase(pluginName))) {
            setupEconomy();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent e) {
        String pluginName = e.getPlugin().getName();
        if ("Vault".equalsIgnoreCase(pluginName)) {
            if (economy != null) {
                plugin.getLogger().warning("Vault disabled. Switching to Free Mode.");
            }
            economy = null;
            return;
        }

        if ("Coffers".equalsIgnoreCase(pluginName) || "CoffersLegacy".equalsIgnoreCase(pluginName)) {
            setupEconomy();
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

    // ✅ NEW (compat): helps GUI/balance reflection that expects Player signatures
    public double balance(Player p) {
        if (p == null) return 0.0;
        return balance((OfflinePlayer) p);
    }

    // ✅ NEW (compat): common method name
    public double getBalance(Player p) {
        return balance(p);
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

    // ✅ NEW: boolean deposit helper (exchange can use this for better safety)
    public boolean deposit(OfflinePlayer p, double amount) {
        if (economy == null) return false;
        if (amount <= 0 || !Double.isFinite(amount)) return false;

        EconomyResponse res = economy.depositPlayer(p, amount);
        if (!res.transactionSuccess()) {
            plugin.getLogger().warning("[Vault] Deposit failed for " + p.getName() + ": " + res.errorMessage);
        }
        return res.transactionSuccess();
    }
}
