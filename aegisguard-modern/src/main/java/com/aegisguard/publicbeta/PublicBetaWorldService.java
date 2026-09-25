package com.aegisguard.publicbeta;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Restart-gated, identity-checked provisioning for Public Beta worlds.
 * It never deletes worlds and never adopts an unregistered folder collision.
 */
public final class PublicBetaWorldService {

    public static final String WORLD_PERMISSION = "aegis.admin.publicbeta.worlds";

    public enum Role {
        WELCOME_HUB("welcome-hub"),
        PLAY_WORLD("play-world"),
        TEST_LAB("test-lab");

        private final String key;
        Role(String key) { this.key = key; }
        public String key() { return key; }
    }

    public enum State { NOT_CONFIGURED, PENDING_RESTART, PROVISIONING, READY, PARTIAL_FAILURE, BLOCKED }

    public record OperationResult(boolean success, String message) {
        public static OperationResult ok(String message) { return new OperationResult(true, message); }
        public static OperationResult fail(String message) { return new OperationResult(false, message); }
    }

    private static final String ROOT = "public-beta-mode";
    private static final int REGISTRY_SCHEMA = 1;
    private final AegisGuard plugin;
    private final File registryFile;
    private final NamespacedKey instanceKey;
    private final NamespacedKey roleKey;
    private YamlConfiguration registry;

    public PublicBetaWorldService(AegisGuard plugin) {
        this.plugin = plugin;
        this.registryFile = new File(plugin.getDataFolder(), "public-beta/worlds.yml");
        this.instanceKey = new NamespacedKey(plugin, "public_beta_instance");
        this.roleKey = new NamespacedKey(plugin, "public_beta_world_role");
        this.registry = loadRegistry();
    }

    public synchronized State state() {
        return parseState(registry.getString("state"));
    }

    public synchronized String stateDetail() {
        return registry.getString("detail", "");
    }

    public synchronized boolean isRegistered() {
        return registryFile.isFile() && instanceMatches();
    }

    public synchronized String worldName(Role role) {
        String registered = registry.getString(rolePath(role) + ".name", "").trim();
        if (!registered.isEmpty() && instanceMatches()) return registered;
        String nested = plugin.getConfig().getString(ROOT + ".worlds." + role.key() + ".name", "").trim();
        if (!nested.isEmpty()) return nested;
        String legacy = plugin.getConfig().getString(ROOT + ".worlds." + role.key(), "").trim();
        if (!legacy.isEmpty()) return legacy;
        return switch (role) {
            case WELCOME_HUB -> primaryWorldName();
            case PLAY_WORLD -> "aegis_beta_play";
            case TEST_LAB -> "aegis_beta_test_lab";
        };
    }

    public World world(Role role) {
        String name = worldName(role);
        return name.isEmpty() ? null : Bukkit.getWorld(name);
    }

    public boolean isPublicBetaWorld(World world) {
        if (world == null) return false;
        for (Role role : Role.values()) {
            if (world.getName().equalsIgnoreCase(worldName(role))) return true;
        }
        return false;
    }

    public synchronized OperationResult stageProvisioning(World primaryWorld) {
        if (!plugin.getConfig().getBoolean(ROOT + ".enabled", false)) {
            return OperationResult.fail("Public Beta mode is disabled.");
        }
        if (primaryWorld == null) return OperationResult.fail("The primary server world is not loaded.");
        String instance = configuredInstance();
        if (!safeInstance(instance)) return OperationResult.fail("The Public Beta instance ID is blank or unsafe.");

        String play = configuredManagedName(Role.PLAY_WORLD, "aegis_beta_play");
        String lab = configuredManagedName(Role.TEST_LAB, "aegis_beta_test_lab");
        if (!validWorldName(play) || !validWorldName(lab)) {
            return OperationResult.fail("Play and Test Lab names may use only letters, numbers, dot, underscore, and hyphen.");
        }
        if (primaryWorld.getName().equalsIgnoreCase(play)
                || primaryWorld.getName().equalsIgnoreCase(lab)
                || play.equalsIgnoreCase(lab)) {
            return OperationResult.fail("Welcome Hub, Play World, and Test Lab names must be distinct.");
        }
        if (looksPrivate(primaryWorld.getName()) || looksPrivate(play) || looksPrivate(lab)) {
            return OperationResult.fail("A world name appears to reference private development data.");
        }
        OperationResult playCheck = checkInitialTarget(play);
        if (!playCheck.success()) return playCheck;
        OperationResult labCheck = checkInitialTarget(lab);
        if (!labCheck.success()) return labCheck;

        registry = new YamlConfiguration();
        registry.set("schema", REGISTRY_SCHEMA);
        registry.set("instance-id", instance);
        registry.set("data-scope", plugin.getConfig().getString(ROOT + ".isolation.data-scope", "PUBLIC_BETA_ONLY"));
        registry.set("state", State.PENDING_RESTART.name());
        registry.set("detail", "Owner confirmed provisioning; restart required.");
        registry.set("requested-at", System.currentTimeMillis());
        writeRole(Role.WELCOME_HUB, primaryWorld.getName(), primaryWorld.getUID(), false, "ADOPTED_PRIMARY");
        writePendingRole(Role.PLAY_WORLD, play);
        writePendingRole(Role.TEST_LAB, lab);
        try {
            saveRegistry();
            return OperationResult.ok("Provisioning staged. Restart the server to create the Play and Test Lab worlds.");
        } catch (IOException error) {
            return OperationResult.fail("Could not save the provisioning request: " + safeMessage(error));
        }
    }

