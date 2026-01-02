package com.aegisguard.economy.exchange;

import java.util.UUID;

/**
 * TODO: Wire these 3 methods to your existing player data system.
 */
public final class AegisClaimBlockAccount implements ClaimBlockAccount {

    @Override
    public long getBalance(UUID playerId) {
        // TODO: return AegisGuard.getInstance().getPlayerData(playerId).getClaimBlocks();
        return 0;
    }

    @Override
    public boolean withdraw(UUID playerId, long amount) {
        if (amount <= 0) return false;
        // TODO:
        // var data = AegisGuard.getInstance().getPlayerData(playerId);
        // if (data.getClaimBlocks() < amount) return false;
        // data.setClaimBlocks(data.getClaimBlocks() - amount);
        // return true;
        return false;
    }

    @Override
    public void deposit(UUID playerId, long amount) {
        if (amount <= 0) return;
        // TODO:
        // var data = AegisGuard.getInstance().getPlayerData(playerId);
        // data.setClaimBlocks(data.getClaimBlocks() + amount);
    }
}
