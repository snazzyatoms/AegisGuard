package com.aegisguard.api.service;

import com.aegisguard.economy.CurrencyType;
import org.bukkit.entity.Player;

public interface EconomyAccess {

    boolean isVaultReady();

    boolean has(Player player, double amount, CurrencyType type);

    boolean withdraw(Player player, double amount, CurrencyType type);

    void deposit(Player player, double amount, CurrencyType type);

    double getBalance(Player player, CurrencyType type);

    String format(double amount, CurrencyType type);
}
