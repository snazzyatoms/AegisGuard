# AegisGuard
### Simple. Steadfast. Eternal.

AegisGuard is a modern land-protection plugin for Minecraft, built for **Paper, Purpur, Spigot, and Folia**. It combines reliable claim security with economy features, RPG-style progression, and accessible in-game management tools.

The **V1.3.5 branch** contains the reviewed 1.3.5 source line. It keeps the 1.3.0 player systems and adds optional WorldEdit/FAWE full plot-build backups for staff, complete claim-data snapshots, and default-off automatic player-plot/server-zone backups with batching, change detection, retention, and load throttling. Updating the branch does not create a GitHub Release.

| Requirement | Support |
| :--- | :--- |
| Minecraft | 1.20+ |
| Java | 21+ |
| Platforms | Spigot, Paper, Purpur, Folia |
| Latest release | [AegisGuard 1.3.5](https://github.com/snazzyatoms/AegisGuard/releases/tag/1.3.5) |

## Why AegisGuard?

Land is more than protected space. It can become part of your server's economy, communities, and player progression.

| Feature | Description |
| :--- | :--- |
| Folia Ready | Designed for Paper and Folia with Folia-safe scheduling. |
| Land Management | Create and manage claims, members, permissions, and settings in game. |
| Empire Building | Build sub-claims, rentable zones, plot progression, and economy-driven communities. |
| Guardian Codex | Use a clear GUI to manage claims without relying on commands for every action. |
| Travel System | Visit friends, public destinations, and server warps through visual menus. |
| Active Security | Protect claims with anti-sniper tools, mob vaporizers, emergency lockdown, hopper/liquid/teleport/storm wards, and configurable safeguards. |
| Guest Passes | Grant temporary, self-expiring access without permanent trust. |
| Realm Profiles | Give each plot a public name, category, greeting, and noticeboard. |
| Routes & Checkpoints | Offer staff-authored exploration routes players can browse and discover. |
| Alliance Access | Let allied players share opt-in plot permissions — all risky toggles default **OFF**. |
| Staff Audit Ledger | Review important admin and safety actions from a dedicated audit history. |
| Module Switchboard | Optional systems ship **on** (wilderness revert ships **off**). Disabled modules leave the menu. Claiming and plot protection stay on. |
| Language Picker | Choose from nine language packs in Settings without cycling one click at a time. |

---

## Quick Start

1. Run `/ag wand` to receive the claim-selection scepter.
2. Right-click the first corner of the area.
3. Left-click the opposite corner.
4. Run `/ag claim` to create the claim.
5. Run `/ag menu` to open the Guardian Codex and manage it.

New plots are fully protected on create.

Optional first steps after your first claim:

- Open **Settings** to pick a language and tune greetings, notifications, and sounds.
- Use **Guest Passes** for short-term helpers.
- Use **Realm Profile** to set your plot's public identity.
- Replay the first-claim walkthrough any time with `/ag guide`.

> "Forged to shield thy lands from peril and strife."

---

## Updating from 1.2.7

1. Stop the server.
2. Confirm **Java 21** or newer.
3. Replace the old JAR with `AegisGuard-1.3.5.jar`.
4. Start the server. Config and language merge run on enable. Existing plots load as-is.
5. Confirm with `/agadmin transition` (aliases `upgrade`, `v130`).
6. Run `/agadmin doctor` only if something looks wrong.

Do **not** use Bukkit `/reload`. Use `/agadmin reload` for supported config and language reloads.

A folder backup of `plugins/AegisGuard/` is recommended. It is not required to keep claims.

---

## What's New in 1.3.5

- **Seamless 1.2.7 upgrade** — JAR swap and start; `/agadmin transition` confirms status; Doctor is optional
- **Module switchboard** — `modules:` in `config.yml`; extras ship on except wilderness revert (SQL-only, off by default); menus hide disabled modules
- **Staff Audit Ledger** — restore, repair, migration, bypass, Guest Pass, Lockdown, and Alliance actions
- **Temporary Guest Passes** — time-limited access that never overwrites permanent roles
- **Emergency Plot Lockdown** — a fast, reversible safety switch for disputes or griefing
- **Realm Profiles & Noticeboards** — public plot identity and visitor-facing notices
- **Clearer player guidance** — better denial messages and an optional first-claim walkthrough (`/ag guide`)
- **Routes and Checkpoints** — discovery-focused exploration paths; optional teleport defaults **OFF**
- **Alliance Access** — per-plot toggles for enter, interact, containers, build, animals, and friendly PvP; all default **OFF**
- **Language picker** — Modern English, Old English, Mexican Spanish, Argentinian Spanish, Brazilian Portuguese, French, Italian, German, and Polish
- **Server-zone stewardship** — wand-create and convert-to-server share one Steward pipeline
- **Plot backups** — optional full-height WorldEdit/FAWE copies with atomic manifests, per-file SHA-256 checksums, exact plot coverage checks, one-chunk Folia ownership tasks, durable tile progress, rescue snapshots, and protected storage retention. Staff-first and default **off**; Folia requires FAWE by default.
- **Automatic plot backups** — optional player-plot and server-zone snapshots processed in small Folia-safe batches, with unchanged-data skipping for data-only passes, eligibility controls, low-TPS pause, and retention. Full build copies are separate and default **off**.
- **Recovery operations** — preview/selective restore, duplicate prevention, maintenance locking, restart pause/retry, integrity filters, storage dry-run, health reporting, audit details, and optional default-off Discord failure warnings.

Existing 1.2.7 installations migrate safely. New systems use safe defaults. Alliance or guest access never grants ownership, money control, rentals, or management rights unless you explicitly allow a related action.

The 1.2.7 territory platform remains: Ascension Hall, Expansion Horizons, rentals, Travel Atlas, ClaimBlocks, markets, TradeStalls, Territory Doctor, and recovery tools.

## Known Issues in 1.3.0

These issues exist in public `1.3.0` and are addressed by the `V1.3.5` branch. A separate release action is required before 1.3.5 becomes a published GitHub Release.

- Restoring a snapshot after a plot changes owner can restore the wrong live plot
- Staff snapshot restore can run off the region/main thread (unsafe on Folia)
- Snapshot prune can delete more snapshots than the configured cap when age and count limits both apply
- Rollback does not clear role nicknames added after the snapshot
- Player menu footer can leave empty clickable holes for non-admins
- Staff Tools can still show Routes, Arena, Expansions, Audit, and Snapshots when those modules are turned off
- Saving a plot can auto-lift an expired timed lockdown as a side effect

---

## Quick Commands

```text
/ag wand                     Get the Aegis Scepter
/ag claim                    Create a claim from the current selection
/ag menu                     Open the territory dashboard
/ag guide                    Replay the first-claim walkthrough
/ag level                    Open the Ascension Hall
/ag visit                    Open the Travel Atlas
/ag alliance ...             Create, invite, accept, leave, or disband an alliance

/agadmin menu                Open the Staff Command Center
/agadmin transition          Confirm 1.2.7 to 1.3.0 upgrade
/agadmin doctor              Optional diagnostics and repair tools
/agadmin reload              Reload supported settings and languages
```

Install only `AegisGuard-1.3.5.jar` in the server's `/plugins` folder. API JARs are for developers and do not belong there.

---

**Simple. Steadfast. Eternal.**
*Forged by Aegis Divine.*
