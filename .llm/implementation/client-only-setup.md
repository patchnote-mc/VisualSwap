<!-- last updated: 2026-06-27 -->

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
