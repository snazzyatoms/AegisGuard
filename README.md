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

**Pages**
- **Wiki:** https://github.com/snazzyatoms/AegisGuard/wiki  
- **Issues:** https://github.com/snazzyatoms/AegisGuard/issues  
- **Releases:** https://github.com/snazzyatoms/AegisGuard/releases  

**Plugin Listings**
- **Spigot:** https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/  
- **Hangar:** https://hangar.papermc.io/snazzyatoms/AegisGuard  
- **CurseForge:** https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy  

**Community**
- **Discord:** https://discord.gg/Y2NpuR7UZE  

---

## ⚡ What is AegisGuard?

AegisGuard is a **modern, Folia-native land protection and economy ecosystem** for Minecraft servers.  
It doesn’t just lock chunks. It turns land into a **living asset** in your server’s economy.

### Players can

- Claim land using a **visual selection wand** and intuitive GUI menus.
- Pay **upkeep taxes** to keep their claims active.
- List plots on a **Global Marketplace** for sale or rent.
- Watch inactive land automatically flow into a server-wide **Auction House**.

### Admins can

- Define **Server Zones** (spawn, warzones, markets, jails, etc.).
- Manage flags, roles, zoning, and protections through GUIs instead of commands.
- Integrate with **Dynmap / BlueMap / Pl3xMap** and **Vault** for a full ecosystem.

---

## ✨ What’s New in v1.2.4

v1.2.4 focuses on **polish, clarity, and the new Codex language structure**.

- **Codex Language Packs (New Structure):** GUI text and messages are now organized by language folders, making translation packs clean, modular, and easy to maintain.
- **Legacy Cleanup:** `messages.yml` is treated as legacy/redundant for the new system (kept for compatibility where needed, but no longer the “big bucket” file).
- **GUI Text Parity:** Consistent keys and formatting across language packs so missing/partial translations are easier to spot and fix.
- **Placeholder & Lore Fixes:** Eliminates common issues where placeholders show up unreplaced (example: cost/balance/track placeholders inside lore).
- **General Stability Pass:** Small fixes that smooth out the day-to-day experience (menus, formatting, and edge cases).

> For the full, granular changelog: check **Releases** on GitHub.

---

## 🎯 Key Features

### ⚡ Folia-Native Architecture

- Automatically detects **Folia / Paper / Spigot** at runtime.
- Uses Folia scheduling APIs where available for true region-safe execution.
- Heavy tasks (upkeep, auctions, data saves, map sync) are designed to stay off the main thread whenever possible.

---

### 💰 Living Land Economy

Turn land into a **real estate loop**:

- **Upkeep System:** Plots require periodic taxes.
- **Auction House:** Expired or seized plots go up for public auction.
- **Global Marketplace:** Players buy, sell, and rent plots through GUI menus.
- **Flexible Costs:** Use Vault money, items, XP, or XP levels as currency (depending on configuration and your enabled economy options).

---

### 🏰 Empire Building (Player Features)

- **Claims & Sub-Claims (Zoning):** Create main plots, then define sub-zones (rooms, shops, apartments) to rent out.
- **Plot Leveling:** Invest resources to level up plots and unlock buffs (Speed, Haste, Regen, etc.).
- **Biome Cosmetics:** Change the biome atmosphere of a plot for visual flair.
- **Particle Borders & Holograms:** Show borders and plot info without spamming chat.

---

### 🛡️ Server Plot Architecture (Admin Tools)

- **Sentinel’s Scepter:** `/agadmin wand` – create server-owned zones that ignore normal limits/costs.
- **Plot Conversion:** `/agadmin convert` – stand in a player plot and seize it as a server zone.
- **Master Key Mode:** High-trust permission that lets staff adjust plots by presence and authority.

---

### 🧭 GUI-First Design

Almost everything is driven by menus instead of raw commands:

- **Guardian Codex:** `/ag menu` – central hub for claims, settings, travel, and upgrades.
- **Flags & Roles:** Configure protections and trust levels visually.
- **Cosmetics & Travel:** Warp between plots and style your land without typing long commands.

---

### 🗺️ Map & Ecosystem Integrations

- **Dynmap:** Async plot overlays on your web map.
- **BlueMap:** 3D map overlays for claims.
- **Pl3xMap:** Lightweight modern map support.
- **Vault:** Economy backbone for upkeep and transactions.
- **PlaceholderAPI:** Placeholders for scoreboards, GUIs, and HUDs.

---

## ✅ Compatibility

- **Minecraft:** `1.20.4+` *(recommended to stay current with Paper/Folia builds)*
- **Server Software:** Folia (recommended), Paper, Spigot
- **Java:** 17+ (recommended for modern server environments)

> If you’re running Spigot, you may not benefit from Folia-specific scheduling behavior. Folia/Paper are the intended “full feature” path.

---

## 📦 Install

1. Download AegisGuard from one of the official listings:
   - Hangar: https://hangar.papermc.io/snazzyatoms/AegisGuard  
   - Spigot: https://www.spigotmc.org/resources/aegisguard-modern-land-protection-economy.130333/  
   - CurseForge: https://www.curseforge.com/minecraft/bukkit-plugins/aegisguard-modern-land-protection-economy  

2. Drop the `.jar` into your server’s `plugins/` folder.
3. Start the server once to generate config files.
4. Configure economy/storage/language options as desired.
5. Join and open the main menu:
   - `/ag menu`

---

## 🔁 Update Guide (v1.2.4)

When updating to **v1.2.4**, the biggest difference is the **Codex language pack structure**.

**Recommended steps:**
1. **Back up** your `plugins/AegisGuard/` folder.
2. Replace the old jar with the **v1.2.4** jar.
3. Start the server and let new files generate.
4. Review your language folder structure (see below).
5. If your build includes a reload/refresh toggle or admin reload command, use it after edits.

**Important note about language files:**
- v1.2.4 expects language text to live inside **per-language folders** (instead of one mega `messages.yml`).
- If you previously customized text in `messages.yml`, migrate those edits into the appropriate language files.

---

## ⌨️ Commands (Essentials)

AegisGuard is GUI-first, but these are the commonly used entry points:

- `/ag menu`  
  Opens the **Guardian Codex** (main hub).

- `/agadmin wand`  
  Gives the admin **Sentinel’s Scepter** for server zones.

- `/agadmin convert`  
  Converts the plot you’re standing in into a server-owned zone.

> Full command list, permissions, and advanced usage live on the **Wiki**:  
> https://github.com/snazzyatoms/AegisGuard/wiki

---

## 🌐 Localization (Codex Language Packs)

v1.2.4 introduces a cleaner layout for translations.

**Typical structure:**
```text
plugins/AegisGuard/
└─ languages/
   ├─ modern_english/
   │  ├─ guis.yml
   │  ├─ chat.yml
   │  └─ ...
   ├─ hybrid_english/
   │  ├─ guis.yml
   │  └─ ...
   ├─ old_english/
   │  ├─ guis.yml
   │  └─ ...
   └─ spanish_neutral/
      ├─ guis.yml
      └─ ...
