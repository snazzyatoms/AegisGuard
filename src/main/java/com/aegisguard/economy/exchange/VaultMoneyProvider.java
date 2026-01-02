package com.aegisguard.economy.exchange;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;

public final class VaultMoneyProvider implements MoneyProvider {

    private final Economy economy;
    private final DecimalFormat fallbackFormat = new DecimalFormat("#,##0.00");

    public VaultMoneyProvider(Economy economy) {
        this.economy = economy;
    }

    @Override
    public boolean isAvailable() {
        return economy != null;
    }

    @Override
    public boolean has(Player player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (economy == null) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(Player player, double amount) {
        if (economy == null) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    @Override
    public String format(double amount) {
        if (economy == null) return fallbackFormat.format(amount);
        try {
            return economy.format(amount);
        } catch (Throwable ignored) {
            return fallbackFormat.format(amount);
        }
    }
}
