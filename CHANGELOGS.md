# Changelogs

This file tracks notable changes to **pvp-core** (formerly PvPCore).

## 2026-11-24 (repository setup)

### Added
- Published the project as `kub0san/pvp-core` on GitHub from a clean initial commit.
- Added `README.md`, `CHANGELOGS.md` and the GPL-3.0 `LICENSE` to the repository root.
- Added ADR 0009 superseding ADR 0001's "No git" decision.

### Changed
- Local-only agent files, JVM crash dumps and tooling data are now excluded via `.gitignore`.

## 2026-11-12 (Combat Tag)

### Added
- **Combat Tag** (`org.pvpcore.features.combat`): modular, API-only combat-state tracking.
  - Master switch `enabled: true` in `combat/config.yml`; disabling it turns off tagging and anchor tracking without unloading the module.
  - Logout penalty (`penalty.mode`): `none` (default), `drop` or `kill`.
  - Smart triggers for explosions, ender-pearl landings and proximity, plus WorldGuard safe-zone entry blocking.
  - `CombatManager` owns live sessions and attributes indirect damage to the responsible player where reliable.

### Changed
- `logout-penalty-enabled` was replaced by `penalty.mode`; the old key is ignored.
- `penalty.mode` defaults to `none` so upgrading servers do not silently start punishing combat logs.
- `CombatConfig` is nanos-first, and every `CombatManager`/`AnchorAttributionTracker` call is server-thread checked.

### Fixed
- Numeric config overflow no longer produces never-sticking combat tags; out-of-range values fall back to defaults with a warning.
- Non-number YAML values for numeric keys now warn and fall back instead of being silently coerced.
