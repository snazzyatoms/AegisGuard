# AegisGuard 1.3.0 Release Artifacts

Run the release build from the repository root:

```text
mvn clean package
```

Maven copies these artifacts into the local `releases/` directory:

| Artifact | Purpose |
|---|---|
| `AegisGuard-1.3.0.jar` | Minecraft server plugin |
| `AegisGuard-1.3.0-api.jar` | Public API for plugin developers |
| `AegisGuard-1.3.0-dev-api.jar` | Compatibility copy of the public API |

Only `AegisGuard-1.3.0.jar` belongs in a Minecraft server's `plugins` directory. The API artifacts are compile-time resources for add-on developers.

## Release Verification

Use the full confidence build before publishing:

```text
mvn clean verify
```

The GitHub Actions workflow performs the same Java 21 verification and uploads the server JAR as a workflow artifact. For runtime checks, `scripts/smoke-test.ps1` validates plugin startup, `/agadmin reload`, clean shutdown, and exception-free logs against a supplied Folia, Paper, Purpur, or Spigot server fixture.

Publish the verified server JAR through the GitHub Releases page. Keep checksums and release notes alongside the uploaded artifact so server owners can confirm exactly what they installed.
