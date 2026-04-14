# AegisGuard 1.2.7 Legacy Direct Releases

Direct jars are now the primary way to distribute the legacy AegisGuard line.

Legacy artifacts:
- `AegisGuard-1.2.7-legacy.jar` - standard legacy plugin jar
- `AegisGuard-1.2.7-legacy-api.jar` - direct API jar for plugin developers
- `AegisGuard-1.2.7-legacy-dev-api.jar` - compatibility copy of the same API jar

Legacy bundled artifacts:
- `AegisGuard-1.2.7-legacy-bundled.jar` - legacy bundled jar with database drivers included
- `AegisGuard-1.2.7-legacy-bundled-api.jar` - bundled legacy API jar
- `AegisGuard-1.2.7-legacy-bundled-dev-api.jar` - compatibility copy of the same API jar

Guidance:
- use the standard legacy jar for most older servers
- use the bundled legacy jar when you want the database stack included out of the box
- give plugin developers the `-api.jar` file directly instead of pointing them at a build service
