package com.patchnote.visualswap.client.swap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SwapDetectionTest
{
    @Test
    void ignoresFirstPostSpawnObservation()
    {
        assertFalse(SwapDetection.isDeliberateSwap(false, false, true, false));
        assertFalse(SwapDetection.isDeliberateSwap(false, false, false, true));
    }

    @Test
    void ignoresAnEmptyCurrentHand()
    {
        assertFalse(SwapDetection.isDeliberateSwap(true, true, true, true));
    }

    @Test
    void detectsAChangedItem()
    {
        assertTrue(SwapDetection.isDeliberateSwap(true, false, true, false));
    }

    @Test
    void detectsASelectionSignalWhenTheItemIsIdentical()
    {
        assertTrue(SwapDetection.isDeliberateSwap(true, false, false, true));
    }

    @Test
    void ignoresAnUnchangedItemWithoutASelectionSignal()
    {
        assertFalse(SwapDetection.isDeliberateSwap(true, false, false, false));
    }
}
