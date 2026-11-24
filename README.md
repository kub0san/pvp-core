# pvp-core

An all-in-one Paper PvP plugin: competitive mechanics, kits, teleportation, combat-state tracking, and optional PvP world generation.

pvp-core bundles the mechanics that used to live in separate plugins into a single jar. Features are organised into modules with an ordered lifecycle, and each one can be toggled from `/pvpcore list`.

## Features

- **Kits** — per-player kits and ender chests, public/premade kits, kit rooms, sharing, transfer, regear, heal and repair.
- **Teleportation** — `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpcancel`, `/tphere`, `/back`, `/spawn`, `/setspawn` and `/rtp`, with region settings and a management GUI.
- **Combat Tag** — API-only combat-state tracking with optional logout penalty, smart triggers and respawn-anchor attribution.
- **PvP mechanics** — Faster Crystals, Shield Fixes, Consistent IPC, Knockback Sync, Anti-Totem-Ghost, and legacy projectile/ender-pearl behaviour.
- **World generation** — optional PvP world generator with biome and terrain profiles under the `pvp-core:*` namespace.

Optional dependencies fail closed: if PacketEvents, WorldGuard, Multiverse-Core or KnockbackSync are missing, the integrations that need them report as disabled and never behave as active.

## Requirements

- **Java 21+** at runtime (built with JDK 25, emits Java 21 bytecode)
- **Paper 1.21.11 – 26.3** (target server: Leaf 26.3-27, Paper API 26.3.build.27-alpha; Java 21 bytecode, Java 25 build toolchain)
- Optional: PacketEvents 2.14.0+, WorldGuard, Multiverse-Core, KnockbackSync

## Testing

- Shared test server: `C:\Tools\global-test-server-26.3` (Leaf 26.3-27, Java 25, game port 25566). Canonical guide: `C:\Projects\GLOBAL_TEST_SERVER.md`.
- From that folder: `.\TestServer.ps1 setup`, `deploy pvp-core`, `restart`, `verify`, `snapshot`, `reset`, `stop`. `.\TestServer.ps1 -Help` prints the full command table and MCP map.
- Player data is scrubbed except qcart (`befc5d95-fd63-4cae-b0f5-7c2fb271c699`). Craftwire is the primary E2E tool; a green handshake is not a pass.

## Building

```powershell
.\gradlew.bat clean shadowJar
```

The shaded jar is written to `build/libs/pvp-core-1.0-SNAPSHOT.jar`. Drop it into your server's `plugins/` folder.

Run verification with:

```powershell
.\gradlew.bat check
```

`check` runs the UTF-8 source-encoding guard, compiles the sources and runs the JUnit 5 unit tests.

## Configuration

On first run the plugin generates its config files in `plugins/pvp-core/`:

| File | Purpose |
|------|---------|
| `config.yml` | Main settings |
| `messages.yml` | MiniMessage message strings |
| `combat/config.yml` | Combat Tag settings |
| `kits/config.yml` | Kits and kit-room settings |
| `teleport/config.yml` | Teleport requests and warmups |
| `teleport/rtp.yml` | RTP regions and bounds |
| `rtp-gui.yml` | RTP management GUI |
| `worldgen/config.yml` | World generation profile |

Configs are kept in sync with bundled defaults on enable; `/pvpcore reload` re-reads them. A legacy `plugins/PvPCore` folder is migrated to `plugins/pvp-core` on startup when the new folder does not exist.

## Commands

- `/pvpcore` (aliases `/pc`, `/pvp-core`) — `help | list | toggle | enable | disable | reload`
- `/kit` and the `/k1`–`/k9`, `/ec1`–`/ec9` shortcuts — kits and ender chests
- `/regear`, `/heal`, `/repair` — kit utilities
- `/tpa`, `/tpahere`, `/rtp`, `/back`, `/spawn` — teleportation
- See `plugin.yml` for the full command tree and permissions. Legacy `perplayerkit.*` permissions are retained for compatibility.

## Project layout

- `org.pvpcore.module` — feature-module contract and dependency-derived lifecycle ordering
- `org.pvpcore.features.{kits,teleport,worldgen,combat}` — feature modules
- `org.pvpcore.mechanic` — toggleable PvP mechanics with fail-closed requirement checks
- `org.pvpcore.commands` — CommandAPI registration and command implementations
- `org.pvpcore.gui` — GUI foundation and kit/RTP screens
- `org.pvpcore.config` — config and message loading
- `org.pvpcore.storage` — SQLite-backed kit and teleport storage
- `docs/adr` — architectural decision records

## Changelog

See [CHANGELOGS.md](CHANGELOGS.md).

## Credits and license

pvp-core is licensed under the GNU General Public License v3.0 — see [LICENSE](LICENSE).
Attribution for upstream projects and adapted mechanics is in [CREDITS.md](CREDITS.md).
