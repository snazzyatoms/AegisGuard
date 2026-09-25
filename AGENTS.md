# AegisGuard Agent Notes

## Project Layout
- Multi-module Maven project; plugin source lives in `aegisguard-modern/`.
- Main class: `com.aegisguard.AegisGuard`
- Target platforms: Spigot, Paper, Purpur, Folia (declares `folia-supported: true`).
- Java baseline: 21 (configured in `aegisguard-modern/pom.xml`).
- Minecraft baseline: 1.20+ (paper-api 1.20.4-R0.1-SNAPSHOT).

## Build & Test
```bash
# Compile only
mvn clean compile -f aegisguard-modern/pom.xml

# Run the full unit-test suite (446 tests)
mvn test -f aegisguard-modern/pom.xml

# Build the release JARs (produces AegisGuard-1.4.0.jar and AegisGuard-1.4.0-dev-api.jar)
mvn clean package -f aegisguard-modern/pom.xml -DskipTests
```

## Verification Tips
- Watch for deprecation warnings with `-Dmaven.compiler.showDeprecation=true`.
- The shade plugin bundles HikariCP; the filter excludes signing metadata and now also `META-INF/MANIFEST.MF` / `module-info.class` to keep the plugin manifest intact.
- Folia readiness is centralized in `com.aegisguard.scheduler.AegisScheduler`; avoid scheduling through `Bukkit.getScheduler()` directly.
