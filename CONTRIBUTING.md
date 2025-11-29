## ▶️ Running the Plugin for Testing

### Build the JAR:
```bash
mvn clean package
Navigate to the output:
php-template
Copy code
target/AegisGuard-<version>.jar
Install for testing:
Drop the JAR into your server’s plugins/ folder

Start the server

Verify:

AegisGuard loads correctly

No console errors

Commands and GUIs function normally

Database + YAML stores behave as expected

🔀 Submitting Pull Requests
Before creating a PR:
1. Sync your fork with the latest main:
bash
Copy code
git fetch upstream
git merge upstream/main
2. Ensure your build passes:
bash
Copy code
mvn clean install
3. Push your branch:
bash
Copy code
git push origin feature/my-feature
When opening a Pull Request:
Please include:

A clear, descriptive title

Explanation of what you changed

Why the change is necessary

Any console logs related to the fix

Notes on backward compatibility (if applicable)

Maintainers may request adjustments before merging.

🐛 Reporting Issues
To report bugs efficiently, include:

Server type: Paper, Folia, or Spigot

Server version: e.g., 1.20.4 or 1.21.1

AegisGuard version: from /version AegisGuard

Exact steps to reproduce

Full console logs or stack traces (if applicable)

Other installed plugins (optional but helpful)

Good bug reports = faster fixes.

Submit issues here:
👉 https://github.com/snazzyatoms/AegisGuard/issues

📏 Coding Style & Standards
Please follow these conventions:

Use the existing project structure and naming patterns

Maintain compatibility with Java 17

Keep imports clean and organized

Avoid adding unnecessary dependencies

Document public APIs

Avoid blocking operations on the main thread

For Folia support: use GlobalRegionScheduler properly

For database operations: ensure thread safety

📝 Commit Message Standards
Good commit message examples:
vbnet
Copy code
Fix: Prevent NPE in plot lookup when world is missing
Improve: Added PAPI placeholder for plot sale price
Feature: Added biome cosmetics API + GUI hooks
Refactor: Cleaned up ProtectionManager flag handling
Docs: Updated installation instructions and quick start
Avoid vague commits:
❌ “fix stuff”

❌ “changes”

❌ “update”

🤝 Code of Conduct
Be respectful, patient, and collaborative

Keep discussions focused on code, not people

Harassment or discrimination is not tolerated

Everyone should feel welcome contributing

⚖️ License
All contributions must be compatible with the MIT License, which AegisGuard uses.
By contributing, you agree that your work is licensed under MIT.

Thank you for helping shape AegisGuard into a modern, powerful land-protection ecosystem.
If you need help, join the community:

👉 Discord: https://discord.gg/Y2NpuR7UZE

yaml
Copy code
