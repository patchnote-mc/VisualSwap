package com.patchnote.visualswap.client.tracker;

import net.minecraft.world.item.ItemStack;

import static com.patchnote.visualswap.client.utils.Constants.NO_SLOT;

/// Immutable per-tick snapshot of the inputs the swap logic reads. Carried/derived state lives on the client.
///
/// {@code slotSelected} records whether the local player selected a hotbar slot this tick (hotbar key or scroll wheel);
/// unlike the held-item comparison it also catches re-selecting the current slot or switching to an identical item.
public record ClickTickState(int tick, boolean initialized, ItemStack mainHand, int selectedSlot, boolean attackDown,
                             boolean useDown, boolean swinging, int swingTime, float cooldownAtTick,
                             boolean hasPiercingComponent, boolean slotSelected)
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
            false,
            false
    );
}