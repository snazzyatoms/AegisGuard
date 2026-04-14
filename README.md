<p align="center">
  <img width="100%" alt="AegisGuard Feature Art" src="https://github.com/user-attachments/assets/03f02b56-925b-468e-8d29-2839b6f06c95" />
</p>

<p align="center">
  <strong>Simple. Steadfast. Eternal.</strong>
</p>

---

# AegisGuard 1.2.7

This repository now uses a clean multi-module layout for the full `1.2.7` line.

Instead of treating the modern plugin as the repo root and placing the other variants in awkward nested folders, AegisGuard now mirrors the clearer split used for Coffers:

- `aegisguard-paper`
- `aegisguard-legacy`
- `aegisguard-paper-bundled`
- `aegisguard-legacy-bundled`

This makes GitHub browsing, direct uploads, releases, and developer integrations much cleaner.

---

## What Changed In 1.2.7

- introduced a clean root module structure for all AegisGuard variants
- added a proper legacy line for older Java and server environments
- added bundled variants for larger servers that want drivers included out of the box
- added direct API jars for plugin developers
- moved away from JitPack as the primary release path
- standardized direct release outputs across modern, legacy, bundled, and legacy bundled lines

---

## Module Layout

| Folder | Purpose |
|--------|---------|
| `aegisguard-paper` | Main modern plugin line |
| `aegisguard-legacy` | Legacy plugin line for older server and JVM targets |
| `aegisguard-paper-bundled` | Modern plugin with bundled database and runtime dependencies |
| `aegisguard-legacy-bundled` | Legacy plugin with bundled database and runtime dependencies |

---

## Which Jar To Use

### Server Owners

| Jar | Use This For |
|-----|--------------|
| `AegisGuard-1.2.7.jar` | Modern Paper, Spigot, Purpur, and similar modern server setups |
| `AegisGuard-1.2.7-legacy.jar` | Older legacy-compatible server setups |
| `AegisGuard-1.2.7-bundled.jar` | Modern servers that want bundled database and runtime support |
| `AegisGuard-1.2.7-legacy-bundled.jar` | Legacy servers that want bundled database and runtime support |

### Plugin Developers

| Jar | Purpose |
|-----|---------|
| `AegisGuard-1.2.7-api.jar` | Modern integration API |
| `AegisGuard-1.2.7-legacy-api.jar` | Legacy integration API |
| `AegisGuard-1.2.7-bundled-api.jar` | Bundled modern API distribution |
| `AegisGuard-1.2.7-legacy-bundled-api.jar` | Bundled legacy API distribution |

Compatibility `-dev-api.jar` files are also produced for direct-release workflows.

---

## Release Folders

Each module publishes its artifacts into its own `releases` folder:

- [paper releases](./aegisguard-paper/releases)
- [legacy releases](./aegisguard-legacy/releases)
- [paper bundled releases](./aegisguard-paper-bundled/releases)
- [legacy bundled releases](./aegisguard-legacy-bundled/releases)

---

## Direct Releases

AegisGuard `1.2.7` no longer depends on JitPack as its main delivery path.

Instead, the line is intended to be distributed directly through release jars:

- plugin jars for server owners
- API jars for developers
- bundled plugin jars for convenience-focused or larger installs

That means developers integrating with AegisGuard can reference the released API jars directly instead of relying on JitPack repo resolution.

See [DIRECT_RELEASES.md](./DIRECT_RELEASES.md) for the release matrix.

---

## Build From Root

From the repo root:

```powershell
mvn clean package
```

This builds all four variants from one place.

---

## Notes

- modern and legacy lines are both maintained in the same repository
- bundled variants exist so small servers do not need to download large all-in-one jars unless they want that convenience
- the API jars are intended to support plugin developers who want to integrate with AegisGuard events, plots, claim blocks, economy hooks, and related services

---

<p align="center">
  <strong>AegisGuard 1.2.7</strong> | Organized for direct releases and cleaner multi-line maintenance
</p>
