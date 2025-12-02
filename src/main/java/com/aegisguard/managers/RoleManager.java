package com.yourname.aegisguard.managers;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class RoleManager {

    private final JavaPlugin plugin;
    
    // Cache: "viceroy" -> Role Object
    private final Map<String, RoleDefinition> privateRoles = new LinkedHashMap<>();
    private final Map<String, RoleDefinition> guildRoles = new LinkedHashMap<>();

    public RoleManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAllRoles();
    }

    public void loadAllRoles() {
        privateRoles.clear();
        guildRoles.clear();

        // 1. Load Private Estate Roles (roles.yml)
        loadRoleFile("roles.yml", privateRoles);

        // 2. Load Guild/Alliance Roles (alliance_roles.yml)
        loadRoleFile("alliance_roles.yml", guildRoles);
        
        plugin.getLogger().info("Loaded " + privateRoles.size() + " Private Roles and " + guildRoles.size() + " Guild Roles.");
    }

    private void loadRoleFile(String fileName, Map<String, RoleDefinition> targetMap) {
        File file = new File(plugin.getDataFolder(), fileName);
        
        // If file doesn't exist, try to save from resources
        if (!file.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not find role file: " + fileName);
                return;
            }
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection rolesSection = config.getConfigurationSection("roles");

        if (rolesSection == null) return;

        for (String key : rolesSection.getKeys(false)) {
            // Check if enabled
            if (!rolesSection.getBoolean(key + ".enabled", true)) continue;

            // Read Data
            String name = ChatColor.translateAlternateColorCodes('&', rolesSection.getString(key + ".name", key));
            int priority = rolesSection.getInt(key + ".priority", 0);
            List<String> description = new ArrayList<>();
            String descLine = rolesSection.getString(key + ".description");
            if (descLine != null) description.add(ChatColor.translateAlternateColorCodes('&', descLine));
            
            // Icon Parsing
            String matName = rolesSection.getString(key + ".icon", "PAPER");
            Material icon = Material.getMaterial(matName);
            if (icon == null) icon = Material.PAPER;

            // Permissions / Defaults
            List<String> defaults = rolesSection.getStringList(key + ".defaults");
            // Support "flags" keyword used in alliance file too
            if (defaults.isEmpty()) defaults = rolesSection.getStringList(key + ".flags");

            // Create Object
            RoleDefinition role = new RoleDefinition(key, name, priority, icon, description, defaults);
            targetMap.put(key, role);
        }
    }

    /**
     * Get a specific Private Role (e.g., "viceroy")
     */
    public RoleDefinition getPrivateRole(String id) {
        return privateRoles.get(id.toLowerCase());
    }

    /**
     * Get a specific Guild Role (e.g., "grandmaster")
     */
    public RoleDefinition getGuildRole(String id) {
        return guildRoles.get(id.toLowerCase());
    }

    /**
     * Get the highest priority role (The Owner/Leader role)
     */
    public RoleDefinition getTopPrivateRole() {
        return privateRoles.values().stream().max(Comparator.comparingInt(RoleDefinition::getPriority)).orElse(null);
    }
    
    public RoleDefinition getTopGuildRole() {
        return guildRoles.values().stream().max(Comparator.comparingInt(RoleDefinition::getPriority)).orElse(null);
    }

    public Collection<RoleDefinition> getAllPrivateRoles() {
        return privateRoles.values();
    }

    public Collection<RoleDefinition> getAllGuildRoles() {
        return guildRoles.values();
    }

    // ==========================================================
    // 📦 INNER CLASS: RoleDefinition (Data Holder)
    // ==========================================================
    public static class RoleDefinition {
        private final String id;
        private final String displayName;
        private final int priority;
        private final Material icon;
        private final List<String> description;
        private final Set<String> defaultFlags;

        public RoleDefinition(String id, String displayName, int priority, Material icon, List<String> description, List<String> defaultFlags) {
            this.id = id;
            this.displayName = displayName;
            this.priority = priority;
            this.icon = icon;
            this.description = description;
            this.defaultFlags = new HashSet<>(defaultFlags);
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public int getPriority() { return priority; }
        public Material getIcon() { return icon; }
        public List<String> getDescription() { return description; }
        public Set<String> getDefaultFlags() { return defaultFlags; }
        
        public boolean hasDefaultFlag(String flag) {
            return defaultFlags.contains("ALL") || defaultFlags.contains(flag);
        }
    }
}
