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

        boolean attackPressed = attackStarted || (swingStarted && !current.attackDown());

        // SwapHandler.eventTick has already run this tick (see VisualSwapClient), so the swap window is up to date.
        boolean swapWindowOpen = SwapHandler.INSTANCE.isSwapWindowOpen(current.tick());

        ItemFlash.INSTANCE.onTick(
                current.tick(),
                current.selectedSlot(),
                current.mainHand(),
                current.attackDown(),
                current.useDown(),
                attackPressed,
                useStarted,
                swapWindowOpen
        );
    }
}
