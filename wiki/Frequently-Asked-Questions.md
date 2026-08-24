# Frequently Asked Questions

This FAQ covers common questions about **AegisGuard** `v1.3.5`.

> **Server configuration:** AegisGuard is highly configurable. Commands, costs, economy routes, integrations, and optional systems may differ between servers. Disabled modules do not appear on `/ag menu`.

---

## Is AegisGuard compatible with Folia?

**Yes.** AegisGuard `v1.3.0` declares Folia support and detects Folia at startup.

On Folia, AegisGuard uses compatible global-region, entity-region, and asynchronous scheduling paths where appropriate (including the optional Arena scheduler). On standard servers, it uses the normal Bukkit scheduler path.

AegisGuard is designed for modern Paper-compatible servers and also includes a standard Bukkit or Spigot execution path. Always test a new plugin version on a staging server before deploying it to a large live network.

---

## Which Minecraft and Java versions are supported?

AegisGuard `v1.3.0` targets the modern Minecraft server ecosystem.

| Requirement | Supported Target |
| :--- | :--- |
| Minecraft Server API | `1.20+` |
| Java | **Java `21` or newer** |
| Server Software | Paper and Folia are recommended; a standard Bukkit or Spigot path is also included. |

Legacy Minecraft versions, including `1.8` through `1.12`, are not supported. Servers still on Java 17 must upgrade the JVM before running 1.3.0.

---

## Can I update from 1.2.7 to 1.3.0 without losing claims?

**Yes.** Existing 1.2.7 claims, configs, and plot data remain valid.

Swap the JAR, start the server, and config plus language merge run on enable. Confirm with `/agadmin transition` (aliases `upgrade`, `v130`). `/agadmin doctor` is optional — use it only if something looks wrong.

Do **not** use Bukkit `/reload`. Use `/agadmin reload` for supported config and language reloads.

A copy of `plugins/AegisGuard/` is recommended. It is not required to keep claims. The plugin also writes its own config backup when schema migration runs.

New 1.3.0 systems such as Guest Passes, Emergency Lockdown, Realm Profiles, Routes, and Alliance Access do not wipe ownership, money, rentals, or permanent roles.

---

## Can I disable economy features or other modules?

**Yes.** Optional systems live under `modules:` in `config.yml` and ship **on**, except **wilderness revert**, which ships **off**.

Turn a module off and reload or restart. That icon leaves the player menu. Claiming, plot protection, roles, settings, the guidebook, and core staff tools stay available.

AegisGuard can route supported costs through Vault, ClaimBlocks, or configured fallbacks. If Vault is unavailable or disabled, AegisGuard can fall back to ClaimBlocks for systems that support that route.

To run a protection-only or free-claim server, turn off unused modules and review claim costs and upkeep together.

> Disabling Vault alone does not automatically make every AegisGuard action free. Review `modules:`, currency routes, ClaimBlock settings, claim costs, and upkeep together.

Third-party hooks (Dynmap, Discord, protection-compat) stay **off** until you enable them.

---

## Can players pay with items, experience, or levels instead of money?

AegisGuard `v1.3.0` includes currency types for Vault money, ClaimBlocks, experience, levels, and items. The default configuration routes primary player economy features through Vault and can fall back to ClaimBlocks where supported.

The configuration also includes item-cost settings, such as a material and amount. Before enabling an item, experience, or level-based payment flow on a live server, test the configured feature on a staging server to confirm that it is wired into the intended action.

For the standard supported economy flow, use Vault with a compatible economy provider or ClaimBlocks.

Optional Routes rewards may also use Vault money or ClaimBlocks when the server enables them. Those rewards are optional and do not replace the main land economy.

---

## How do I create a spawn or server zone?

Use the server-zone workflow:

1. Run `/agadmin wand server` to receive the **Sentinel's Scepter**.
2. Right-click the first corner of the desired area.
3. Left-click the opposite corner.
4. Run `/agadmin claim` to create the server zone.

This creates a staff-owned **server zone**, not a personal player claim. Wand-create and convert-to-server share one Steward pipeline.

Recommended permissions:

- `aegis.admin`
- `aegis.admin.wand`
- `aegis.serverzone.manage`

Use `/agadmin menu` to access available administrative controls for server-owned areas, including Route Editor and Audit Ledger tools where permitted.

> Prefer the Sentinel's Scepter workflow for new server zones. `/agadmin convert` can still convert an existing player plot into a server zone when you need that path.

---

## Can players rent out parts of their land?

**Yes, when zoning and rentals are enabled by the server.**

Players can create a subplot inside a claim:

1. Stand inside a claim you manage.
2. Use the Aegis Scepter to select two corners within that claim.
3. Run one of the following commands:

```text
/ag subplot <name>
/ag subzone <name>
```

The subplot appears in the **Zone Manager**, where server-supported rental, room, market, and access controls can be configured.

```text
/ag zone
```

**My Rentals** and **My Tenants** gather contracts and renters when those modules are on.

This system is suitable for apartments, hotels, market stalls, storage rooms, and other managed spaces.

Temporary helpers can also use **Guest Passes** without becoming permanent tenants or trusted members. Guest Passes never transfer ownership or rental contracts.

---

## What are Guest Passes?

Guest Passes grant temporary, self-expiring access to a plot without changing permanent roles.

