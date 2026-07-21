package com.patchnote.visualswap.client.tracker;

/// One-tick latch for "the local player selected a hotbar slot this tick". Set from the
/// {@code Inventory.setSelectedSlot} hook (hotbar number keys and the scroll wheel both route through it) and consumed
/// once per tick by {@link ClickTickTracker} into the {@link ClickTickState} snapshot.
///
/// This is what lets the swap logic register a deliberate slot switch that the held-item comparison can't see:
/// re-pressing the key for the slot already held, or switching to a different slot holding an identical item.
public final class HotbarSelectSignal
{
    // Only ever touched on the client thread: the local player's setSelectedSlot (input + packet handling) and the
    // end-of-tick capture both run there.
    private static boolean selected;

    private HotbarSelectSignal() { }

    public static void mark() { selected = true; }

    /// @return whether a hotbar selection happened since the last consume, then clears the latch.
    public static boolean consume()
    {
        boolean was = selected;
        selected = false;
        return was;
    }

    public static void clear() { selected = false; }
}