    public synchronized OperationResult cancelPending() {
        if (state() != State.PENDING_RESTART) return OperationResult.fail("There is no untouched pending request to cancel.");
        if ("READY".equals(registry.getString(rolePath(Role.PLAY_WORLD) + ".status"))
                || "READY".equals(registry.getString(rolePath(Role.TEST_LAB) + ".status"))) {
            return OperationResult.fail("Provisioning already created a world and cannot be cancelled here.");
        }
        try {
            Files.deleteIfExists(registryFile.toPath());
            registry = new YamlConfiguration();
            return OperationResult.ok("Pending Public Beta provisioning was cancelled. No world was deleted.");
        } catch (IOException error) {
            return OperationResult.fail("Could not cancel the pending request: " + safeMessage(error));
        }
    }

    /** Called synchronously from plugin enable before AegisGuard starts its repeating tasks. */
    public synchronized void provisionAtStartup() {
        if (!plugin.getConfig().getBoolean(ROOT + ".enabled", false) || !registryFile.isFile()) return;
        registry = loadRegistry();
        if (!instanceMatches()) {
            block("Registry instance ID does not match this server; refusing all world operations.");
            return;
        }
        State current = state();
        if (current == State.NOT_CONFIGURED || current == State.BLOCKED) return;

        registry.set("state", State.PROVISIONING.name());
        registry.set("detail", "Loading registered Public Beta worlds during startup.");
        saveRegistryLogged();

        if (!verifyAdoptedHub()) return;
        boolean play = ensureManaged(Role.PLAY_WORLD);
        boolean lab = ensureManaged(Role.TEST_LAB);
        registry.set("last-attempt-at", System.currentTimeMillis());
        if (play && lab) {
            registry.set("state", State.READY.name());
            registry.set("detail", "Welcome Hub adopted; Play and Test Lab worlds are ready.");
            plugin.getLogger().info("[Public Beta] Play and Test Lab worlds are ready.");
        } else {
            registry.set("state", State.PARTIAL_FAILURE.name());
            registry.set("detail", "One or more managed worlds could not be safely created or loaded. See role errors and server log.");
        }
        saveRegistryLogged();
    }

    private boolean verifyAdoptedHub() {
        String path = rolePath(Role.WELCOME_HUB);
        String name = registry.getString(path + ".name", "");
        String expectedUuid = registry.getString(path + ".uuid", "");
        World hub = Bukkit.getWorld(name);
        if (hub == null || !hub.getUID().toString().equalsIgnoreCase(expectedUuid)) {
            block("The adopted primary Welcome Hub is missing or has a different UUID.");
            return false;
        }
        markWorld(hub, Role.WELCOME_HUB);
        hub.save();
        registry.set(path + ".status", "READY");
        return true;
    }

    private boolean ensureManaged(Role role) {
        String path = rolePath(role);
        String name = registry.getString(path + ".name", "").trim();
        String status = registry.getString(path + ".status", "PENDING");
        String expectedUuid = registry.getString(path + ".uuid", "").trim();
        try {
            World loaded = Bukkit.getWorld(name);
            if (loaded != null) {
                if (!"READY".equals(status) || expectedUuid.isEmpty()) {
                    return roleError(role, "A loaded world already uses this unregistered name.");
                }
                return verifyManagedIdentity(role, loaded, expectedUuid);
            }

            Path folder = worldFolder(name);
            if (Files.exists(folder) && (!"READY".equals(status) || expectedUuid.isEmpty())) {
                return roleError(role, "An unregistered world folder already exists at " + folder + ".");
            }

            World world;
            if (Files.exists(folder)) {
                world = new WorldCreator(name).environment(World.Environment.NORMAL).createWorld();
                if (world == null) return roleError(role, "Paper returned no world while loading the registered folder.");
                return verifyManagedIdentity(role, world, expectedUuid);
            }

            world = new WorldCreator(name)
                    .environment(World.Environment.NORMAL)
                    .type(WorldType.NORMAL)
                    .generateStructures(true)
                    .createWorld();
            if (world == null) return roleError(role, "Paper returned no world during creation.");
            markWorld(world, role);
            world.save();
            registry.set(path + ".uuid", world.getUID().toString());
            registry.set(path + ".status", "READY");
            registry.set(path + ".created-at", System.currentTimeMillis());
            registry.set(path + ".error", null);
            saveRegistryLogged();
            return true;
        } catch (Throwable error) {
            plugin.getLogger().log(Level.SEVERE, "[Public Beta] Could not provision " + role.key() + ".", error);
            return roleError(role, safeMessage(error));
        }
    }

