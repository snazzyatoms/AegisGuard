# AegisGuard 1.3.0

### *Protect your world. Empower your players. Ascend.*

AegisGuard `1.3.0` builds on the complete territory platform from `1.2.7` with seven focused milestones for clearer staff oversight, safer temporary access, stronger plot identity, guided onboarding, exploration routes, and opt-in alliance cooperation.

Existing **1.2.7 data and configuration remain valid**. Schema migration adds new defaults safely without overwriting customized settings.

Built for **Java 21+**, **Minecraft 1.20+**, modern server software including **Paper, Purpur, Spigot, and Folia**, and the AegisGuard config migration path introduced in prior releases.

---

## Highlights

### Staff Audit Ledger
A structured staff audit trail for sensitive administrative actions, with category filtering in the Audit Admin GUI so operators can review restore, repair, migration, bypass, Guest Pass, Lockdown, and Alliance activity in one place.

### Temporary Guest Passes
Issue time-limited, self-expiring plot access without granting permanent trust. Presets cover visitor, event guest, temporary builder, and temporary trusted guest. Expiry and revoke never rewrite permanent roles.

### Emergency Plot Lockdown
A fast, reversible safety switch for griefing, disputes, or maintenance. Lockdown restricts sensitive actions while remaining easy to lift from the plot menu.

### Realm Profiles and Noticeboards
Give each plot a public identity: display name, category, greeting, description, and a noticeboard visitors can read from the Travel/Visit experience.

### Clearer Player Guidance
Blocked-action messages now explain the next useful step (including Guest Pass guidance where relevant). An optional first-claim walkthrough is skippable, never blocks claiming, and can be replayed from Settings or `/ag guide`. Player notification preferences remain first-class.

### Routes and Checkpoints
Staff can publish named exploration routes with ordered checkpoints. Players browse progress, discover checkpoints by proximity, and may receive optional completion rewards. Optional teleport defaults **OFF** so discovery never requires teleporting.

### Alliance Access
Player alliances are completely separate from plot ownership, money, rentals, and administration. Membership alone grants nothing. Each plot must opt into Alliance Access with six toggles, all default **OFF**:

- Enter
- Interact
- Containers
- Build
- Animals
- Friendly PvP

**Alliance Entry** is wired into private plot-entry protection. **Alliance Friendly PvP** is wired into open-plot PvP damage cancellation between alliance members. Alliance access never grants ownership, money, rentals, or `MANAGE` / `MANAGE_MEMBERS`.

---

## Polish Pack (1.3.0)

- Hooks and protection-compat integrations are **opt-in** (shipped defaults and missing-key fallbacks are off)
- Richer storage documentation (`yml` / `sqlite` / `mysql` / `mariadb`, backend/type equality, satellite YAML caveat)
- Rent Confirm GUI before Vault charges; unified **My Rentals** hub for full-plot + zone contracts
- Plot-local role nicknames, `trusted` catalog role, `co_owner` gains `MANAGE_MEMBERS`, member capacity enforced, `owner` not assignable
- Guest Pass / Alliance **ANIMALS** permissions honored by animal damage and interact protection
- Docs catch-up for Safe Travel, alliance invite expiry, and Java 21 verification notes

## Further Polish (1.3.0)

- Zone leave/cancel parity in My Rentals; opt-in rental auto-renew with Vault balance checks
- Role-flag editor overrides enforced in protection checks; all six Alliance toggles remain wired
- Add trusted players and Guest Passes by name/offline; Kick/Ban management GUI beside Roles
- YML ↔ SQL plot migrator (Doctor/Admin Storage Migrate) with backups; SQLite honors `storage.database.file`
- Confirm GUI for `/ag rental renew` / cancel; Auctions menu gated on auction system (not upkeep)
- Landlord **My Tenants** hub; player **Settlements Inbox**; Group Plots dashboard; ownership transfer confirm
- ClaimBlocks gift (`/ag giftblocks`) with permission + daily/capped limits; adjacent claim merge MVP (`/ag merge`)
- Visit discover filters (featured / for-sale / for-rent / category); nicknames + capacity on Travel entries
- Alliance roster + pending invites GUI; Plot Status upkeep pay-early; Doctor delinquents + settlements panels
- Richer PlaceholderAPI; Discord webhook events for market/rental/lockdown/guest-pass (all opt-in false)
- Map markers: For Rent color + realm display name; route guidance action-bar distance + sparse particles
- **Language picker:** Settings no longer cycles packs one click at a time. Choose Your Language opens a menu of every installed pack so players can pick directly.
- **Server-zone stewardship unify:** wand create and convert-to-server both grant Steward to the acting staffer, clear prior access on convert, open Claim Settings when configured, and gate server-zone manage on `server_zone_manage_permissions` (or Steward role)—not blanket admin alone

## Safety and Defaults

- Risky Alliance Access toggles default **OFF**
- Hooks and protection-compat plugins default **OFF** (opt-in)
- Wilderness revert ships **OFF** (SQL-only, opt-in). Most servers start on YAML, where the feature is a no-op. SQL being on does not turn it on; the owner must set `modules.wilderness_revert` / `wilderness_revert.enabled` to true.
- Guest Passes are additive and temporary; permanent roles are untouched
- Lockdown is reversible and plot-scoped
- Routes never alter claim boundaries
- Config schema upgrades through the existing migration service with backups
- Existing 1.2.7 plots, roles, economy data, and customized config remain valid

---

## Compatibility

| Requirement | Support |
| :--- | :--- |
| **Java** | `21+` (required runtime baseline for 1.3.0) |
| **Minecraft** | `1.20+` |
| **Server Software** | Spigot, Paper, Purpur, Folia, and compatible Bukkit forks |
| **Upgrade path** | From AegisGuard `1.2.7` with automatic config schema migration |
| **Languages** | Modern English, Old English, Mexican Spanish, Argentinian Spanish, Brazilian Portuguese, French, Italian, German, and Polish |

> Test Minecraft or server-software upgrades on a staging server before deploying them to a live community.

---

## Installation and Updating

1. Stop the server completely.
2. Confirm the host is running **Java 21 or newer**.
3. Replace the old `AegisGuard` plugin JAR with `AegisGuard-1.3.0.jar`.
4. Start the server. Config and language merge run on enable. Existing plots load as-is.
5. Optional but recommended: keep a copy of `plugins/AegisGuard/` (the plugin also writes its own config backup). This is not required to keep claims.
6. Review `plugins/AegisGuard/config.yml` and your language files if you want to enable new 1.3.0 options.
7. Confirm the upgrade with `/agadmin transition` (alias `/agadmin upgrade`).
8. Run `/agadmin doctor` only if something looks wrong. Doctor is optional hygiene, not part of the version bridge.

> Do **not** use Bukkit's global `/reload` command. Use `/agadmin reload` for supported configuration and language reloads.

---

## Quick Commands

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

## Suggested GitHub release title

`AegisGuard 1.3.0 - Audit Ledger, Guest Passes, Lockdown, Profiles, Guidance, Routes, and Alliance Access`

---

**Simple. Steadfast. Eternal.**  
*Forged by Aegis Divine.*
