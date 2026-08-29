# AegisGuard
### Simple. Steadfast. Eternal.

AegisGuard is a modern land-protection plugin for Minecraft, built for **Paper, Purpur, Spigot, and Folia**. It combines reliable claim security with economy features, RPG-style progression, and accessible in-game management tools.

**AegisGuard 1.4.0** is the current source line, built on the public **1.3.5** release. Existing `1.2.7`, `1.3.0`, and `1.3.5` data remain valid through automatic schema migration.

| Requirement | Support |
| :--- | :--- |
| Minecraft | 1.20+ |
| Java | 21+ |
| Platforms | Spigot, Paper, Purpur, Folia |
| Current line | AegisGuard 1.4.0 |
| Latest published release | [AegisGuard 1.3.5](https://github.com/snazzyatoms/AegisGuard/releases/tag/1.3.5) |

## Why AegisGuard?

Land is more than protected space. It can become part of your server's economy, communities, and player progression.

| Feature | Description |
| :--- | :--- |
| Folia Ready | Designed for Paper and Folia with Folia-safe scheduling. |
| Land Management | Create and manage claims, members, permissions, and settings in game. |
| Empire Building | Build sub-claims, rentable zones, plot progression, and economy-driven communities. |
| Guardian Codex | Use a clear GUI to manage claims without relying on commands for every action. |
| Travel System | Visit friends, public destinations, server warps, and **Teleport Beacons** with a per-plot arrival choice (`/ag arrival`). |
| Active Security | Protect claims with anti-sniper tools, mob vaporizers, emergency lockdown, hopper/liquid/teleport/storm wards, and configurable safeguards. |
| Guest Passes | Grant temporary, self-expiring access without permanent trust. |
| Realm Profiles | Give each plot a public name, category, greeting, and noticeboard. |
| Routes & Checkpoints | Offer staff-authored exploration routes players can browse and discover. |
| Alliance Access | Let allied players share opt-in plot permissions — all risky toggles default **OFF**. |
| Staff Audit Ledger | Review important admin and safety actions from a dedicated audit history. |
| Module Switchboard | Optional systems ship **on** (wilderness revert ships **off**). Disabled modules leave the menu. Claiming and plot protection stay on. |
| Language Picker | Choose from nine language packs in Settings without cycling one click at a time. |
| Bedrock GUIs | With Geyser/Floodgate, Bedrock players are detected automatically and can complete chest menus with left-click and sneak-left. |

---

## Quick Start

1. Run `/ag wand` to receive the claim-selection scepter.
2. Right-click the first corner of the area.
3. Left-click the opposite corner.
4. Run `/ag claim` to create the claim.
5. Run `/ag menu` to open the Guardian Codex and manage it.

New plots are fully protected on create. The server's `claims.min_radius` applies on **both** axes (default `5` means at least a 10×10 plot).

Optional first steps after your first claim:

- Open **Settings** to pick a language and tune greetings, notifications, and sounds.
- Use **Guest Passes** for short-term helpers.
- Use **Realm Profile** to set your plot's public identity.
- Place **Teleport Beacons** with `/ag beacon` if you want linked pads on the plot.
- Replay the first-claim walkthrough any time with `/ag guide`.

> "Forged to shield thy lands from peril and strife."

---

## Updating from 1.2.7, 1.3.0, or 1.3.5

1. Stop the server.
2. Confirm **Java 21** or newer.
3. Replace the old JAR with `AegisGuard-1.4.0.jar`.
4. Start the server. Config and language merge run on enable (`config_schema` `1294` → `1305`, with a backup). Existing plots load as-is and stay on classic arrival.
5. Confirm with `/agadmin transition` (aliases `upgrade`, `v130`).
6. Run `/agadmin doctor` only if something looks wrong.

Do **not** use Bukkit `/reload`. Use `/agadmin reload` for supported config and language reloads.

A folder backup of `plugins/AegisGuard/` is recommended. It is not required to keep claims.

---

## What's New in 1.4.0

- **Travel Atlas** — Destinations, My Beacons, Arrival, and Caravans in one menu. `/ag beacon` opens My Beacons. `/ag arrival <classic|beacon>` still sets listing landings; travelers may override when allowed. Beacon listings fail closed (`beacon_no_public_arrival`) if no public pad exists.
- **Quick-Claim** — `/ag quickclaim [radius]` claims a square around you; Settings only on the main hub and staff menu; claiming honors `max_claims_per_player`.
- **Restore-safe roles** — snapshot restore merges members/roles by default; `/ag roles lock|unlock|undo`.
- **Guardian Succession** — `/ag heir`, `/ag succession`, Stewardship GUI, transfer cooldown and rollback.
- **Caravans** — `/ag caravan` charge-then-deliver shipments on public beacon hops.
- **Schema migration** — `config_schema` moves `1294` → `1305`; new keys auto-merge on upgrade with a backup.

## What's New in 1.3.5

- **Teleport Beacons** — linked pads on claims you manage; Safe Travel; optional public arrival for visit, market, and auction; configurable fees (`owner_choice`, `always`, or `off`). `/ag home` stays personal plot spawn.
- **Language placeholders** — `{PLOT}`, `{MIN}`, `{PLAYER}`, and other `{KEY}` tokens now fill in on `lang/` translations, not only English fallbacks.
- **ClaimBlocks** — expanding land no longer drives the wallet negative; extra area is refused if it cannot be covered. Group claims check the leader's ClaimBlocks.
- **Minimum claim size** — `claims.min_radius` is enforced on both width and depth for wand claims, group claims, expansion, and Ascension growth.
- **Bedrock / Geyser GUIs** — Floodgate and Geyser players are auto-detected (`gui.bedrock.detect`, default on). Java keeps right-click second actions; Bedrock uses left-click and sneak-left.
- **Complete claim-data snapshots** — rollback restores guest passes, lockdown, alliance access, and noticeboards with owner, flags, members, and bounds.
- **Plot backups** — optional full-height WorldEdit/FAWE copies with atomic manifests, SHA-256 checksums, Folia-safe chunk tasks, and protected retention. Default **off**; Folia requires FAWE by default.
- **Automatic plot backups** — optional player-plot and server-zone snapshots in small batches. Default **off**.
- **Recovery operations** — preview/selective restore, maintenance locking, restart pause/retry, health reporting, and optional Discord failure warnings.
- **1.3.0 soak fixes** — correct plot lookup after ownership transfer, Folia-safe restore, snapshot prune, role nicknames, player-menu footer, hidden Staff Tools modules, lockdown no longer auto-lifts on save, and schema migration no longer re-enables a legacy `*.enabled: false`.

### Still included from 1.3.0

Module switchboard, Staff Audit Ledger, Guest Passes, Emergency Lockdown, Realm Profiles, first-claim walkthrough, Routes, Alliance Access, language picker, and server-zone stewardship.

The 1.2.7 territory platform remains: Ascension Hall, Expansion Horizons, rentals, Travel Atlas, ClaimBlocks, markets, TradeStalls, Territory Doctor, and recovery tools.

---

## Quick Commands

```text
/ag wand                     Get the Aegis Scepter
/ag claim                    Create a claim from the current selection
/ag menu                     Open the territory dashboard
/ag guide                    Replay the first-claim walkthrough
/ag level                    Open the Ascension Hall
/ag visit                    Open the Travel Atlas
/ag quickclaim [radius]      Claim a square around you
/ag beacon                   Open the Atlas My Beacons tab
/ag arrival <classic|beacon> Choose how visitors arrive at the plot you manage
/ag heir [player|clear]      Name a succession heir
/ag caravan                  Dispatch and track trade caravans
/ag alliance ...             Create, invite, accept, leave, or disband an alliance

/agadmin menu                Open the Staff Command Center
/agadmin transition          Confirm upgrade status from 1.2.7, 1.3.0, or 1.3.5
/agadmin doctor              Optional diagnostics and repair tools
/agadmin reload              Reload supported settings and languages
```

Install only `AegisGuard-1.4.0.jar` in the server's `/plugins` folder. API JARs are for developers and do not belong there.

---

## Wiki pages

- [Installation](Installation)
- [Player Handbook](Player-Handbook)
- [The Land Economy](The-Land-Economy)
- [Permissions and Commands](Permissions-and-Commands)
- [Integrations and Compatibility](Integrations-and-Compatibility)
- [Frequently Asked Questions](Frequently-Asked-Questions)
- [AegisGuard API](AegisGuard-API)

---

**Simple. Steadfast. Eternal.**
*Forged by Aegis Divine.*
