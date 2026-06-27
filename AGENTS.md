<!-- last updated: 2026-06-27 -->

# AGENTS.md — Visual Swap architecture & flows

Authoritative architecture/flow guide for this repo. Working strategy lives in
`.github/copilot-instructions.md`; the pointer/essentials live in `CLAUDE.md`;
longer design notes in `.llm/`; per-file source notes in `.index/`.

## What this mod is

Visual Swap is a **client-side-only** Fabric mod for Minecraft `26.2` that
visualizes the *attribute-swapping* behavior (the "swap hit" bug) — the case
where a follow-up attack lands using a stale/swapped attribute snapshot. The
mod's job is purely cosmetic/diagnostic: surface when a swap-hit happens via an
on-screen glyph and a world particle. It changes no gameplay and runs entirely
on the client.

> **Status:** the swap-detection and rendering features are **NOT YET
> IMPLEMENTED** — see [Roadmap](#roadmap). What exists today is the client-only
> mod skeleton plus the build pipeline that bakes glyph art from
> `swap_hit_masks.json`.

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
    entrypoint (`ClientModInitializer`). Today it just logs init; it will own
    the swap-hit detection + rendering registration.

Mixins (configs present, both currently empty):

- `visual-swap.mixins.json` — package `com.patchnote.visualswap.mixin`.
- `visual-swap.client.mixins.json` — package
  `com.patchnote.visualswap.client.mixin`, `"environment": "client"`.

Both target `compatibilityLevel: JAVA_25` and require annotations
(`overwrites.requireAnnotations = true`, `injectors.defaultRequire = 1`).

## Build pipeline

Standard Loom build (`./gradlew build`) with one project-specific wrinkle: the
**particle sprite bake**.

- Single source of truth: `src/main/resources/assets/visual-swap/swap_hit_masks.json`.
  Each entry is a 7×7 (`#` = filled) glyph with a `particle` name, a HUD `color`
  (ARGB) and an opaque `particleColor` (RGB) for the baked sprite. Two masks
  today: `normal` (→ `swap_hit`) and `consecutive` (→ `swap_hit_consecutive`).
- `tasks.bakeParticleSprites` (in `build.gradle`) rasterizes each mask into
  `build/generated/particle-sprites/assets/visual-swap/textures/particle/<particle>.png`,
  scaling each cell by `cellPx = 8`. Filled cells use `particleColor`; empty
  cells are transparent.
- That generated dir is wired into the **main** resources
  (`sourceSets.main.resources.srcDir generatedParticleDir`), so the PNGs ship in
  the jar **without being committed**. `processResources` and `sourcesJar`
  `dependsOn bakeParticleSprites`.
- `processResources` also expands `${version}` in `fabric.mod.json` from
  `project.version` (`mod_version` in `gradle.properties`).

Edit a mask in the JSON and **both** the (future) HUD glyph and the baked
particle update from the same data.

## Toolchain / versions

See `CLAUDE.md` and `gradle.properties` for the authoritative list. In short:
Minecraft `26.2`, Fabric Loader `0.19.3`, Fabric API `0.153.0+26.2`, Loom
`1.17-SNAPSHOT`, Gradle wrapper `9.5.1`, Java `25`, **Mojang mappings only**
(never Yarn). Decompiled sources are vendored locally and regenerated via the
`mc_decompiled/` and `fabric_decompiled/` setup scripts — consult them only when
something won't compile or an API is genuinely unclear, and cache findings under
the `*_decompiled/.index` / `.knowledge` dirs (see `.github/copilot-instructions.md`).

## Tests

`build.gradle` wires plain JUnit 5 (`useJUnitPlatform()`) for Minecraft-free
unit tests of the swap-hit logic (the planned `SwapHitState`). These tests must
never load Minecraft, so no `fabric-loader-junit` is needed. No test sources
exist yet.

## Roadmap (not yet implemented)

The build wiring and asset pipeline anticipate these pieces; treat the names
below as the intended design, not existing code:

- `SwapHitState` — pure, Minecraft-free state machine that decides when a swap
  hit (and a *consecutive* swap hit) occurred. Unit-tested in isolation.
- `SwapHitGlyph` — client-side reader of `swap_hit_masks.json` that drives the
  HUD glyph rendering (the masks' `color`/`rows`), paired with the baked
  particle sprites.
- The `VisualSwapClient` entrypoint wiring detection → HUD glyph + world
  particle, plus any mixins needed to observe attack/attribute application.

When you implement these, add `.index/` entries, fill the mixin configs, and
update this file (bumping the date above) in the same pass.
