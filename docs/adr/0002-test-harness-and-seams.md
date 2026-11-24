# 0002 — Test harness and seams

- **Status:** accepted (2026-11-15)
- **Related:** remaining-work plan §3.1, §3.2, §10 D2

## Context

The plan proposed *"not before T20, then JUnit 5 + Paper mocks at the
`KitManager`/`KitsContext` seam only"*, with `/tdd` applied to T20–T24 and T40.

Facts checked on the tree:

- `build.gradle.kts` has **zero** test dependencies; `test` is `NO-SOURCE`.
- `AGENTS.md` states: *"No tests. Do not invent a test harness; verification is
  `./gradlew check` plus manual server runs."*
- The highest-risk work in the flow — T10–T14, 400+ static call sites across four
  layers — happens **before** T20, so a harness landing at T20 protects only the tail.
- Paper mocks for a plugin this size (adventure, inventory GUIs, packet listeners)
  are incomplete and can produce false confidence.

## Decision

**No test harness in this flow.** Verification is:

1. `./gradlew.bat check` while iterating, `./gradlew.bat check shadowJar` before a
   ticket is called done;
2. the per-ticket smoke rows from plan §8;
3. the two-axis review per ticket.

If a harness is wanted later it becomes **its own ticket after T14**, scoped to
`KitCache` and `KitRecordStore` only, and `AGENTS.md`'s "no tests" line is updated
in that same ticket.

## Consequences

- T10–T14 (the largest risk in the flow) ships with no unit coverage; the review
  gate and the runtime smoke matrix carry that risk.
- `AGENTS.md` stays accurate as written.
- No new dependencies, no network fetches, no `src/test` tree.
