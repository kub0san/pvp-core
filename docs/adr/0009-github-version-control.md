# 0009 — GitHub version control

- **Status:** accepted (2026-11-24)
- **Supersedes:** [0001-version-control-and-fixed-points.md](./0001-version-control-and-fixed-points.md)

## Context

ADR 0001 recorded a decision to use no version control, with immutable directory snapshots as fixed points. That decision no longer holds: the project is being published as a public repository, `kub0san/pvp-core`, using `shenanigans-core` as the reference for repository conventions.

## Decision

- Use Git for version control, with `master` as the initial branch (matching `shenanigans-core`).
- The public history starts from a single `chore: initial import` commit rebuilt from the current tree; the earlier local commit history is preserved in an out-of-repo backup only.
- Subsequent changes use atomic conventional commits (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`).
- Local-only agent metadata, JVM crash dumps and tooling data are excluded via `.gitignore`. Architectural records under `docs/adr` are published; agent scratch under `docs/agents` and `docs/superpowers` is not.
- Pushing is an explicit, deliberate step — a push publishes immediately.

## Consequences

- Snapshots under `.snapshots/` are no longer the fixed point for review; commits and tags take that role.
- Git history, `git diff` and blame are available for review.
- The repository is public: anything committed is visible to anyone, so local paths, credentials and machine-specific configuration must stay out of the tracked tree.
- The old ADR 0001 remains in the tree as historical context but must not be followed.
