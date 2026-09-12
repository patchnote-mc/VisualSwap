# Visual Swap

A **client-side-only** Fabric mod for Minecraft `1.21.11` that visualizes
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

Swap-window detection, HUD glyphs, hotbar highlights, item flashes, particle
bursts, consecutive-chain feedback, and the configuration screen are
implemented. See [AGENTS.md](AGENTS.md) for the current architecture and flows.

## Build

```
./gradlew build
```

Built jar lands in `build/libs/`. The build bakes the glyph art from
`src/main/resources/assets/visual-swap/swap_hit_masks.json` into particle
sprites (see [AGENTS.md](AGENTS.md) → "Build pipeline").

## Stack

Minecraft `1.21.11` · Fabric (Loader `0.19.3`, API `0.141.6+1.21.11`, Loom
`1.17.19`) · Java `21` · Mojang mappings.

## For contributors / agents

Start with [AGENTS.md](AGENTS.md) (architecture & flows) and
[CLAUDE.md](CLAUDE.md) (working instructions).
