# AegisGuard 1.2.7 Release Artifacts

This module produces the AegisGuard server plugin and its developer API artifacts.

Artifacts:

- `AegisGuard-1.2.7.jar` - Minecraft server plugin
- `AegisGuard-1.2.7-api.jar` - public API for plugin developers
- `AegisGuard-1.2.7-dev-api.jar` - compatibility copy of the public API

Build from the repository root with `mvn clean package`. Release artifacts are copied to the root `releases/` directory.

Only the standard server plugin belongs in a server's `plugins` directory. Optional integrations and JDBC drivers remain external dependencies, while the API artifacts are provided solely for developers compiling compatible add-ons.

Before publishing, run `mvn clean verify`. For local server lifecycle checks, run `scripts/smoke-test.ps1` from the repository root and point `-FixturesRoot` at folders containing accepted Folia, Paper, Purpur, or Spigot test servers.
