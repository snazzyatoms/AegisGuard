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

- **messages.yml Removed (Migrated to Codex):** The old `messages.yml` system has been **removed** and replaced with the **language folder Codex system**.  
  All GUI text, chat strings, and translations now live inside per-language folders (instead of a single mega-file).
- **Codex Language Packs (New Structure):** Language packs are now clean, modular, and easy to maintain or expand.
- **GUI Text Parity:** Consistent keys and formatting across language packs so missing/partial translations are easier to spot and fix.
- **Placeholder & Lore Fixes:** Eliminates common issues where placeholders show up unreplaced (example: cost/balance/track placeholders inside lore).
- **General Stability Pass:** Small fixes that smooth out the day-to-day experience (menus, formatting, and edge cases).

### 🌐 Codex Localization (v1.2.4)

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
