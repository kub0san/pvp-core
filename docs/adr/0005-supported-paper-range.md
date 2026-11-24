# 0005 — Supported Paper range

- **Status:** accepted / ratified (2026-11-15)
- **Related:** remaining-work plan §10 D5; `AGENTS.md`; `build.gradle.kts`; `plugin.yml`

## Context

This is already settled in the project docs; the remaining-work plan listed it as
an ADR candidate so the spec can reference it rather than re-argue it.

- `AGENTS.md`: Paper **1.21.11 through 26.3.x**; not Spigot, not Folia.
- `build.gradle.kts`: `compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")`,
  `options.release = 21`, newer APIs reached through reflection.
- `plugin.yml`: `api-version: '1.21.11'`, with a comment: do not bump it when only
  the build target changes.
- `org.pvpcore.util.VersionSupport` reports and compares the running version.

## Decision

Minimum supported Paper stays **1.21.11**. Compile against `paper-api:1.21.11`,
emit Java 21 bytecode, reach newer Paper APIs only through `VersionSupport`
reflection. Do not bump `api-version` for build-target-only changes.

## Consequences

- The compat smoke tasks (`runServer12111`, `runServer262`, isolated `run/` dirs)
  remain the verification for the range.
- No behavior change; this ADR is a ratification, not a new constraint.
