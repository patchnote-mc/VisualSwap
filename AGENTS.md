<!-- last updated: 2026-07-01 -->

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
> `stun_slam` mask with an `xN` chain counter, and the hotbar lights the whole chain
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
  - `com.patchnote.visualswap.client.hud.SwapWindowState` — the pure, Minecraft-free swap-window
    state (arm on swap, use→consecutive, active/expiry); unit-tested in `src/test`.
  - `com.patchnote.visualswap.client.particles.SwapHitMasks` — shared reader of
    `swap_hit_masks.json` (the glyph shapes/colours), used by both the HUD glyph
    and the particle tint. Ported from AttributeSwapFixes.
- `src/client/` — client-only source set; everything that touches the client.
  - `com.patchnote.visualswap.client.VisualSwapClient` — the `client`
    entrypoint (`ClientModInitializer`). Registers the particle types/factories
    and HUD glyph, and drives the swap-window detection (see below).
  - `com.patchnote.visualswap.client.{VisualSwapParticles, SwapGlyphParticle,
    SwapHitGlyph}` — particle registration, the two-tier in-world particle, and
    the below-the-hotbar glyph HUD element. Ported from AttributeSwapFixes.

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
  Each entry is a 7×7 (`#` = filled) glyph with a `particle` name, a HUD `color`
  (ARGB) and a `particleColor` (ARGB) runtime tint. `color` and `particleColor`
  are each objects carrying a `vanilla` and a `practice` variant, selected at
  runtime by `ModConfig.indicatorType` (`IndicatorType.{VANILLA,PRACTICE}`, read
  live by `SwapHitMasks.Mask.color()`/`particleColor()`). Four masks today:
  `possible` (→ `swap_possible`), `attacked` (→ `swap_attacked`) and
  `lunge_failed` and `stun_slam` (both HUD glyph only — no particle type is
  registered for them; their sprites still bake, harmlessly, but go unused).
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

## Tests

`build.gradle` wires plain JUnit 5 (`useJUnitPlatform()`) for Minecraft-free
unit tests of the swap logic. These tests must never load Minecraft, so no
`fabric-loader-junit` is needed. `src/test/java/.../SwapWindowTest.java` covers
`SwapWindow` (inactive before swap, active within window then expiry, use
in/out of window → consecutive, new swap resets, clear).

## Swap-window detection & rendering

Revamped 2026-06-28 to a **switch/use-driven window** model (replacing the earlier
attack-time attribute-discrepancy detection, now removed). No mixins — Fabric
events + plain API cover everything.

**Detection (`SwapWindow`, pure + unit-tested; driven by `VisualSwapClient`).**
The window clock is `player.tickCount` (consistent across the tick handler and the
attack handler, since `tickCount` increments in `tickEntities` between
`handleKeybinds` and `END_CLIENT_TICK`).

- **Switch** — in `END_CLIENT_TICK`, if the main-hand item differs from the
  previous-tick snapshot (`ItemStack.matches`), `SwapWindow.onSwap(tick)` arms the
  window for `WINDOW_TICKS` (= `SwapWindow.DEFAULT_WINDOW_TICKS` = **2**). Attribute
  swapping is a *same-tick* effect — held-item attributes lag the slot by exactly
  one reconciliation (`detectEquipmentUpdates`, once/entity-tick), and the client
  only observes the swap at end-of-tick — so 2 = 1-tick lag + 1-tick observation is
  the tight, mechanically-grounded span (not the old arbitrary 5). Switching to an
  empty hand clears it.
  Watching the *item* covers hotbar keys and scroll; offhand is best-effort.
- **Use** — a rising edge of `options.keyUse.isDown()` during an active window
  marks it `consecutive` (`SwapWindow.onUse`).
- **Use (sub-condition)** — `UseEntityCallback` on an entity, while the
  window is active (or a same-tick switch is detected via the snapshot), spawns the
  attacked particle burst on the target. No use-on-entity is required for the glyph.

