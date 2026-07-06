<!-- last updated: 2026-07-06 -->

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
    entrypoint (`ClientModInitializer`). Registers the particle types/factories
    and HUD glyph, wires the Fabric events, and delegates the swap-window
    detection to `SwapHandler` (see below).
  - `com.patchnote.visualswap.client.swap.SwapHandler` — drives detection each
    tick: reads the `ClickTickTracker` snapshots, feeds `SwapWindowState`, and
    pushes render state to the glyph, hotbar highlight, and particles.
  - `com.patchnote.visualswap.client.hud.SwapWindowState` — the pure, Minecraft-free swap-window
    state (arm on swap, click→flash, active/expiry, chain counting). No Minecraft
    imports; all timing is driven by an integer tick passed in.
  - `com.patchnote.visualswap.client.particles.{ParticlesHandler, SwapParticle,
    SwapParticleProvider, SwapParticleType, SwapParticleOptions, SwapHitMasks}` —
    particle-type registration, the in-world particle + its provider, the custom
    `ParticleOptions` that carries a particle's full spawn spec, and the shared
    reader of `swap_hit_masks.json` (glyph shapes/colours) used by both the HUD
    glyph and the particle tint. Ported from AttributeSwapFixes.
  - `com.patchnote.visualswap.client.hud.SwapHitGlyph` — the below-the-hotbar
    glyph HUD element.
  - `com.patchnote.visualswap.client.config.screen.*` — the hand-built config
    GUI (`ModMenuIntegration` opens `VisualSwapConfigScreen`, no longer the
    AutoConfig auto-screen). Preset selector (Vanilla/Practice/Custom) + size
    slider + the preset's From/To highlight colours on top; a scrollable
    `FlashRulesList` table below, one editable row per `ModConfig.FlashRule` (item
    id + live icon, flash-on / intensity cycle chips, a per-row tint-colour hex box
    + live swatch, per-row delete, and Add-rule). **Preset model:** `PresetType` is
    an enum (VANILLA/PRACTICE/CUSTOM identity + each type's *fixed defaults*); `Preset`
    is a plain **data class** (`sizeMultiplier`, `fromColor`, `toColor`) so its fields
    serialise — an enum would persist only its name, which is why the Custom size/From/To
    now survive restarts. `ModConfig` holds `preset` (active `PresetType`) + `customPresetData`
    (the Custom `Preset` data object); `ModConfig.getFromColor/getToColor/getSize()` resolve
    to `customPresetData` under Custom else the type's default, and the render consumers
    (`SwapHotbarHighlight`, `SwapHitMasks`, `SwapParticleProvider`) read those. **Per-preset
    per-item colours:** each `FlashRule` stores a `Map<PresetType,Integer>` tint (see
    `PresetType.getFlashTint`). The size slider, From/To boxes, and every row's
    colour box show the *active preset's* effective value and are **editable only under
    Custom** (`PresetType.isColorEditable()` — greyed/disabled otherwise); switching the
    preset re-points them all (`FlashRulesList.setPreset`, `SizeSlider.update`) while a
    working `Preset` copy preserves in-progress Custom edits across toggles. Widgets are
    all stock (`StringWidget` for title/headers/captions, `EditBox` for hex,
    `CycleButton`/`Button`/`SizeSlider`) plus one custom `ColorSwatch` (the only thing
    with no vanilla equivalent); the screen no longer overrides `extractRenderState`.
    Edits stay on working copies and are written back to `ModConfig` + `AutoConfig`
    `save()` only on "Done". Uses the 26.2 extract-render model — see
    `mc_decompiled/.knowledge/screen-and-widget-api.txt`.

Mixins:

- `visual-swap.mixins.json` — package `com.patchnote.visualswap.mixin` (empty).
- `visual-swap.client.mixins.json` — package
  `com.patchnote.visualswap.client.mixin`, `"environment": "client"`. Holds the
  one active mixin, `HudHotbarHighlightMixin` (see
  [the hotbar highlight](#swap-window-detection--rendering)). The swap-window
  *detection* still uses no mixins; this one is purely for drawing a HUD
  highlight behind the hotbar items, which the HUD-element API can't reach.

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
  `particle`, so no particle type is registered and the bake skips it (no unused
  sprite ships).
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
attack-time attribute-discrepancy detection, now removed). No mixins — Fabric
events + plain API cover everything.

**Detection (`SwapWindowState`, pure; driven by `SwapHandler.eventTick`).**
`VisualSwapClient` wires the Fabric events and, in `END_CLIENT_TICK`, calls
`ClickTickTracker` (which captures an immutable per-tick `ClickTickState`) and then
`SwapHandler.eventTick`. The window clock is `player.tickCount` (carried on the
snapshot as `tick()`), consistent across the tick handler and the interact handler
since `tickCount` increments between `handleKeybinds` and `END_CLIENT_TICK`.

- **Switch** — `SwapHandler.detectSwap` compares the current and previous
  snapshots: if the main-hand item differs (`!ItemStack.isSameItem`),
  `SwapWindowState.eventSwap(tick, failed)` arms the window for `WINDOW_TICKS`
  (= **2**). Attribute swapping is a *same-tick* effect — held-item attributes lag
  the slot by exactly one reconciliation (`detectEquipmentUpdates`, once/entity-tick),
  and the client only observes the swap at end-of-tick — so 2 = 1-tick lag + 1-tick
  observation is the tight, mechanically-grounded span. Switching to an empty hand
  calls `clear()`. Watching the *item* covers hotbar keys and scroll; the first
  post-spawn tick is skipped so empty→held isn't read as a swap.
- **Click** — when `ClickTickTracker.attacked()` reports a rising edge (spear swing,
  or a left/right-click edge), `SwapHandler` calls `SwapWindowState.eventClick(tick)`,
  which flashes the `attacked` glyph for `GLYPH_VISIBLE_TICKS` (= 5) and credits the
  chain (below). `eventTickEnd(tick)` then records whether the possible window was
  open this tick.
- **Use (sub-condition)** — `AttackEntityCallback`/`UseEntityCallback` on an entity,
  while the window is active (or a same-tick switch is detected via the snapshot),
  spawn the particle burst on the target (`SwapHandler.eventInteractEntity`). No
  use-on-entity is required for the glyph.

**Rendering** (particle + glyph ported from AttributeSwapFixes). `SwapHandler.updateAttackState`
pushes render state each tick.
- **Glyph** — `SwapHitGlyph` shows **while the window is active** (pushed via
  `eventUpdate(visible, attacked, failed, consecutive, chainCount)`), rasterizing the
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
  chainCount)`. `HudHotbarHighlightMixin` then fills a box **behind each involved
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
  impulse (outward/up) and lifetime; all per-particle properties travel with the
  particle in a custom `SwapParticleOptions` (no shared spawn-time state), tinted with
  the mask `particleColor`.

Particle types + client factories register from the client entrypoint — built-in
registries are still unfrozen at client-init time (Fabric freezes them later in
`Minecraft.<init>`), so no main entrypoint is added.

Tunable constants: `SwapWindowState.WINDOW_TICKS`/`GLYPH_VISIBLE_TICKS`,
`SwapHitGlyph.SCALE`/`VERTICAL_OFFSET`, `ParticlesHandler.PARTICLES_PER_HIT`/`MAX_CHAIN_HITS`,
the `SwapParticleOptions` per-tier presets, and the `AttackParticleProps` style params.
