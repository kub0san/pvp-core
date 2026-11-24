# 0004 — Deferred domains

- **Status:** accepted (2026-11-15)
- **Related:** remaining-work plan §4.9, §10 D4

## Context

Two areas are the densest behavior in the repo and have zero coverage:

- **Combat** — `CombatManager`, `AnchorAttributionTracker`, `CombatSession`, the
  resolver chain, `CombatPenaltyService`, `SafeZoneListener`.
- **Item rules** — `ItemFilter`, `ItemPurger`, `ItemPurgeService`.

Both are server-thread sensitive (combat) or recursively subtle (item filtering
through shulkers and bundles). A behavior-preserving refactor here can silently
stop being behavior-preserving with nothing to catch it.

## Decision

**Both are out of scope for this flow.** No ticket in §4 may change their
semantics. They are revisited only after a harness exists — which ADR 0002 declined,
so realistically not before T14 closes.

Item-rules unification is *naturally* next after T14 (most `ItemFilter.get()` hits
vanish there) but is explicitly not part of this flow.

## Consequences

- Plan §4.9 is binding, not a soft preference.
- The smoke rows for combat and regear remain verification-only; a failure there
  is a new ticket, never an in-scope edit.