    private boolean verifyManagedIdentity(Role role, World world, String expectedUuid) {
        if (!world.getUID().toString().equalsIgnoreCase(expectedUuid)) {
            return roleError(role, "Registered UUID does not match the loaded world.");
        }
        String markedInstance = world.getPersistentDataContainer().get(instanceKey, PersistentDataType.STRING);
        String markedRole = world.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
        if (!configuredInstance().equals(markedInstance) || !role.name().equals(markedRole)) {
            return roleError(role, "World identity marker does not match this Public Beta instance and role.");
        }
        registry.set(rolePath(role) + ".status", "READY");
        registry.set(rolePath(role) + ".error", null);
        return true;
    }

    private void markWorld(World world, Role role) {
        world.getPersistentDataContainer().set(instanceKey, PersistentDataType.STRING, configuredInstance());
        world.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role.name());
    }

    private boolean roleError(Role role, String error) {
        registry.set(rolePath(role) + ".status", "ERROR");
        registry.set(rolePath(role) + ".error", error);
        plugin.getLogger().severe("[Public Beta] " + role.key() + ": " + error);
        saveRegistryLogged();
        return false;
    }

    private void block(String detail) {
        registry.set("state", State.BLOCKED.name());
        registry.set("detail", detail);
        plugin.getLogger().severe("[Public Beta] " + detail);
        saveRegistryLogged();
    }

    private OperationResult checkInitialTarget(String name) {
        if (Bukkit.getWorld(name) != null) return OperationResult.fail("A loaded world already uses the name '" + name + "'.");
        Path folder = worldFolder(name);
        if (Files.exists(folder)) return OperationResult.fail("A world folder already exists and will not be adopted: " + folder);
        return OperationResult.ok("Available");
    }

    private void writePendingRole(Role role, String name) {
        writeRole(role, name, null, true, "AEGIS_MANAGED");
        String path = rolePath(role);
        registry.set(path + ".status", "PENDING");
        registry.set(path + ".environment", "NORMAL");
        registry.set(path + ".generator", "NORMAL");
        registry.set(path + ".seed", "RANDOM");
        registry.set(path + ".generate-structures", true);
    }

    private void writeRole(Role role, String name, UUID uuid, boolean owned, String source) {
        String path = rolePath(role);
        registry.set(path + ".name", name);
        registry.set(path + ".uuid", uuid == null ? null : uuid.toString());
        registry.set(path + ".owned-by-aegisguard", owned);
        registry.set(path + ".source", source);
        registry.set(path + ".status", uuid == null ? "PENDING" : "READY");
    }

    private String configuredManagedName(Role role, String fallback) {
        String nested = plugin.getConfig().getString(ROOT + ".worlds." + role.key() + ".name", "").trim();
        if (!nested.isEmpty()) return nested;
        return plugin.getConfig().getString(ROOT + ".worlds." + role.key(), fallback).trim();
    }

    private String primaryWorldName() {
        return Bukkit.getWorlds().isEmpty() ? "world" : Bukkit.getWorlds().get(0).getName();
    }

    private String configuredInstance() {
        return plugin.getConfig().getString(ROOT + ".isolation.instance-id", "aegisguard-public-beta").trim();
    }

    private boolean instanceMatches() {
        String stored = registry.getString("instance-id", "").trim();
        return !stored.isEmpty() && stored.equals(configuredInstance());
    }

    private static boolean safeInstance(String value) {
        return value != null && !value.isBlank() && !value.equalsIgnoreCase("private-development");
    }

    static boolean validWorldName(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,64}");
    }

    static boolean looksPrivate(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.contains("private") || normalized.contains("development") || normalized.contains("dev_server");
    }

    private Path worldFolder(String name) {
        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path target = container.resolve(name).normalize();
        if (!target.startsWith(container) || target.equals(container)) {
            throw new IllegalArgumentException("Unsafe world folder target: " + target);
        }
        return target;
    }

    private String rolePath(Role role) { return "worlds." + role.key(); }

    private YamlConfiguration loadRegistry() {
        return registryFile.isFile() ? YamlConfiguration.loadConfiguration(registryFile) : new YamlConfiguration();
    }

    private void saveRegistryLogged() {
        try {
            saveRegistry();
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "[Public Beta] Could not save world registry.", error);
        }
    }

    private void saveRegistry() throws IOException {
        File parent = registryFile.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        Path target = registryFile.toPath();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, registry.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static State parseState(String value) {
        if (value == null || value.isBlank()) return State.NOT_CONFIGURED;
        try { return State.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return State.BLOCKED; }
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "unknown error";
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
