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

## Safety and Defaults

- Risky Alliance Access toggles default **OFF**
- Hooks and protection-compat plugins default **OFF** (opt-in)
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
| **Languages** | Modern English, Old English, Mexican Spanish, and Argentinian Spanish |

> Test Minecraft or server-software upgrades on a staging server before deploying them to a live community.

---

## Installation and Updating

1. Stop the server completely.
2. Back up your existing `plugins/AegisGuard/` folder and server world data.
3. Confirm the host is running **Java 21 or newer**.
4. Replace the old `AegisGuard` plugin JAR with `AegisGuard-1.3.0.jar`.
5. Start the server and allow AegisGuard to complete its safety backup and configuration checks.
6. Review `plugins/AegisGuard/config.yml` and your language files.
7. Run `/agadmin doctor` before reopening the server to players.

> Do **not** use Bukkit's global `/reload` command. Use `/agadmin reload` for supported configuration and language reloads.

---

## Quick Commands

```text
/ag menu                 Open the territory dashboard
/ag guide                Replay the first-claim walkthrough
/ag alliance ...         Create, invite, accept, leave, or disband an alliance
/ag notice ...           Manage the plot noticeboard
/agadmin menu            Open the Staff Command Center
/agadmin doctor          Run diagnostics and repair tools
```

---

## Suggested GitHub release title

`AegisGuard 1.3.0 - Audit Ledger, Guest Passes, Lockdown, Profiles, Guidance, Routes, and Alliance Access`

---

**Simple. Steadfast. Eternal.**  
*Forged by Aegis Divine.*