Plot managers can open **Guest Passes** from `/ag menu` to issue, review, and revoke passes. Presets and durations are chosen in the GUI. Passes expire automatically, even after a server restart.

---

## What is Emergency Lockdown?

Emergency Lockdown is a fast, reversible safety switch for griefing, disputes, or maintenance.

Open it from `/ag menu` while standing in a claim you manage. While active, sensitive actions are restricted until the lockdown is lifted. It does not delete the claim or wipe permanent member roles.

---

## What is Alliance Access?

Alliance Access lets allied players share optional plot permissions.

Important rules:

- Membership alone grants nothing.
- Each plot must opt in.
- All risky toggles default **OFF**.
- Alliance Access never grants ownership, money, rentals, or management rights.

Use `/ag alliance` for membership commands, then open **Alliance Access** from `/ag menu` to join a plot and enable only the toggles you want.

Alliance Access is separate from **Group Claims**. Groups share ownership and treasury. Alliances share only the permissions you enable.

---

## What are Realm Profiles and Routes?

**Realm Profiles** let a plot present a public name, category, greeting, and noticeboard. Use `/ag profile` or the Realm Profile button in `/ag menu`.

**Routes and Checkpoints** are staff-authored exploration paths. Players browse them from **Routes** in `/ag menu`. Staff with `aegis.admin.routes` can edit routes from the Admin GUI. Routes do not change claim boundaries.

---

## Where is Claim Status?

**Claim Status** is on the Territory row of `/ag menu`. Stand inside a plot to open a snapshot of owner, protections, growth, ClaimBlocks, and access. It is not on Preferences.

---

## How do players change language?

Open **Settings** from `/ag menu` and use **Choose Your Language**. Packs include Modern English, Old English, Mexican Spanish, Argentinian Spanish, Brazilian Portuguese, French, Italian, German, and Polish.

---

## Do snapshots restore builds?

**By default, no.** Claim snapshots always store plot records (owner, bounds, flags, members, guest passes, lockdown, alliance access, noticeboard). They do not copy world blocks unless staff enable `snapshots.build_backup` **and** WorldEdit or FastAsyncWorldEdit is installed. Even then, restore is staff-first, size-capped, and skipped when a plot is too large. Keep world backups separately.

---

## My Dynmap markers are not showing. What should I check?

Check the following:

1. Confirm Dynmap is installed and enabled.
2. Confirm the AegisGuard setting `hooks.dynmap.enabled` is set to `true` in `config.yml`. Map hooks ship **off**.
3. Restart the server after changing hook settings. Avoid using `/reload` for plugin-hook troubleshooting.
4. Check the server console for Dynmap hook or configuration messages.
5. If map tiles themselves are missing or black, run the appropriate Dynmap render command for your server, such as `/dynmap fullrender`.

Marker styling and sync behavior are configured under `hooks.dynmap`, including the layer name, sync interval, and plot-border colors.

---

## How do I enable Wilderness Revert?

Wilderness Revert ships **off** and is intended for SQL-backed storage. YAML is a no-op. Turning SQL on does not enable it.

To enable it:

1. Configure AegisGuard to use an SQL storage backend.
2. Set `modules.wilderness_revert` and `wilderness_revert.enabled` to `true` in `config.yml`.
3. Configure values such as `revert_after_hours`, `interval_seconds`, and `revert_batch_size` for your server.
4. Restart the server and check the console for the Wilderness Revert startup message.

> **Important:** AegisGuard `v1.3.0` skips Wilderness Revert startup when the active storage backend is not SQL. Test the feature carefully on a backup or staging server before enabling it in production.

---

## Can I customize the Aegis Scepter?

**Yes.** Server owners can customize the standard claim wand through `config.yml`, including its material, name, lore, and claim-consumption behavior.

The administrative Sentinel's Scepter is configured separately under the `admin.wand` section.

AegisGuard identifies its selection tools with Persistent Data Container tags. This prevents ordinary renamed or crafted items from being treated as valid AegisGuard wands.

---

## Does AegisGuard affect player saves or player inventories?

AegisGuard primarily manages land, permissions, selections, economy integrations, and plugin data. It does not alter player save files.

Inventory changes may occur only when a configured system intentionally gives, consumes, or exchanges items, such as claim-wand behavior or configured economy features.

Back up the server before major updates, migrations, or significant configuration changes.

---

## Where can players replay the first-claim walkthrough?

If the server enables the first-claim walkthrough, players can reopen it with:

```text
/ag guide
```

It is also available from **Settings** in `/ag menu`. The walkthrough is skippable and does not block or delay claiming.

---

## What should I do if a command or menu is unavailable?

Check the following before reporting an issue:

1. Confirm that the feature is enabled in `modules:` and the related config section.
2. Confirm that the player has the required permission.
3. Confirm that optional dependencies, such as Vault or Dynmap, are installed and enabled when required. Hooks default off.
4. For Routes editing, confirm the staff member has `aegis.admin.routes`.
5. Restart the server after changing hook or integration settings. Do not use Bukkit `/reload`.
6. Review the server console and `latest.log` for AegisGuard warnings or errors.

If the issue remains, provide the server version, AegisGuard version, relevant configuration section, permissions, and the applicable console error when contacting support.

---

> *Simple. Steadfast. Eternal.*
