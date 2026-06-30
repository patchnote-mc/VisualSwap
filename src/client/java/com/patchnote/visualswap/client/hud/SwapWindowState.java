package com.patchnote.visualswap.client.hud;

/**
 * State for the GLYPH. Driven by {@code VisualSwapClient}.
 * <p>
 * Model
 * <ul>
 *     <li>{@link #onSwap(int)} arms the window for {@link #WINDOW_TICKS}, while {@code possible}, {@code possible} GLYPH is shown</li>
 *     <li>{@link #onClick(int)} shows the {@code attacked} GLYPH attacked within window</li>
 *     <li></li>
 * </ul>
 */
public final class SwapWindowState
{
    /* CONSTANTS */

    /**
     * Length of the window, in ticks.
     * <p>
     * Attribute swapping is a same-tick effect. The client only observes the swap at end-of-tick, so:
     * <p>
     * {@code 2} = {@code 1-tick reconciliation lag} + {@code 1 tick of client observation}
     * <p>
     * is the tightest mechanically-grounded value.
     */
    private static final int WINDOW_TICKS = 2;

    /// Number of ticks {@code attacked} GLYPH is on screen
    private static final int FLASH_TICKS = 5;

    /* VARIABLES & STATE */

    private static final int NO_TICK = Integer.MIN_VALUE;

    private int lastSwapTick = NO_TICK;
    private int flashUntilTick = NO_TICK;
    private boolean possibleLastTick;

    /// Reset all state (e.g. switched to an empty hand, or no player).
    public void clear()
    {
        this.lastSwapTick = NO_TICK;
        this.flashUntilTick = NO_TICK;
        this.possibleLastTick = false;
    }

    /* EVENTS */

    /// Call when item is swapped
    public void onSwap(int tick) { this.lastSwapTick = tick; }

    /// Call when mouse clicked
    public void onClick(int tick)
    {
        if (possible(tick) || this.possibleLastTick)
        {
            this.flashUntilTick = tick + FLASH_TICKS;
        }
    }

    /// Call on Tick End
    public void onTickEnd(int tick) { this.possibleLastTick = possible(tick); }

    /* QUERIES */

    /** Whether a swap is currently possible (the possible window is open) at {@code tick}. */
    public boolean possible(int tick)
    {
        if (this.lastSwapTick == NO_TICK) return false;

        int elapsed = tick - this.lastSwapTick;
        return elapsed >= 0 && elapsed < WINDOW_TICKS;
    }

    /** Whether the attacked flash is currently running at {@code tick}. */
    public boolean attacked(int tick) { return this.flashUntilTick != NO_TICK && tick < this.flashUntilTick; }

    /** Whether the GLYPH should be drawn at {@code tick} (possible window open, or attacked flash running). */
    public boolean visible(int tick) { return possible(tick) || attacked(tick); }
}
