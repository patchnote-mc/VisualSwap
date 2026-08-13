<!-- last updated: 2026-07-19 -->

# AGENTS.md — Visual Swap architecture & flows

Authoritative architecture/flow guide for this repo. Working strategy lives in
`.github/copilot-instructions.md`; the pointer/essentials live in `CLAUDE.md`;
longer design notes in `.llm/`.

## What this mod is

Visual Swap is a **client-side-only** Fabric mod for Minecraft `26.2` that
visualizes the *attribute-swapping* behavior (the "swap hit" bug) — the case
where a follow-up attack lands using a stale/swapped attribute snapshot. The
mod's job is purely cosmetic/diagnostic: surface when a swap-hit happens via an
on-screen glyph and a world particle. It changes no gameplay and runs entirely
on the client.

> **Status (2026-06-29):** swap-window detection + visualizations are
> **IMPLEMENTED** — see [Swap-window detection & rendering](#swap-window-detection--rendering).
> Switching the held item opens a short window during which a glyph shows below the
> hotbar (attacked variant if *use* is pressed); using on an entity during the
> window spawns a world particle burst. When the glyph turns *attacked*, the two
> hotbar slots involved in the swap are also highlighted with a gray box behind
> the items. All driven from `swap_hit_masks.json`. **Stun slam (2026-06-30):** two
> or more swap-hits chained back-to-back (a second qualifying swap landing while the
> first's flash is still on screen) escalate the display — the glyph switches to the
> `consecutive` mask with an `xN` chain counter, and the hotbar lights the whole chain
> of slots as a heat gradient (origin → latest hit) instead of the single from→to pair.

## Client-only contract (important)

This mod **must never load on a dedicated server.**

- `src/main/resources/fabric.mod.json` declares `"environment": "client"`.
  Fabric Loader therefore skips the mod entirely on a dedicated server — it is
  not registered, not in the server mod list, and none of its code runs. (This
  is the chosen behavior: a clean skip, **no custom server-side warning** — see
  `.llm/implementation/client-only-setup.md` for why.)
- There is **only a `client` entrypoint** (`VisualSwapClient`). The `main`
  (`ModInitializer`) entrypoint was intentionally removed; do not re-add one.
- Keep all client-only API usage (anything under `net.minecraft.client.*`,
  client Fabric events, etc.) in the `client` source set so it can never be
  classloaded on a server.

When adding code, preserve this contract: nothing in `src/main` may reference
client-only classes in a way that a (hypothetical) server load would touch, and
no server/common entrypoint should be introduced.

## Source layout

Loom is configured with `splitEnvironmentSourceSets()` (see `build.gradle`), so
there are two source sets, both registered as the `visual-swap` mod:

- `src/main/` — the "common" source set. For this client-only mod it holds only
  side-agnostic shared code (constants, the SLF4J logger) and **all resources**
  (assets, `fabric.mod.json`, mixin configs, the mask JSON).
  - `com.patchnote.visualswap.VisualSwap` — final, non-instantiable holder of
    `MOD_ID` (`"visual-swap"`) and `LOGGER`. **Not** an entrypoint.
- `src/client/` — client-only source set; everything that touches the client.
  - `com.patchnote.visualswap.client.VisualSwapClient` — the `client`
    entrypoint (`ClientModInitializer`). Registers the HUD glyph, wires the
    Fabric events, and delegates the swap-window detection to `SwapHandler` (see
    below). It registers **no** particle type — see the particle burst note.
  - `com.patchnote.visualswap.client.swap.SwapHandler` — drives detection each
    tick: reads the `ClickTickTracker` snapshots, feeds `SwapWindowState`, and
    pushes render state to the glyph, hotbar highlight, and particles.
  - `com.patchnote.visualswap.client.hud.SwapWindowState` — the pure, Minecraft-free swap-window
    state (arm on swap, click→flash, active/expiry, chain counting). No Minecraft
    imports; all timing is driven by an integer tick passed in.
  - `com.patchnote.visualswap.client.particles.{ParticlesHandler, SwapParticle,
    SwapHitMasks}` — `ParticlesHandler` builds the in-world particle burst and
    hands each `SwapParticle` (a `SingleQuadParticle`) straight to the vanilla
    `ParticleEngine` (**no** custom `ParticleType` — see the particle burst note),
    and `SwapHitMasks` reads `swap_hit_masks.json` (glyph shapes/colours) for both
    the HUD glyph and the particle tint. Ported from AttributeSwapFixes.
  - `com.patchnote.visualswap.client.hud.SwapHitGlyph` — the below-the-hotbar
    glyph HUD element.
  - `com.patchnote.visualswap.client.config.screen.*` — the hand-built config
    GUI (`ModMenuIntegration` opens `VisualSwapConfigScreen`, no longer the
    AutoConfig auto-screen). Built on the vanilla `gui.layouts` package:
    `HeaderAndFooterLayout` (fixed title-only header, fixed **Discard/Cancel/Done**
    footer) plus a fixed **rules header** (`RuleColumnsHeader`, an
    `AbstractContainerWidget`) pinned just under the title — it hosts the **search box**
    in the Item column (magnifier where the item icons sit), the Trigger column
    caption, a **bulk tint swatch** in the colour column (above each row's own swatch;
    click → a confirm, then the picker recolours **every** rule at once via
    `FlashRulesList.setColorForAll`, ignoring the filter), and the **Add / Clear / Reset**
    icon buttons on the right (aligned above each row's duplicate/delete icons), all off
    the same right-anchored column maths as the rows. The whole middle is a `ScrollableLayout` styled like a
    vanilla list panel (`menu_list_background` + header/footer separators drawn
    in the screen's `extractRenderState` override). Content: a 2×3 `GridLayout`
    on top — preset selector & size slider | From/To colour **swatches** |
    `HotbarSwapPreview` + a **Reset colours** icon button in the bottom-right cell —
    then a rule-count line over
    `FlashRulesList`, a plain vertical `Layout` of `FlashRuleRow`s (each an
    `AbstractContainerWidget` with `NO_SCROLL`; the page owns all scrolling),
    one editable row per `ModConfig.FlashRule` (a **regex item selector** + a clickable
    live-icon **preview** (`ItemPreviewButton`, `×N` badge; opens the `RegexPreviewModal`), a flash-on
    (Trigger) cycle chip, a **config (gear) `IconButton`** — opens the `RuleConfigModal` with the rule's
    flash Strength cycler + Swap Effects On/Off (moved off the row 2026-07-19), a tint-colour **swatch only** — click
    opens the picker, no inline hex box — **up/down reorder** + **duplicate + delete** `IconButton`s). Row
    add/remove/duplicate/reorder/clear/reset and every filter keystroke re-init via
    `rebuildWidgets()`. Each rebuild **preserves the scroll position** (captured/restored
    via the scroll container's `AbstractScrollArea` — `currentScroll`/`restoreScroll`) instead
    of snapping to the top; content-replacing actions (filter/reset/discard) opt back into
    top via `resetScroll`. New rules insert at the **top** and the screen focuses the new
    row's item box and scrolls it in (`FlashRuleRow.focusItemInput` + `ensureRowVisible`);
    a **duplicated** row is flagged (`FlashRulesList.consumeRevealTarget`, resolved to an
    index like the preview target) and `revealRow`/`ensureRowVisible`'d into view.
    **Icons:** the add / delete(X) / duplicate / reset / clear / search / config (settings gear) / dirty-dot glyphs
    are Material Symbols SVGs rasterized to white 128² PNGs (+ a `blur` `.png.mcmeta` for
    smooth downscaling) by `.gen/gen_icons.py` (cairosvg) into
    `src/main/resources/assets/visual-swap/textures/gui/icons/`, tinted per state and
    scaled at blit time by `Icons.blit` / `IconButton`. **Swap preview:**
    `HotbarSwapPreview` renders two real hotbar slots (cropped `hud/hotbar`
    sprite, `hud/hotbar_selection` on the right slot), From/To fills behind the
    items exactly as `SwapHotbarHighlight` draws them in-game, and flashes the
    right item through the real `WHITE_SILHOUETTE` shader — it registers the
    item's coords+tint in `ItemFlashPreview` each frame and
    `GuiItemFlashPreviewMixin` re-blits it from the item atlas
    (`ItemFlashPipeline.silhouetteBlit`). The flashing slot follows the rule
    whose colour box was last user-edited (row → list → screen callback;
    retargeted across rebuilds by its position in the rebuild seed, falling
    back to the mace rule), live-tracking that rule's item/colour/intensity
    under the working preset. **Preset model:** `PresetType` is
    an enum (VANILLA/PRACTICE/CUSTOM identity + each type's *fixed defaults*); `Preset`
    is a plain **data class** (`sizeMultiplier`, `fromColor`, `toColor`) so its fields
    serialise — an enum would persist only its name, which is why the Custom size/From/To
    now survive restarts. `ModConfig` holds `preset` (active `PresetType`) + `customPresetData`
    (the Custom `Preset` data object); `ModConfig.getFromColor/getToColor/getSize()` resolve
    to `customPresetData` under Custom else the type's default, and the render consumers
    (`SwapHotbarHighlight`, `SwapHitMasks`, `SwapParticleProvider`) read those. **Per-preset
    per-item colours:** each `FlashRule` stores a `Map<PresetType,Integer>` tint (see
    `PresetType.getFlashTint`). The size slider, From/To swatches, and every row's
    colour swatch show the *active preset's* effective value and are **editable only under
    Custom** (`PresetType.isColorEditable()` — greyed/disabled otherwise); switching the
    preset re-points them all (`FlashRulesList.setPreset`, `SizeSlider.update`) while a
    working `Preset` copy preserves in-progress Custom edits across toggles. Widgets are
    stock (`StringWidget`, `EditBox` for the item id + search, `CycleButton`/`Button`/`SizeSlider`)
    plus the custom `ColorSwatch`, `IconButton`, `RuleColumnsHeader`, and `HotbarSwapPreview`.
    Edits stay on working copies and are written back to `ModConfig` + `AutoConfig`
    `save()` only on "Done". **QOL/production (2026-07-07):** the screen snapshots the
    saved config on open and diffs the working state against it (`Preset.sameValuesAs`,
    `FlashRule.sameValuesAs`/`listsSameValues`) to drive an **unsaved-changes** state — an
    amber dirty-dot icon beside the title, a gated **Discard** button (reverts to the
    snapshot), and a confirm-before-leaving guard on Cancel/Esc. **Per-row markers:** each
    working `FlashRule` carries a transient `savedOrigin` link to the saved rule it descends
    from (stamped self on the snapshot, carried by the copy ctor); rows draw a left-gutter dot
    — **green** when `isNew()` (no origin: added or duplicated) or **orange** when `isModified()`
    (origin present but values differ). A bulk **Reset rules** value-matches defaults back to
    unclaimed saved rules (`linkToSavedByValue`) so only genuine deltas light up. **Regex selectors + ordering
    (2026-07-08):** a rule's `item` is a **regex** matched against item ids with `Matcher.find()` (`utils/ItemRegex`; a
    full literal id matches only itself, `_sword` bulk-selects). The row's icon / `isItemValid` / `×N` count come from
    `ItemRegex.summarize`; clicking the icon opens the interactive `RegexPreviewModal` (below). Precedence is an explicit
    per-rule **`order`** (lower wins), reordered by the row's **up/down** buttons (`FlashRulesList.moveUp/moveDown`),
    stamped from row position in `toRules()`; `validatePostLoad` sorts by `order` then re-stamps a dense sequence. The
    old same-item **conflict subsystem is gone** — overlaps are resolved by order (first match wins), so only *invalid*
    rules (blank / uncompilable / zero-match pattern) block **Done** (`FlashRulesList.invalidCount`). Runtime resolution
    is `ItemFlash.getRuleFor(stack, forAttack)` → `hud/click/FlashRuleIndex.forCurrentConfig()`, a per-`Item` memoised
    winner cache (rules pre-sorted by `order`, patterns compiled once, rebuilt when the config's rule list is replaced),
    so the tick path is O(1) amortised. **Flash duration:** the global `ModConfig.flashVisibleTicks` (2–10, read live by
    `ItemFlash` and `SwapWindowState`) is edited by a `TicksSlider` under the top grid and controls item flashes,
    attacked glyphs, and hotbar highlights together. A consecutive hit re-times all active chain item flashes to the
    latest hit's expiration. Chain credit itself uses a separate fixed two-tick click clock, so changing render
    duration never changes which attribute swaps count as consecutive. `clearPreviousFlashOnSwap` clears older item-flash timelines only when a qualifying
    attribute-swap input starts a fresh, non-consecutive hit; switching without that input respects their existing
    timers. **Flash trigger mode (2026-07-19):** the global
    `ModConfig.flashOnlyOnSwap` (default on) gates the item flash — when on, a press only lights a slot when
    `SwapHandler.attributeSwapThisTick` reports that the same input belongs to `SwapWindowState.acceptsClick`'s
    two-tick attribute-swap window, including the end-of-tick observation bridge for its second valid input tick;
    when off it fires on every matching attack/use. It does **not** affect the hotbar highlight (always swap-driven).
    Surfaced as an "Only On Swap" sub-toggle under Item Flash in the Effects modal. **Per-rule swap effects (2026-07-19):**
    each `FlashRule` carries a `showSwapEffects` flag (default off), edited — together with the flash Strength — in the
    `RuleConfigModal` opened from each row's config (gear) button; one flag gates **both** the swap-hit glyph and the
    hotbar highlight per switched-to item (see the Glyph / Hotbar highlight render bullets). **Split resets:** **Reset rules** (rules
    table → `FlashRule.defaultFlashRules()`) and **Reset colours** (Custom From/To + size
    → factory), each gated to when it would actually change something. **Search filter**
    (`FlashRulesList.setFilter`, view-only — `toRules()` still returns all), **duplicate**
    / **clear-all** rows, a live rule count + **empty state**, an invalid-item warning on
    Done (`FlashRulesList.invalidCount()`), and vanilla `.setTooltip` hints on the preset /
    trigger / intensity / size / action controls. Destructive actions confirm through a
    `ConfirmModal` (see the `screen.modal` package below). Uses the 26.2 extract-render
    model — see `mc_decompiled/.knowledge/screen-and-widget-api.txt` and
    `mc_decompiled/.knowledge/gui-layouts.txt`.
  - `com.patchnote.visualswap.client.screen.overlay.*` — a reusable floating-layer
    framework for any screen (built for the config screen; intended for onboarding
    callouts later). `Overlay` (abstract, vanilla tooltip nine-slice panel, child
    widgets move with it, modal vs passive) + `OverlayManager` (one per screen: the
    screen routes every input event to it FIRST and calls `extract` LAST on its own
    strata; modal overlays capture input, click-outside/Esc dismisses; hover tooltips
    are immediate-mode — re-request each frame via `showTooltip`). Subclasses:
    `TooltipOverlay` (vanilla-look tooltip at any position from arbitrary `Component`
    lines; `forItem` builds real `Screen.getTooltipFromItem` lines — currently used
    only by the two `HotbarSwapPreview` slots, which show short *explanatory* text on
    hover) and `ColorPickerOverlay` (opened by clicking any `ColorSwatch` while the
    preset is Custom; opaque panel + custom flat `TabButton` tabs: HSV pinwheel +
    value/alpha sliders (default, `HueSatWheel` — a `NativeImage`-baked
    `DynamicTexture`, value applied as a grey tint at blit), HSVA `GradientSlider`s,
    and hex entry; alpha controls only for the From/To colours — rule tints are
    RGB-only. Live-applies by writing straight onto the target (the swatch's rule /
    the working `Preset`), so no rebuild is needed; HSV state is the source of truth so
    hue survives zero-saturation edits). See
    `mc_decompiled/.knowledge/tooltips.txt` + `.knowledge/dynamic-textures.txt`.
  - `com.patchnote.visualswap.client.screen.modal.*` — modals that are their *own*
    `Screen` (vs. the in-screen overlays above). `Modal` (abstract) captures whatever
    screen was open as its **backdrop**, renders it dimmed behind a centred panel (via a
    scrim + the backdrop's `extractRenderState` with an off-screen mouse), and returns to
    it on close — so it owns all screen routing and can be opened from anywhere with
    `open()`. `ConfirmModal` is the yes/no dialog (title + body + Cancel/Confirm) that
    gates the reset/clear/discard/leave-unsaved actions; after Confirm runs the action it
    returns to the backdrop **only if the action didn't itself navigate away** (checked
    via `Minecraft.gui.screen()`). `RegexPreviewModal` (+ `MatchedItemsList`, an `AbstractScrollArea`) previews a rule's
    regex: a scrollable table of every matched item (icon + id, the matched substring highlighted via `ItemRegex.spans`),
    each row a **checkbox** toggling the id in the rule's `excludedItems` set — so `minecraft:cod` can keep the fish but
    drop `cod_bucket` without touching the pattern. Edits land on the shared working rule; returning re-inits the config
    screen (which always re-`init`s on show), which picks them up. `utils/ItemIcons` builds the bind-safe item stacks.
    `RuleConfigModal` (2026-07-19) hosts a rule's secondary settings — the flash Strength cycler and the Swap Effects
    On/Off — opened from the row's config (gear) button; it mirrors the `EffectsModal` look (full-width rows, hovering a
    row prints its help text in the panel's help area) and writes straight onto the shared working rule, same flow as
    the `RegexPreviewModal`. `EffectsModal` (the master Toggles panel opened from the config header) shares that idiom.

Mixins:

- `visual-swap.mixins.json` — package `com.patchnote.visualswap.mixin` (empty).
- `visual-swap.client.mixins.json` — package
  `com.patchnote.visualswap.client.mixin`, `"environment": "client"`. Four
  active mixins — three render-only, plus one that feeds swap *detection*:
  - `HotbarSelectMixin` (`Inventory.setSelectedSlot` HEAD, local player only) —
    the sole detection mixin: marks `HotbarSelectSignal` when the player selects a
    hotbar slot (hotbar keys and scroll both route through `setSelectedSlot`), so a
    switch is registered even when it lands on the slot already held or on an
    identical item — cases the held-item comparison can't see.
  - `HudHotbarHighlightMixin` (`Hud.extractSlot` HEAD) — draws the swap
    highlight fill behind hotbar items, which the HUD-element API can't reach.
  - `HotbarItemGlowMixin` (`GuiRenderer.submitBlitFromItemAtlas` TAIL) —
    re-blits a flashing hotbar item as a `WHITE_SILHOUETTE` tint silhouette
    (via `ItemFlashPipeline.silhouetteBlit`).
  - `GuiItemFlashPreviewMixin` (same injection) — the config screen's swap
    preview: re-blits any GUI item registered in `ItemFlashPreview`
    (position-keyed, frame-scoped) through the same silhouette path.

Both target `compatibilityLevel: JAVA_25` and require annotations
(`overwrites.requireAnnotations = true`, `injectors.defaultRequire = 1`).

## Build pipeline

Standard Loom build (`./gradlew build`) with one project-specific wrinkle: the
**particle sprite bake**.

- Single source of truth: `src/main/resources/assets/visual-swap/swap_hit_masks.json`.
  Each entry is a 7×7 (`#` = filled) glyph with an optional `particle` name, a HUD
  `color` (ARGB) and a `particleColor` (ARGB) runtime tint. `color` and
  `particleColor` are each objects carrying a `vanilla` and a `practice` variant,
  selected at runtime by `ModConfig.preset` (`PresetType.{VANILLA,PRACTICE,CUSTOM}`,
  read live by `SwapHitMasks.Mask.color()`/`particleColor()`; under `CUSTOM` the
  `failed` mask takes the user `from` colour and every other mask the `to`
  colour, via `ModConfig.getFromColor()`/`getToColor()`). Four masks today: `possible` (→ `swap_possible`), `attacked`
  (→ `swap_attacked`) and `consecutive` (→ `swap_consecutive`) each have a
  `particle` and bake a sprite; `failed` is **HUD glyph only** — it omits
  `particle`, so the bake skips it (no unused sprite ships). `possible` bakes a
  sprite but is currently never spawned (only `attacked`/`consecutive` are). The
  baked sprites are stitched into the vanilla particle atlas by its directory
  source over `textures/particle/`, so `ParticlesHandler` looks them up by id at
  spawn — no particle definition JSON or registered type.
- `tasks.bakeParticleSprites` (in `build.gradle`) rasterizes each mask into
  `build/generated/particle-sprites/assets/visual-swap/textures/particle/<particle>.png`,
  scaling each cell by `cellPx = 8`. Filled cells are baked **opaque white** and
  tinted at render time by the mask's `particleColor` (so one static sprite
  serves both the vanilla and practice variants); empty cells are transparent.
- That generated dir is wired into the **main** resources
  (`sourceSets.main.resources.srcDir generatedParticleDir`), so the PNGs ship in
  the jar **without being committed**. `processResources` and `sourcesJar`
  `dependsOn bakeParticleSprites`.
- `processResources` also expands `${version}` in `fabric.mod.json` from
  `project.version` (`mod_version` in `gradle.properties`).

Edit a mask in the JSON and **both** the HUD glyph (read at runtime by
`SwapHitGlyph`) and the baked particle update from the same data.

## Toolchain / versions

See `CLAUDE.md` and `gradle.properties` for the authoritative list. In short:
Minecraft `26.2`, Fabric Loader `0.19.3`, Fabric API `0.153.0+26.2`, Loom
`1.17-SNAPSHOT`, Gradle wrapper `9.5.1`, Java `25`, **Mojang mappings only**
(never Yarn). Decompiled sources are vendored as git submodules, initialized via the
`mc_decompiled/` and `fabric_decompiled/` setup scripts — consult them only when
something won't compile or an API is genuinely unclear, and cache findings under
the `*_decompiled/.index` / `.knowledge` dirs (see `.github/copilot-instructions.md`).

## Swap-window detection & rendering

Revamped 2026-06-28 to a **switch/use-driven window** model (replacing the earlier
attack-time attribute-discrepancy detection, now removed). Fabric events + plain API
cover most of it; the one detection mixin is `HotbarSelectMixin` (see Mixins), which
supplies the slot-select signal the item comparison can't.

**Detection (`SwapWindowState`, pure; driven by `SwapHandler.eventTick`).**
`VisualSwapClient` wires the Fabric events and, in `END_CLIENT_TICK`, calls
`ClickTickTracker` (which captures an immutable per-tick `ClickTickState`) and then
`SwapHandler.eventTick`. The window clock is `player.tickCount` (carried on the
snapshot as `tick()`), consistent across the tick handler and the interact handler
since `tickCount` increments between `handleKeybinds` and `END_CLIENT_TICK`.

- **Switch** — `SwapHandler.detectSwap` arms the window when the current snapshot
  shows a deliberate slot switch: either the main-hand item differs
  (`!ItemStack.isSameItem`) **or** `ClickTickState.slotSelected` is set (a hotbar-key
  / scroll selection this tick, captured via `HotbarSelectMixin` →
  `HotbarSelectSignal`). `SwapWindowState.eventSwap(tick, failed)` arms it for
  `WINDOW_TICKS` (= **2**). Attribute swapping is a *same-tick* effect — held-item
  attributes lag the slot by exactly one reconciliation (`detectEquipmentUpdates`,
  once/entity-tick), and the client only observes the swap at end-of-tick — so 2 =
  1-tick lag + 1-tick observation is the tight, mechanically-grounded span. Switching
  to an empty hand calls `clear()`. Adding the slot-select signal means re-selecting
  the slot already held, or switching to a different slot holding an identical item,
  now opens the window too (the item comparison alone misses both). The first
  post-spawn tick is skipped so empty→held isn't read as a swap.
- **Click** — when `ClickTickTracker.attacked()` reports a rising edge (spear swing,
  or a left/right-click edge), `SwapHandler` calls `SwapWindowState.eventClick(tick, flashVisibleTicks)`,
  which flashes the `attacked` glyph for the configured duration (default 5) and credits the
  chain (below). `eventTickEnd(tick)` then records whether the possible window was
  open this tick.
- **Use (sub-condition)** — `AttackEntityCallback`/`UseEntityCallback` on an entity,
  while the window is active (or a same-tick switch is detected via the snapshot),
  spawn the particle burst on the target (`SwapHandler.eventInteractEntity`). No
  use-on-entity is required for the glyph.

**Rendering** (particle + glyph ported from AttributeSwapFixes). `SwapHandler.updateAttackState`
pushes render state each tick.
- **Glyph** — `SwapHitGlyph` shows **while the window is active** (pushed via
  `eventUpdate(visible, attacked, failed, consecutive, chainCount)`), **gated per switched-to item**: `SwapHandler`
  latches `effectsAllowed = ItemFlash.showsEffectsFor(mainHand)` on each swap (the switched-to item's highest-precedence
  matching rule must opt in via `FlashRule.showSwapEffects`, default off; an item matching no rule never shows it). The
  pre-click "possible" glyph tracks that live value; once a click starts the **attacked flash**, the glyph + hotbar
  highlight instead read `flashEffectsAllowed` — the opt-in **latched when the flash began** (OR-extended across a chain)
  — so switching away mid-flash keeps them on for the full window rather than cutting them short. The **same flag drives
  both** the glyph and the highlight (below). It rasterizes the
  mask `rows`/`color` (shared, cached `SwapHitMasks`) to the HUD
  (`GuiGraphicsExtractor.fill`), registered `attachElementAfter(HOTBAR)` and drawn
  centred horizontally at screen-centre + `VERTICAL_OFFSET`. Mask priority is
  `consecutive > failed > attacked > possible`. When `chainCount >= 2` an `xN`
  counter (`GuiGraphicsExtractor.text`, `Minecraft.font`) is drawn to the right of
  the glyph in the mask's colour.
- **Failed glyph** — *not* a separate state; it is the **`attacked` flash rendered
  with the `failed` mask**. `eventSwap(tick, failed)` records whether the swap landed
  on a **piercing weapon** (`PIERCING_WEAPON` component, via `ClickTickState.hasPiercingComponent`)
  while the **previous item's attack-strength bar was still charging**
  (`cooldownAtTick() < 1.0`, read from the previous-tick snapshot since the swap
  resets the ticker). `eventClick` freezes that bit into `flashFailed`; `failed(tick)`
  is then `attacked(tick) && flashFailed`, so the variant is locked in for the single
  flash's lifetime. No `failed` particle exists (the mask is HUD-only).
- **Hotbar highlight** — on the rising edge of `attacked`, `SwapHandler` freezes the
  swap's two hotbar slots (`swapFromSlot` recorded at swap time, plus the selected
  `swapToSlot`) and pushes them to `SwapHotbarHighlight.eventUpdate(active, trail, len,
  chainCount)` with `active = attacked && flashEffectsAllowed` — so, like the glyph, it only
  draws when the swap's item opted in (`FlashRule.showSwapEffects`), off the value latched when the flash began (so
  switching away mid-flash doesn't hide it). `HudHotbarHighlightMixin` then fills a box **behind each involved
  item** — after the hotbar bar blits but before the item icon, so the item stays
  visible. Colours come live from `ModConfig.preset` via `Preset.getFromColor()/getToColor()`
  (`VANILLA` gray, `PRACTICE` red→green, `CUSTOM` the user pair). Slot geometry is
  shared with the mixin through `HotbarGeometry`.
- **Consecutive chain (stun slam)** — detection lives in `SwapWindowState.eventClick`:
  each swap is credited once (`lastCreditedSwapTick`); a new credited swap landing
  while a flash is still on screen increments `chainCount` (else resets it to 1).
  `chainCount(tick)`/`consecutive(tick)` (`>= 2`) are valid only while `attacked`.
  `SwapHandler` accumulates the ordered slot trail (`chainTrail`/`chainTrailLen`):
  seeded with `from,to` on the rising edge of `attacked`, appending the latest `to`
  whenever `chainCount` rises. For `chainCount >= 2`, `SwapHotbarHighlight` lights
  **every** slot in the trail as an HSV heat gradient (origin → latest hit) instead
  of the single from→to pair. A HUD element can only attach before/after the whole
  hotbar, never between the bar and the items, which is why this one path uses a mixin.
- **Particle burst** — `SwapHandler.eventInteractEntity` calls
  `ParticlesHandler.spawnParticles(client, target, chainHits, props)` with
  `chainHits = chainCount + 1`. It emits a gaussian cloud of `SwapParticle` at the
  target's mid-height (`getY(0.5)`), `PARTICLES_PER_HIT` (9) × `clamp(chainHits, 1,
  MAX_CHAIN_HITS)`, using the `consecutive` sprite for chains ≥ 2 else `attacked`.
  The attack style (`AttackParticleProps.{NORMAL,CRIT,SMASH}`) sets each particle's
  impulse (outward/up) and lifetime; `ParticlesHandler.Tier` holds the per-tier
  physics (baseSize/gravity/friction/jitter/upBias) applied per particle, tinted
  with the mask `particleColor`.

**No custom particle type is registered.** `ParticlesHandler` builds each
`SwapParticle` directly and calls `Minecraft.particleEngine.add(...)`; the sprite
is looked up from the vanilla particle atlas
(`getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES).getSprite(id)`). This is
deliberate: `BuiltInRegistries.PARTICLE_TYPE` is network-synced and Fabric flags
it MODDED the instant a mod adds an entry, so registering there pollutes the
**integrated server's** registry when a player opens their world to LAN — Fabric's
registry-sync then disconnects any joining client that lacks Visual Swap. Building
particles client-side keeps the effect purely local with zero server-visible
footprint. **Do not reintroduce a `ParticleType`/`ParticleOptions`.** See
`.llm/implementation/client-only-setup.md`.

Tunable constants: `SwapWindowState.WINDOW_TICKS`,
`SwapHitGlyph.SCALE`/`VERTICAL_OFFSET`, `ParticlesHandler.PARTICLES_PER_HIT`/`MAX_CHAIN_HITS`,
the `SwapParticleOptions` per-tier presets, and the `AttackParticleProps` style params.
