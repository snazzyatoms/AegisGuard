This ensures:

Your POM is valid

JitPack-compatible dependencies resolve correctly

The shading relocations succeed

No API mismatches exist

If your build succeeds locally, your pull request will succeed on JitPack too.

▶️ Running the Plugin for Testing

Build the JAR:

mvn clean package


Navigate to:

target/AegisGuard-<version>.jar


Drop the jar into your test server’s plugins/ folder.

Start the server and verify:

AegisGuard loads correctly

No errors in console

Commands and GUIs open normally

Database + YAML stores function as expected

🔀 Submitting Pull Requests

Before creating a PR:

Sync your fork with the latest main

git fetch upstream
git merge upstream/main


Ensure your build passes:

mvn clean install


Push your branch:

git push origin feature/my-feature


Open a Pull Request:

Write a clear title

Describe exactly what you changed and why

Attach console logs if applicable

Mention any breaking changes

Maintainers may request adjustments before merging.

🐛 Reporting Issues

When reporting bugs, please include:

Server type: Paper, Folia, Spigot

Server version (e.g. 1.20.4, 1.21.1)

AegisGuard version (from /version AegisGuard)

Steps to reproduce the issue

Full console logs or stack trace (if available)

Other installed plugins (optional)

Good bug reports = fast fixes.

Submit issues here:
https://github.com/snazzyatoms/AegisGuard/issues

📏 Coding Style & Standards

Please follow these conventions:

Use the existing project structure and naming patterns

Maintain compatibility with Java 17

Keep imports clean & organized

Avoid adding unnecessary dependencies

Document public APIs

Avoid blocking operations on the main thread

For Folia-specific code: always use GlobalRegionScheduler correctly

For database code: ensure thread safety

📝 Commit Message Standards

Example commit messages:

Fix: Prevent NPE in plot lookup when world is missing
Improve: Added PAPI placeholder for plot sale price
Feature: Added biome cosmetics API + GUI hooks
Refactor: Cleaned up ProtectionManager flag handling
Docs: Updated installation instructions and quick start


Avoid vague messages like:
❌ “fix stuff”
❌ “changes”
❌ “update”

🤝 Code of Conduct

Be respectful, patient, and collaborative.
All contributors, maintainers, and users should feel welcome.

Harassment, discrimination, or toxicity will not be tolerated.

⚖️ License

Contributions must be compatible with the MIT License, the license used by AegisGuard.
By contributing, you agree to license your work under MIT.

Thank you for helping shape AegisGuard into a modern, powerful protection ecosystem.
If you need help, hop into the Discord:

👉 https://discord.gg/Y2NpuR7UZE
