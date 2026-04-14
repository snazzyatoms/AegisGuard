# AegisGuard 1.2.7 Monorepo

This folder is an upload-friendly multi-module layout for the full `1.2.7` line.

Modules:
- `aegisguard-paper` - modern Paper/Spigot line
- `aegisguard-legacy` - Java 16 / legacy server line
- `aegisguard-paper-bundled` - modern bundled distribution
- `aegisguard-legacy-bundled` - legacy bundled distribution

Why this exists:
- avoids awkward nested paths like `AegisGuard/AegisGuard legacy/src/...`
- removes spaces from module folder names
- makes GitHub browsing and uploads cleaner
- mirrors the clearer split used for Coffers

Build from this root:

```powershell
mvn clean package
```

Each module still publishes its own release jars into its local `releases` folder.
