# 0006 — Issue tracker convention

- **Status:** accepted (2026-11-15)
- **Related:** remaining-work plan §1 P2 / P2a, §10 D6, §18

## Context

The plan's `/to-spec`, `/to-tickets`, `/implement` and `/code-review` all read the
same tracker convention. P2 asked for `/setup-matt-pocock-skills` or an explicit
local fallback.

Facts: no version control, no remote, no CI, solo project. The Claude Code skills
the plan assumes are not available in OpenCode. A hosted tracker would need an
account, network and upkeep.

## Decision

**Local file tracker.**

- Tickets: `.scratch/<feature-slug>/issues/NN-slug.md`
  (e.g. `.scratch/kits-static-retirement/issues/T10-expand.md`).
- Flow tickets T00–T03: `.scratch/flow/issues/`.
- Blocking edges are **prose inside each file**, not links.
- Spec: `.scratch/spec.md`.
- `GLOSSARY.md` at the repo root; ADRs in `docs/adr/`.
- `.scratch/` is excluded from snapshots (ADR 0001).

Feature slugs: `kits-static-retirement`, `kit-manager-decomposition`,
`module-lifecycle-host`, `message-facade`, `mechanic-status`,
`gui-settings-session`, `release-readiness`.

## Consequences

- No cross-referencing, no automation, no queryability beyond grep.
- About 30 small files; edges must be read, not followed.
- Review diffs stay clean because `.scratch/` is not in snapshots.
- If a real tracker is ever adopted, the files migrate; the ticket bodies are
  plain Markdown.
