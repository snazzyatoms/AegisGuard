import org.bukkit.Material;
import org.bukkit.event.Event;
// ... keep your other imports

// ...

    /**
     * Reload/refresh detection MUST be strict.
     * Otherwise "Language: ..." buttons get mistaken for reload triggers.
     *
     * Preferred: PDC aegis_action=reload|refresh|reload_all|refresh_lang
     * Fallback: only allow specific materials + words like reload/refresh/recargar/refrescar/actualizar.
     */
    private boolean isReloadTrigger(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // 1) PDC detection (best)
        try {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            NamespacedKey actionKey = new NamespacedKey(plugin, "aegis_action");
            if (pdc.has(actionKey, PersistentDataType.STRING)) {
                String v = pdc.get(actionKey, PersistentDataType.STRING);
                if (v != null) {
                    String vv = v.trim().toLowerCase(Locale.ROOT);
                    return vv.equals("reload")
                            || vv.equals("refresh")
                            || vv.equals("reload_all")
                            || vv.equals("refresh_lang")
                            || vv.equals("reload_settings");
                }
            }
        } catch (Throwable ignored) {}

        // 2) Fallback detection (STRICT)
        // Only allow obvious reload materials so WRITABLE_BOOK ("Language") never matches.
        Material type = item.getType();
        boolean allowedMat =
                type == Material.REDSTONE ||
                type == Material.RECOVERY_COMPASS ||
                type == Material.COMMAND_BLOCK ||
                type == Material.REPEATING_COMMAND_BLOCK ||
                type == Material.CHAIN_COMMAND_BLOCK;

        if (!allowedMat) return false;

        String name = meta.hasDisplayName() ? meta.getDisplayName() : "";
        name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name));
        if (name == null) name = "";
        String n = name.toLowerCase(Locale.ROOT);

        // IMPORTANT: do NOT match "language"/"idioma" alone
        return n.contains("reload")
                || n.contains("refresh")
                || n.contains("recargar")
                || n.contains("refrescar")
                || n.contains("actualizar");
    }
