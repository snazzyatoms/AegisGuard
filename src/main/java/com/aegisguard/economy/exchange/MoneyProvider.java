package com.aegisguard.economy.exchange;

import org.bukkit.entity.Player;

public interface MoneyProvider {
    boolean isAvailable();

    boolean has(Player player, double amount);

    boolean withdraw(Player player, double amount);

    boolean deposit(Player player, double amount);

    String format(double amount);
}
