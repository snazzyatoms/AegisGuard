# AegisGuard Public Beta — Play & Test Server

This project is the configuration and plugin source for the separate public server named **AegisGuard Public Beta — Play & Test Server**.

## Non-negotiable isolation boundary

Run the Public Beta from a dedicated server root. That root must have its own copies of every path below:

```text
AegisGuard-Public-Beta-Server/
├── plugins/
│   └── AegisGuard/                 Public Beta plugin data only
├── logs/                           Public Beta logs only
├── backups/                        Public Beta backups only
├── worlds/
│   ├── <primary-world>/             Adopted as the Welcome Hub
│   ├── aegis_beta_play/             Created by AegisGuard
│   └── aegis_beta_test_lab/         Created by AegisGuard
├── config/                         Public Beta Paper configuration only
├── bukkit.yml
├── spigot.yml
├── server.properties
└── paper.jar
```

Never point this server at the private 1.4 development server root, plugin data folder, database, world container, logs, or backup destination. Never restore private claims, player data, worlds, or backups into this server. The reverse is also prohibited.

The packaged `config.yml` enables `public-beta-mode` and identifies this instance as `aegisguard-1.4.0-public-beta`. The profile store repeats that identity in `plugins/AegisGuard/public-beta/player-profiles.yml` so an operator can verify a backup before restoring it.

## AegisGuard world provisioning

Do not manually create or copy the Play and Test Lab folders. In game, use:

```text
/agadmin publicbeta worlds
```

The owner-only wizard adopts the server's existing primary world as the Welcome Hub, validates the two managed world names and target folders, and stages creation only after confirmation. Restart the server once. During that startup AegisGuard creates `aegis_beta_play` and `aegis_beta_test_lab` with normal Overworld terrain, structures, and random seeds. Later restarts load only worlds whose registry UUID and world identity marker match this Public Beta instance.

The packaged default protects the entire adopted Welcome Hub world. Operators can safely reapply full-world protection with `/agadmin publicbeta hubprotect world confirm`, or choose a bounded zone with `/agadmin publicbeta hubprotect <radius> confirm`. Expansion refuses to overwrite any overlapping player plot. Players and staff can return safely with `/ag hub` or the **Return to Welcome Hub** button in `/ag beta`.

Provisioning state is stored in `plugins/AegisGuard/public-beta/worlds.yml`. AegisGuard never deletes worlds, overwrites a folder collision, or silently adopts an unregistered world. It also creates a protected Hub server zone around the primary spawn when the area is clear; an overlapping player claim is reported and left untouched.

The Test Lab uses normal AegisGuard ownership checks. A player may enter the Test Lab flow only with the scoped `PUBLIC_BETA_PLAYER` profile marker, and owner-facing controls remain limited to plots the player owns. The marker is not a Bukkit permission group and does not grant OP, console, staff recovery, server-zone management, global configuration, ownership of another plot, or access to another player’s plot.

## Acceptance test before opening

Use a new, non-OP Minecraft account with no staff permissions:

1. Join and confirm the player appears at the protected Welcome Hub.
2. Verify a supported client language shows the detected-language confirmation.
3. Verify an unsupported language opens the picker directly.
4. Select a language and confirm it is stored under the player UUID in `public-beta/player-profiles.yml`.
5. Confirm the localized **AegisGuard Public Beta Guide** written book is received.
6. Verify the localized first-join welcome screen appears with Read Guidebook, Choose Play or Test Lab, Open AegisGuard Menu, and Decide Later.
7. Read the book and confirm it explains commands, world choices, allowed actions, forbidden authority, Test Lab ownership limits, and voice modes.
8. Close it and right-click the book again to open the destination menu; sneak-right-click must reopen the readable guide.
9. Run `/ag menu`, select Public Beta, and confirm the same destination menu opens. Also verify `/ag beta` opens it directly.
10. From Play World and Test Lab, run `/ag hub` and use the Return to Welcome Hub button. Confirm both arrive safely at the registered Hub spawn.
11. As a non-admin, try breaking, placing, and claiming far outside the Hub spawn area. Confirm all are denied with guidance to use Play World or Test Lab.
12. As an operator, run `/agadmin publicbeta hubprotect world` and verify it previews rather than changing data; repeat with `confirm` and verify success or an exact overlapping-plot conflict.
13. Test all destination, language, voice, and Later choices.
14. Enter the Test Lab, create a player-owned plot, and verify owner settings work there.
15. Attempt to manage another player’s plot and verify access is denied.
16. Verify the account is not OP and cannot use console, staff recovery, server-zone management, or global configuration.
17. With Simple Voice Chat installed on server and clients, verify Proximity, Global across worlds, and Current World Only with two non-OP players.
18. Verify Hearth temporarily isolates voice and the saved beta scope resumes afterward; verify a personal SVC group suspends Aegis routing and the saved scope resumes after leaving it.
19. Restart the server and verify the world registry, profile, language, voice mode, claims, and guide flow remain scoped to this Public Beta instance.

Do not call the server ready based only on a successful build. Complete this live non-admin acceptance test on the actual Public Beta server.
