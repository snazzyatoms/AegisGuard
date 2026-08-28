# AegisGuard 1.4.0

### *Protect your world. Empower your players. Ascend.*

AegisGuard `1.4.0` builds on the public `1.3.5` release. It gives every plot a **per-plot Travel Atlas arrival choice** and hardens Teleport Beacons against duplicate pads and accidental stand prompts, from a live public-test soak.

Existing **1.2.7, 1.3.0, and 1.3.5 data remain valid**. `config_schema` moves from `1294` to `1300`; migration auto-merges the new `teleport_beacons` keys with a timestamped backup. Existing plots default to **classic** arrival, so enabling 1.4.0 never suddenly gates old servers on pads.

Built for **Java 21+**, **Minecraft 1.20+**, **Paper, Purpur, Spigot, and Folia**.

---

## What's new

### Per-plot arrival choice (classic vs beacon)

Each plot manager decides how their **public listings** — Visit Discover/Warps, auction visit, and market jump — let visitors land:

- **classic** — Safe Travel to the plot spawn / listing point (1.3.0 style), even when pads exist.
- **beacon** — visitors **must** land on a public arrival pad. If none exists, the trip **fails closed** (`beacon_no_public_arrival`) instead of silently dropping the visitor at spawn.

Set the mode with `/ag arrival <classic|beacon>` while managing the plot (running `/ag arrival` alone reports the current mode). The choice is persisted in YML, SQL, and versioned plot snapshots, so restores keep it. `/ag home` stays personal plot spawn and is unaffected.

New `teleport_beacons` config keys:

- `force_public_arrival` (default **false**): a network-wide override. When `true`, every public listing behaves as if its owner chose beacon arrival (1.3.5-style mandatory pads).
- `prompt_cooldown_seconds` (default **7**): how long a player lingers near a linked pad before the stand confirm opens, and the minimum gap between repeat prompts. Replaces the old hard-coded 2.5s delay.
- `create_cooldown_seconds` (default **8**): rate-limits sneak-binding new pads.

### Beacon anti-duplicate and prompt hardening

- **One pad per block.** Creation checks for an existing pad first, so a bound block never gets a second pad. Startup de-duplicates pads by world/x/y/z, keeps the oldest, unbinds the extras, and logs it.
- **One directed link.** Linking keeps a single A→B route and never links a pad to itself.
- **Calmer stand prompt.** The confirm no longer stacks on an already-open confirm, and a throttled, Folia-safe end-rod sparkle plays on a usable linked pad while a player lingers, before the confirm.
- **Clear link rules.** A pad can link to your own pads, public pads, and alliance pads (when the destination plot allows alliance entry), plus friend/trusted pads only when the destination pad opts in to member/trusted use — never into a stranger's private pad.

---

## Coming next (not in 1.4.0)

The Travel Atlas **GUI consolidation** is the next milestone and is **not** part of 1.4.0: folding the beacon manager into the Visit GUI as Atlas tabs (Destinations / My Beacons / Arrival), the create/link wizard UI, `/ag beacon` opening the Atlas on My Beacons, a single Player-dashboard Travel button, and the new translated GUI strings across all nine language packs and the Codex. See [`aegisguard-modern/UPCOMING.md`](aegisguard-modern/UPCOMING.md).

---

## Upgrade

1. Stop the server completely.
2. Confirm the host is running **Java 21 or newer**.
3. Replace the plugin JAR with `AegisGuard-1.4.0.jar`.
4. Start the server. Config and language merge run on enable (`config_schema` `1294` → `1300`, with a backup). Existing plots load as-is and stay on classic arrival.
5. Confirm with `/agadmin transition` (aliases `upgrade`, `v130`). Doctor is optional.
6. Do **not** use Bukkit `/reload`.

Owners who want mandatory pad arrival can set `teleport_beacons.force_public_arrival: true`, or let each manager opt in per plot with `/ag arrival beacon`.

---

## Compatibility

| Requirement | Support |
| :--- | :--- |
| **Java** | `21+` |
| **Minecraft** | `1.20+` |
| **Server software** | Spigot, Paper, Purpur, Folia, and compatible Bukkit forks |
| **Upgrade path** | From AegisGuard `1.2.7`, `1.3.0`, or `1.3.5` with automatic config schema migration |
| **Languages** | Modern English, Old English, Mexican Spanish, Argentinian Spanish, Brazilian Portuguese, French, Italian, German, and Polish |
| **Optional** | Vault, PlaceholderAPI, WorldEdit/FAWE, Floodgate, Geyser-Spigot |

---

## Release files

### Server owners

Install this file in the server's `/plugins` folder:

`AegisGuard-1.4.0.jar`

### Plugin developers

These files are for compiling integrations. They do **not** belong in `/plugins`:

`AegisGuard-1.4.0-api.jar`  
`AegisGuard-1.4.0-dev-api.jar`

---

## Quick commands

```text
/ag menu                     Open the territory dashboard
/ag beacon                   Manage teleport pads on the claim you are standing in
/ag arrival <classic|beacon> Choose how visitors arrive at the plot you manage
/agadmin menu                Open the Staff Command Center
/agadmin transition          Confirm upgrade status from 1.2.7, 1.3.0, or 1.3.5
/agadmin doctor              Optional diagnostics and repair tools
```

---

See also [`RELEASE_NOTES_1.3.5.md`](RELEASE_NOTES_1.3.5.md) and [`RELEASE_NOTES_1.3.0.md`](RELEASE_NOTES_1.3.0.md) for the systems this release still includes.

**Simple. Steadfast. Eternal.**  
*Forged by Aegis Divine.*
