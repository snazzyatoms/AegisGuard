# AegisGuard Example Add-on

This is a minimal example plugin that uses the public **AegisGuard API**.

## What it does

- Hooks into the `AegisGuardAPI` service registered by AegisGuard.
- Listens for `PlotEnterEvent` and `PlotLeaveEvent`.
- Sends a simple welcome/farewell message to the player.

## Build

1. Build the main AegisGuard project to produce the API JAR:
   ```bash
   cd ../../aegisguard-modern
   mvn clean package -DskipTests
   ```
   This creates `../../releases/AegisGuard-1.4.0-dev-api.jar`.

2. Build this example:
   ```bash
   mvn clean package
   ```

## Install

1. Install `AegisGuard-1.4.0.jar` on your server.
2. Place `AegisGuardExampleAddon-1.0.0.jar` in the `plugins` folder.
3. Start the server.

## Extend it

Open `ExampleAddonPlugin.java` and explore the available API methods through
`AegisGuardAPI`. You can read claims, check permissions, listen to events, and
react to AegisGuard actions without modifying the main plugin.
