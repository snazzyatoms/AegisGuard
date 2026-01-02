package com.aegisguard.economy;

import com.aegisguard.AegisGuard;
import com.aegisguard.claimblocks.ClaimBlockManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * EconomyManager
 * - Unified currency handling (Vault / EXP / LEVEL / ITEM / CLAIM_BLOCKS)
 * - Adds safe balance helpers so GUIs can display balances reliably (especially Claim Blocks)
 * - Keeps all existing behavior; only adds compatibility + safeguards.
 */
public class EconomyManager {

    private final AegisGuard plugin;

    public EconomyManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    public boolean has(Player p, double amount, CurrencyType type) {
        if (p == null) return false;
        if (p.hasPermission("aegis.admin.bypass")) return true;
        if (amount <= 0) return true;
        if (type == null) return false;

        // Normalize amount so fractional values cannot bypass checks.
        final int intCost = toIntCost(amount);
        final long longCost = toLongCost(amount);

        return switch (type) {
            case VAULT -> plugin.vault() != null && plugin.vault().has(p, amount);
            case EXP -> getTotalExperience(p) >= intCost;
            case LEVEL -> p.getLevel() >= intCost;
            case ITEM -> {
                Material mat = (plugin.cfg() != null) ? plugin.cfg().getWorldItemCostType(p.getWorld()) : Material.DIAMOND;
                yield p.getInventory().containsAtLeast(new ItemStack(mat), intCost);
            }
            case CLAIM_BLOCKS -> {
                if (plugin.getClaimBlockManager() == null) yield false;
                yield plugin.getClaimBlockManager().getAvailableBlocks(p.getUniqueId()) >= longCost;
            }
            default -> false;
        };
    }

