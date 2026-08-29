# Installation

AegisGuard is designed for a straightforward installation on single servers and larger Paper or Folia networks.

**AegisGuard 1.3.5** installs the same way as previous releases. Existing 1.2.7 and 1.3.0 claims, configs, and plot data remain valid and are migrated automatically on first startup.

## Requirements

Confirm that the server meets these requirements before installing.

| Requirement | Supported Version / Software |
| :--- | :--- |
| Java | **Java 21** or newer (required for AegisGuard 1.3.5) |
| Server Software | Paper, Purpur, Pufferfish, or Folia (Spigot-compatible forks supported) |
| Minecraft | `1.20`+ through current supported releases |

> **Note:** Minecraft versions `1.8` through `1.12` are not supported because of legacy API limitations. Servers still on Java 17 must upgrade the JVM before running 1.3.5.

---

## Install AegisGuard

### 1. Download

Download the latest stable AegisGuard **1.3.5** release from an official source:

- [GitHub Releases](https://github.com/snazzyatoms/AegisGuard/releases/tag/1.3.5) — Recommended
- SpigotMC — paste your resource URL here when the 1.3.5 listing is live

Use the main plugin jar from the release assets: `AegisGuard-1.3.5.jar`. Do **not** install `AegisGuard-1.3.5-api.jar` or `AegisGuard-1.3.5-dev-api.jar` as the server plugin. Those are compile-time files for developers.

### 2. Add the Plugin

1. Stop the server completely with the `stop` command.
2. Place `AegisGuard-1.3.5.jar` in the server's `plugins` folder.
3. Start the server.

On first launch, AegisGuard creates its data folder and writes default configuration files. Configuration schema upgrades are applied automatically when needed. Optional systems ship **on** except **wilderness revert**, which ships **off**. Third-party hooks stay off until you opt in.

### 3. Confirm It Loaded

After the server starts, check the console for the AegisGuard enable message. Folia servers should also report that Folia-compatible scheduling has been enabled.

Run `/ag` or `/ag menu` in game. If AegisGuard responds or opens the Guardian Codex, the installation is complete.

Do **not** use Bukkit's global `/reload` command. Use `/agadmin reload` for supported configuration and language reloads. Restart the server after changing storage or integrations.

---

## Optional Dependencies

AegisGuard works without dependencies. Install the following plugins to enable additional features. Map, Discord, and protection-compat hooks stay **off** in `config.yml` until you enable them.

| Plugin | Enables |
| :--- | :--- |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Economy features, upkeep, marketplace support, optional beacon fees, and optional route rewards. Requires a compatible economy provider. |
| [Dynmap](https://www.spigotmc.org/resources/dynmap.274/) | Live claim rendering on the server map. |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | AegisGuard placeholders for chat, scoreboards, and other integrations. |
| [LuckPerms](https://luckperms.net/) | Permission and group management. |
| WorldEdit or FastAsyncWorldEdit | Optional staff plot-build backups (`snapshots.build_backup`, default **off**). Folia requires FAWE by default. |
| Floodgate and/or Geyser-Spigot | Optional. When present, AegisGuard detects Bedrock clients so chest GUIs use left-click and sneak-left (`gui.bedrock.detect`, default on). |

---

## Updating from 1.2.7, 1.3.0, or 1.3.5 to 1.4.0

Use this process when updating AegisGuard:

1. Stop the server completely.
2. Confirm the host is running **Java 21+**.
3. Remove the previous AegisGuard jar from the `plugins` folder.
4. Add `AegisGuard-1.4.0.jar`.
5. Start the server. Config and language merge run on enable. Existing plots load as-is and stay on classic arrival.
6. Confirm the upgrade with `/agadmin transition` (aliases `/agadmin upgrade`, `/agadmin v130`).
7. Run `/agadmin doctor` only if something looks wrong.

A copy of `plugins/AegisGuard/` (and world data) is recommended. It is **not** required to keep claims. The plugin also writes its own config backup when schema migration runs.

> **Your data is preserved.** Updating does not remove `config.yml`, language files, or existing plot data stored in YAML or a configured SQL database. 1.4.0 bumps `config_schema` from `1294` to `1300`, auto-merging the new `teleport_beacons` keys (`force_public_arrival`, `prompt_cooldown_seconds`, `create_cooldown_seconds`) with a backup when migration runs.

After updating, verify:

- `/agadmin transition` reports that you are on the current schema (`1300`)
- `/ag menu` opens normally
- Existing claims still protect correctly (and remain on classic arrival)
- Optional new features appear with safe defaults (Guest Passes, Lockdown, Realm Profile, Routes, Alliance Access, Teleport Beacons)
- Modules you turned off in `modules:` do not appear on the player menu
- Plot-build backups stay **off** until you set `snapshots.build_backup.enabled` and install WorldEdit or FAWE
- `/ag beacon` opens the pad manager on a plot you can manage
- `/ag arrival` reports the plot's arrival mode and `/ag arrival beacon` requires a public pad
- Translated menus show real names and numbers instead of leftover `{KEY}` tokens

---

## Fresh Install Checklist

- [ ] Java 21+ is installed on the host
- [ ] AegisGuard 1.3.5 jar is in `plugins/`
- [ ] Server starts without AegisGuard errors
- [ ] `/ag wand` and `/ag claim` work for a test player
- [ ] `/ag menu` opens the Guardian Codex
- [ ] Vault/economy is installed if you want paid claims, markets, or rewards
- [ ] Review `modules:` if you want a protection-only server (turn extras off)

---

## Support

- [GitHub Releases](https://github.com/snazzyatoms/AegisGuard/releases/tag/1.3.5)
- [GitHub Issues](https://github.com/snazzyatoms/AegisGuard/issues)
- [Support Discord](https://discord.gg/nHwdhKzeKR)

> *Simple. Steadfast. Eternal.*
