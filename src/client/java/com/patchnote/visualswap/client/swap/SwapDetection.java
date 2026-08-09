package com.patchnote.visualswap.client.swap;

/// Shared decision for whether an observed main-hand state represents a deliberate hotbar swap.
public final class SwapDetection
{
    private SwapDetection() { }

    public static boolean isDeliberateSwap(boolean previousInitialized, boolean currentEmpty, boolean itemChanged,
                                           boolean slotSelected)
    {
        return previousInitialized && !currentEmpty && (itemChanged || slotSelected);
    }
}