    public boolean withdraw(Player p, double amount, CurrencyType type) {
        if (p == null) return false;
        if (p.hasPermission("aegis.admin.bypass")) return true;
        if (amount <= 0) return true;
        if (type == null) return false;

        if (!has(p, amount, type)) return false;

        final int intCost = toIntCost(amount);
        final long longCost = toLongCost(amount);

        // Switch EXPRESSION: guarantees a return for every path (fixes "missing return statement").
        return switch (type) {
            case VAULT -> plugin.vault() != null && plugin.vault().charge(p, amount);

            case EXP -> {
                setTotalExperience(p, getTotalExperience(p) - intCost);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
                yield true;
            }

            case LEVEL -> {
                p.setLevel(Math.max(0, p.getLevel() - intCost));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
                yield true;
            }

            case ITEM -> {
                Material mat = (plugin.cfg() != null) ? plugin.cfg().getWorldItemCostType(p.getWorld()) : Material.DIAMOND;
                p.getInventory().removeItem(new ItemStack(mat, intCost));
                p.updateInventory();
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                yield true;
            }

            case CLAIM_BLOCKS -> {
                if (plugin.getClaimBlockManager() == null) yield false;
                boolean ok = plugin.getClaimBlockManager().spend(p.getUniqueId(), longCost);
                if (ok) p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.15f);
                yield ok;
            }

            default -> false;
        };
    }

    public void deposit(Player p, double amount, CurrencyType type) {
        if (p == null) return;
        if (amount <= 0) return;
        if (type == null) return;

        final int intAmt = toIntAmount(amount);
        final long longAmt = toLongAmount(amount);

        switch (type) {
            case VAULT -> {
                if (plugin.vault() != null) plugin.vault().give(p, amount);
            }
            case EXP -> {
                if (intAmt > 0) p.giveExp(intAmt);
            }
            case LEVEL -> {
                if (intAmt > 0) p.giveExpLevels(intAmt);
            }
            case ITEM -> {
                Material mat = (plugin.cfg() != null) ? plugin.cfg().getWorldItemCostType(p.getWorld()) : Material.DIAMOND;
                int stackAmt = Math.max(1, (int) Math.min(Integer.MAX_VALUE, longAmt));
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(mat, stackAmt));
                for (ItemStack drop : left.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
            case CLAIM_BLOCKS -> {
                // NOTE: Existing behavior preserved: this is used as "refund spent blocks" (upgrades).
                if (plugin.getClaimBlockManager() != null) {
                    plugin.getClaimBlockManager().refund(p.getUniqueId(), longAmt);
                }
            }
            default -> {
                // no-op
            }
        }
    }

    /**
     * Balance helper for GUIs.
     * Returns -1 when balance is not supported/known for that currency.
     */
    public double getBalance(Player p, CurrencyType type) {
        if (p == null || type == null) return -1;

        return switch (type) {
            case CLAIM_BLOCKS -> {
                if (plugin.getClaimBlockManager() == null) yield -1;
                yield plugin.getClaimBlockManager().getAvailableBlocks(p.getUniqueId());
            }
            case VAULT -> tryVaultBalance(p);
            case EXP -> getTotalExperience(p);
            case LEVEL -> p.getLevel();
            case ITEM -> {
                Material mat = (plugin.cfg() != null) ? plugin.cfg().getWorldItemCostType(p.getWorld()) : Material.DIAMOND;
                yield countItem(p, mat);
            }
            default -> -1;
        };
    }

    /**
     * Alias for older/reflection-based callers.
     */
    public double balance(Player p, CurrencyType type) {
        return getBalance(p, type);
    }

    public String format(double amount, CurrencyType type) {
        if (type == null) return String.valueOf(amount);

        return switch (type) {
            case VAULT -> plugin.vault() != null ? plugin.vault().format(amount) : String.valueOf(amount);
            case EXP -> toIntAmount(amount) + " XP";
            case LEVEL -> toIntAmount(amount) + " Levels";
            case ITEM -> {
                Material mat = (plugin.cfg() != null)
                        ? plugin.cfg().getWorldItemCostType(null)
                        : Material.DIAMOND;

                long amt = toLongAmount(amount);
                String name = mat.name().toLowerCase().replace("_", " ");
                name = capitalizeWords(name);
                if (amt != 1) name += "s";
                yield amt + " " + name;
            }
            case CLAIM_BLOCKS -> toLongAmount(amount) + " Claim Blocks";
            default -> String.valueOf(amount);
        };
    }

    // =========================================================================
    // ✅ NEW (v1.2.5): Exchange Helpers (Vault <-> ClaimBlocks)
    // =========================================================================

    /**
     * Returns true if Vault is allowed by config and the vault wrapper exists.
     * (Does not guarantee an economy provider is installed, but your VaultHug should handle that.)
     */
    public boolean isVaultReady() {
        if (plugin.cfg() != null) {
            boolean useVault = plugin.cfg().raw().getBoolean("economy.use_vault", true);
            boolean vaultEnabled = plugin.cfg().raw().getBoolean("economy.vault.enabled", true);
            if (!useVault || !vaultEnabled) return false;
        }
        return plugin.vault() != null;
    }

    public boolean hasVaultMoney(Player p, double amount) {
        if (p == null) return false;
        if (amount <= 0) return true;
        return isVaultReady() && plugin.vault().has(p, amount);
    }

    public boolean withdrawVaultMoney(Player p, double amount) {
        if (p == null) return false;
        if (amount <= 0) return true;
        return isVaultReady() && plugin.vault().charge(p, amount);
    }

    public void depositVaultMoney(Player p, double amount) {
        if (p == null) return;
        if (amount <= 0) return;
        if (!isVaultReady()) return;
        plugin.vault().give(p, amount);
    }

    /**
     * Add ClaimBlocks that were purchased via the exchange.
     * This records EXCHANGE lots so sell-lock (EXCHANGE_PURCHASED_ONLY) works properly.
     */
    public void grantClaimBlocksFromExchange(Player p, long blocks) {
        if (p == null) return;
        if (blocks <= 0) return;

        if (plugin.getClaimBlockManager() != null) {
            plugin.getClaimBlockManager().addBoughtFromExchange(p.getUniqueId(), blocks);
        }
    }

    /**
     * Withdraw ClaimBlocks for an exchange SELL operation (sell-lock aware).
     * Use this instead of withdraw(...CLAIM_BLOCKS...) for exchange selling.
     */
    public ClaimBlockManager.SaleWithdrawResult withdrawClaimBlocksForExchangeSell(Player p, long blocks, ClaimBlockManager.SellLockConfig lock) {
        if (p == null) return ClaimBlockManager.SaleWithdrawResult.fail("invalid_player", blocks, 0, 0);
        if (blocks <= 0) return ClaimBlockManager.SaleWithdrawResult.ok(blocks, 0, 0, 0);

        if (plugin.getClaimBlockManager() == null) {
            return ClaimBlockManager.SaleWithdrawResult.fail("claimblocks_unavailable", blocks, 0, 0);
        }

        boolean bypass = p.hasPermission("aegisguard.claimblocks.selllock.bypass");
        return plugin.getClaimBlockManager().withdrawForExchangeSell(p.getUniqueId(), blocks, lock, bypass);
    }

    // ----------------------------
    // Internals
    // ----------------------------

    /** Costs should never be fractional; round UP to prevent paying 0 for 0.1, etc. */
    private int toIntCost(double amount) {
        return (int) Math.max(0, Math.ceil(amount));
    }

    private long toLongCost(double amount) {
        return (long) Math.max(0L, Math.ceil(amount));
    }

    /** For display/deposit, use normal rounding (more intuitive). */
    private int toIntAmount(double amount) {
        return (int) Math.max(0, Math.round(amount));
    }

    private long toLongAmount(double amount) {
        return Math.max(0L, Math.round(amount));
    }

    private int countItem(Player p, Material mat) {
        if (p == null || mat == null) return 0;
        int total = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == mat) total += it.getAmount();
        }
        return total;
    }

    private double tryVaultBalance(Player p) {
        if (plugin.vault() == null) return -1;

        Object v = plugin.vault();

        // Try common wrapper methods without hard-linking to a specific API.
        for (String methodName : new String[]{"balance", "getBalance", "getPlayerBalance"}) {
            try {
                Method m = v.getClass().getMethod(methodName, Player.class);
                Object out = m.invoke(v, p);
                return asDouble(out);
            } catch (Throwable ignored) { }
        }
        return -1;
    }

    private double asDouble(Object o) {
        if (o == null) return -1;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); } catch (Throwable ignored) { }
        return -1;
    }

    private String capitalizeWords(String s) {
        if (s == null || s.isBlank()) return s;
        char[] chars = s.toCharArray();
        boolean found = false;

        for (int i = 0; i < chars.length; i++) {
            if (!found && Character.isLetter(chars[i])) {
                chars[i] = Character.toUpperCase(chars[i]);
                found = true;
            } else if (Character.isWhitespace(chars[i]) || chars[i] == '.' || chars[i] == '\'') {
                found = false;
            }
        }
        return String.valueOf(chars);
    }

    private int getTotalExperience(Player player) {
        int level = player.getLevel();
        int experience;

        if (level >= 0 && level <= 15) {
            experience = (int) Math.ceil(Math.pow(level, 2) + 6 * level);
        } else if (level <= 30) {
            experience = (int) Math.ceil((2.5 * Math.pow(level, 2) - 40.5 * level + 360));
        } else {
            experience = (int) Math.ceil(((4.5 * Math.pow(level, 2) - 162.5 * level + 2220)));
        }

        return experience + Math.round(player.getExp() * player.getExpToLevel());
    }

    private void setTotalExperience(Player player, int amount) {
        amount = Math.max(0, amount);
        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.giveExp(amount);
    }
}
