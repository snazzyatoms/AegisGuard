# Contributing to AegisGuard 🛡️

Thank you for your interest in contributing to AegisGuard!  
This document explains how to work with the project using our **Maven + JitPack/JitCI build pipeline** and how to submit high-quality contributions.

---

## 📚 Table of Contents
- [Development Requirements](#development-requirements)  
- [How the Build System Works](#how-the-build-system-works)  
- [Forking & Cloning](#forking--cloning)  
- [Local Building (Maven + JitPack-Compatible)](#local-building-maven--jitpack-compatible)  
- [Running the Plugin for Testing](#running-the-plugin-for-testing)  
- [Submitting Pull Requests](#submitting-pull-requests)  
- [Reporting Issues](#reporting-issues)  
- [Coding Style & Standards](#coding-style--standards)  
- [Commit Message Standards](#commit-message-standards)  
- [Code of Conduct](#code-of-conduct)  
- [License](#license)  

---

## 🛠️ Development Requirements

Before contributing, make sure you have:

- **Java 21 (required)**  
- **Maven 3.8+**  
- A GitHub account  
- Basic understanding of Bukkit/Paper plugin development  
- (Optional) A test server running Paper/Folia 1.20+  

---

## 🧬 How the Build System Works

AegisGuard does **not** use Gradle or local shading scripts.  
It uses:

- **Maven** for building & dependency management  
- **JitPack / JitCI** for automated builds and release artifacts  
- **GitHub Releases + Hangar + Spigot** to publish builds  

### Why this matters  
When you submit a PR, **JitPack** automatically tries to compile the plugin using your changes.  
If Maven builds correctly, the PR will pass.  
If not, you’ll see errors in JitPack like:

- Missing dependencies  
- Incorrect imports  
- API incompatibilities  

Your local build must match the CI pipeline.

---

## 🔧 Forking & Cloning

The correct workflow is:

1. **Fork the repository**  
   https://github.com/snazzyatoms/AegisGuard

2. Clone your fork:
   ```bash
   git clone https://github.com/<your-username>/AegisGuard.git
   cd AegisGuard
