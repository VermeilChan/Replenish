# Replenish 🌾

Tiny, blazing-fast auto-replant QoL plugin for Spigot/Paper, inspired by Hypixel's Replenish mechanic.

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
- ⚡ Designed for high-performance and large farms
- 🔊 Fully configurable sounds

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

## Configuration

### Common settings

```yaml
enabled: true
requirePlayerSeed: true
directPickup: true
replantDelayTicks: 1
maxReplantsPerTick: 1024
checkUpdates: true
```

<details>
<summary>Full default config.yml</summary>

```yaml
# ============================================
#         REPLENISH CONFIG
# ============================================

# --------------------------
# GENERAL SETTINGS
# --------------------------

config-version: 5              # DON'T TOUCH THIS!!!

enabled: true                  # master switch: false disables all replanting globally
requirePlayerSeed: true        # consume 1 seed from inventory on mature harvest
directPickup: true             # drops go straight to the player
replantDelayTicks: 1           # 1 tick = 50ms

maxReplantsPerTick: 1024      # maximum crops replanted per tick (20 ticks/second)
        # raise this on beefier servers if replants lag behind
# lower it on weaker servers to reduce load

checkUpdates: true             # check GitHub for new releases on server startup

# --------------------------
# CROP TOGGLES (turn on/off)
# --------------------------

crops:
  wheat: true
  carrots: true
  potatoes: true
  nether_wart: true
  cocoa: true
  beetroots: true

# --------------------------
# CHAT MESSAGES (customize these)
# --------------------------
# Placeholders: {crop}, {tool}, {seed}, {count}

messages:
  inventory-full: "&8[&eReplenish&8] &8» &7Your inventory was full, so some items dropped on the ground instead."
  requires-tool: "&8[&eReplenish&8] &8» &7You need a &e{tool} &7to harvest &e{crop}&7."
  need-seed: "&8[&eReplenish&8] &8» &7You need &e{count}x {seed} &7in your inventory to replant this."

# --------------------------
# SOUND EFFECTS (customize or disable)
# --------------------------
# Each of the four feedback sounds can be individually enabled/disabled
# and customized with any Bukkit Sound, volume, and pitch.
#
# sound:  Must match a Bukkit Sound enum name (case-insensitive).
#         Full list: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html
#         Invalid names log a warning and fall back to the default.
# volume: 0.0 (silent) to 1.0 (loudest)
# pitch:  0.5 (low) to 2.0 (high); 1.0 = normal
#
# To disable a sound entirely, set enabled: false.

sounds:
  # Played when harvested crops go into your inventory
  pickup:
    enabled: true
    sound: ENTITY_ITEM_PICKUP
    volume: 1.0
    pitch: 1.0

  # Played when your inventory is full and items drop on the ground
  inventory-full:
    enabled: true
    sound: BLOCK_NOTE_BLOCK_BASS
    volume: 1.0
    pitch: 0.5

  # Played when you try to harvest without the required tool
  denied-tool:
    enabled: true
    sound: ENTITY_VILLAGER_NO
    volume: 1.0
    pitch: 0.5

  # Played when you try to harvest but lack a seed to replant
  denied-seed:
    enabled: true
    sound: ENTITY_VILLAGER_NO
    volume: 1.0
    pitch: 0.5
```

</details>

## 💡 Contributions & Pull Requests

Managing direct PRs gets a bit overwhelming for me, so I keep them disabled for this project to protect my peace. But I'm open to discussion! If you have a features, optimizations, bug fixes, improvements etc., open an issue first. We can chat about it there and see if it fits the project.
Otherwise, you are highly encouraged to fork or clone this repo and do whatever you want with it. Vibe-code, rip it apart, and build what you want not what other people expect you to build.
Everyone starts somewhere, and you are goated. You matter, so go start now.

## License
This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
