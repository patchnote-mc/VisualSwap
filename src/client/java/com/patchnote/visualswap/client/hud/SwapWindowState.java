package com.patchnote.visualswap.client.hud;

import static com.patchnote.visualswap.client.utils.Constants.NO_TICK;

/**
 * State for the GLYPH. Driven by {@code VisualSwapClient}.
 * <p>
 * Model
 * <ul>
 *     <li>{@link #eventSwap(int, boolean)} arms the window for {@link #WINDOW_TICKS}; while armed {@code possible} or {@code failed} GLYPH is shown depending on passed argument</li>
 *     <li>Click within the window (set by {@link #eventClick(int, int)}) flashes the {@code attacked} GLYPH</li>
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

    /* VARIABLES & STATE */

    private int lastSwapTick = NO_TICK;
    private int flashUntilTick = NO_TICK;
    private boolean possibleLastTick;

    private boolean failed;
    private boolean flashFailed;

    /// Number of swap-hits chained. Valid only while {@link #attacked}.
    private int chainCount;
    /// The swap tick already credited to the chain, so re-clicking the same swap can't count twice.
    private int lastCreditedSwapTick = NO_TICK;
    /// Click tick of the last credited attribute swap. Chain timing is mechanical and independent of render duration.
    private int lastCreditedClickTick = NO_TICK;

    /// Reset all state (e.g. when switched to an empty hand, or no player).
    public void clear()
    {
        this.lastSwapTick = NO_TICK;
        this.flashUntilTick = NO_TICK;
        this.possibleLastTick = false;
        this.failed = false;
        this.flashFailed = false;
        this.chainCount = 0;
        this.lastCreditedSwapTick = NO_TICK;
        this.lastCreditedClickTick = NO_TICK;
    }

    /* EVENTS */

    /// Call when item is swapped.
    public void eventSwap(int tick, boolean failed)
    {
        this.lastSwapTick = tick;
        this.failed = failed;
    }

    /// Call when mouse clicked.
    public boolean eventClick(int tick, int visibleTicks)
    {
        if (acceptsClick(tick))
        {
            // Credit each swap once. Consecutive is based on the fixed two-tick attribute-swap cadence, never on how
            // long the previous hit happens to render.
            if (this.lastSwapTick != this.lastCreditedSwapTick)
            {
                this.chainCount = willChain(tick) ? this.chainCount + 1 : 1;
                this.lastCreditedSwapTick = this.lastSwapTick;
                this.lastCreditedClickTick = tick;
                this.flashUntilTick = tick + Math.max(1, visibleTicks);
                this.flashFailed = this.failed;
                return true;
            }
        }
        return false;
    }

    /// Call on Tick End
    public void eventTickEnd(int tick) { this.possibleLastTick = possible(tick); }

    /* QUERIES */

    /** Whether a swap is currently possible (the possible window is open) at {@code tick}. */
    public boolean possible(int tick)
    {
        if (this.lastSwapTick == NO_TICK) return false;

        int elapsed = tick - this.lastSwapTick;
        return elapsed >= 0 && elapsed < WINDOW_TICKS;
    }

    /// Whether an input observed at {@code tick} belongs to the two-tick attribute-swap window. The previous-tick
    /// sample bridges the second valid input tick to the end-of-tick snapshot where the client can observe it.
    public boolean acceptsClick(int tick) { return possible(tick) || this.possibleLastTick; }

    /** Whether the attacked flash is currently running at {@code tick}. */
    public boolean attacked(int tick) { return this.flashUntilTick != NO_TICK && tick < this.flashUntilTick; }

    /**
     * Whether the active attacked flash is a failed lunge-swap (renders {@code lunge_failed} instead of
     * {@code attacked}).
     */
    public boolean failed(int tick) { return attacked(tick) && this.flashFailed; }

    /** Number of swap-hits chained into the active flash (1 = single, 0 when no flash is running). */
    public int chainCount(int tick) { return attacked(tick) ? this.chainCount : 0; }

    /// Whether a newly credited attribute swap at {@code tick} extends the prior chain. Consecutive credits may land
    /// at most two ticks apart; this uses its own click clock so visual duration cannot change classification.
    public boolean willChain(int tick)
    {
        if (this.lastCreditedClickTick == NO_TICK) return false;
        int elapsed = tick - this.lastCreditedClickTick;
        return elapsed >= 0 && elapsed <= WINDOW_TICKS;
    }

    /// Chain depth to render for an entity interaction that arrives before the end-of-tick click observation. A newly
    /// pending swap extends the active chain; an observed but not-yet-credited swap starts at one; repeated interactions
    /// against an already-credited swap keep the existing depth.
    public int anticipatedChainCount(int tick, boolean newSwapPending)
    {
        int current = willChain(tick) ? this.chainCount : 0;
        boolean uncreditedSwap = newSwapPending ||
                this.lastSwapTick != NO_TICK && this.lastCreditedSwapTick != this.lastSwapTick;
        return current + ((uncreditedSwap || current == 0) ? 1 : 0);
    }

    /** Whether the active flash is a stun slam: two or more swap-hits chained in a row. */
    public boolean consecutive(int tick) { return chainCount(tick) >= 2; }

    /** Whether the GLYPH should be drawn at {@code tick} (possible window open, or the attacked flash running). */
    public boolean visible(int tick) { return possible(tick) || attacked(tick); }
}
