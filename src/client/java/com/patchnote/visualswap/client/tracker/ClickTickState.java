package com.patchnote.visualswap.client.tracker;

import net.minecraft.world.item.ItemStack;

import static com.patchnote.visualswap.client.utils.Constants.NO_SLOT;

/// Immutable per-tick snapshot of the inputs the swap logic reads. Carried/derived state lives on the client.
public record ClickTickState(int tick, boolean initialized, ItemStack mainHand, int selectedSlot, boolean attackDown,
                             boolean useDown, boolean swinging, int swingTime, float cooldownAtTick,
                             boolean hasPiercingComponent)
{
    public static final ClickTickState EMPTY = new ClickTickState(
            0,
            false,
            ItemStack.EMPTY,
            NO_SLOT,
            false,
            false,
            false,
            0,
            0.0f,
            false
    );
}