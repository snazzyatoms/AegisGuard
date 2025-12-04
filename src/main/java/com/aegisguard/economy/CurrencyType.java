package com.aegisguard.economy;

/**
 * Defines the types of currency accepted by AegisGuard.
 * v1.3.0 primarily uses VAULT (Credits).
 */
public enum CurrencyType {
    VAULT,  // Standard Economy ($)
    EXP,    // Player XP Points (Future/Legacy)
    LEVEL,  // Player XP Levels (Future/Legacy)
    ITEM    // Physical Items (Replaced by Liquidation Chute in v1.3.0)
}
