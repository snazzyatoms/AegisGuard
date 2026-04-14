# AegisGuard 1.2.7 Direct Releases

Direct jars are now the primary way to distribute AegisGuard.

Modern artifacts:
- `AegisGuard-1.2.7.jar` - standard modern plugin jar
- `AegisGuard-1.2.7-api.jar` - direct API jar for plugin developers
- `AegisGuard-1.2.7-dev-api.jar` - compatibility copy of the same API jar

Bundled artifacts:
- `AegisGuard-1.2.7-bundled.jar` - modern bundled jar with database drivers included
- `AegisGuard-1.2.7-bundled-api.jar` - bundled-line API jar
- `AegisGuard-1.2.7-bundled-dev-api.jar` - compatibility copy of the same API jar

Guidance:
- use the standard jar for most servers
- use the bundled jar when you want the database stack included out of the box
- give plugin developers the `-api.jar` file directly instead of pointing them at a build service
