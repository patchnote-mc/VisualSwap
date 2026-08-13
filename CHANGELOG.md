<!---------------------------------------------------------------------------->

### Update 1.2.0

---

## Added

- Added Clear Previous Flash, clears older item flashes when a new attribute swapped occurs.
- Added separate per-rule controls for the Glyph and Hotbar Highlight.
- Added configurable colours for glyphs in the Custom preset.
- Added a glyph preview for seeing glyph colors in game.

## Changed

- Flash duration is now configurable from 2–10 ticks.
- Flash duration now controls item flashes, attacked glyphs, and hotbar highlights together.
- Consecutive-chain item flashes and highlights now end together with the latest hit.
- The item preview now follows whichever rule is being edited, including regex selectors, trigger settings, colours, exclusions, and secondary settings.

## Fixed

- Fixed item flashes only triggering when clicking on the exact same tick as switching. Clicks throughout the complete two-tick attribute swap window now work properly.
- Fixed long Flash Duration causing later attribute-swap to count as consecutive.
- Fixed ordinary item switching clearing existing flashes.
- Fixed swap detection when reselecting the current slot or switching between slots containing identical items.
- Fixed offhand interactions triggering main-hand swap effects.
- Fixed the config screen’s unsaved-change, reset, and validation states not refreshing immediately after some edits.
- Fixed regex-based rules not displaying their matched item in the preview.
- Fixed confirmation-modal titles and descriptions clipping outside panel bounds.
