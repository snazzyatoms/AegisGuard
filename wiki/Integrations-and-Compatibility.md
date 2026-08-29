# Integrations and Compatibility

AegisGuard `v1.4.0` is designed to work alongside the modern Minecraft plugin ecosystem. It provides land protection while allowing compatible economy, permissions, mapping, shop, database, and community tools to operate around it.

> **Important:** Do not run AegisGuard alongside another player land-protection plugin in the same world. Two protection systems can compete for the same interaction events, resulting in inconsistent protection behavior, blocked actions, or bypasses.

> **Upgrade note:** Existing 1.2.7 and 1.3.0 integrations remain valid in 1.3.5. Teleport Beacons, Guest Passes, Emergency Lockdown, Realm Profiles, Routes, Alliance Access, and the Staff Audit Ledger do not require additional bridge plugins. Floodgate/Geyser are optional for Bedrock GUI mapping.

> **Hooks default off.** Map, Discord, and protection-compat integrations stay **off** in `config.yml` until you enable them. Optional AegisGuard modules are separate from those hooks and live under `modules:`.

---

## Supported and Recommended Plugins

The following plugins are supported integrations or commonly recommended companions for an AegisGuard server.

| Plugin | Function | Compatibility Notes |
| :--- | :--- | :--- |
| Vault | Economy API | Recommended for servers that use currency-based claims, markets, upkeep, ClaimBlock exchange, or optional route rewards. |
| EssentialsX | Economy and Chat | Compatible as an economy provider and general server utility suite. |
| ChestShop | Player Shops | Compatible. Players can create shops within claims when their role and claim settings allow it. Local Market bridges may also link to external shops. |
| QuickShop | Player Shops | Compatible. Claim protection controls chest access while authorized members can create and manage shops. |
| Shopkeepers | Player Shops | Compatible as an external shop option where Local Market bridges or claim shop-interact settings allow it. |
| ExcellentShop | Player Shops | Compatible as an external shop option on servers that configure market bridges. |
| ShopGUI+ | Server Shop | Compatible with menu-based server economies. |
| Dynmap | Web Map | Can display player and server-plot borders when map rendering is enabled. For-rent styling and realm display names are available. |
| BlueMap | Web Map | Compatible through standard marker support for 3D map visualization. |
| Pl3xMap | Web Map | Compatible with fast 2D map rendering. |
| PlaceholderAPI | Placeholders | Use AegisGuard placeholders such as `%aegis_owner%`, `%aegis_plot%`, and `%aegis_role%` where configured. |
| LuckPerms | Permissions | Recommended permission manager for player, staff, and integration permission nodes, including `aegis.admin.routes`, `aegis.admin.audit`, and `aegis.beacon`. |
| Floodgate / Geyser-Spigot | Bedrock proxy | Optional. AegisGuard detects Bedrock clients at runtime so chest GUIs use left-click and sneak-left. No compile-time Geyser dependency. |
| WorldGuard | Global Regions | Can be used for global regions, such as spawn, alongside AegisGuard player claims. Avoid overlapping protection rules unless intentionally configured. |
| Multiverse | World Management | Compatible with multi-world server setups. |
| DecentHolograms | Visuals | Generally compatible with AegisGuard visual systems. |

> **Map rendering:** Border colors, marker behavior, and display options depend on the enabled map integration and server configuration. Player and server plots can use different visual styling where supported. Enable the map hook in `config.yml` after installing the map plugin.

---

## Native Integrations

AegisGuard includes selected integrations directly in the core plugin. These features do not require a separate bridge plugin when they are enabled and configured.

### Discord Integration

Connect important server events to Discord through webhooks. Discord logging is **opt-in** and off by default.

Supported event logging may include:

- Land claims.
- Claim deletions.
- Plot merges.
- Administrative actions.
- Market and rental events.
- Lockdown and Guest Pass activity.

Configure Discord integration by placing the webhook URL in `config.yml` and enabling the relevant event options.

> Keep webhook URLs private. Anyone with the URL may be able to post messages to that Discord channel.

### Local Market Bridges

AegisGuard can expose optional third-party shop or market commands from the Local Market hub when:

- The named plugin is installed.
- Local Market bridges are enabled in `config.yml`.
- The plot qualifies for local market tools.
- Shop-interact rules allow the action when the server requires them.

These bridges do not hard-depend on a specific shop API. They open the configured command for the installed plugin.

### Database Support

AegisGuard supports local and networked database storage options.

| Database | Recommended Use |
| :--- | :--- |
| MySQL or MariaDB | Recommended for production servers, larger communities, and multi-server environments. |
| SQLite | Default local file storage. Suitable for smaller servers and requires no separate database setup. |
| YAML plot storage | Suitable for smaller servers that prefer file-based plot data. Most servers start here. |

Keep `storage.backend` and `storage.type` equal. Plot data only; satellite files stay YAML.

**Wilderness revert** ships **off**. It is SQL-only and a no-op on YAML. Turning SQL on does not enable it; set `modules.wilderness_revert` only if you want that feature.

1.3.5 may also create additional plugin data files for systems such as alliances, routes, teleport beacons, and restore operations. Back up the full `plugins/AegisGuard/` folder and any configured SQL database before major updates, configuration changes, or migrations. A folder backup is recommended, not required, to keep claims on a 1.2.7 or 1.3.0 to 1.3.5 JAR swap.

Use `/agadmin migrate` (storage migrate in Doctor/Admin) to move plot data between YAML and SQL with backups.

---

## Features That Work Without Extra Plugins

