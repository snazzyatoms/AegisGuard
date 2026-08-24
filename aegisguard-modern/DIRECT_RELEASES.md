# AegisGuard 1.3.5 Release Artifacts

This module produces the AegisGuard server plugin and its developer API artifacts.

Artifacts:

- `AegisGuard-1.3.5.jar` - Minecraft server plugin
- `AegisGuard-1.3.5-api.jar` - public API library for plugin developers; do not install it as a server plugin
- `AegisGuard-1.3.5-dev-api.jar` - compatibility copy of the developer API; do not install it as a server plugin

Build from the `aegisguard-modern` directory with `mvn clean verify`. Release artifacts are copied to that module's `releases/` directory.

Only the standard server plugin belongs in a server's `plugins` directory. Optional integrations and JDBC drivers remain external dependencies, while the API artifacts are provided solely for developers compiling compatible add-ons.

Before publishing, run `mvn clean verify`. For local server lifecycle checks, run `scripts/smoke-test.ps1` from the repository root and point `-FixturesRoot` at folders containing accepted Folia, Paper, Purpur, or Spigot test servers.
