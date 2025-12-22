<p align="center">
  <img width="100%" alt="AegisGuard Feature Art" src="https://github.com/user-attachments/assets/03f02b56-925b-468e-8d29-2839b6f06c95" />
</p>

<p align="center">
  <strong>Simple. Steadfast. Eternal.</strong>
</p>

<p align="center">
  <a href="https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/"><img src="https://img.shields.io/badge/Spigot-Download-orange?style=for-the-badge"></a>
  <a href="https://hangar.papermc.io/snazzyatoms/AegisGuard"><img src="https://img.shields.io/badge/Hangar-Download-green?style=for-the-badge"></a>
  <a href="https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy"><img src="https://img.shields.io/badge/CurseForge-Download-purple?style=for-the-badge"></a>
  <a href="https://github.com/snazzyatoms/AegisGuard/wiki"><img src="https://img.shields.io/badge/Wiki-Documentation-blue?style=for-the-badge"></a>
  <a href="https://discord.gg/Y2NpuR7UZE"><img src="https://img.shields.io/badge/Discord-Join%20Community-7289da?style=for-the-badge&logo=discord&logoColor=white"></a>
</p>

---

<p align="center">
  <em>“Forged to shield thy lands from peril and strife. With the Sacred Scepter of Aegis, you claim, shape, and safeguard your realm with precision.”</em>
</p>

---

## 🔗 Quick Links

| **Resource** | **Platform** |
| :--- | :--- |
| **Documentation** | [Wiki & Guides](https://github.com/snazzyatoms/AegisGuard/wiki) |
| **Support** | [Discord Community](https://discord.gg/Y2NpuR7UZE) |
| **Tracking** | [Issues](https://github.com/snazzyatoms/AegisGuard/issues) • [Releases](https://github.com/snazzyatoms/AegisGuard/releases) |
| **Downloads** | [Spigot](https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/) • [Hangar](https://hangar.papermc.io/snazzyatoms/AegisGuard) • [CurseForge](https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy) |

---

## ⚡ What is AegisGuard?

AegisGuard is a **modern, Folia-native land protection and economy ecosystem** for Minecraft servers.  
It doesn’t just lock chunks. It turns land into a **living asset** in your server’s economy.

### 👥 For Players
* **Visual Claiming:** Claim land using a visual selection wand and intuitive GUI menus.
* **Living Economy:** Pay **upkeep taxes** to keep claims active.
* **Real Estate:** List plots on a **Global Marketplace** for sale or rent.
* **Auction House:** Watch inactive/seized land automatically flow into a server-wide auction.

### 🛡️ For Admins
* **Zone Management:** Define Server Zones (spawn, warzones, markets, jails) with ease.
* **GUI-First:** Manage flags, roles, zoning, and protections through menus, not commands.
* **Integration:** Seamless support for **Dynmap, BlueMap, Pl3xMap**, and **Vault**.

---

## ✨ What’s New in v1.2.4

**Focus:** Polish, Clarity, and the new **Codex Language Structure**.

> [!WARNING]
> **Migration Alert:** The old `messages.yml` system has been **removed** and replaced with the **Codex system**.

* **Codex Language Packs:** All GUI text, chat strings, and translations now live inside per-language folders.
* **GUI Text Parity:** Consistent keys across language packs make fixing missing translations easier.
* **Placeholder & Lore Fixes:** Fixed issues where placeholders (cost/balance) failed to parse inside lore.
* **General Stability:** Polish pass on menus, formatting, and edge cases.

### 🌐 Codex Localization Structure
Translations are now modular and cleaner.

```text
plugins/AegisGuard/
└─ languages/
   ├─ modern_english/
   │  ├─ guis.yml
   │  ├─ chat.yml
   │  └─ ...
   ├─ hybrid_english/
   │  └─ guis.yml
   ├─ old_english/
   │  └─ guis.yml
   └─ spanish_neutral/
      └─ guis.yml
```

---

## 🎯 Key Features

### ⚡ Folia-Native Architecture
* **Auto-Detection:** Automatically detects Folia, Paper, or Spigot at runtime.
* **Region-Safe:** Uses Folia scheduling APIs for true region-safe execution.
* **Async Heavy Lifting:** Upkeep, auctions, data saves, and map syncs stay off the main thread.

### 💰 Living Land Economy
Turn land into a real estate loop:
* **Upkeep System:** Plots require periodic taxes.
* **Auction House:** Expired or seized plots go up for public auction.
* **Global Marketplace:** Players buy, sell, and rent plots via GUI.
* **Flexible Costs:** Support for Vault money, Items, XP, or XP Levels.

### 🏰 Empire Building (Player Features)
* **Claims & Sub-Claims:** Create main plots and defined sub-zones (rooms, shops, apartments) to rent out.
* **Plot Leveling:** Invest resources to unlock buffs (Speed, Haste, Regen).
* **Biome Cosmetics:** Change the biome atmosphere of a plot.
* **Visuals:** Particle borders and holograms replace chat spam.

### 🛡️ Server Plot Architecture (Admin Tools)
* **Sentinel’s Scepter:** `/agadmin wand` – Create server-owned zones that ignore limits.
* **Plot Conversion:** `/agadmin convert` – Seize a player plot and convert it to a server zone instantly.
* **Master Key Mode:** High-trust permission for staff to adjust plots by presence.

### 🧭 GUI-First Design
Almost everything is driven by menus:
* **Guardian Codex:** `/ag menu` – The central hub for claims, settings, travel, and upgrades.
* **Flags & Roles:** Configure protections and trust levels visually.

---

## 🗺️ Integrations

AegisGuard supports a full ecosystem approach:

* **Economy:** [Vault](https://www.spigotmc.org/resources/vault.34315/) (Required for economy features)
* **Mapping:** Dynmap, BlueMap, Pl3xMap (Async overlays)
* **Placeholders:** PlaceholderAPI (HUD/Scoreboard support)

---

## 📦 Installation & Updates

### Fresh Install
1.  Download AegisGuard from **[Hangar](https://hangar.papermc.io/snazzyatoms/AegisGuard)** or **[Spigot](https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/)**.
2.  Drop the `.jar` into `plugins/`.
3.  Start server to generate configs.
4.  Join and type `/ag menu`.

### 🔁 Updating to v1.2.4
1.  **Back up** your `plugins/AegisGuard/` folder.
2.  Replace the jar with v1.2.4.
3.  Start the server.
4.  **Note:** If you had custom messages in `messages.yml`, you must now apply them to the new files in `languages/<your_language>/`.

---

## ⌨️ Commands (Essentials)

| Command | Description |
| :--- | :--- |
| `/ag menu` | Opens the **Guardian Codex** (Main Hub). |
| `/agadmin wand` | Gives the **Sentinel’s Scepter** for server zones. |
| `/agadmin convert` | Converts the plot you are standing in to a Server Zone. |

> Full command list and permissions available on the [Wiki](https://github.com/snazzyatoms/AegisGuard/wiki).

---

## 🧪 Try AegisGuard Live

Test the plugin on our public demo server:

```yaml
IP:       72.5.47.116:25570
Version:  1.20.4+ (Daily World Reset • Sandbox Mode)
Command:  /ag menu
```

---

## 🆘 Support & Contributing

* **Discord:** [Join for Support](https://discord.gg/Y2NpuR7UZE)
* **Bug Reports:** [GitHub Issues](https://github.com/snazzyatoms/AegisGuard/issues)
* **Wiki:** [Read the Docs](https://github.com/snazzyatoms/AegisGuard/wiki)

**Contributing:** PRs are welcome! We specifically need help with **Translation Packs** and GUI consistency.
