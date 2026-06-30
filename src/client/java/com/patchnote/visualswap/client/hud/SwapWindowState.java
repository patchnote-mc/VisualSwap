package com.patchnote.visualswap.client.hud;

/**
 * State for the GLYPH. Driven by {@code VisualSwapClient}.
 * <p>
 * Model
 * <ul>
 *     <li>{@link #onSwap(int, boolean)} arms the window for {@link #WINDOW_TICKS}; while open the {@code possible} GLYPH is shown</li>
 *     <li>{@link #onClick(int)} within the window flashes the {@code attacked} GLYPH</li>
 *     <li>{@code lunge_failed} is the same flash with a different mask: an {@code attacked} that began on a
 *     swap onto a lunge spear before the previous item's cooldown finished (the boolean carried by {@code onSwap})</li>
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

    /// Whether the swap that opened the current window qualifies as a failed lunge-swap.
    private boolean lungeFailSwap;
    /// Whether the active {@code attacked} flash should render as {@code lunge_failed} (frozen when the flash arms).
    private boolean flashLungeFailed;

    /// Reset all state (e.g. switched to an empty hand, or no player).
    public void clear()
    {
        this.lastSwapTick = NO_TICK;
        this.flashUntilTick = NO_TICK;
        this.possibleLastTick = false;
        this.lungeFailSwap = false;
        this.flashLungeFailed = false;
    }

    /* EVENTS */

    /// Call when item is swapped. {@code lungeFail} marks a swap onto a lunge spear before the previous item's cooldown finished.
    public void onSwap(int tick, boolean lungeFail)
    {
        this.lastSwapTick = tick;
        this.lungeFailSwap = lungeFail;
    }

    /// Call when mouse clicked
    public void onClick(int tick)
    {
        if (possible(tick) || this.possibleLastTick)
        {
            this.flashUntilTick = tick + FLASH_TICKS;
            this.flashLungeFailed = this.lungeFailSwap;
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

    /** Whether the active attacked flash is a failed lunge-swap (renders {@code lunge_failed} instead of {@code attacked}). */
    public boolean lungeFailed(int tick) { return attacked(tick) && this.flashLungeFailed; }

    /** Whether the GLYPH should be drawn at {@code tick} (possible window open, or the attacked flash running). */
    public boolean visible(int tick) { return possible(tick) || attacked(tick); }
}
