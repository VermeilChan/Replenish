# Replenish 🌾

Tiny, blazing-fast auto-replant plugin for Spigot/Paper, inspired by Hypixel's Replenish enchantment.

![Preview](assets/output.webp)

---

## What it does

- 🌾 Auto-replants Wheat, Carrots, Potatoes, Nether Wart, Cocoa, Beetroots
- 🌱 Immature crops keep their current growth stage
- 🎒 Optional seed consumption (on by default)
- 📦 Optional direct pickup into your inventory (on by default)
- 🪓 Requires the correct tool (Hoes / Axes)
- 🧭 Correctly replants Cocoa with the proper facing
- 🍀 Fortune enchantments works normally
- ⚡ Extremely lightweight and designed for large farms

---

## Quick install

1. Drop the JAR into `plugins/`
2. Start the server
3. Edit `plugins/Replenish/config.yml` if desired
4. Done.

---

## Default behavior (no config edits)

* Breaking a **mature** crop:

    * Replants at age **0**
    * Consumes **1 seed** from your inventory (or off-hand) if `requirePlayerSeed` is `true`
    * Drops go straight to you if `directPickup` is `true`

* Breaking an **immature** crop:
    * Replants at the **same age** (no seed needed)

---

## Commands & permissions

| Command              | Description               |
|----------------------|---------------------------|
| `/replenish status`  | Show current settings     |
| `/replenish version` | Show plugin version       |
| `/replenish toggle`  | Enable/disable the plugin |
| `/replenish reload`  | Reload the configuration  |

Permissions:

* `replenish.status` (default: **true**)
* `replenish.version` (default: **true**)
* `replenish.use` (default: **op**)
* `replenish.toggle` (default: **op**)
* `replenish.reload` (default: **op**)
* `replenish.*` (default: **op**)

---

## Config

```yaml
enabled: true
requirePlayerSeed: true
directPickup: true
replantDelayTicks: 1
maxReplantsPerTick: 1024
checkUpdates: true
crops:
  wheat: true
  carrots: true
  potatoes: true
  nether_wart: true
  cocoa: true
  beetroots: true
```

## 💡 Contributions & Pull Requests

Managing direct PRs gets a bit overwhelming for me, so I keep them disabled for this project to protect my peace. But I'm open to discussion! If you have a features, optimizations, bug fixes, improvements etc., open an issue first. We can chat about it there and see if it fits the project.
Otherwise, you are highly encouraged to fork or clone this repo and do whatever you want with it. Vibe-code, rip it apart, and build what you want not what other people expect you to build.
Everyone starts somewhere, and you are goated. You matter, so go start now.

## License
This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
