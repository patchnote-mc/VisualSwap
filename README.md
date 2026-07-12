# Visual Swap

A **client-side-only** Fabric mod for Minecraft `26.2` that visualizes
Minecraft's *attribute-swapping* behavior — the "swap hit" case where a
follow-up attack lands using a stale/swapped attribute snapshot. It's purely
cosmetic/diagnostic: it surfaces swap hits with an on-screen glyph and a world
particle, and changes no gameplay.

## Client-only

This mod runs **only on the client**. `fabric.mod.json` declares
`"environment": "client"`, so Fabric Loader skips it entirely on a dedicated
server — it is never registered there and none of its code runs. There is no
`main`/server entrypoint, only a `client` one.

## Status

Early scaffold. The client-only setup and the glyph/particle build pipeline are
in place; swap-hit detection and rendering are not implemented yet. See
[AGENTS.md](AGENTS.md) for architecture and the roadmap.

## Build

```
./gradlew build
```

Built jar lands in `build/libs/`. The build bakes the glyph art from
`src/main/resources/assets/visual-swap/swap_hit_masks.json` into particle
sprites (see [AGENTS.md](AGENTS.md) → "Build pipeline").

## Stack

Minecraft `26.1` · Fabric (Loader `0.19.3`, API) · Java `25` · Mojang mappings.

## For contributors / agents

Start with [AGENTS.md](AGENTS.md) (architecture & flows) and
[CLAUDE.md](CLAUDE.md) (working instructions).
