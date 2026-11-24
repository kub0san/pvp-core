# 0007 — Context hygiene

- **Status:** accepted (2026-11-15)
- **Related:** remaining-work plan §10 D7, §12

## Context

The plan flags this as *"the one most likely to be violated."* Mid-ticket
compaction is how a tracer bullet silently becomes a horizontal phase again: the
agent loses the ticket boundary and starts "while I'm here" work.

## Decision

- **One ticket per context window.** No batching.
- `/compact` only at phase boundaries, never mid-ticket.
- If two tickets touch the same file, they are sequenced by a blocking edge, not
  merged.
- If remaining work does not fit the window, split it into a subagent or a new
  ticket — do not widen the current ticket.

Phase boundaries where a fresh window is expected: into grilling, tickets →
implementation, implementation → review, review → findings, end of flow → retro.

## Consequences

- Ticket scope stays reviewable by a fresh context.
- The review axis can attack scope creep because the boundary is explicit.
- Slower than batching, which is the point.
