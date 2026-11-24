# 0001 — Version control and fixed points

- **Status:** superseded by [0009-github-version-control.md](./0009-github-version-control.md) (2026-11-24). Kept for historical context; the repository is now version-controlled with Git and published on GitHub.
- **Related:** remaining-work plan §1 P1, §17

## Context

The remaining-work plan makes version control a hard precondition: `/code-review`
diffs `HEAD` against a fixed point, and `/implement` ends by committing. The plan's
P1 asked for `git init`, an initial commit, and a tag (e.g. `arch-pass-1`).

Two facts blocked that. There is no git identity configured at any level (system,
global, repo), and the user declined version control for this project outright:
*"i haven't actually set it up for my project and i don't plan on for a long time."*

## Decision

**No git.** Fixed points are directory snapshots instead of commits.

- `.snapshots/<slug>/` — an exact copy of the tree at that moment.
- `.snapshots/<slug>/MANIFEST.sha256` — `"<sha256>  <relative path>"` per file.
- `.snapshots/<slug>/SNAPSHOT.md` — why/when, exclusions, build result.
- `.snapshots/arch-pass-1/` is the baseline fixed point for the whole flow: 283
  files, `./gradlew.bat clean check shadowJar` → BUILD SUCCESSFUL.
- Exclusions: `.git/`, `.gradle/`, `build/`, `out/`, `.idea/`, `*.iml`, `run/`,
  `CombatLog-ref/`, `.snapshots/`, `.scratch/`.

`/code-review` diffs the working tree against the snapshot named on the ticket;
`/implement` takes a `<ticket>-before` snapshot at ticket start.

## Consequences

- No `git diff`, blame, or history. Review is a manifest comparison plus reading
  changed files.
- Snapshots are immutable; a snapshot is never edited to absorb drift.
- `.scratch/` is excluded so review diffs do not carry other tickets' files.
- An empty `.git/` left over from the attempt may remain on disk; a safety hook
  (`powershell.remove-item-git-metadata`) blocks its removal.
