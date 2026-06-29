package com.patchnote.visualswap;

/**
 * Pure, Minecraft-free state for the swap-hit glyph. Driven by the client glue in {@code VisualSwapClient}.
 *
 * <p>Model:
 * <ul>
 *   <li>switching the held item {@linkplain #onSwap arms} the <em>possible</em> window for {@link #WINDOW_TICKS}
 *       ticks — while it is {@linkplain #possible open} the glyph shows the <em>possible</em> variant;</li>
 *   <li>a click (attack or use) {@linkplain #onClick fires} the <em>attacked</em> variant if a swap is
 *       {@linkplain #possible possible} this tick (a same-tick swap+attack) or was possible in the previous tick;</li>
 *   <li>the attacked variant shows as a short {@link #FLASH_TICKS}-tick {@linkplain #attacked flash}, then reverts.</li>
 * </ul>
 *
 * <p>The glyph is {@linkplain #visible visible} whenever the possible window is open <em>or</em> an attacked flash is
 * running. The window clock is the caller's tick counter ({@code player.tickCount}); {@link #endTick} must be called
 * once at the end of each tick so the "was possible last tick" test stays correct.
 */
public final class SwapWindow
{
    /**
     * Length of the <em>possible</em> window, in ticks. Attribute swapping is a same-tick effect:
     * {@code getMainHandItem()} flips immediately on a slot change, but the held-item ATTACK_DAMAGE reconciliation
     * ({@code LivingEntity.detectEquipmentUpdates}) runs only once per entity tick — so the swapped-from attributes
     * survive for exactly one tick. The client only observes the swap at end-of-tick, so {@code 2} = the 1-tick
     * reconciliation lag + 1 tick of client observation is the tightest mechanically-grounded value.
     */
    private static final int WINDOW_TICKS = 2;

    /** How many ticks the <em>attacked</em> variant stays on screen after a qualifying click (~250ms). */
    private static final int FLASH_TICKS = 5;

    private static final int NO_TICK = Integer.MIN_VALUE;

    private int lastSwapTick = NO_TICK;
    private int flashUntilTick = NO_TICK;
    private boolean possibleLastTick;

    /** Arm/refresh the possible window at {@code tick} (held item changed to a non-empty item). */
    public void onSwap(int tick) { this.lastSwapTick = tick; }

    /** Reset all state (e.g. switched to an empty hand, or no player). */
    public void clear()
    {
        this.lastSwapTick = NO_TICK;
        this.flashUntilTick = NO_TICK;
        this.possibleLastTick = false;
    }

    /**
     * Register a click (attack or use) at {@code tick}: start the attacked flash iff the swap-hit is exploitable —
     * either possible <em>this</em> tick (a same-tick swap+attack, which is the real exploit) or possible in the
     * previous tick. Relies on {@code onSwap} having run earlier this tick (the client calls it before
     * {@code onClick}).
     */
    public void onClick(int tick)
    {
        if (possible(tick) || this.possibleLastTick)
        {
            this.flashUntilTick = tick + FLASH_TICKS;
        }
    }

    /** Whether a swap is currently possible (the possible window is open) at {@code tick}. */
    public boolean possible(int tick)
    {
        if (this.lastSwapTick == NO_TICK) return false;

        int elapsed = tick - this.lastSwapTick;
        return elapsed >= 0 && elapsed < WINDOW_TICKS;
    }

    /** Whether the attacked flash is currently running at {@code tick}. */
    public boolean attacked(int tick) { return this.flashUntilTick != NO_TICK && tick < this.flashUntilTick; }

    /** Whether the glyph should be drawn at {@code tick} (possible window open, or attacked flash running). */
    public boolean visible(int tick)
    {
        return possible(tick) || attacked(tick);
    }

    /** Advance per-tick bookkeeping; call once at the end of each tick so {@link #onClick}'s test stays correct. */
    public void endTick(int tick) { this.possibleLastTick = possible(tick); }
}
