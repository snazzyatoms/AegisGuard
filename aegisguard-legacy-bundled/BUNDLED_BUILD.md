# AegisGuard Legacy Bundled Build

This project is the legacy bundled distribution for larger or convenience-focused servers.

Release outputs:
- `AegisGuard-1.2.7-legacy-bundled.jar` - legacy plugin jar with bundled database drivers
- `AegisGuard-1.2.7-legacy-bundled-api.jar` - direct API jar for plugin developers
- `AegisGuard-1.2.7-legacy-bundled-dev-api.jar` - compatibility copy of the same API jar

Bundled runtime components:
- HikariCP
- SQLite JDBC
- MySQL Connector/J
- MariaDB Java Client

Recommended use:
- small or normal legacy servers: use the regular legacy jar
- larger legacy installs or convenience-first setups: use the bundled legacy jar

Direct release artifacts are the distribution path for this line.
