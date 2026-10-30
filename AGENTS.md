# AegisGuard Agent Notes

## Project Layout
- Multi-module Maven project; plugin source lives in `aegisguard-modern/`.
- Main class: `com.aegisguard.AegisGuard`
- Target platforms: Spigot, Paper, Purpur, Folia (declares `folia-supported: true`).
- Java baseline: 21 (configured in `aegisguard-modern/pom.xml`).
- Minecraft baseline: 1.20+ (paper-api 1.20.4-R0.1-SNAPSHOT).

## Build & Test
```bash
# Compile only
mvn clean compile -f aegisguard-modern/pom.xml

# Run the full unit-test suite (513 tests)
mvn test -f aegisguard-modern/pom.xml

# Build the release JARs (produces AegisGuard-1.4.0.jar and AegisGuard-1.4.0-dev-api.jar)
mvn clean package -f aegisguard-modern/pom.xml -DskipTests
```

## Verification Tips
- Watch for deprecation warnings with `-Dmaven.compiler.showDeprecation=true`.
- The shade plugin bundles HikariCP; the filter excludes signing metadata and now also `META-INF/MANIFEST.MF` / `module-info.class` to keep the plugin manifest intact.
- Folia readiness is centralized in `com.aegisguard.scheduler.AegisScheduler`; avoid scheduling through `Bukkit.getScheduler()` directly.

## Documentation Conventions
- **Organize by theme, not version.** Landing and overview docs group systems by pillar (travel, economy, community, admin, network); per-version detail belongs in `wiki/Release-Highlights.md` and the changelog txt — not stacked "What's New" sections on `Home.md`.
- **Write fixes as value propositions.** Lead with the player/owner outcome ("the nine-language codex never drops a message under load"); keep the technical detail in a subordinate clause or parentheses. Avoid raw Java jargon (locks, races, publication) in user-facing text.
- **Don't bury the flagship.** The multi-server network is the climactic feature — it closes the README highlights, the CurseForge headline features, and the overview document.
- **Canonical content, no duplication.** Install steps live in `wiki/Installation.md`; the full command/permission matrix lives in `wiki/Permissions-and-Commands.md`; version history lives in `Release-Highlights.md`/`changelog.txt`. Other pages summarize and link.
- **Audience routing.** `wiki/Home.md` ends with player / owner / developer paths; `wiki/_Sidebar.md` mirrors those groups. `wiki/Glossary.md` defines product nouns (Scepter, Codex, ClaimBlocks, Hearth, Frequency, etc.) — link to it instead of re-explaining terms.
- **Wiki links** use page names (`[Install](Installation)`) or `Page#anchor` anchors; `wiki/` pages are pasted manually into the GitHub Wiki, so keep filenames wiki-style (`Hyphenated-Title.md`).
- Keep `AegisGuard CurseForge description.md` and `AegisGuard CurseForge description FINAL.txt` in sync — they are the same listing copy.

## GUI Conventions
- `/agadmin` (Staff Command Center) stays a flat sectioned launcher: four color-banded rows (Policy / Territory & Claims / Recovery & Integrity / Content & Maintenance), every tool one click. When the tool count outgrows the bands, promote the section frame panes into clickable category sub-menus — the `addSectionFrame` + `aegis_action` routing already supports that move. `InterfaceQualityContractTest` pins the section keys, pane colors, `SLOT_TOOL_ARENA = 38`, and action names.