**Rendering** (particle + glyph ported from AttributeSwapFixes).
- **Glyph** — `SwapHitGlyph` shows **while the window is active** (pushed each tick
  via `updateState(visible, attacked, lungeFailed, stunSlam, chainCount)`), rasterizing
  the mask `rows`/`color` (shared `SwapHitMasks`) to the HUD (`GuiGraphicsExtractor.fill`)
  centred **below the hotbar** (`attachElementAfter(HOTBAR)`). Mask priority is
  `stunSlam > lungeFailed > attacked > possible`. When `chainCount >= 2` an `xN`
  counter (`GuiGraphicsExtractor.text`, `Minecraft.font`) is drawn to the right of
  the glyph in the mask's colour.
- **Lunge-failed glyph** — *not* a separate state; it is the **`attacked` flash
  rendered with the `lunge_failed` mask**. `onSwap(tick, lungeFail)` records whether
  the swap landed on a **lunge spear** (`PIERCING_WEAPON` component +
  `Enchantments.LUNGE` level > 0) while the **previous item's attack-strength bar was
  still charging** (`getAttackStrengthScale(0) < 1.0`, read from the previous-tick
  snapshot since the swap itself resets the ticker). When `onClick` arms the attacked
  flash it freezes that bit into `flashLungeFailed`; `lungeFailed(tick)` is then
  `attacked(tick) && flashLungeFailed`. So the variant is locked in for the single
  flash's lifetime — there is one flash with one timeline, and switching items mid-flash
  cannot retroactively change which mask it shows. No `lunge_failed` particle/flash exists.
- **Hotbar highlight** — when the glyph turns **attacked** (the rising edge of
  `possible → attacked`), `VisualSwapClient` freezes the swap's two hotbar slots
  (the swapped-from slot, plus the now-selected swapped-to slot it records at swap
  time) and pushes them to `SwapHotbarHighlight` for the flash's duration.
  `HudHotbarHighlightMixin` (`@Inject` at the head of `Hud.extractSlot`) then fills
  a box **behind each involved item** — after the hotbar bar blits but before the
  item icon, so the item stays visible over the highlight. In `VANILLA` it's a
  cooldown-style gray (`0x7FFFFFFF`; the swapped-to slot a little more opaque); in
  `PRACTICE` the two slots scream the from->to direction with full-opacity hues
  (red from, green to) instead of an opacity ramp, selected live from
  `ModConfig.indicatorType` in `SwapHotbarHighlight`.
- **Stun-slam chain** — detection lives in `SwapWindowState.onClick`: each swap is
  credited once (`lastCreditedSwapTick`); a new credited swap landing while a flash is
  still on screen increments `chainCount` (else resets it to 1). `chainCount(tick)`/
  `stunSlam(tick)` (`>= 2`) are valid only while `attacked`. `VisualSwapClient`
  accumulates the ordered slot trail (`chainTrail`/`chainTrailLen`): seeded with
  `from,to` on the rising edge of `attacked`, appending the latest `to` whenever
  `chainCount` rises. `SwapHotbarHighlight.update(active, trail, len, chainCount)`
  then, for `chainCount >= 2`, lights **every** slot in the trail as a heat gradient
  (origin → latest hit; gold-warmed gray in `VANILLA`, red→gold in `PRACTICE`)
  instead of the single from→to pair. A HUD element can only
  attach before/after the whole hotbar, never between the bar and the items, which
  is why this one path uses a mixin.
- **Particle burst** — on the attack sub-condition, a gaussian cloud of
  `SwapGlyphParticle` at the target's mid-height (`getY(0.5)`), count/spread per
  tier (9/0.35 normal, 18/0.45 consecutive); each particle's motion is injected by
  its tier provider (gentle float vs crit-spray), tinted with the mask
  `particleColor`. (The particle wiring is expected to be repurposed later.)

Particle types + client factories register from the client entrypoint — built-in
registries are still unfrozen at client-init time (Fabric freezes them later in
`Minecraft.<init>`), so no main entrypoint is added.

Tunable constants: `SwapWindow.DEFAULT_WINDOW_TICKS`, `VisualSwapClient.WINDOW_TICKS`
+ burst count/spread, `SwapHitGlyph.SCALE`/`BOTTOM_MARGIN`,
`SwapGlyphParticle.Provider.normal`/`consecutive` motion params.
