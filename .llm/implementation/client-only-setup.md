<!-- last updated: 2026-07-11 -->

# Client-only setup

Visual Swap is a client-side-only mod. This note records *how* that is enforced
and *why* the dedicated-server behavior is what it is.

## How it's enforced

- `src/main/resources/fabric.mod.json` → `"environment": "client"`.
- Only a `client` entrypoint (`VisualSwapClient`). The example scaffold's `main`
  (`ModInitializer`) entrypoint was removed.
- `com.patchnote.visualswap.VisualSwap` was demoted from `ModInitializer` to a
  plain, final, non-instantiable holder for `MOD_ID` + `LOGGER`. It stays in the
  `main` (common) source set and must remain side-agnostic (no client imports).
- All client-only API usage lives in the `client` source set.

Do not re-introduce a `main`/server entrypoint or set `environment` back to
`"*"`.

## Don't touch network-synced registries (LAN-host contract)

`"environment": "client"` only stops the mod loading on a **dedicated** server.
It does **not** protect the *integrated* server: when a player opens their
single-player world to LAN, the integrated server shares the client JVM (and its
registries), and Visual Swap's client code has run. So anything the mod adds to a
**network-synced** registry (`BuiltInRegistries.PARTICLE_TYPE`, `ITEM`, `BLOCK`,
`ENTITY_TYPE`, …) leaks onto that host.

Fabric flags a synced registry `MODDED` the instant a mod adds a non-`minecraft`
entry, then registry-sync pushes those entries to every joining client during
configuration. A client **without** Visual Swap is then disconnected:

- no Fabric API → *"This server requires Fabric Loader and Fabric API installed"*;
- Fabric API but no Visual Swap → *"unknown remote registry entries"*
  (`visual-swap:swap_*`). `RegistryAttribute.OPTIONAL` does **not** rescue this
  case — it's only consulted when the whole registry is missing on the client, not
  for missing entries in a registry the client already has.

**Rule:** never register into a synced registry from this mod. History: the swap
particle used to register three `PARTICLE_TYPE` entries and broke exactly this
LAN-join path (fixed 2026-07-11). `ParticlesHandler` now builds particles
client-side and adds them straight to `Minecraft.particleEngine`, looking up the
sprite from the vanilla particle atlas — zero synced-registry footprint. Keep it
that way.

## Server behavior decision

Requirement as stated: "client side only; when run on a server, warn and do not
register as a mod."

These two cannot be fully combined in Fabric:

- To print a **custom warning** on a dedicated server, the mod must load there,
  which means it *is* registered (shows in the server mod list) and a server/
  common entrypoint must run.
- To **not register at all** on a server, declare `"environment": "client"`;
  Fabric Loader then skips the mod entirely — but our code never runs, so we
  cannot emit a custom warning (only Fabric's own generic skip log line).

**Chosen (per user, 2026-06-27): truly client-only, no custom warning.**
`"environment": "client"` → the mod is never loaded/registered on a dedicated
server, zero footprint, not in the mod list. The trade-off accepted is that
there is no Visual-Swap-authored warning on the server; Fabric's own loader log
is the only signal.

The rejected alternative ("warn, then stay inert") would have kept
`"environment": "*"` plus a `main` entrypoint that detects `EnvType.SERVER`,
logs a warning, and registers nothing. If the priority ever flips to wanting a
visible custom server warning, that's the switch to make.
