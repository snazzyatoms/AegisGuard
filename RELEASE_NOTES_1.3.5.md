# AegisGuard 1.3.5

### *Protect your world. Empower your players. Ascend.*

AegisGuard `1.3.5` is the soak-and-feature follow-up to `1.3.0`. Claim snapshots now restore 1.3.0 plot maps, wiki sources live in the repo, and staff can optionally back up plot **builds** with WorldEdit or FastAsyncWorldEdit. Players can place **Teleport Beacons** — linked pads with Safe Travel, public arrival for listings, and a server-configurable fee policy.

Existing **1.2.7 and 1.3.0 data remain valid**. Schema `1292` adds recovery-safety, build-integrity/storage safeguards, and bounded automatic player-plot/server-zone backup defaults; automatic backups and automatic build copies remain off until enabled.

Built for **Java 21+**, **Minecraft 1.20+**, **Paper, Purpur, Spigot, and Folia**.

---

## Highlights

### Teleport Beacons

Players place linked pads on claims they manage, confirm in a GUI, and land only at the paired pad. Visit, market, and auction listings can require a public arrival beacon. `teleport_beacons.charges.mode` is `owner_choice` (pads may be free or paid), `always` (server-wide fee), or `off`. Optional fees can pay the plot owner. Pads survive claim merges and unbind when a plot is deleted.

### Complete claim-data snapshots

Rollback restores guest passes, emergency lockdown, alliance access, and noticeboard posts together with owner, flags, members, and bounds. Older snapshot files without those keys stay valid.

### Soak fixes

Staff restore looks up the live plot by id after ownership transfer, and does not lift timed lockdown as a side effect of taking a snapshot. Rollback runs on the plot region thread (Folia-safe). The player menu footer has no empty air slots. Disabled Routes / Arena / Expansions / Audit / Snapshot modules stay off the Staff Tools row. Schema migration no longer turns a legacy `*.enabled: false` back on when `modules.*` is missing.

Restore dispatch now goes through one Paper/Spigot/Folia scheduling boundary. World and plot mutations run on the owning region, player feedback runs on the player scheduler, and schematic file reads/writes run asynchronously. Scheduler shutdown, retired entities, unloaded worlds, and rejected tasks fail closed. Staff receives a distinct **build queued** or **partial** result instead of a false completed message while schematic work remains.

### Transaction-safe restoration

Every restore now shows a preview with owner, world, bounds, selected data categories, estimated chunks, and build-backup availability. Staff can restore all data, builds, or individual categories through `/agadmin restore here confirm [scope]`.

A confirmed restore creates an atomic, durable rescue snapshot before changing live data. One restore may run per plot, and the plot is maintenance-locked against player, staff, piston, fluid, fire, entity, and explosion changes until the operation completes. Folia build jobs use one chunk per owning-region task and are awaited; success is not reported while a paste remains queued.

Restore lifecycle state is stored in `plugins/AegisGuard/restore-operations.yml`. Every Folia chunk tile has durable completed, pending, and failed checkpoints. Interrupted operations restart as **paused for staff review** and are never replayed automatically. Staff can retry the same transaction without repeating checkpointed tiles, or release a reviewed operation with `/agadmin restore operation <operation-id> retry|release`. Partial restores remain locked until one of those explicit actions.

### Gradual automatic player-plot and server-zone backups

`snapshots.automatic_player` can create recovery snapshots for eligible player plots and server zones in small round-robin batches. It is default **off**, while `include_server_zones` defaults **on** inside that disabled feature. Batch interval and size, minimum backup interval, per-plot count and age retention, group-plot/world eligibility, owner inactivity, and low-TPS pausing are configurable.

Data-only automation hashes the complete restorable plot state and skips unchanged plots. Player and server-zone histories use distinct automatic snapshot types. Server zones share the same fair queue, TPS throttle, retention, restore lock, and Folia region scheduler. The older all-at-once server-zone timer is suppressed while the bounded coordinator owns server-zone backups, preventing duplicate timers. Automatic build copies have their own default-off switch and deliberately do not claim unchanged detection because inventories and other plugins can alter a build without a reliable block event. A manual restore and an automatic backup can never run on the same plot simultaneously.

Versioned plot-data snapshots now include market, rental, auction, progression, social, spawn, cosmetic, warp, zone, stall, and listing state. New snapshots preserve the exact external rental offer and contract too—including deposit, term, original start/expiry times, reminder state, and auto-renew—while older snapshots use backward-compatible reconciliation. Full and selective economy restores update these rental indexes before their durable save; zone restores likewise reconcile deposit indexes.

### Optional full plot backups

When `snapshots.build_backup.enabled` is true and WorldEdit or FAWE is installed, a staff snapshot copies the plot's complete block volume—from the world's minimum through maximum build height—under `plugins/AegisGuard/plot-backups/`. Standard servers use the configurable chunk cap; Folia uses one chunk per owning-region task, processed sequentially. On Folia, the default safety policy requires FAWE; other integrations fail closed until an owner explicitly stages and overrides that policy.

Each complete backup has an atomic manifest with plot/world/bounds identity, integration and format information, per-file byte counts and SHA-256 checksums, full-area coverage validation, and restore-operation links. Missing, corrupt, incomplete, incompatible, or malformed backups are refused before live mutation. Partial captures are discarded. Retention applies per plot and globally; active source/rescue backups are protected, and unknown artifacts are moved to a recoverable quarantine instead of immediately deleted.

The staff snapshot browser includes plot/owner/time/type/build/integrity filters, checksum and integration status, a restore-operation viewer with retry/release controls, and a storage dry run. `/agadmin health` reports integration compatibility, storage integrity, orphan files, operation states, scheduler platform, and maintenance locks. Optional Discord alerts cover restore failures and serious backup-storage warnings; both ship **off**.

Confirmation on restore stays on. Huge plots over `max_volume` skip the build copy. Default **off**. Automatic player-plot/server-zone build copies are controlled separately and are also default **off**. If a compatible build integration is unavailable, data-only backups and data-only restores continue safely.

### Wiki in the repository

GitHub Wiki paste sources live in [`wiki/`](wiki/).

---

## Upgrade

Swap the JAR, start the server, and config plus language merge run on enable. Confirm with `/agadmin transition`. Doctor is optional. Do not use Bukkit `/reload`.

Plot-build backups stay off until you enable them and install WorldEdit or FAWE. Folia uses the fail-closed FAWE policy by default; validate build capture and restore on a staging server before enabling it in production.

---

See also [`RELEASE_NOTES_1.3.0.md`](RELEASE_NOTES_1.3.0.md) for the 1.3.0 player systems this release still includes.
