# Contributing to AegisGuard 🛡️

Thank you for your interest in contributing to AegisGuard!  
This document explains how to develop, build, test, and submit changes using AegisGuard’s **Maven + JitPack/JitCI** pipeline.

---

## 📦 Development Requirements

Before you begin, ensure you have:

- **Java 17 (required)**  
- **Maven 3.8+**  
- Git installed  
- A Paper/Folia test server (recommended)  
- Basic understanding of Bukkit/Paper development  

---

## 🧬 How the Build System Works

AegisGuard uses:

- **Maven** for project builds  
- **JitPack / JitCI** for automated CI builds  
- **GitHub Releases, Hangar, Spigot** for distributing releases  

When you submit a pull request, JitPack will:

1. Compile the project using Maven  
2. Resolve dependencies  
3. Run shading/relocation  
4. Verify that everything builds cleanly  

If your build succeeds locally using the same Maven commands,  
**your PR will pass CI as well.**

---

# 🔧 Getting Started

## 1. Fork the Repository

Go to:  
https://github.com/snazzyatoms/AegisGuard

Click **Fork** in the top-right.

---

## 2. Clone Your Fork

```bash
git clone https://github.com/<your-username>/AegisGuard.git
cd AegisGuard

---
3. Create a Development Branch

```bash
git checkout -b feature/my-feature
