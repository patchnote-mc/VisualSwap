# Description

Visual Swap is a client-side-only Fabric mod for Minecraft 26.2 that visualizes
the game's attribute-swapping behavior (the "swap hit" bug) — when a follow-up
attack lands using a stale/swapped attribute snapshot.

It is purely cosmetic and diagnostic: when a swap hit happens it shows an
on-screen glyph and a matching world particle, with a distinct variant for
consecutive swap hits. No gameplay is changed.

The mod runs only on the client. Installed on a dedicated server it is skipped
by Fabric Loader and does nothing.
