package com.patchnote.visualswap.client;

import com.patchnote.visualswap.VisualSwap;

import net.fabricmc.api.ClientModInitializer;

public class VisualSwapClient implements ClientModInitializer
{
    @Override
    public void onInitializeClient()
    {
        VisualSwap.LOGGER.info("Visual Swap (client-only) initializing.");
    }
}
