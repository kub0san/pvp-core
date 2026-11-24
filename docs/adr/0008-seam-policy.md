# 0008 — Seam policy

- **Status:** accepted (2026-11-15)
- **Related:** remaining-work plan §3.1; `plugin.yml`; `AGENTS.md` (kits section)

## Context

Plan §3.1 requires the spec to name its seams at the highest point. Facts checked:

- `plugin.yml` publishes **commands and permissions only** — no Java API.
- `AGENTS.md` records that `org.pvpcore.features.kits.API` was **removed**: there
  is no public kits Java API.
- Therefore the real external compatibility contract is legacy top-level commands
  (`/kit`, `/ec`, `/regear`, `/tpa`, …) plus `perplayerkit.*` permissions.

Candidate seams from §3.1: `KitManager` public surface, `KitsContext`, `Messages`
façade (does not exist), `MechanicStatus` (does not exist), `StorageWorkQueue`,
`GuiScreen.afterInventory`, individual mechanic classes.

## Decision

**One stable seam:** `KitManager` remains the only publicly referenced kits type;
`KitsContext` is the wiring seam behind it.

- `Messages`, `MechanicStatus`, `KitCache`, `KitRecordStore`, `PlayerKitSession`
  and `KitLoader` are seams **created by** T40 / T50 / T20–T23. They are **not**
  exposed through `KitsContext`.
- `GuiScreen.afterInventory` and the individual mechanic classes are **not** seams.
- Compatibility is judged by **commands and permissions**, not Java signatures.

## Consequences

- Any ticket introducing a type must keep it out of `KitsContext` unless the spec
  says otherwise.
- The review's spec axis checks this: a new public accessor on `KitsContext` is a
  finding.
- `StorageWorkQueue` remains the concurrency boundary for persistence but is not a
  broad seam.
