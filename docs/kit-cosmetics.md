# Kit Cosmetics GUI

Cosmetic-only editing of a player's own saved personal kit. The editor is **item-centric**: an item is selected in the kit editor, then a compact one-row hub offers Rename, Enchant, Trim (armor only), Apply and Cancel. Shield banner customization was removed; see [Shield banners](#shield-banners-removed).

## Config

`customize:` section in `kits/config.yml`:

- `enabled` (default true) — hides the hub entry, blocks direct entry and rejects Apply when false.
- `allow-click-events` (default false) — allow `<click:...>` tags in item names.
- `allow-hover-events` (default false) — allow `<hover:...>` tags in item names.
- `max-name-length` (default 256, clamped 1..1024).
- `allowed-trim-patterns`, `allowed-trim-materials` (empty = all).

Legacy `customize.max-layers` and `customize.allowed-banner-patterns` are no longer read. They are removed from existing configs automatically by `ConfigUpdater` (registered in the kits `ConfigSpec.removedKeys`); no manual cleanup is needed.

## Permissions

`perplayerkit.kit` + `LocationFeature.KITS`, personal kit editor only. Guards are rechecked at Apply. The item filter must be ready before entry.

## Item hub

- **Rename** — chat prompt accepting MiniMessage; formatting-only by default, with click/hover tags rejected unless enabled in config. `cancel` or timeout changes nothing.
- **Enchant** — paged catalog of registered enchantments. Selecting an entry cycles its level; right-click removes it. The resulting item is validated by `ItemFilter` before staging; unrelated enchantments are preserved. With the shipped default `item-filter.enabled: false`, validation is a no-op: over-levelled, conflicting and unsupported enchantments are accepted and the browser's compatibility badge is informational only. Enable `item-filter` to enforce rules.
- **Trim** — offered only for genuine armor (`CosmeticValidator.isArmor`). Patterns and materials are picked from `CosmeticCatalog`; a Remove control clears the trim. Shields and weapons never see the button.
- **Apply / Cancel** — Apply commits staged changes; Cancel discards and returns to the editor. Back from a sub-editor keeps staged edits; closing the hub discards only when it is the last customize screen and no write or rename prompt is pending. Sub-editor close does not discard.

## Shield banners (removed)

Shield banner customization was intentionally removed with the oversized customize flow: the Banner Designer and Banner Pattern Picker screens, `Mode.BANNER`, `CosmeticTarget.SHIELD`, banner-only validator/service checks and config keys are gone. A possible reimplementation is deferred and tracked in [issue #35](https://github.com/kub0san/pvp-core/issues/35) after UX and architecture reconsideration.

Saved shields are unaffected: banner layers on shields in stored kits/enderchests remain opaque and survive edit/copy/save/load untouched. No banner-layer validation or stripping was added to `ItemFilter`; `ShieldMetadataPreservationTest` pins this invariant.

## Staging and save

Choices are staged with an explicit Apply. Back discards; reload invalidates open sessions. Names are formatting-only by default (colours, decorations, gradients); click/hover tags are rejected unless enabled.

Apply returns QUEUED, not saved. The write completes after close/quit; only feedback and session cleanup are discarded. Reload invalidates open sessions.
