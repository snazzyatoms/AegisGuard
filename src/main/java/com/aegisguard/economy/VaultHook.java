package com.aegisguard.economy;

import com.aegisguard.AegisGuard;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent; // --- NEW IMPORT ---
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

        // --- IMPROVEMENT ---
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

    /** Re-try when Vault enables after us */
    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        // --- MODIFIED ---
        // Added config check
        if (economy == null && "Vault".equalsIgnoreCase(e.getPlugin().getName())) {
            if (!plugin.cfg().useVault()) return; // Check again in case of /ag reload
            setupEconomy();
        }
    }

    /** --- NEW ---
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
// ... existing code ...
        // ... existing code ...
            economy = null;
            return;
        }
// ... existing code ...
        // ... existing code ...
        if (rsp != null) {
            this.economy = rsp.getProvider();
// ... existing code ...
        } else {
// ... existing code ...
        }
    }

    public boolean isEnabled() {
// ... existing code ...
    }

    /** Nicely format currency for messages. Falls back to plain amount. */
    public String format(double amount) {
// ... existing code ...
        // ... existing code ...
        return String.format("$%,.2f", amount);
    }

    public double balance(OfflinePlayer p) {
// ... existing code ...
    }

    /** Charge a player. Returns true on success. Free if Vault missing or amount <= 0. */
    public boolean charge(Player p, double amount) {
// ... existing code ...
        // ... existing code ...
        if (!economy.has(op, amount)) return false;

        EconomyResponse res = economy.withdrawPlayer(op, amount);
// ... existing code ...
        // ... existing code ...
        return true;
    }

    /**
     * Give money to a player (no-op if Vault missing or amount <= 0).
     * --- MODIFIED --- to support offline players for refunds.
     */
    public void give(OfflinePlayer op, double amount) {
        if (economy == null || amount <= 0 || !Double.isFinite(amount)) return;
        // OfflinePlayer op = p; // No longer needed
        EconomyResponse res = economy.depositPlayer(op, amount);
        if (!res.transactionSuccess()) {
            plugin.getLogger().warning("[Vault] deposit failed for " + op.getName() + ": " + res.errorMessage);
        }
    }
}
