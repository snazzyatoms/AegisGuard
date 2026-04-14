package com.aegisguard.economy;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

/**
 * VaultHook
 * - Optional money economy bridge for AegisGuard.
 * - Prefers direct Coffers/CoffersLegacy connections when available.
 * - Falls back to Vault providers when direct Coffers is unavailable.
 */
public class VaultHook implements Listener {

    private static final String COFFERS_PROVIDER = "coffers";
    private static final String COFFERS_LEGACY_PROVIDER = "cofferslegacy";
    private static final String AEGIS_SOURCE = "aegisguard";
    private static final String VAULT_PLUGIN = "Vault";
    private static final String VAULT_ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private final AegisGuard plugin;
    private EconomyAccess economy;

    public VaultHook(AegisGuard plugin) {
        this.plugin = plugin;

        if (!plugin.cfg().useVault()) {
            plugin.getLogger().info("External economy features disabled via config.yml (Free Mode).");
            return;
        }

        setupEconomy();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void setupEconomy() {
        EconomyAccess previous = this.economy;
        EconomyAccess selected = findPreferredEconomy();
        this.economy = selected;

        if (previous == selected) {
            return;
        }

        if (selected == null) {
            if (previous != null) {
                plugin.getLogger().warning("No supported external economy backend is currently available. Economy features are in Free Mode.");
            }
            return;
        }

        if (selected.isDirectCoffers()) {
            plugin.getLogger().info("Connected directly to Coffers economy backend: "
                    + selected.backendName() + " (plugin " + selected.sourcePluginName() + ").");
            return;
        }

        if (selected.isCoffersPreferred()) {
            plugin.getLogger().info("Vault hooked successfully! Preferring Coffers economy provider: "
                    + selected.backendName() + " (plugin " + selected.sourcePluginName() + ").");
        } else {
            plugin.getLogger().info("Vault hooked successfully! Provider: " + selected.backendName()
                    + " (plugin " + selected.sourcePluginName() + ")");
        }
    }

    private EconomyAccess findPreferredEconomy() {
        EconomyAccess direct = findDirectCoffersEconomy();
        if (direct != null) {
            return direct;
        }

        RegisteredServiceProvider<?> selected = selectEconomyProvider(vaultEconomyRegistrations());
        if (selected == null) {
            return null;
        }

        try {
            return new VaultEconomyAccess(selected, isPreferredCoffersProvider(selected));
        } catch (Throwable t) {
            plugin.getLogger().warning("Detected Vault economy provider '" + providerDebugName(selected)
                    + "', but wrapping it failed: " + t.getMessage());
            return null;
        }
    }

    private EconomyAccess findDirectCoffersEconomy() {
        EconomyAccess modern = tryDirectCoffers("Coffers", false);
        if (modern != null) {
            return modern;
        }
        return tryDirectCoffers("CoffersLegacy", true);
    }

    private EconomyAccess tryDirectCoffers(String pluginName, boolean legacy) {
        Plugin hookedPlugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (hookedPlugin == null || !hookedPlugin.isEnabled()) {
            return null;
        }

        try {
            return legacy ? new DirectLegacyCoffersAccess(hookedPlugin) : new DirectCoffersAccess(hookedPlugin);
        } catch (Throwable t) {
            plugin.getLogger().warning("Detected " + pluginName + ", but direct economy hookup failed: " + t.getMessage());
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Collection<RegisteredServiceProvider<?>> vaultEconomyRegistrations() {
        Class<?> serviceClass = vaultEconomyClass();
        if (serviceClass == null) {
            return Collections.emptyList();
        }

        Collection<?> registrations = Bukkit.getServicesManager().getRegistrations((Class) serviceClass);
        if (registrations == null || registrations.isEmpty()) {
            return Collections.emptyList();
        }

        return (Collection<RegisteredServiceProvider<?>>) (Collection<?>) registrations;
    }

    private Class<?> vaultEconomyClass() {
        Plugin vaultPlugin = Bukkit.getPluginManager().getPlugin(VAULT_PLUGIN);
        if (vaultPlugin == null || !vaultPlugin.isEnabled()) {
            return null;
        }

        try {
            return Class.forName(VAULT_ECONOMY_CLASS, false, vaultPlugin.getClass().getClassLoader());
        } catch (ClassNotFoundException ignored) {
            try {
                return Class.forName(VAULT_ECONOMY_CLASS);
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("Vault is installed, but its economy API class could not be resolved.");
                return null;
            }
        }
    }

    private boolean isVaultPresent() {
        return vaultEconomyClass() != null;
    }

    private boolean isVaultEconomyService(Class<?> serviceClass) {
        return serviceClass != null && VAULT_ECONOMY_CLASS.equals(serviceClass.getName());
    }

    private RegisteredServiceProvider<?> selectEconomyProvider(Collection<RegisteredServiceProvider<?>> registrations) {
        RegisteredServiceProvider<?> best = null;
        int bestScore = Integer.MIN_VALUE;

        for (RegisteredServiceProvider<?> registration : registrations) {
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

    private int providerPreferenceScore(RegisteredServiceProvider<?> registration) {
        String providerName = normalize(providerDebugName(registration));
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

    private boolean isPreferredCoffersProvider(RegisteredServiceProvider<?> registration) {
        return providerPreferenceScore(registration) >= 250;
    }

    private String providerDebugName(RegisteredServiceProvider<?> registration) {
        Object provider = registration != null ? registration.getProvider() : null;
        if (provider == null) {
            return "unknown";
        }

        try {
            Method method = provider.getClass().getMethod("getName");
            Object value = method.invoke(provider);
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (Exception ignored) {
        }

        return provider.getClass().getSimpleName();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent e) {
        if (!isVaultPresent()) {
            return;
        }
        if (isVaultEconomyService(e.getProvider().getService())) {
            setupEconomy();
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent e) {
        if (!isVaultPresent()) {
            return;
        }
        if (isVaultEconomyService(e.getProvider().getService())) {
            setupEconomy();
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        String pluginName = e.getPlugin().getName();
        if ("Vault".equalsIgnoreCase(pluginName)
                || "Coffers".equalsIgnoreCase(pluginName)
                || "CoffersLegacy".equalsIgnoreCase(pluginName)) {
            setupEconomy();
        }
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent e) {
        String pluginName = e.getPlugin().getName();
        if ("Vault".equalsIgnoreCase(pluginName)
                || "Coffers".equalsIgnoreCase(pluginName)
                || "CoffersLegacy".equalsIgnoreCase(pluginName)) {
            setupEconomy();
        }
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public String format(double amount) {
        if (economy == null) {
            return String.format("$%,.2f", amount);
        }
        try {
            return economy.format(amount);
        } catch (Exception e) {
            return String.valueOf(amount);
        }
    }

    public double balance(OfflinePlayer p) {
        if (economy == null || p == null) return 0.0;
        return economy.balance(p);
    }

    public double balance(Player p) {
        if (p == null) return 0.0;
        return balance((OfflinePlayer) p);
    }

    public double getBalance(Player p) {
        return balance(p);
    }

    public boolean has(OfflinePlayer p, double amount) {
        if (economy == null) return true;
        if (amount <= 0) return true;
        return economy.has(p, amount);
    }

    public boolean charge(OfflinePlayer p, double amount) {
        if (economy == null) return true;
        if (amount <= 0) return true;
        if (!Double.isFinite(amount)) return false;
        return economy.withdraw(p, amount);
    }

    public void give(OfflinePlayer p, double amount) {
        if (economy == null) return;
        if (amount <= 0 || !Double.isFinite(amount)) return;
        economy.deposit(p, amount);
    }

    public boolean deposit(OfflinePlayer p, double amount) {
        if (economy == null) return false;
        if (amount <= 0 || !Double.isFinite(amount)) return false;
        return economy.deposit(p, amount);
    }

    private interface EconomyAccess {
        String backendName();

        String sourcePluginName();

        boolean isCoffersPreferred();

        boolean isDirectCoffers();

        String format(double amount);

        double balance(OfflinePlayer player);

        boolean has(OfflinePlayer player, double amount);

        boolean withdraw(OfflinePlayer player, double amount);

        boolean deposit(OfflinePlayer player, double amount);
    }

    private final class VaultEconomyAccess implements EconomyAccess {
        private final RegisteredServiceProvider<?> registration;
        private final Object provider;
        private final boolean preferredCoffers;
        private final Method getNameMethod;
        private final Method formatMethod;
        private final Method getBalanceMethod;
        private final Method hasMethod;
        private final Method withdrawPlayerMethod;
        private final Method depositPlayerMethod;
        private final Method transactionSuccessMethod;
        private final Field errorMessageField;

        private VaultEconomyAccess(RegisteredServiceProvider<?> registration, boolean preferredCoffers) throws Exception {
            this.registration = registration;
            this.provider = registration.getProvider();
            this.preferredCoffers = preferredCoffers;

            Class<?> type = provider.getClass();
            this.getNameMethod = type.getMethod("getName");
            this.formatMethod = type.getMethod("format", double.class);
            this.getBalanceMethod = type.getMethod("getBalance", OfflinePlayer.class);
            this.hasMethod = type.getMethod("has", OfflinePlayer.class, double.class);
            this.withdrawPlayerMethod = type.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            this.depositPlayerMethod = type.getMethod("depositPlayer", OfflinePlayer.class, double.class);

            Class<?> responseType = withdrawPlayerMethod.getReturnType();
            this.transactionSuccessMethod = responseType.getMethod("transactionSuccess");

            Field field;
            try {
                field = responseType.getField("errorMessage");
            } catch (NoSuchFieldException ignored) {
                field = null;
            }
            this.errorMessageField = field;
        }

        @Override
        public String backendName() {
            try {
                Object value = getNameMethod.invoke(provider);
                return value != null ? String.valueOf(value) : provider.getClass().getSimpleName();
            } catch (Exception ignored) {
                return provider.getClass().getSimpleName();
            }
        }

        @Override
        public String sourcePluginName() {
            return registration.getPlugin() != null ? registration.getPlugin().getName() : "unknown";
        }

        @Override
        public boolean isCoffersPreferred() {
            return preferredCoffers;
        }

        @Override
        public boolean isDirectCoffers() {
            return false;
        }

        @Override
        public String format(double amount) {
            try {
                Object value = formatMethod.invoke(provider, amount);
                return value != null ? String.valueOf(value) : String.valueOf(amount);
            } catch (Exception e) {
                return String.valueOf(amount);
            }
        }

        @Override
        public double balance(OfflinePlayer player) {
            try {
                Object value = getBalanceMethod.invoke(provider, player);
                return value instanceof Number number ? number.doubleValue() : 0.0D;
            } catch (Exception e) {
                return 0.0D;
            }
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            try {
                Object value = hasMethod.invoke(provider, player, amount);
                return value instanceof Boolean ok && ok;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            if (!has(player, amount)) {
                return false;
            }
            try {
                Object response = withdrawPlayerMethod.invoke(provider, player, amount);
                return transactionSuccessful(response);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            try {
                Object response = depositPlayerMethod.invoke(provider, player, amount);
                boolean success = transactionSuccessful(response);
                if (!success) {
                    String error = transactionError(response);
                    plugin.getLogger().warning("[Vault] Deposit failed for " + player.getName()
                            + (error == null || error.isEmpty() ? "" : ": " + error));
                }
                return success;
            } catch (Exception e) {
                plugin.getLogger().warning("[Vault] Deposit failed for " + player.getName() + ": " + e.getMessage());
                return false;
            }
        }

        private boolean transactionSuccessful(Object response) throws Exception {
            if (response == null) {
                return false;
            }
            Object value = transactionSuccessMethod.invoke(response);
            return value instanceof Boolean ok && ok;
        }

        private String transactionError(Object response) {
            if (response == null || errorMessageField == null) {
                return null;
            }
            try {
                Object value = errorMessageField.get(response);
                return value != null ? String.valueOf(value) : null;
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private abstract static class ReflectiveEconomyAccess implements EconomyAccess {
        protected final Plugin sourcePlugin;
        protected final Object economyObject;

        private ReflectiveEconomyAccess(Plugin sourcePlugin, Object economyObject) {
            this.sourcePlugin = sourcePlugin;
            this.economyObject = economyObject;
        }

        @Override
        public String sourcePluginName() {
            return sourcePlugin.getName();
        }

        @Override
        public boolean isCoffersPreferred() {
            return true;
        }

        @Override
        public boolean isDirectCoffers() {
            return true;
        }

        protected Method publicMethod(Class<?> owner, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
            return owner.getMethod(name, parameterTypes);
        }

        protected Method declaredMethod(Class<?> owner, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        }

        protected Object invoke(Method method, Object target, Object... args) throws Exception {
            return method.invoke(target, args);
        }
    }

    private final class DirectCoffersAccess extends ReflectiveEconomyAccess {
        private final Method defaultCurrencyId;
        private final Method getBalance;
        private final Method deposit;
        private final Method withdraw;
        private final Method format;

        private DirectCoffersAccess(Plugin sourcePlugin) throws Exception {
            super(sourcePlugin, resolveModernCoffersEconomy(sourcePlugin));
            Class<?> type = economyObject.getClass();
            this.defaultCurrencyId = publicMethod(type, "defaultCurrencyId");
            this.getBalance = publicMethod(type, "getBalance", UUID.class);
            this.deposit = publicMethod(type, "deposit", UUID.class, BigDecimal.class, String.class);
            this.withdraw = publicMethod(type, "withdraw", UUID.class, BigDecimal.class, String.class);
            this.format = publicMethod(type, "format", BigDecimal.class);
        }

        @Override
        public String backendName() {
            return "Coffers";
        }

        @Override
        public String format(double amount) {
            try {
                return String.valueOf(invoke(format, economyObject, BigDecimal.valueOf(amount)));
            } catch (Exception e) {
                return String.valueOf(amount);
            }
        }

        @Override
        public double balance(OfflinePlayer player) {
            try {
                Object value = invoke(getBalance, economyObject, player.getUniqueId());
                return value instanceof BigDecimal decimal ? decimal.doubleValue() : 0.0D;
            } catch (Exception e) {
                return 0.0D;
            }
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            return balance(player) >= amount;
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            try {
                Object result = invoke(withdraw, economyObject, player.getUniqueId(), BigDecimal.valueOf(amount), "AegisGuard withdraw");
                return isModernTransactionSuccessful(result);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            try {
                Object result = invoke(deposit, economyObject, player.getUniqueId(), BigDecimal.valueOf(amount), "AegisGuard deposit");
                return isModernTransactionSuccessful(result);
            } catch (Exception e) {
                return false;
            }
        }

        private boolean isModernTransactionSuccessful(Object result) throws Exception {
            if (result == null) {
                return false;
            }
            Method successful = publicMethod(result.getClass(), "successful");
            Object value = invoke(successful, result);
            return value instanceof Boolean ok && ok;
        }
    }

    private final class DirectLegacyCoffersAccess extends ReflectiveEconomyAccess {
        private final Method defaultCurrencyId;
        private final Method getBalance;
        private final Method deposit;
        private final Method withdraw;
        private final Method format;
        private final Method actorFactory;
        private final Method successMethod;

        private DirectLegacyCoffersAccess(Plugin sourcePlugin) throws Exception {
            super(sourcePlugin, resolveLegacyCoffersEconomy(sourcePlugin));
            Class<?> type = economyObject.getClass();
            this.defaultCurrencyId = declaredMethod(type, "getDefaultCurrencyId");
            this.getBalance = declaredMethod(type, "getBalance", UUID.class, String.class);

            Class<?> actorClass = sourcePlugin.getClass().getClassLoader()
                    .loadClass("com.aegisguard.coffers.legacy.LegacyTransactionActor");
            this.actorFactory = declaredMethod(actorClass, "system", String.class);

            this.deposit = declaredMethod(type, "deposit", UUID.class, String.class, BigDecimal.class, actorClass, String.class);
            this.withdraw = declaredMethod(type, "withdraw", UUID.class, String.class, BigDecimal.class, actorClass, String.class);
            this.format = declaredMethod(type, "format", String.class, BigDecimal.class);

            Class<?> resultClass = sourcePlugin.getClass().getClassLoader()
                    .loadClass("com.aegisguard.coffers.legacy.LegacyTransactionResult");
            this.successMethod = declaredMethod(resultClass, "isSuccessful");
        }

        @Override
        public String backendName() {
            return "CoffersLegacy";
        }

        @Override
        public String format(double amount) {
            try {
                return String.valueOf(invoke(format, economyObject, defaultCurrencyId(), BigDecimal.valueOf(amount)));
            } catch (Exception e) {
                return String.valueOf(amount);
            }
        }

        @Override
        public double balance(OfflinePlayer player) {
            try {
                Object value = invoke(getBalance, economyObject, player.getUniqueId(), defaultCurrencyId());
                return value instanceof BigDecimal decimal ? decimal.doubleValue() : 0.0D;
            } catch (Exception e) {
                return 0.0D;
            }
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            return balance(player) >= amount;
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            try {
                Object result = invoke(withdraw, economyObject, player.getUniqueId(), defaultCurrencyId(),
                        BigDecimal.valueOf(amount), systemActor(), "AegisGuard withdraw");
                return isLegacyTransactionSuccessful(result);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            try {
                Object result = invoke(deposit, economyObject, player.getUniqueId(), defaultCurrencyId(),
                        BigDecimal.valueOf(amount), systemActor(), "AegisGuard deposit");
                return isLegacyTransactionSuccessful(result);
            } catch (Exception e) {
                return false;
            }
        }

        private String defaultCurrencyId() throws Exception {
            return String.valueOf(invoke(defaultCurrencyId, economyObject));
        }

        private Object systemActor() throws Exception {
            return invoke(actorFactory, null, AEGIS_SOURCE);
        }

        private boolean isLegacyTransactionSuccessful(Object result) throws Exception {
            if (result == null) {
                return false;
            }
            Object value = invoke(successMethod, result);
            return value instanceof Boolean ok && ok;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object resolveModernCoffersEconomy(Plugin sourcePlugin) throws Exception {
        ClassLoader loader = sourcePlugin.getClass().getClassLoader();
        Class<?> serviceClass = loader.loadClass("com.aegisguard.coffers.api.CoffersEconomy");
        RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) serviceClass);
        if (registration != null && registration.getProvider() != null) {
            return registration.getProvider();
        }

        Method method = sourcePlugin.getClass().getMethod("economy");
        return method.invoke(sourcePlugin);
    }

    private static Object resolveLegacyCoffersEconomy(Plugin sourcePlugin) throws Exception {
        Method method = sourcePlugin.getClass().getDeclaredMethod("economy");
        method.setAccessible(true);
        return method.invoke(sourcePlugin);
    }
}
