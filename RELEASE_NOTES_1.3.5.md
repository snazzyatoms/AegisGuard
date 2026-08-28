# AegisGuard 1.3.5

### *Protect your world. Empower your players. Ascend.*

AegisGuard `1.3.5` is the public follow-up to `1.3.0`. It adds **Teleport Beacons**, completes claim-data snapshots, optional WorldEdit/FAWE plot-build backups, and a soak of fixes from live `1.3.0` servers and Spigot review.

Existing **1.2.7 and 1.3.0 data remain valid**. Schema `1294` covers current 1.3.5 config. Automatic plot backups and automatic build copies stay **off** until you enable them.

Built for **Java 21+**, **Minecraft 1.20+**, **Paper, Purpur, Spigot, and Folia**.

---

## What's new

### Teleport Beacons

Players place linked pads on claims they manage, confirm in a GUI, and land only at the paired pad. Visit, market, and auction listings can require a public arrival beacon. `teleport_beacons.charges.mode` is `owner_choice` (pads may be free or paid), `always` (server-wide fee), or `off`. Optional fees can pay the plot owner. Pads survive claim merges and unbind when a plot is deleted. Beacon travel uses Safe Travel. `/ag home` stays personal plot spawn.

### Language placeholders

`{PLOT}`, `{MIN}`, `{PLAYER}`, and other `{KEY}` tokens now fill in on strings loaded from `lang/`, not only English fallbacks. Translated menus and chat show real names and numbers.

### ClaimBlocks and claim size

New claims count land as **used** plot area and no longer also `spend()` that area. Expanding land is refused when the wallet cannot cover the extra blocks, so available ClaimBlocks do not go negative. Group claims check the **leader's** ClaimBlocks. A one-time ledger repair runs only when an old double-count would over-commit the wallet; beacon and exchange spend is left alone.

`claims.min_radius` is enforced on **both** axes for wand claims, group claims, expansion, and Ascension growth (default `5` means at least a 10×10 plot). Skinny strips no longer pass just because one side is long.

### Bedrock / Geyser chest GUIs

Java clients keep the usual left / right / shift-right mapping. With Floodgate and/or Geyser, Bedrock players are detected automatically (`gui.bedrock.detect`, default on). Bedrock uses left-click for the main action, sneak+left (or swap-offhand) for the second action, and drop to cancel. Horizon Sigils accept left- or right-click in the world.

### Complete claim-data snapshots

Rollback restores guest passes, emergency lockdown, alliance access, and noticeboard posts together with owner, flags, members, and bounds. Older snapshot files without those keys stay valid. Versioned plot-data snapshots include market, rental, auction, progression, social, spawn, cosmetic, warp, zone, stall, and listing state.

### Optional full plot backups

When `snapshots.build_backup.enabled` is true and WorldEdit or FAWE is installed, a staff snapshot can copy the plot's block volume under `plugins/AegisGuard/plot-backups/`. Default **off**. Folia requires FAWE by default. Confirmation on restore stays on.

### Gradual automatic backups

`snapshots.automatic_player` can snapshot eligible player plots and server zones in small batches. Default **off**. Automatic build copies are a separate default-off switch.

---

## Bug fixes (from 1.3.0 soak and review)

- Restoring a snapshot after a plot changes owner no longer restores the wrong live plot
- Staff snapshot restore runs on the plot region thread (Folia-safe)
- Snapshot prune respects the configured cap when age and count limits both apply
- Rollback clears role nicknames added after the snapshot
- Player menu footer no longer leaves empty clickable holes for non-admins
- Staff Tools hides Routes, Arena, Expansions, Audit, and Snapshots when those modules are off
- Saving a plot no longer auto-lifts an expired timed lockdown as a side effect
- Schema migration no longer turns a legacy `*.enabled: false` back on when `modules.*` is missing
- Language `{KEY}` placeholders apply to `lang/` translations
- ClaimBlocks no longer go negative when expanding land
- `claims.min_radius` is enforced on both width and depth
- Geyser/Floodgate Bedrock players can complete player chest GUIs without relying on right-click
- Restore dispatch fails closed on scheduler shutdown, unloaded worlds, and rejected tasks; staff see **build queued** or **partial** instead of a false completed message
- Beacon travel does not double-charge; pads survive claim merges

---

## Transaction-safe restoration

Every restore shows a preview with owner, world, bounds, selected data categories, estimated chunks, and build-backup availability. Staff can restore all data, builds, or individual categories through `/agadmin restore here confirm [scope]`.

A confirmed restore creates an atomic rescue snapshot before changing live data. One restore may run per plot, and the plot is maintenance-locked until the operation completes. Folia build jobs use one chunk per owning-region task. Interrupted operations restart as **paused for staff review** and are never replayed automatically. Staff can retry or release with `/agadmin restore operation <operation-id> retry|release`.

---

## Upgrade

1. Stop the server completely.
2. Confirm the host is running **Java 21 or newer**.
3. Replace the plugin JAR with `AegisGuard-1.3.5.jar`.
4. Start the server. Config and language merge run on enable. Existing plots load as-is.
5. Confirm with `/agadmin transition` (aliases `upgrade`, `v130`). Doctor is optional.
6. Do **not** use Bukkit `/reload`.

Plot-build backups stay off until you enable them and install WorldEdit or FAWE. Validate capture and restore on a staging server before enabling them in production.

---

## Compatibility

| Requirement | Support |
| :--- | :--- |
| **Java** | `21+` |
| **Minecraft** | `1.20+` |
| **Server software** | Spigot, Paper, Purpur, Folia, and compatible Bukkit forks |
| **Upgrade path** | From AegisGuard `1.2.7` or `1.3.0` with automatic config schema migration |
| **Languages** | Modern English, Old English, Mexican Spanish, Argentinian Spanish, Brazilian Portuguese, French, Italian, German, and Polish |
| **Optional** | Vault, PlaceholderAPI, WorldEdit/FAWE, Floodgate, Geyser-Spigot |

---

## Release files

### Server owners

Install this file in the server's `/plugins` folder:

`AegisGuard-1.3.5.jar`

### Plugin developers

These files are for compiling integrations. They do **not** belong in `/plugins`:

`AegisGuard-1.3.5-api.jar`  
`AegisGuard-1.3.5-dev-api.jar`

---

## Quick commands

```text
/ag menu                 Open the territory dashboard
/ag beacon               Manage teleport pads on the claim you are standing in
/ag guide                Replay the first-claim walkthrough
/agadmin menu            Open the Staff Command Center
/agadmin transition      Confirm upgrade status from 1.2.7 or 1.3.0
/agadmin doctor          Optional diagnostics and repair tools
```

---

See also [`RELEASE_NOTES_1.3.0.md`](RELEASE_NOTES_1.3.0.md) for the 1.3.0 player systems this release still includes.

**Simple. Steadfast. Eternal.**  
*Forged by Aegis Divine.*
