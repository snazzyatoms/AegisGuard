# AegisGuard 1.3.0

### *Protect your world. Empower your players. Ascend.*

AegisGuard `1.3.0` is the public follow-up to `1.2.7`. It adds staff oversight, safer temporary access, stronger plot identity, guided onboarding, exploration routes, opt-in alliance cooperation, a module switchboard, and a seamless JAR-swap upgrade from 1.2.7.

Existing **1.2.7 data and configuration remain valid**. Schema migration adds new defaults without overwriting customized settings. Plots, owners, flags, and members load as-is.

Built for **Java 21+**, **Minecraft 1.20+**, **Paper, Purpur, Spigot, and Folia**.

---

## Highlights

### Seamless upgrade from 1.2.7

Swap the JAR, start the server, and config plus language merge run on enable. Confirm with `/agadmin transition` (aliases `/agadmin upgrade`, `/agadmin v130`). Doctor is optional hygiene, not part of the version bridge. Do not use Bukkit `/reload`.

### Module switchboard

Optional systems live under `modules:` in `config.yml` and ship **on**, except **wilderness revert**, which ships **off** (SQL-only; YAML is a no-op). Owners can turn modules off; player and staff menus hide those entries. Claiming, plot protection, roles, settings, the guidebook, and core staff tools stay available. Third-party hooks stay off until you opt in.

### Staff Audit Ledger

A structured staff audit trail for sensitive administrative actions, with category filtering for restore, repair, migration, bypass, Guest Pass, Lockdown, and Alliance activity.

### Temporary Guest Passes

Time-limited, self-expiring plot access without permanent trust. Presets cover visitor, event guest, temporary builder, and temporary trusted guest. Wall-clock or Active Playtime expiry. Expiry and revoke never rewrite permanent roles.

### Emergency Plot Lockdown

A fast, reversible safety switch for griefing, disputes, or maintenance.

### Realm Profiles and Noticeboards

Public plot identity: display name, category, greeting, description, and a noticeboard visitors can read from Travel and discovery.

### Clearer player guidance

Blocked-action messages explain the next useful step. An optional first-claim walkthrough is skippable, never blocks claiming, and can be replayed from Settings or `/ag guide`.

### Routes and checkpoints

Staff publish named exploration routes with ordered checkpoints. Players browse progress and discover checkpoints by proximity. Optional teleport defaults **OFF**.

### Alliance Access

Alliances are separate from ownership, money, rentals, and administration. Membership alone grants nothing. Each plot opts into toggles that default **OFF**: Enter, Interact, Containers, Build, Animals, Friendly PvP.

### Language picker

Settings opens **Choose Your Language** with every installed pack: Modern English, Old English, Mexican Spanish, Argentinian Spanish, Brazilian Portuguese, French, Italian, German, and Polish.

### Server-zone stewardship

Wand-create and convert-to-server share one pipeline. Convert grants Steward to the acting staffer and gates manage on permission or Steward — not blanket admin.

---

## Known issues (fixed in 1.3.5)

These issues exist in public `1.3.0`. They are addressed in the `1.3.5` tree and the upcoming `1.3.5` release (not published on GitHub yet). Until that release is available, treat the items below as known `1.3.0` soak issues.

- Restoring a snapshot after a plot changes owner can restore the wrong live plot
- Staff snapshot restore can run off the region/main thread (unsafe on Folia)
- Snapshot prune can delete more snapshots than the configured cap when age and count limits both apply
- Rollback does not clear role nicknames added after the snapshot
- Player menu footer can leave empty clickable holes for non-admins
- Staff Tools can still show Routes, Arena, Expansions, Audit, and Snapshots when those modules are turned off
- Saving a plot can auto-lift an expired timed lockdown as a side effect

---

## Also in 1.3.0

- Module-aware AegisGuard menu: same framed layout; disabled modules do not appear as icons
- Claim snapshots store **claim data only** (owner, bounds, flags, members). Full world-block plot backups are planned for a later update
- Optional Arena cooperative PvE (on by default; Folia-safe scheduler)
- Direct language picker with synced Codex fallbacks
- Restyled Staff Tools and Claim Status
- My Rentals, My Tenants, Settlements Inbox, ClaimBlock gifts, adjacent claim merge
- YML ↔ SQL plot migrator with backups
- Richer PlaceholderAPI and opt-in Discord webhook events
- Protection wards for hopper, liquid, teleport, and storm

---

## Safety and defaults

- Risky Alliance Access toggles default **OFF**
- Hooks and protection-compat plugins default **OFF**
- Wilderness revert ships **OFF**. SQL being on does not enable it
- Guest Passes are additive and temporary
- Lockdown is reversible and plot-scoped
- Routes never alter claim boundaries
- Config schema upgrades with an automatic backup
- Existing 1.2.7 plots, roles, economy data, and customized config remain valid

---

## Compatibility

| Requirement | Support |
| :--- | :--- |
| **Java** | `21+` |
| **Minecraft** | `1.20+` |
| **Server Software** | Spigot, Paper, Purpur, Folia, and compatible Bukkit forks |
| **Upgrade path** | From AegisGuard `1.2.7` with automatic config schema migration |
| **Languages** | Modern English, Old English, Mexican Spanish, Argentinian Spanish, Brazilian Portuguese, French, Italian, German, and Polish |

> Test Minecraft or server-software upgrades on a staging server before deploying them to a live community.

---

## Installation and updating

1. Stop the server completely.
2. Confirm the host is running **Java 21 or newer**.
3. Replace the old `AegisGuard` plugin JAR with `AegisGuard-1.3.0.jar`.
4. Start the server. Config and language merge run on enable. Existing plots load as-is.
5. Optional: keep a copy of `plugins/AegisGuard/` (the plugin also writes its own config backup). This is not required to keep claims.
6. Review `plugins/AegisGuard/config.yml` if you want to turn modules off or enable new options.
7. Confirm the upgrade with `/agadmin transition`.
8. Run `/agadmin doctor` only if something looks wrong.

> Do **not** use Bukkit's global `/reload` command. Use `/agadmin reload` for supported configuration and language reloads.

Wiki and SpigotMC listings will follow this GitHub Release.

---

## Release files

### Server owners

Install this file in the server's `/plugins` folder:

`AegisGuard-1.3.0.jar`

### Plugin developers

These files are for compiling integrations. They do **not** belong in `/plugins`:

`AegisGuard-1.3.0-api.jar`  
`AegisGuard-1.3.0-dev-api.jar`

---

## Quick commands

```text
/ag menu                 Open the territory dashboard
/ag guide                Replay the first-claim walkthrough
/ag alliance ...         Create, invite, accept, leave, or disband an alliance
/ag notice ...           Manage the plot noticeboard
/agadmin menu            Open the Staff Command Center
/agadmin transition      Confirm 1.2.7 → 1.3.0 upgrade status
/agadmin doctor          Optional diagnostics and repair tools
```

---

**Simple. Steadfast. Eternal.**  
*Forged by Aegis Divine.*
