# AegisGuard Bundled Build

This project builds the large-server bundled edition of AegisGuard 1.2.7 while keeping the standard project lean for normal installs.

Release outputs:
- `AegisGuard-1.2.7-bundled.jar` - modern plugin jar with bundled database drivers
- `AegisGuard-1.2.7-bundled-api.jar` - direct API jar for plugin developers
- `AegisGuard-1.2.7-bundled-dev-api.jar` - compatibility copy of the same API jar

Bundled runtime components:
- HikariCP
- SQLite JDBC
- MySQL Connector/J
- MariaDB Java Client

Recommended use:
- standard project: smaller regular installs
- bundled project: larger or convenience-focused installs

Direct release artifacts are the distribution path for this line.
