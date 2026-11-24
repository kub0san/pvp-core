# Kit Cosmetics GUI

Cosmetic-only GUI for a player's own saved personal kit: armour trims, fully layered shield banners, and validated MiniMessage item names.

## Config

New `customize:` section in `kits/config.yml`:

- `enabled` (default true) — hides the button, blocks entry, rejects Apply when false.
- `max-layers` (default 6, clamped 1..16)
- `allow-click-events` (default false)
- `allow-hover-events` (default false)
- `max-name-length` (default 256, clamped 1..1024)
- `allowed-trim-patterns`, `allowed-trim-materials`, `allowed-banner-patterns` (empty = all)

## Permissions

`perplayerkit.kit` + `LocationFeature.KITS`, personal kit editor only. Guards rechecked at Apply.

## Staging

Choices are staged with explicit Apply. Back discards; Reset clears; rename cancel/timeout changes nothing. Names are formatting-only by default (colours, decorations, gradients); click/hover tags rejected unless enabled.

## Banner fail-closed

Unknown/unavailable banner layers lock banner editing for that shield; trim/rename still work. No silent strip.

## Async save

Apply returns QUEUED (not saved). Write completes after close/quit; only feedback/session cleanup discarded. Reload invalidates open sessions.