# Credits

PvPCore is built on the work of these projects and people. Thank you.

## PvPCore

- Project: pvp-core
- License: GNU General Public License v3.0 (see `LICENSE`)

## CombatLog

- Author: NikeyV1
- Source: https://github.com/NikeyV1/CombatLog
- License: GNU General Public License v3.0 or later
- Used for: The logout penalty policies (drop / kill), the smart triggers (any-explosion tagging,
  ender-pearl landing tagging, proximity "combat zone" tagging) and the WorldGuard safe-zone
  entry blocking, all adapted into PvPCore's Combat Tag feature with the CombatLog copyright
  notice retained in the adapted files. PvPCore replaces the tagging model, the scheduler-per-timer
  design, the custom `allow-combat-entry` flag (PvPCore uses the built-in `pvp` deny flag instead),
  and the restriction listeners (elytra, riptide, mending, command blocking), which are not
  reproduced. This repository keeps a copy of the reference source under `CombatLog-ref/` for
  comparison; it is not part of the shipped plugin.

CombatLog is free software: you can redistribute it and/or modify it under the terms of the GNU
General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. It is distributed in the hope that it will be
useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details:
https://www.gnu.org/licenses/.

## FasterCrystals

- Author: Jyguy
- Source: https://github.com/mcpvp-club/FasterCrystals
- License: GNU General Public License v3.0
- Copyright (C) 2023-2026 Jyguy
- Used for: The Faster Crystals mechanic. Its packet-listener and placement logic is adapted
  for PvPCore. PvPCore adds a Minecraft 26.3 `PUNCH` path and keeps placement protection
  events that are not present upstream.

FasterCrystals is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software Foundation,
either version 3 of the License, or (at your option) any later version. It is distributed
in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
Public License for more details: https://www.gnu.org/licenses/.

## InstantPearlCatch

- Author: shardmods
- Source: https://github.com/shardmods/InstantPearlCatch
- License: MIT
- Used for: Behavioural reference for the Consistent IPC mechanic. PvPCore independently
  implements the tick-order bypass and early-hit prevention using the Bukkit API and
  PacketEvents, replacing the original mod's NMS Mixins. No InstantPearlCatch source code
  is copied into PvPCore.

## ShieldFixes

- Project: ShieldFixes
- Author: boggymc
- Source: https://modrinth.com/plugin/shieldfixes-server
- License: GNU General Public License v3.0
- Used for: Behavioural reference for the Shield Fixes mechanic (MC-105068 and MC-238293).
  PvPCore independently implements the Paper 26.3 packet handling; no ShieldFixes source code
  is copied into PvPCore.

## Shield-Sync

- Idea: Shield-Sync by pheological (concept credit: xSweetJapan)
- Source: https://github.com/pheological/Shield-Sync
- License: None declared; the upstream repository has no LICENSE file, so its code is treated
  as all rights reserved.
- Used for: Concept and behavioural reference for the Shield Sync mechanic only. No Shield-Sync
  source code is copied or adapted into PvPCore. PvPCore's implementation is a clean-room
  version based on the described behaviour, and intentionally fixes the reference's packet
  cancellation, delay-reset, off-hand-only, outlier-filter, keep-alive, custom-sound and
  PacketEvents-lifecycle problems.

## PASF (paper-attribute-swap-fix)

- Author: SalmonBlock
- Source: https://github.com/SalmonBlock/paper-attribute-swap-fix
- License: GNU Lesser General Public License v3.0 only (LGPL-3.0-only)
- Used for: Behavioural reference for the Attribute Swapping mechanic. No PASF source
  code is copied into PvPCore; the implementation is independent.

## Minecraft (Mojang)

- Author: Mojang Studios
- Source: The official Minecraft 1.21.5 and 1.21.6 server jars and Mojang's published
  obfuscation mappings.
- Used for: Behavioural reference for the Legacy Projectiles, Legacy Ender Pearls, and
  Consistent IPC mechanics. The upstream changes (the projectile target tolerance margin,
  the 26.2 Ender Pearl momentum reset, and the projectile collision logic) were verified
  against the official server jars, and the mechanics reproduce the pre-1.21.6 and pre-26.2
  behaviours. No Mojang code is copied, adapted or redistributed in PvPCore; the Minecraft
  jar and the mappings are only used offline to study the behaviour.