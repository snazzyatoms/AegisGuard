# AegisGuard 1.4.0

### *Protect your world. Empower your players. Ascend.*

AegisGuard `1.4.0` builds on the public `1.3.5` release. It finishes the Travel Atlas, adds Quick-Claim and restore-safe roles, ships Guardian Succession, introduces Caravans & Trade Routes, and lets staff opt a server/spawn plot into Keep Health / Keep Hunger sanctuary.

Existing **1.2.7, 1.3.0, and 1.3.5 data remain valid**. `config_schema` moves from `1294` to `1308`; migration auto-merges new keys with a timestamped backup. Existing plots default to **classic** arrival, so enabling 1.4.0 never suddenly gates old servers on pads.

Built for **Java 21+**, **Minecraft 1.20+**, **Paper, Purpur, Spigot, and Folia**.

This document describes the `V1.4.0` source line. It is **not** a GitHub Release.

---

## What's new

### Travel Atlas

Visit is one travel menu with **Destinations**, **My Beacons**, **Arrival**, and **Caravans**. `/ag beacon` opens My Beacons. Plot managers still choose how public listings land:

- **classic** — Safe Travel to the plot spawn / listing point (1.3.0 style), even when pads exist.
- **beacon** — visitors **must** land on a public arrival pad. If none exists, the trip **fails closed** (`beacon_no_public_arrival`).

Set the mode with `/ag arrival <classic|beacon>`. Travelers may override when the owner permits. `/ag home` stays personal plot spawn. Beacons stay one pad per block and one directed A→B link. `teleport_beacons.force_public_arrival` can require pad arrival network-wide.

### Quick-Claim and menu navigation

`/ag quickclaim [radius]` (alias `/ag qc`) and a Territory hub button claim a square around you through the existing claim pipeline. Settings appears only on the main player hub and the staff menu; Back returns to the screen that opened it. Claiming honors `max_claims_per_player`.

### Restore-safe roles

Snapshot restore **merges** members and roles by default (`snapshots.restore.protect_roles: true`). Owners can lock members so restore and role edits cannot silently drop them. `/ag roles lock|unlock|undo` writes to the audit ledger.

### Guardian Succession

Granting `co_owner` or `steward` auto-locks that member. `/ag heir`, `/ag succession assume|rollback|menu`, and the Access-page Stewardship GUI cover inactivity assume, transfer cooldown, and a short rollback window.

### Caravans & Trade Routes

`/ag caravan` dispatches charge-then-deliver shipments along public beacon hops. Insurance, weighted route events (safe, ambush, toll, boon, delay), Folia-safe ticks, YAML+SQL persistence, and resume-on-load are included. Gate with `modules.caravans`.

### Server sanctuary

Staff who can manage a **server plot** (spawn, hub, and other staff-owned land) can turn on **Keep Health**, **Keep Hunger**, **Keep XP**, and **Keep Inventory** in Claim Settings → Safety. Players standing in that plot then do not lose hearts or food, and deaths there can keep levels and items. Eating can still fill the hunger bar. Void and `/kill` still apply. All four toggles start **off**. Personal player claims never get them. There is **no hub-flight Safety flag**. Plot flight still unlocks at Ascension / Horizon level 30. Staff can fly when `flight_skill.staff_always` is on, or grant a temporary flight skill with `/agadmin skill fly <player> [seconds]`.

### Claim presets and staff seasons

After a successful claim, the main preset chooser opens (Home / Shop / Arena / Farm, or Spawn / Hub / Shop / Arena on server plots). Presets never overwrite sanctuary flags or Hearth. Staff can run a season overlay: `/agadmin season` pins featured plots on Atlas Discover and featured routes in the Routes browser.

### Hearth rooms

Plot owners and server-plot stewards can turn on **Hearth** in Claim Settings → Safety. Public chat then stays in the room you are standing in. A room is a 3D subplot (`/ag subplot`) — the house, the pit, the lobby — or the rest of that plot if you are not in a subplot. People outside cannot hear inside, and the room cannot hear the street. That is how a closed house and an arena safe-zone stay quiet without scanning doors. Staff with `aegis.admin.hearth` still hear every room and are not muffled when they speak. Aegis Frequency remains the opt-in plot-member radio. Hearth starts **off**.

---

## Upgrade

1. Stop the server completely.
2. Confirm the host is running **Java 21 or newer**.
3. Replace the plugin JAR with `AegisGuard-1.4.0.jar`.
4. Start the server. Config and language merge run on enable (`config_schema` `1294` → `1308`, with a backup). Existing plots load as-is and stay on classic arrival.
5. Confirm with `/agadmin transition` (aliases `upgrade`, `v130`, `v140`). Doctor is optional.
6. Do **not** use Bukkit `/reload`.

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

## Plugin file

Install this file in the server's `/plugins` folder:

`AegisGuard-1.4.0.jar`

API JARs (`AegisGuard-1.4.0-api.jar`, `AegisGuard-1.4.0-dev-api.jar`) are for developers and do **not** belong in `/plugins`.

---

## Quick commands

```text
/ag menu                     Open the territory dashboard
/ag quickclaim [radius]      Claim a square around you
/ag visit                    Open the Travel Atlas
/ag beacon                   Open the Atlas My Beacons tab
/ag arrival <classic|beacon> Choose how visitors arrive at the plot you manage
/ag heir [player|clear]      Name a succession heir
/ag succession               Open Stewardship / assume / rollback
/ag caravan                  Dispatch and track trade caravans
/agadmin season              Staff season featured plots and routes
/agadmin skill fly <player>  Grant a temporary flight skill
/agadmin menu                Open the Staff Command Center
/agadmin transition          Confirm upgrade status from 1.2.7, 1.3.0, or 1.3.5
/agadmin doctor              Optional diagnostics and repair tools
```

---

See also [`RELEASE_NOTES_1.3.5.md`](RELEASE_NOTES_1.3.5.md) and [`RELEASE_NOTES_1.3.0.md`](RELEASE_NOTES_1.3.0.md) for the systems this line still includes.

**Simple. Steadfast. Eternal.**  
*Forged by Aegis Divine.*
