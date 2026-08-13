<!-- generated: 2026-07-05 — codebase audit, findings only (no fixes applied) -->

# ISSUES — Visual Swap audit

Audit of the whole repo (Java source, resources, build, docs). Findings only —
nothing here is fixed. Each item lists a severity and a confidence. "Confidence"
is how sure the finding is a real defect vs. an intentional design choice.

Severity legend: **[High]** likely wrong/impactful · **[Med]** real but bounded ·
**[Low]** cosmetic / maintenance / doc drift.

---

## Correctness & logic

### 1. `ItemFlash.heldSlot` is effectively dead — the "flash while held" path never persists — [Med] (confidence: high)

[ItemFlash.java:45-54](src/client/java/com/patchnote/visualswap/client/hud/click/ItemFlash.java#L45-L54), read
at [ItemFlash.java:63](src/client/java/com/patchnote/visualswap/client/hud/click/ItemFlash.java#L63)

`onTick` only enters the `if (pressed)` branch on the *press edge* (`attackPressed`/`usePressed`
are edge-triggered
by [ItemFlashHandler](src/client/java/com/patchnote/visualswap/client/hud/click/ItemFlashHandler.java#L19-L24)).
On the very next tick, while the button is still held, `pressed` is `false`, so the
`else` branch immediately resets `heldSlot = NO_SLOT`. Nothing ever keeps
`heldSlot` set across ticks, so `isActive`'s `slot == this.heldSlot` term is only
ever true on the same tick the 5-tick `slotsExpirationTick` already covers. The
"keep flashing while the key is held" feature the variable name implies does not work.

### 2. Glyph mask JSON is re-opened and re-parsed on every particle spawned — [Med] (confidence: high)

[SwapParticleProvider.java:83](src/client/java/com/patchnote/visualswap/client/particles/SwapParticleProvider.java#L83), [:91](src/client/java/com/patchnote/visualswap/client/particles/SwapParticleProvider.java#L91), [:99](src/client/java/com/patchnote/visualswap/client/particles/SwapParticleProvider.java#L99) → [SwapHitMasks.load](src/client/java/com/patchnote/visualswap/client/particles/SwapHitMasks.java#L39-L60)

The `rgb` suppliers call `SwapHitMasks.possible()/attacked()/consecutive().particleColor()`
inside `createParticle`. Each of those does a full classpath `getResourceAsStream`
open + Gson parse of `swap_hit_masks.json`. A single swap-hit spawns up to
`PARTICLES_PER_HIT * MAX_CHAIN_HITS = 9 * 4 = 36` particles
([ParticlesHandler.java:27-28](src/client/java/com/patchnote/visualswap/client/particles/ParticlesHandler.java#L27-L28),[:69](src/client/java/com/patchnote/visualswap/client/particles/ParticlesHandler.java#L69)),
so ~36 disk reads + JSON parses per hit, on the render/particle path. The HUD glyph
already caches masks once via `ensureLoaded`; the particle path does not. Also a
robustness issue: `load` throws `IllegalStateException` if the resource is missing,
which would now surface mid-particle-creation.

### 3. `SwapHitGlyph.ensureLoaded` sets `loaded = true` before the loads succeed — [Med] (confidence: high)

[SwapHitGlyph.java:99-105](src/client/java/com/patchnote/visualswap/client/hud/SwapHitGlyph.java#L99-L105)

`this.loaded = true;` is set *before* `SwapHitMasks.possible()` etc. run. If any
`load` throws (missing/malformed resource), `loaded` is already `true` and the mask
fields stay `null`. The exception propagates out of `extractRenderState` that frame,
and every subsequent frame skips `ensureLoaded` (because `loaded == true`) and then
NPEs on `mask.canDraw()` at [:62](src/client/java/com/patchnote/visualswap/client/hud/SwapHitGlyph.java#L62).
A transient load failure permanently wedges the glyph. Set `loaded = true` only
after all masks are assigned.

### 4. `ParticlesHandler.spawningProps` is a shared mutable static passed out-of-band — [Low] (confidence: med)

[ParticlesHandler.java:32-34](src/client/java/com/patchnote/visualswap/client/particles/ParticlesHandler.java#L32-L34),[:67](src/client/java/com/patchnote/visualswap/client/particles/ParticlesHandler.java#L67);
consumed
at [SwapParticleProvider.java:50](src/client/java/com/patchnote/visualswap/client/particles/SwapParticleProvider.java#L50)

The attack style (`NORMAL`/`CRIT`/`SMASH`) is stashed in a static field before the
`addParticle` loop and read back inside `createParticle`. It only works because
particle creation is synchronous with `addParticle`. Any future async/batched
particle creation, or two `spawnParticles` calls interleaving, would read the wrong
style. Carrying the impulse already works via `xAux/yAux/zAux`; the style should
travel the same way rather than via a global.

---

## Build, tests & resources

### 5. Unit tests referenced everywhere but do not exist — [Med] (confidence: high)

[build.gradle:49-58](build.gradle#L49-L58); AGENTS.md "Tests" section; CLAUDE.md

`build.gradle` wires JUnit 5 (`useJUnitPlatform()`) and AGENTS.md states
`src/test/java/.../SwapWindowTest.java` covers `SwapWindow`. No `src/test` tree exists
(`git ls-files` shows no test sources). The `test` task passes vacuously, giving a
false sense of coverage for the one piece of pure, testable logic (`SwapWindowState`).

### 6. `failed` mask points at a particle type that is never registered — [Low] (confidence: high)

[swap_hit_masks.json](src/main/resources/assets/visual-swap/swap_hit_masks.json) (
`"failed": { "particle": "swap_failed" }`)
vs. [ParticlesHandler.registerTypes](src/client/java/com/patchnote/visualswap/client/particles/ParticlesHandler.java#L46-L51)

Only `swap_possible`, `swap_attacked`, `swap_consecutive` are registered (and only
those three have `particles/*.json`). The bake task still rasterizes `swap_failed.png`
from the `failed` mask ([build.gradle:80-101](build.gradle#L80-L101)), producing an
unused sprite. Documented as "harmless" in AGENTS.md, but the mask's `particle` field
is dead/misleading and the extra PNG ships in the jar.

---

## Documentation drift (CLAUDE.md requires docs be kept fresh)

### 7. Stale `en_us.json` lang keys — [Low] (confidence: high)

[en_us.json:3-5](src/main/resources/assets/visual-swap/lang/en_us.json#L3-L5)

Keys reference `text.autoconfig.visual-swap.option.indicatorType[.@Tooltip]`, but the
config field was renamed to `preset` (commit "Rename IndicatorType to Preset") and the
AutoConfig-generated GUI is no longer used (a hand-built screen replaced it). There is
also a junk entry `"1": "----…----"` that serves no purpose.

### 8. `swap_hit_masks.json` `_comment` still says "IndicatorType config" — [Low] (confidence: high)

[swap_hit_masks.json:2](src/main/resources/assets/visual-swap/swap_hit_masks.json#L2)

Same rename drift — should read `Preset`.

### 9. AGENTS.md describes classes/masks/tests that no longer match the code — [Low] (confidence: high)

`AGENTS.md`

Names that have since changed or never existed under these names:

- `SwapWindow` → actual `SwapWindowState`
- `IndicatorType` / `IndicatorType.{VANILLA,PRACTICE}` → actual `Preset` (now also has `CUSTOM`)
- `VisualSwapParticles`, `SwapGlyphParticle` → actual `ParticlesHandler`, `SwapParticle`
- masks `lunge_failed` / `stun_slam` → actual `failed` / `consecutive`
- tests `SwapWindowTest` / `SwapHitState` → do not exist

---

## Minor / maintenance

### 10. `SwapHitMasks.load` double-wraps exceptions, hiding the real cause message — [Low] (confidence: high)

[SwapHitMasks.java:39-59](src/client/java/com/patchnote/visualswap/client/particles/SwapHitMasks.java#L39-L59)

The specific `IllegalStateException("Mask '…' not found")` / `"Missing … resource"`
thrown inside the `try` is caught by the broad `catch (Exception e)` and re-wrapped as
the generic `"Failed to load swap-hit mask"`. The precise message is only visible as a
nested cause. The broad `catch (Exception)` also swallows programming errors (NPEs from
malformed JSON) into the same generic message.

### 11. Hotbar geometry is hardcoded and duplicated across three places — [Low] (confidence: high)

[HotbarItemGlowMixin.java:41-46](src/client/java/com/patchnote/visualswap/client/mixin/HotbarItemGlowMixin.java#L41-L46), [SwapHotbarHighlight.java:55](src/client/java/com/patchnote/visualswap/client/hud/SwapHotbarHighlight.java#L55),[:60](src/client/java/com/patchnote/visualswap/client/hud/SwapHotbarHighlight.java#L60),[:91](src/client/java/com/patchnote/visualswap/client/hud/SwapHotbarHighlight.java#L91)

Magic numbers for the vanilla hotbar layout (`guiWidth/2 - 90 + 2`, slot stride `20`,
slot size `16`, `guiHeight - 19`) are copy-pasted between the two mixins and the
highlight renderer with no shared constant. If vanilla changes the hotbar layout these
drift independently. The glow mixin's offhand exclusion also relies solely on the
`y == guiHeight - 19` / `rel % 20 == 0` checks rather than an explicit slot-source test.

### 12. `ClickTickTracker.capture` copies the main-hand `ItemStack` every client tick — [Low] (confidence: med)

[ClickTickTracker.java:51](src/client/java/com/patchnote/visualswap/client/tracker/ClickTickTracker.java#L51)

`player.getMainHandItem().copy()` allocates a fresh stack each tick even when nothing is
happening. Needed for the immutable snapshot, but a cheaper equality/identity check
could avoid the per-tick allocation on idle ticks.

### 13. `.github-token` plaintext file in the repo working tree — [Low] (confidence: high)

Repo root `.github-token` (93 bytes)

It is gitignored and **not** tracked (verified via `git ls-files` and history — clean),
so this is not a leak today. Flagging only so it is never force-added; a real secret in
the working directory is worth being deliberate about.

---

## Notes / verified non-issues

- **Dual event registration** (`AttackEntityCallback` + `UseEntityCallback` both →
  `onInteractEntity`, [VisualSwapClient.java:39-40](src/client/java/com/patchnote/visualswap/client/VisualSwapClient.java#L39-L40))
  does **not** double-spawn: left-click fires only Attack, right-click only Use.
- **`ClickTickTracker.attacked()` boolean precedence**
  ([:32-37](src/client/java/com/patchnote/visualswap/client/tracker/ClickTickTracker.java#L32-L37))
  is correct — `&&` binds tighter than `||`, grouping as intended.
- **ModMenu is `suggests` while `ModMenuIntegration` classloads its API**
  ([fabric.mod.json](src/main/resources/fabric.mod.json)) — correct standard pattern;
  the entrypoint is only loaded by ModMenu itself, so it is safe when absent.

---

*Not verified: full `./gradlew build` was not run as part of this audit, so no
compile/API-mapping errors are asserted here. The findings above are from static review.*
