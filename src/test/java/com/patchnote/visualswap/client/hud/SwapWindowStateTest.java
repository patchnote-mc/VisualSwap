package com.patchnote.visualswap.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
        state.eventClick(10, 5);
        state.eventClick(11, 5);

        assertEquals(1, state.chainCount(11));

        state.eventSwap(12, false);
        state.eventClick(12, 5);
        assertEquals(2, state.chainCount(12));
    }

    @Test
    void startsANewChainAfterThePreviousFlashExpires()
    {
        SwapWindowState state = new SwapWindowState();
        state.eventSwap(10, false);
        state.eventClick(10, 5);

        state.eventSwap(15, false);
        state.eventClick(15, 5);

        assertEquals(1, state.chainCount(15));
    }

    @Test
    void configuredDurationControlsTheWholeAttackedWindow()
    {
        SwapWindowState state = new SwapWindowState();
        state.eventSwap(10, false);
        state.eventClick(10, 8);

        assertTrue(state.attacked(17));
        assertFalse(state.attacked(18));

        state.eventSwap(17, false);
        state.eventClick(17, 3);

        assertEquals(1, state.chainCount(19));
        assertFalse(state.attacked(20));
    }

    @Test
    void longRenderDurationDoesNotExtendTheConsecutiveWindow()
    {
        SwapWindowState state = new SwapWindowState();
        state.eventSwap(10, false);
        state.eventClick(10, 40);

        state.eventSwap(13, false);
        state.eventClick(13, 40);

        assertTrue(state.attacked(39));
        assertEquals(1, state.chainCount(13));
    }

    @Test
    void nextTickAttributeSwapExtendsTheChainRegardlessOfRenderDuration()
    {
        SwapWindowState state = new SwapWindowState();
        state.eventSwap(10, false);
        state.eventClick(10, 1);

        state.eventSwap(11, false);
        state.eventClick(11, 1);

        assertEquals(2, state.chainCount(11));
    }

    @Test
    void anticipatesOnlyAnUncreditedInteraction()
    {
        SwapWindowState state = new SwapWindowState();
        state.eventSwap(10, false);

        assertEquals(1, state.anticipatedChainCount(10, true));

        state.eventClick(10, 5);
        assertEquals(1, state.anticipatedChainCount(10, false));
        assertEquals(2, state.anticipatedChainCount(10, true));
    }

    @Test
    void anticipatesAnObservedSwapWhoseSelectionSignalWasAlreadyConsumed()
    {
        SwapWindowState state = new SwapWindowState();
        state.eventSwap(8, false);
        state.eventClick(8, 5);
        state.eventSwap(10, false);

        assertEquals(2, state.anticipatedChainCount(10, false));

        state.eventClick(10, 5);
        assertEquals(2, state.anticipatedChainCount(10, false));
    }
}
