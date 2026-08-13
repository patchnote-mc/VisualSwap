package com.patchnote.visualswap.client.hud.click;

import com.patchnote.visualswap.client.swap.SwapHandler;
import com.patchnote.visualswap.client.tracker.ClickTickState;
import com.patchnote.visualswap.client.tracker.ClickTickTracker;

public final class ItemFlashHandler
{
    private ItemFlashHandler() { }

    /* EVENTS */

    /// Drive the item flash from the snapshots the tracker captured this tick.
    public static void eventEndClientTick()
    {
        ClickTickState previous = ClickTickTracker.getPreviousState();
        ClickTickState current = ClickTickTracker.getCurrentState();

        boolean hasPrev = previous.initialized();
        boolean attackStarted = hasPrev && current.attackDown() && !previous.attackDown();
        boolean useStarted = hasPrev && current.useDown() && !previous.useDown();
        boolean swingStarted = hasPrev && current.swinging()
                && (!previous.swinging() || current.swingTime() < previous.swingTime());

        // SwapHandler.eventTick has already run this tick (see VisualSwapClient), including the observation bridge for
        // an input made on the second attribute-swap tick.
        boolean attributeSwap = SwapHandler.INSTANCE.attributeSwapThisTick();
        // A delayed swing while the attack key remains down is the actual qualifying attack for some weapons. Include
        // it in swap-only mode; otherwise retain the ordinary rising-edge behavior so a held key does not re-flash.
        boolean attackPressed = attackStarted || (swingStarted && (!current.attackDown() || attributeSwap));

        ItemFlash.INSTANCE.onTick(
                current.tick(),
                current.selectedSlot(),
                current.mainHand(),
                current.attackDown(),
                current.useDown(),
                attackPressed,
                useStarted,
                attributeSwap
        );
    }
}
