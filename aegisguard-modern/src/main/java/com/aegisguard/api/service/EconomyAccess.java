package com.aegisguard.api.service;

import com.aegisguard.economy.CurrencyType;
import org.bukkit.entity.Player;

/**
 * Access to the server's economy through the plugin's currency abstraction.
 * {@link #isVaultReady()} reports whether a real economy backend is available;
 * all balance operations are no-ops when it is not, so callers should gate
 * price-bearing features on it.
 */
public interface EconomyAccess {

    boolean isVaultReady();

    boolean has(Player player, double amount, CurrencyType type);

    boolean withdraw(Player player, double amount, CurrencyType type);

    void deposit(Player player, double amount, CurrencyType type);

    double getBalance(Player player, CurrencyType type);

    String format(double amount, CurrencyType type);
}
