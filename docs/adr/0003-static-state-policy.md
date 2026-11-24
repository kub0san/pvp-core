# 0003 — Static state policy

- **Status:** accepted (2026-11-15)
- **Related:** remaining-work plan §4.2, §10 D3

## Context

Measured static-shim hit counts in `src/main/java` (plan §9):

| Shim | Hits |
| --- | ---: |
| `Lang.get()` | 182 |
| `KitManager.get()` | 67 |
| `Kits.config()` | 51 |
| `KitSlots.*` | 38 |
| `KitRoomDataManager.get()` | 31 |
| `Kits.isEnabled()` | 18 |
| `BroadcastManager.get()` | 18 |
| `ItemFilter.get()` | 15 |
| `Kits.storageManager` (public field) | 14 |
| `KitShareManager.get()` | 11 |
| `StyleManager.get()` | 7 |
| `LocationAccess.get()` | 7 |

These shims are the only way 400+ call sites reach kits state. Deleting them
before the call sites move is what makes the refactor dangerous without tests.

## Decision

- **Zero** static compat accessors in `gui/**`, `commands/**`, `listeners/**`.
- Pure util helpers stay static.
- `Lang` may survive as a kits-scoped provider if its defaults/migration logic is
  not fully covered.
- Every survivor carries a one-line "why".
- Sequencing is **expand → migrate in batches → contract** (T10–T14).

## Consequences

- Expand makes the migration mechanical; contract makes deletion mechanical.
- T14 has a measurable finish line: every shim at zero hits or documented.
- `KitManager.get()` semantics must not change for external-plugin compatibility
  without checking `plugin.yml` and the docs for a published surface (there is
  none today — see ADR 0008).
