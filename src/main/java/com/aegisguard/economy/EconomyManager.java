package com.aegisguard.economy;

import com.aegisguard.AegisGuard;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

public class EconomyManager {

    private final AegisGuard plugin;

    public EconomyManager(AegisGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if the player has enough of the specific currency.
     */
    public boolean has(Player p, double amount, CurrencyType type) {
        if (p.hasPermission("aegis.admin.bypass")) return true;
        if (amount <= 0) return true;

        return switch (type) {
            case VAULT -> plugin.vault().has(p, amount);
            case EXP -> getTotalExperience(p) >= (int) amount;
            case LEVEL -> p.getLevel() >= (int) amount;
            case ITEM -> {
                Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
                yield p.getInventory().containsAtLeast(new ItemStack(mat), (int) amount);
            }
            case CLAIM_BLOCKS -> {
                long bal = getClaimBlocksBalance(p);
                yield bal >= Math.round(amount);
            }
        };
    }

    /**
     * Deducts the currency from the player.
     * Returns TRUE if successful, FALSE if they couldn't pay.
     */
    public boolean withdraw(Player p, double amount, CurrencyType type) {
        if (p.hasPermission("aegis.admin.bypass")) return true;
        if (amount <= 0) return true;

        if (!has(p, amount, type)) return false;

        switch (type) {
            case VAULT -> {
                return plugin.vault().charge(p, amount);
            }
            case EXP -> {
                setTotalExperience(p, getTotalExperience(p) - (int) amount);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
                return true;
            }
            case LEVEL -> {
                p.setLevel(p.getLevel() - (int) amount);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
                return true;
            }
            case ITEM -> {
                Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
                p.getInventory().removeItem(new ItemStack(mat, (int) amount));
                p.updateInventory();
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                return true;
            }
            case CLAIM_BLOCKS -> {
                long take = Math.max(0L, Math.round(amount));
                boolean ok = withdrawClaimBlocks(p, take);
                if (ok) {
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.1f);
                }
                return ok;
            }
        }
        return false;
    }

