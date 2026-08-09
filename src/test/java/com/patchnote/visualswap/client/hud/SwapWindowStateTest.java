package com.patchnote.visualswap.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SwapWindowStateTest
{
    @Test
    void swapWindowIncludesTwoTicksAndOneEndOfTickBridge()
    {
        SwapWindowState state = new SwapWindowState();
        state.eventSwap(10, false);

        assertTrue(state.possible(10));
        assertTrue(state.possible(11));
        assertFalse(state.possible(12));

        state.eventTickEnd(11);
        assertTrue(state.acceptsClick(12));
        state.eventTickEnd(12);
        assertFalse(state.acceptsClick(13));
    }

    @Test
    void creditsEachSwapOnlyOnce()
    {
        SwapWindowState state = new SwapWindowState();
        state.eventSwap(10, false);
        state.eventClick(10);
        state.eventClick(11);

        assertEquals(1, state.chainCount(11));

        state.eventSwap(12, false);
        state.eventClick(12);
        assertEquals(2, state.chainCount(12));
    }

    @Test
    void startsANewChainAfterThePreviousFlashExpires()
    {
        SwapWindowState state = new SwapWindowState();
        state.eventSwap(10, false);
        state.eventClick(10);

        state.eventSwap(15, false);
        state.eventClick(15);

        assertEquals(1, state.chainCount(15));
    }

    @Test
    void anticipatesOnlyAnUncreditedInteraction()
    {
        SwapWindowState state = new SwapWindowState();
        state.eventSwap(10, false);

        assertEquals(1, state.anticipatedChainCount(10, true));

        state.eventClick(10);
        assertEquals(1, state.anticipatedChainCount(10, false));
        assertEquals(2, state.anticipatedChainCount(10, true));
    }

    @Test
    void anticipatesAnObservedSwapWhoseSelectionSignalWasAlreadyConsumed()
    {
        SwapWindowState state = new SwapWindowState();
        state.eventSwap(8, false);
        state.eventClick(8);
        state.eventSwap(10, false);

        assertEquals(2, state.anticipatedChainCount(10, false));

        state.eventClick(10);
        assertEquals(2, state.anticipatedChainCount(10, false));
    }
}