These 1.3.5 systems are built into AegisGuard and do not require companion plugins:

| Feature | Notes |
| :--- | :--- |
| Teleport Beacons | Linked pads with Safe Travel. Optional Vault fees. No extra plugin. |
| Module switchboard | `modules:` in `config.yml`. Optional systems ship on except wilderness revert. Menus hide disabled modules. |
| Guest Passes | Temporary access without permanent trust. |
| Emergency Lockdown | Fast reversible safety switch for managed plots. |
| Realm Profiles and Noticeboards | Public plot identity and visitor notices. |
| Routes and Checkpoints | Staff-authored exploration paths. Optional money or ClaimBlock rewards use Vault/ClaimBlocks when enabled. |
| Alliance Access | Opt-in ally permissions. Separate from group ownership and economy plugins. |
| Staff Audit Ledger | In-plugin audit history for restore, repair, migration, bypass, and safety actions. |
| First-claim walkthrough | Optional guidance and settings preferences, including the language picker. |
| `/agadmin transition` | Confirms 1.2.7 or 1.3.0 to 1.3.5 upgrade status after a JAR swap. |

---

## Incompatible Land-Protection Plugins

Do **not** run these plugins as parallel player claim systems in the same world as AegisGuard.

| Plugin | Why It Conflicts |
| :--- | :--- |
| GriefPrevention | Provides overlapping claim selection and land-protection behavior. |
| Lands | Provides a competing land-claim and territory-management system. |
| Towny or Factions | Provides overlapping territory, membership, and land-event handling. |
| Residence | Uses conflicting region and selection behavior. |
| RedProtect | Provides conflicting region-protection logic. |
| PlotSquared | Do not use as a parallel claim system in the same world because plot-world and protection behavior can conflict. |

> **Rule of thumb:** If another plugin lets players independently claim and protect land through tools, commands, chunks, or regions, do not use it as a second player-protection system alongside AegisGuard in the same world.

### Migration Guidance

If moving from a supported protection plugin, use AegisGuard's migration tools instead of running both systems permanently. Review every migration in preview mode and create a full server backup before importing live claim data.

Supported import sources include GriefPrevention, GriefDefender, and Lands. Migration actions can appear in the Staff Audit Ledger where auditing is enabled.

---

## Use with Caution

The following plugins and systems can be used with AegisGuard, but require appropriate configuration and staff oversight.

### WorldEdit and FastAsyncWorldEdit

AegisGuard can restrict WorldEdit operations inside claims to protect player builds.

- **Administrators:** Use `/agadmin bypass` only when authorized maintenance is required inside protected land.
- **Players:** Do not grant broad WorldEdit bypass permissions by default. Allow access only when the server's rules and claim-permission model support it.

Review both AegisGuard and WorldEdit or FAWE permissions before allowing large-scale editing tools on a live server. Emergency Lockdown can help restrict sensitive plot activity during an incident, but it is not a substitute for correct WorldEdit permissions.

WorldEdit or FastAsyncWorldEdit can also power **optional plot-build backups** (`snapshots.build_backup`, default **off**). Staff snapshots then copy the claim AABB as a schematic under `plugins/AegisGuard/plot-backups/`. Restore pastes those blocks after claim-data rollback. If WorldEdit is missing, the staff menu keeps a teaser to install it. Player self-backup and bulk dumps of every player plot are not included in 1.3.5.

### Custom Chat Plugins

Plugins that aggressively format, replace, or reroute chat messages can override AegisGuard channel or placeholder behavior.

Examples include VentureChat and ChatControl. If chat features do not behave as expected, review plugin listener priorities, PlaceholderAPI configuration, and formatting rules in the chat plugin.

### Heavy Hologram and Visual Plugins

AegisGuard visual effects are generally compatible with hologram plugins. However, many simultaneous holograms, titles, markers, or spawn-area visuals can create clutter and reduce client performance.

Keep spawn, market, and event areas visually focused. Test visual combinations with typical player counts before deploying them broadly.

### Permission Managers Other Than LuckPerms

Other permission plugins can work if they support Bukkit-style permission nodes. LuckPerms remains the recommended manager for `aegis.user`, `aegis.admin`, ClaimBlocks exchange nodes, `aegis.admin.routes`, and `aegis.admin.audit`.

---

## Compatibility Checklist

Before launching AegisGuard `v1.4.0` on a live server:

1. Confirm the host is running **Java 21+**.
2. Use only one player land-protection system per world.
3. Install Vault and a compatible economy provider if currency features or optional route rewards are enabled.
4. Configure LuckPerms permission bundles for players and staff, including `aegis.admin.routes` and `aegis.admin.audit` when staff will edit routes or review the ledger.
5. Leave unused map, Discord, and protection-compat hooks **off** until you need them.
6. Test shop plugins, map markers, and WorldEdit behavior inside a protected test claim.
7. If using Local Market bridges, confirm the target shop plugin is installed and the bridge command works for a trusted test plot.
8. Configure database storage appropriate for the server size. Do not enable wilderness revert unless you are on SQL and want that feature.
9. Back up data before migrations, major updates, or integration changes.
10. After upgrading from 1.2.7 or 1.3.0, run `/agadmin transition`, then verify claims, markets, Guest Passes, Lockdown, Realm Profile, Routes, Alliance Access, and `/ag beacon`. Run `/agadmin doctor` only if something looks wrong.
11. If Bedrock players join through Geyser, confirm Floodgate/Geyser is loaded and `gui.bedrock.detect` is on.

---

> *Simple. Steadfast. Eternal.*
