# AegisGuard 1.3.5 Release Artifacts

Run the release build from the repository root:

```text
mvn clean package
```

Maven copies these artifacts into the local `releases/` directory:

| Artifact | Purpose |
|---|---|
| `AegisGuard-1.3.5.jar` | Minecraft server plugin |
| `AegisGuard-1.3.5-api.jar` | Public API library for plugin developers; do not install it as a server plugin |
| `AegisGuard-1.3.5-dev-api.jar` | Compatibility copy of the developer API; do not install it as a server plugin |

Only `AegisGuard-1.3.5.jar` belongs in a Minecraft server's `plugins` directory. The API artifacts are compile-time resources for add-on developers.

## Release Verification

Before publishing a build:

```text
mvn clean verify
```

The GitHub Actions workflow runs the same Java 21 verification and uploads the server JAR as a workflow artifact. For runtime checks, `scripts/smoke-test.ps1` validates plugin startup, `/agadmin reload`, clean shutdown, and exception-free logs against a supplied Folia, Paper, Purpur, or Spigot server fixture.

When you publish a GitHub Release, keep checksums and release notes alongside the uploaded artifact so server owners can confirm exactly what they installed.