    /**
     * Refunds/Gives currency to the player.
     */
    public void deposit(Player p, double amount, CurrencyType type) {
        if (amount <= 0) return;

        switch (type) {
            case VAULT -> plugin.vault().give(p, amount);
            case EXP -> p.giveExp((int) amount);
            case LEVEL -> p.giveExpLevels((int) amount);
            case ITEM -> {
                Material mat = plugin.cfg().getWorldItemCostType(p.getWorld());
                HashMap<Integer, ItemStack> left = p.getInventory().addItem(new ItemStack(mat, (int) amount));
                for (ItemStack drop : left.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), drop);
                }
            }
            case CLAIM_BLOCKS -> {
                long give = Math.max(0L, Math.round(amount));
                depositClaimBlocks(p, give);
            }
        }
    }

    /**
     * Returns a nice string like "500 Coins" or "10 Levels".
     */
    public String format(double amount, CurrencyType type) {
        return switch (type) {
            case VAULT -> plugin.vault().format(amount);
            case EXP -> (int) amount + " XP";
            case LEVEL -> (int) amount + " Levels";
            case ITEM -> {
                // Safe, generic item name (format() has no world context here).
                Material mat = Material.EMERALD;

                try {
                    // If your cfg method can handle null world, it will; otherwise we keep EMERALD.
                    Material fromCfg = plugin.cfg().getWorldItemCostType(null);
                    if (fromCfg != null) mat = fromCfg;
                } catch (Throwable ignored) {}

                String name = mat.name().toLowerCase(Locale.ROOT).replace("_", " ");
                name = prettyCapitalize(name);

                if (amount != 1) name += "s";
                yield (int) amount + " " + name;
            }
            case CLAIM_BLOCKS -> {
                long v = Math.max(0L, Math.round(amount));
                yield v + " Claim Blocks";
            }
        };
    }

    // ---------------------------------------------------------------------
    // Claim Blocks bridge (reflection-based so it compiles even if names differ)
    // ---------------------------------------------------------------------

    private long getClaimBlocksBalance(Player p) {
        Object mgr = resolveClaimBlocksManager();
        if (mgr == null || p == null) return 0L;

        UUID uuid = p.getUniqueId();

        // Try common getters:
        Long v =
                tryLong(mgr, "getBalance", Player.class, p) != null ? tryLong(mgr, "getBalance", Player.class, p)
                : tryLong(mgr, "getBalance", UUID.class, uuid) != null ? tryLong(mgr, "getBalance", UUID.class, uuid)
                : tryLong(mgr, "getBlocks", Player.class, p) != null ? tryLong(mgr, "getBlocks", Player.class, p)
                : tryLong(mgr, "getBlocks", UUID.class, uuid) != null ? tryLong(mgr, "getBlocks", UUID.class, uuid)
                : tryLong(mgr, "getClaimBlocks", Player.class, p) != null ? tryLong(mgr, "getClaimBlocks", Player.class, p)
                : tryLong(mgr, "getClaimBlocks", UUID.class, uuid);

        return v != null ? Math.max(0L, v) : 0L;
    }

    private boolean withdrawClaimBlocks(Player p, long amount) {
        if (amount <= 0) return true;

        Object mgr = resolveClaimBlocksManager();
        if (mgr == null) return false;

        UUID uuid = p.getUniqueId();

        // Try direct withdraw/spend methods:
        Boolean ok =
                tryBool(mgr, "withdraw", Player.class, long.class, p, amount) != null ? tryBool(mgr, "withdraw", Player.class, long.class, p, amount)
                : tryBool(mgr, "withdraw", UUID.class, long.class, uuid, amount) != null ? tryBool(mgr, "withdraw", UUID.class, long.class, uuid, amount)
                : tryBool(mgr, "spend", Player.class, long.class, p, amount) != null ? tryBool(mgr, "spend", Player.class, long.class, p, amount)
                : tryBool(mgr, "spend", UUID.class, long.class, uuid, amount) != null ? tryBool(mgr, "spend", UUID.class, long.class, uuid, amount)
                : tryBool(mgr, "remove", Player.class, long.class, p, amount) != null ? tryBool(mgr, "remove", Player.class, long.class, p, amount)
                : tryBool(mgr, "remove", UUID.class, long.class, uuid, amount);

        if (ok != null) return ok;

        // Fallback: setBalance(balance - amount) if such a method exists
        long bal = getClaimBlocksBalance(p);
        if (bal < amount) return false;

        Long newBal = bal - amount;

        boolean set =
                tryVoid(mgr, "setBalance", Player.class, long.class, p, newBal) ||
                tryVoid(mgr, "setBalance", UUID.class, long.class, uuid, newBal) ||
                tryVoid(mgr, "setBlocks", Player.class, long.class, p, newBal) ||
                tryVoid(mgr, "setBlocks", UUID.class, long.class, uuid, newBal) ||
                tryVoid(mgr, "setClaimBlocks", Player.class, long.class, p, newBal) ||
                tryVoid(mgr, "setClaimBlocks", UUID.class, long.class, uuid, newBal);

        return set;
    }

    private void depositClaimBlocks(Player p, long amount) {
        if (amount <= 0) return;

        Object mgr = resolveClaimBlocksManager();
        if (mgr == null) return;

        UUID uuid = p.getUniqueId();

        // Try direct add/grant methods:
        Boolean ok =
                tryBool(mgr, "deposit", Player.class, long.class, p, amount) != null ? tryBool(mgr, "deposit", Player.class, long.class, p, amount)
                : tryBool(mgr, "deposit", UUID.class, long.class, uuid, amount) != null ? tryBool(mgr, "deposit", UUID.class, long.class, uuid, amount)
                : tryBool(mgr, "add", Player.class, long.class, p, amount) != null ? tryBool(mgr, "add", Player.class, long.class, p, amount)
                : tryBool(mgr, "add", UUID.class, long.class, uuid, amount) != null ? tryBool(mgr, "add", UUID.class, long.class, uuid, amount)
                : tryBool(mgr, "grant", Player.class, long.class, p, amount) != null ? tryBool(mgr, "grant", Player.class, long.class, p, amount)
                : tryBool(mgr, "grant", UUID.class, long.class, uuid, amount);

        if (ok != null) return;

        // Fallback: setBalance(balance + amount)
        long bal = getClaimBlocksBalance(p);
        long newBal = bal + amount;

        tryVoid(mgr, "setBalance", Player.class, long.class, p, newBal);
        tryVoid(mgr, "setBalance", UUID.class, long.class, uuid, newBal);
        tryVoid(mgr, "setBlocks", Player.class, long.class, p, newBal);
        tryVoid(mgr, "setBlocks", UUID.class, long.class, uuid, newBal);
        tryVoid(mgr, "setClaimBlocks", Player.class, long.class, p, newBal);
        tryVoid(mgr, "setClaimBlocks", UUID.class, long.class, uuid, newBal);
    }

    private Object resolveClaimBlocksManager() {
        // We try to find a manager/service on the plugin via common method names.
        Object obj =
                tryInvoke(plugin, "claimBlocks") != null ? tryInvoke(plugin, "claimBlocks")
                : tryInvoke(plugin, "claimBlocksManager") != null ? tryInvoke(plugin, "claimBlocksManager")
                : tryInvoke(plugin, "claimBlockManager") != null ? tryInvoke(plugin, "claimBlockManager")
                : tryInvoke(plugin, "blocks") != null ? tryInvoke(plugin, "blocks")
                : tryInvoke(plugin, "claimBlocksService");

        return obj;
    }

    private Object tryInvoke(Object target, String method) {
        if (target == null || method == null) return null;
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable ignored) {}
        return null;
    }

    private Long tryLong(Object target, String method, Class<?> p1, Object a1) {
        try {
            Method m = target.getClass().getMethod(method, p1);
            Object out = m.invoke(target, a1);
            if (out instanceof Number n) return n.longValue();
            if (out != null) return Long.parseLong(String.valueOf(out));
        } catch (Throwable ignored) {}
        return null;
    }

    private Boolean tryBool(Object target, String method, Class<?> p1, Class<?> p2, Object a1, Object a2) {
        try {
            Method m = target.getClass().getMethod(method, p1, p2);
            Object out = m.invoke(target, a1, a2);
            if (out instanceof Boolean b) return b;
            if (out != null) return Boolean.parseBoolean(String.valueOf(out));
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean tryVoid(Object target, String method, Class<?> p1, Class<?> p2, Object a1, Object a2) {
        try {
            Method m = target.getClass().getMethod(method, p1, p2);
            m.invoke(target, a1, a2);
            return true;
        } catch (Throwable ignored) {}
        return false;
    }

    private String prettyCapitalize(String name) {
        if (name == null || name.isBlank()) return "Item";
        char[] chars = name.toCharArray();
        boolean cap = true;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isLetter(c)) {
                chars[i] = cap ? Character.toUpperCase(c) : Character.toLowerCase(c);
                cap = false;
            } else if (Character.isWhitespace(c) || c == '.' || c == '\'') {
                cap = true;
            }
        }
        return String.valueOf(chars);
    }

    // --- XP Calculation Utilities ---

    private int getTotalExperience(Player player) {
        int experience;
        int level = player.getLevel();
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
        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.giveExp(amount);
    }
}
