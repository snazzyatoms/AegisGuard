# AegisGuard
### Simple. Steadfast. Eternal.

AegisGuard is a modern land-protection plugin for Minecraft, built for **Paper, Purpur, Spigot, and Folia**. It combines reliable claim security with economy features, RPG-style progression, and accessible in-game management tools — and on BungeeCord/Velocity networks, it can link multiple servers into one territory network.

| Requirement | Support |
| :--- | :--- |
| Minecraft | 1.20+ |
| Java | 21+ |
| Platforms | Spigot, Paper, Purpur, Folia |
| Current line | AegisGuard 1.4.0 |
| Latest release | [AegisGuard 1.4.0](https://github.com/snazzyatoms/AegisGuard/releases/tag/1.4.0) |

## What Land Becomes

Land is more than protected space. It can become part of your server's economy, communities, and player progression.

| Pillar | What you get |
| :--- | :--- |
| Protection | Claims, sub-zones, and wards against griefing — mobs, liquids, hoppers, teleports, storms, explosives — all managed in-game. |
| Travel | A unified **Travel Atlas**: plot homes, public destinations, linked **Teleport Beacons**, exploration routes, and per-plot arrival rules. |
| Economy | ClaimBlocks with a buy/sell exchange, Vault support, markets, auctions, TradeStalls, rentals, upkeep, and trade **Caravans**. |
| Community | Groups, alliances, realm profiles, guest passes, gatherings, plot chat channels, and room-scoped **Hearth** chat. |
| Events | Optional **Arena Dungeons** — instanced PvE/PvP runs with staging, matchmaking, and sealed boundaries. |
| Progression | Plot Ascension, Expansion Horizons, succession and heirs, and unlocks like level-30 flight. |
| Administration | Staff audit ledger, recovery snapshots, migration tools, diagnostics, and a **module switchboard** so servers only run what they want. |
| Network | On BungeeCord/Velocity, linked servers share discovery, radios, arenas, beacons, and player data — one ecosystem, many backends. |

Every optional system can be turned off in `modules:`, menus hide disabled entries, and players pick from nine language packs.

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

## Quick Commands

```text
/ag wand                     Get the Aegis Scepter
/ag claim                    Create a claim from the current selection
/ag quickclaim [radius]      Claim a square around you
/ag menu                     Open the territory dashboard
/ag visit                    Open the Travel Atlas
/ag beacon                   Manage Teleport Beacon pads
/ag caravan                  Dispatch and track trade caravans
/ag gathering                Host or browse open-house gatherings
/ag arena                    Browse, join, and party for arena runs
/ag chat                     Toggle plot chat (alliance/group radios too)
/ag blocks buy|sell          Trade ClaimBlocks through the exchange

/agadmin menu                Open the Staff Command Center
/agadmin transition          Confirm upgrade status from 1.2.7/1.3.x
```

The full command and permission reference lives in [Permissions and Commands](Permissions-and-Commands). Install only `AegisGuard-1.4.0.jar` in the server's `/plugins` folder — API JARs are for developers.

---

## Updating from 1.2.7, 1.3.0, or 1.3.5

Upgrading is a JAR swap: stop the server, drop in `AegisGuard-1.4.0.jar`, start. Config and language merge on enable (`config_schema` `1294` → `1316`, with a backup), existing plots load as-is, and `/agadmin transition` confirms the upgrade. Do **not** use Bukkit `/reload` — use `/agadmin reload`.

Full steps and a post-upgrade checklist: [Installation → Updating](Installation#updating-from-127-130-or-135-to-140).

## What's New in 1.4.0

Version 1.4.0 completes the travel layer (unified **Travel Atlas**, beacons with arrival rules, **Caravans**), builds arenas into sealed instanced dungeons, adds succession, gatherings, chat radios and Hearth rooms, the ClaimBlocks exchange — and, as its flagship, connects entire servers behind a BungeeCord/Velocity proxy into one network.

The full feature list and the 1.3.5 / 1.3.0 history live in [Release Highlights](Release-Highlights).

---

## Choose your path

- **Players** — [Player Handbook](Player-Handbook), [The Land Economy](The-Land-Economy), [FAQ](Frequently-Asked-Questions), [Glossary](Glossary)
- **Server owners & staff** — [Installation](Installation), [Permissions and Commands](Permissions-and-Commands), [Integrations and Compatibility](Integrations-and-Compatibility), [Release Highlights](Release-Highlights)
- **Developers** — [AegisGuard API](AegisGuard-API)

---

**Simple. Steadfast. Eternal.**
*Forged by Aegis Divine.*
