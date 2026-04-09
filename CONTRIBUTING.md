# Contributing to AegisGuard

Thank you for helping improve **AegisGuard**.

This guide is for contributors working on the current project line, including the `1.2.6.1` bugfix release and the upcoming `1.2.7` development cycle. It focuses on accurate local setup, safe changes, and contributions that keep the plugin stable across modern server software.

---

## Table of Contents

- [Development Requirements](#development-requirements)
- [Project Scope](#project-scope)
- [Forking and Cloning](#forking-and-cloning)
- [Building Locally](#building-locally)
- [Testing Changes](#testing-changes)
- [Pull Request Guidelines](#pull-request-guidelines)
- [Issue Reports and Bugfixes](#issue-reports-and-bugfixes)
- [Code Style and Compatibility Standards](#code-style-and-compatibility-standards)
- [Documentation Contributions](#documentation-contributions)
- [Release and Build Notes](#release-and-build-notes)
- [License](#license)

---

## Development Requirements

Before contributing, make sure you have:

- **Java 17 or newer**
- **Maven 3.9+** recommended
- A GitHub account
- Basic familiarity with Bukkit, Paper, or Folia plugin development
- A local test server for one or more of:
  - **Paper**
  - **Purpur**
  - **Spigot**
  - **Folia**

### Java Notes

AegisGuard currently compiles with **Java 17** using Maven `release 17`.

That means:

- contributors should be able to build with Java 17+
- the plugin is intended to run on Java 17 and newer runtimes
- do not raise the Java target casually, since it would reduce compatibility for server owners still using Java 17

---

## Project Scope

AegisGuard is a modern land-protection and territory-management plugin for **Minecraft 1.20+** server environments.

Primary platform targets are:

- **Paper**
- **Purpur**
- **Spigot**
- **Folia**

The plugin also includes optional compatibility hooks and integrations for systems such as:

- Vault
- PlaceholderAPI
- Dynmap
- BlueMap
- Pl3xMap / Squaremap
- Discord webhooks
- WorldGuard
- GriefPrevention
- GriefDefender
- Towny
- Residence

Contributions should preserve broad compatibility where practical and avoid unnecessary platform lock-in.

---

## Forking and Cloning

Recommended workflow:

1. Fork the repository on GitHub.
2. Clone your fork locally.
3. Create a dedicated feature or fix branch.

Example:

```bash
git clone https://github.com/<your-username>/AegisGuard.git
cd AegisGuard
git checkout -b fix/my-change
```

Keep pull requests focused. Small, clear changes are much easier to review and much safer to merge.

---

## Building Locally

This project uses **Maven** for dependency management and packaging.

### Standard build

```bash
mvn clean package
```

If the build succeeds, the release jar is written to:

```text
target/AegisGuard-<version>.jar
```

The `target/original-...jar` file is the unshaded intermediate jar.
The main `AegisGuard-<version>.jar` is the packaged release artifact.

### Current build baseline

- Java target: **17**
- Packaging: **shaded Maven jar**
- Bundled runtime dependencies include database support such as:
  - SQLite
  - MySQL
  - MariaDB
  - HikariCP

### If Maven fails

Please include:

- your Java version
- your Maven version
- the full compile or stack-trace error
- the branch or commit you were building

---

## Testing Changes

At the moment, AegisGuard does not rely on a large automated test suite, so contributor testing matters a lot.

Please test your changes locally where possible.

### Minimum expectations

- build the project successfully with Maven
- verify the plugin starts without errors
- verify your changed feature works on a local server
- verify your change does not break reload, startup, or shutdown behavior if it touches runtime systems

### Recommended manual testing

Depending on what you changed, test some combination of:

- claiming and unclaiming
- resizing and merging
- plot menus and GUIs
- group plots and treasury actions
- ClaimBlocks earning and exchange flow
- storage startup with the backend you touched
- `/agadmin reload`
- map hooks if you touched hook code
- Folia-safe behavior if you touched tasks or scheduling

### Compatibility-sensitive areas

Please be especially careful in:

- schedulers and repeating tasks
- storage and migration
- ClaimBlocks exchange state
- reload behavior
- map hooks
- external protection compatibility
- language loading and resource extraction

---

## Pull Request Guidelines

When submitting a pull request:

1. Keep the scope clear and limited.
2. Explain what changed and why.
3. Mention any config, language, or storage impact.
4. Mention how you tested it.
5. Include screenshots only if the change is GUI- or documentation-related.

### Good pull requests usually include

- a short summary
- the problem being solved
- the main implementation notes
- testing notes
- any known risk or follow-up work

### Please avoid

- unrelated formatting-only churn
- large mixed refactors without explanation
- silent config-key changes
- breaking language/config structure without migration notes

---

## Issue Reports and Bugfixes

Bug reports are most helpful when they include:

- server software and version
- Minecraft version
- Java version
- AegisGuard version
- other relevant plugins or integrations
- the exact error from console or `latest.log`
- reproduction steps

If the server says:

```text
(Is it up to date?)
```

that does **not** automatically mean the plugin is incompatible with that Minecraft version.
That message is often a generic wrapper around a real exception lower in the stack trace.

Please include the full error block whenever possible.

---

## Code Style and Compatibility Standards

### General standards

- Prefer clear, readable code over clever code.
- Match the style already used in nearby files.
- Keep comments helpful and concise.
- Avoid unnecessary rewrites in unrelated areas.

### Compatibility standards

- Avoid NMS and CraftBukkit internals unless there is no safe alternative.
- Prefer Bukkit, Paper, and Folia-safe public APIs.
- Keep Spigot/Paper/Purpur/Folia behavior in mind when touching tasks or world logic.
- Do not casually narrow support to only the newest server version.

### Stability-first areas

If you touch any of the following, please test carefully:

- `AegisGuard.java`
- storage backends
- reload flow
- map hook managers
- protection hook managers
- migration logic
- exchange or economy systems

---

## Documentation Contributions

Documentation contributions are welcome and appreciated.

Helpful documentation updates include:

- README improvements
- configuration clarifications
- permission fixes
- command documentation updates
- Hangar, GitHub, or release-page copy refreshes
- contributor onboarding improvements

When updating docs, make sure the text matches the current plugin version and current command/config behavior.

---

## Release and Build Notes

For the current project state:

- Version line: **1.2.6.1**
- Java target: **17**
- Supported runtime goal: **Java 17+**
- Platform goal: **modern Minecraft 1.20+ servers**

For future releases such as `1.2.7`, contributors should prefer:

- compatibility hardening
- safe migrations
- stronger diagnostics
- better admin tooling
- cleaner documentation

Please avoid making broad compatibility claims for unreleased Minecraft versions unless the project has actually been tested against them.

---

## License

By contributing to this repository, you agree that your contributions will be licensed under the repository's existing [LICENSE](LICENSE).
