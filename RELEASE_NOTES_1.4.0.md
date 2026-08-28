# AegisGuard 1.4.0

### *Protect your world. Empower your players. Ascend.*

AegisGuard `1.4.0` is the source follow-up to public `1.3.5`. It adds **Aegis Frequency** (same-server plot member chat) and **Visual Presence** (entry titles, for-sale labels, and scepter border direction labels). Cross-server Frequency, township tax, siege, and hologram entities stay on the 2.0 roadmap.

Existing **1.2.7, 1.3.0, and 1.3.5 data remain valid**. Schema `1300` covers current 1.4.0 config. Plots are not rewritten.

Built for **Java 21+**, **Minecraft 1.20+**, **Paper, Purpur, Spigot, and Folia**.

---

## What's new

### Aegis Frequency (plot chat)

`/ag chat` toggles a private channel for the claim you belong to. Chat then stays on that plot even if you walk away. `/ag chat off` returns you to public chat. `/ag chat <message>` sends one Frequency line without changing the toggle.

Only the owner, assigned non-visitor roles, and an active full-plot renter hear Frequency. Guest Passes and alliance-only visitors are not on the channel. The Explore menu includes an Aegis Frequency button. Turn the module off with `modules.plot_chat: false`.

This is the same-server slice of the 2.0 "Aegis Frequency" idea. Bungee/Velocity sync is not included.

### Visual Presence

When you enter a plot, a title shows the plot name and owner. Listed plots can show a for-sale price instead of the owner line. Holding the Aegis Scepter near a border shows the cardinal direction and plot name on the action bar.

These use titles and action bars only. No hologram entities are spawned, so Paper and Folia stay compatible. Turn the module off with `modules.visual_presence: false`.

---

## Upgrade

1. Stop the server completely.
2. Confirm the host is running **Java 21 or newer**.
3. Replace the plugin JAR with `AegisGuard-1.4.0.jar`.
4. Start the server. Config and language merge run on enable. Existing plots load as-is.
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

---

## Release files

Install this file in the server's `/plugins` folder:

`AegisGuard-1.4.0.jar`

API libraries for developers:

`AegisGuard-1.4.0-api.jar`  
`AegisGuard-1.4.0-dev-api.jar`

---

## Quick commands

```text
/ag menu                 Open the territory dashboard
/ag chat                 Toggle Aegis Frequency for the claim you belong to
/ag chat off             Leave Frequency and use public chat
/ag chat <message>       Send one Frequency line
/ag beacon               Manage teleport pads on the claim you are standing in
/agadmin transition      Confirm upgrade status from 1.2.7, 1.3.0, or 1.3.5
```

---

See also [`RELEASE_NOTES_1.3.5.md`](RELEASE_NOTES_1.3.5.md) for Teleport Beacons and the 1.3.5 soak fixes this release still includes.

**Simple. Steadfast. Eternal.**  
*Forged by Aegis Divine.*
