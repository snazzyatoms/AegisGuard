# Contributing to AegisGuard

Thank you for contributing to AegisGuard 1.3.0. This project is a Paper/Folia territory-protection plugin built with Maven and Java 21.

## Development requirements

- Java 21
- Maven 3.8 or later
- Git
- A Paper or Folia test server for gameplay verification

## Build and test

From the `aegisguard-modern` directory, run:

```bash
mvn clean verify
```

This compiles the plugin, runs the automated tests, and creates these artifacts in `releases/`:

- `AegisGuard-1.3.5.jar` — the server plugin
- `AegisGuard-1.3.5-api.jar` — the public developer API library, not a server plugin
- `AegisGuard-1.3.5-dev-api.jar` — a compatibility copy of the developer API, not a server plugin

Only the standard server JAR belongs in a Minecraft server's `plugins` directory.

## Testing changes

Run the full Maven verification suite before opening a pull request. For changes affecting protection, persistence, migrations, GUIs, or scheduling, also test on a copy of a real server data folder.

For AegisGuard 1.3.0 features, test Guest Pass expiry and revocation, Emergency Lockdown, Audit Ledger access, routes, and all Alliance Access toggles with at least two player accounts. Test both Paper and Folia where the change touches movement, entities, scheduling, or world access.

## Contribution rules

- Preserve existing plot ownership, roles, rentals, markets, and progression unless a change is explicitly documented as a migration.
- Keep new gameplay behaviour opt-in or non-disruptive by default.
- Add or update tests for every behaviour change.
- Keep all language packs and GUI navigation in parity.
- Use clear, focused commits and describe player-facing changes in the pull request.

## Reporting issues

Include the AegisGuard version, Java version, server software/version, relevant configuration, steps to reproduce, and the applicable server log excerpt. Do not post secrets, database credentials, or player private data.

## License

Contributions are provided under the repository's MIT License.
