<p align="center">
  <img width="100%" alt="AegisGuard Feature Art" src="https://github.com/user-attachments/assets/03f02b56-925b-468e-8d29-2839b6f06c95" />
</p>

<p align="center">
  <strong>Simple. Steadfast. Eternal.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-1.2.7-gold?style=for-the-badge" alt="Version 1.2.7" />
  <img src="https://img.shields.io/badge/Layout-Multi--Module-2d7ff9?style=for-the-badge" alt="Multi-module layout" />
  <img src="https://img.shields.io/badge/Legacy-Supported-8f5cff?style=for-the-badge" alt="Legacy supported" />
  <img src="https://img.shields.io/badge/Bundled-Available-15a86b?style=for-the-badge" alt="Bundled editions available" />
</p>

---

# AegisGuard 1.2.7

**AegisGuard** is a protection and claim-management platform built for both modern and legacy Minecraft server lines.

Version `1.2.7` gives the project a cleaner identity, a cleaner repository, and a cleaner release family:

- modern and legacy lines are separated properly
- bundled and lean editions are clearly defined
- developer API jars are part of the release flow
- the repository is organized to feel more like a real platform and less like a single loose plugin drop

---

## ✨ A Better Release Family

`1.2.7` is not just another jar update.

This line reshapes AegisGuard into a more polished ecosystem:

- **Modern servers** get a focused Paper-oriented line
- **Legacy servers** get their own dedicated build path
- **Bundled editions** exist for convenience-focused and larger installs
- **API releases** are available directly for developers who want to integrate with AegisGuard

The result is a cleaner experience for server owners, plugin developers, and anyone browsing the project on GitHub.

---

## 🧭 Repository Layout

The repository now uses a clean top-level module structure:

| Module | Purpose |
|--------|---------|
| `aegisguard-modern` | Main modern AegisGuard line |
| `aegisguard-legacy` | Legacy AegisGuard line |
| `aegisguard-modern-bundled` | Modern bundled edition |
| `aegisguard-legacy-bundled` | Legacy bundled edition |

This keeps the repo much cleaner than the older mixed root layout and makes each line easier to understand at a glance.

Release jars are intentionally not stored inside this repository tree. Source stays in this repo; built jars are published separately from the local `D:\AegisGuard-1.2.7\releases` folder.

---

## 🚀 Editions At A Glance

### Server Editions

| Jar | Best For |
|-----|----------|
| `AegisGuard-1.2.7.jar` | Modern server setups |
| `AegisGuard-1.2.7-legacy.jar` | Legacy-compatible server setups |
| `AegisGuard-1.2.7-bundled.jar` | Modern servers that want bundled runtime and database support |
| `AegisGuard-1.2.7-legacy-bundled.jar` | Legacy servers that want bundled runtime and database support |

### Developer Editions

| Jar | Role |
|-----|------|
| `AegisGuard-1.2.7-api.jar` | Modern public API |
| `AegisGuard-1.2.7-legacy-api.jar` | Legacy public API |
| `AegisGuard-1.2.7-bundled-api.jar` | Modern bundled API |
| `AegisGuard-1.2.7-legacy-bundled-api.jar` | Legacy bundled API |

Compatibility `-dev-api.jar` files are also produced for direct distribution workflows.

---

## 🏰 What AegisGuard Brings

AegisGuard is built around a broad protection and progression experience, including:

- land claiming and plot ownership
- claim block earning and exchange systems
- movement-aware plot protections
- group ownership and progression systems
- market, stall, and economy-oriented claim workflows
- snapshots, restoration, and admin recovery tools
- migration tooling for moving from other systems
- multilingual presentation including modern English, old English, and Spanish variants

---

## 🧩 Developer-Friendly Direction

The `1.2.7` line is much friendlier to plugin developers than the older layout.

Instead of centering the project around JitPack-style dependency flow, AegisGuard is now structured around direct release artifacts and cleaner module separation.

That gives developers a clearer path for:

- integrating with AegisGuard APIs
- targeting the correct modern or legacy line
- working with a cleaner repository structure
- avoiding the messier dependency flow that build-service-first releases often create

---

## 📦 Lean Or Bundled

The project now supports two very different server-owner preferences without forcing one compromise on everyone:

- **Lean editions** for servers that want smaller plugin jars and tighter control
- **Bundled editions** for servers that prefer convenience and fewer setup steps

That means small servers are not forced into oversized all-in-one jars, while large servers still have a ready-to-go bundled option.

---

## 🔥 Why This Matters

AegisGuard `1.2.7` is meant to feel cleaner, stronger, and more intentional:

- cleaner source layout
- cleaner GitHub presentation
- cleaner release handling
- cleaner variant separation
- cleaner developer entry point

This is the beginning of AegisGuard behaving more like a proper platform family instead of a single plugin project that kept growing in place.

---

<p align="center">
  <strong>AegisGuard 1.2.7</strong><br />
  Modern. Legacy. Bundled. Developer-ready.
</p>
