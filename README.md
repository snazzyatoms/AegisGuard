# 🛡️ AegisGuard

### The Next Generation of Land Protection & Economy

<img width="1024" height="1536" alt="image" src="https://github.com/user-attachments/assets/6a07c316-e172-45d8-8ca8-9c43a90f7241" />


<br>

[](https://www.spigotmc.org/)
[](https://papermc.io/software/folia)
[](https://www.google.com/search?q=LICENSE)

\</div\>

-----

> *"Hearken, traveler of realms. AegisGuard is a light and noble plugin, forged to shield thy lands from peril and strife. With the Sacred Scepter of Aegis, thou mayest claim, shape, and safeguard thy plots with divine precision."*

-----

## ⚡ Why AegisGuard?

AegisGuard is not just another protection plugin. It is a complete **Land Economy Ecosystem** built from the ground up for performance. Whether you are running a classic Survival server or a massive multi-threaded Network, AegisGuard adapts.

### 🚀 Core Features

  * **Folia Native:** Built with robust reflection to detect **Folia** environments automatically. It utilizes the `GlobalRegionScheduler` for true multi-threaded safety without compromising features.
  * **Hybrid Storage:** Supports **MySQL/SQLite** (via HikariCP) for networks and flat-file **YAML** for simple setups.
  * **Async-First Design:** Heavy tasks like Dynmap rendering, Upkeep calculations, and Auto-saving happen off the main thread. Zero lag.

### 💰 The Land Economy

Turn land into a valuable asset.

  * **Upkeep System:** Owners must pay tax. If they fail, the plot expires.
  * **Auction House:** Expired plots don't just vanish—they go to auction\! Players can bid on abandoned bases.
  * **Global Marketplace:** Players can list their plots for sale or rent via a GUI.
  * **Expansion Requests:** Players can request radius increases via a menu. Admins approve/deny them with a single click.

### 🎨 Visuals & GUI

  * **The Ultimate GUI:** Manage Flags, Roles, and Settings via a clean, icon-based interface (`/ag menu`).
  * **Cosmetics:** Players can buy particle borders (Hearts, Flames, etc.) to show off their land.
  * **Dynmap Integration:** Real-time, asynchronous map rendering of all claims.
  * **Language Styles:** Players can switch between **"Modern English"** and **"Roleplay Style"** messages personally\!

-----

## 📜 Commands

### Player Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/ag wand` | `aegis.claim` | Receive the Aegis Scepter (Claiming Tool). |
| `/ag claim` | `aegis.claim` | Confirm your selection to claim land. |
| `/ag menu` | `aegis.use` | Open the main management GUI. |
| `/ag resize <dir> <amt>` | `aegis.resize` | Expand or shrink your plot boundaries. |
| `/ag sell <price>` | `aegis.market` | List your current plot on the marketplace. |
| `/ag setspawn` | `aegis.home` | Set the teleport point for your plot. |
| `/ag home` | `aegis.home` | Teleport to your plot's spawn. |

### Admin Commands

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/agadmin reload` | `aegis.admin` | Reload config, messages, and storage. |
| `/agadmin menu` | `aegis.admin` | Open the Admin Control Panel. |
| `/agadmin bypass` | `aegis.admin` | Toggle admin bypass mode (break anywhere). |

-----

## ❓ Frequently Asked Questions (FAQ)

\<details\>
\<summary\>\<b\>Is this compatible with Paper/Spigot?\</b\>\</summary\>
Yes\! AegisGuard works on all modern versions of Spigot, Paper, Purpur, and Pufferfish. It detects your server software automatically.
\</details\>

\<details\>
\<summary\>\<b\>Does it work on Folia?\</b\>\</summary\>
Yes\! AegisGuard is one of the few protection plugins natively designed for Folia's threaded region system.
\</details\>

\<details\>
\<summary\>\<b\>Can I disable the Economy features?\</b\>\</summary\>
Absolutely. If you don't use Vault, AegisGuard automatically switches to "Free Mode." You can also toggle specific features like Upkeep or Auctions in the `config.yml`.
\</details\>

\<details\>
\<summary\>\<b\>Where do I get support?\</b\>\</summary\>
If the spirits of the machine are acting up, please open an Issue on GitHub or join our Discord community for support.
\</details\>

-----

## 🔧 Installation

1.  **Download** the `AegisGuard.jar`.
2.  **Drop** it into your `/plugins` folder.
3.  *(Optional)* Install **Vault** and an Economy plugin (EssentialsX, EcoBits) for economy features.
4.  *(Optional)* Install **Dynmap** for web map integration.
5.  **Restart** your server. The spirits will handle the rest.

-----

\<div align="center"\>
\<b\>Made with ❤️ by Aegis Divine\</b\><br>
\<i\>Simple. Steadfast. Eternal.\</i\>
\</div\>
