package com.patchnote.visualswap.client.utils;

import static com.patchnote.visualswap.client.utils.Constants.HOTBAR_SLOTS;
import static com.patchnote.visualswap.client.utils.Constants.NO_SLOT;

/// Single source of truth for the vanilla hotbar-slot layout (`Hud`: slot i at {@code x = guiWidth/2 - 90 + 2 + i*20},
/// {@code y = guiHeight - 16 - 3}). Shared by the glow mixin and the hotbar highlight so the magic numbers don't drift
/// independently if vanilla changes the layout.
public final class HotbarGeometry
{
    private HotbarGeometry() { }

    /// Rendered size (px) of a hotbar item/slot.
    public static final int SLOT_SIZE = 16;

    /// Horizontal stride (px) between adjacent hotbar slots.
    public static final int SLOT_STRIDE = 20;

    /// The hotbar bar is 182px wide and centred, so its left edge sits at {@code guiWidth/2 - 90}.
    private static final int BAR_HALF_WIDTH = 90;

    /// Inset (px) of the first slot's left edge from the bar's left edge.
    private static final int SLOT_INSET = 2;

    /// The slot row's top edge measured up from the screen bottom.
    private static final int SLOT_TOP_FROM_BOTTOM = 19;

    /// @return the left x of hotbar slot 0.
    public static int firstSlotLeft(int guiWidth) { return guiWidth / 2 - BAR_HALF_WIDTH + SLOT_INSET; }

    /// @return the left x of hotbar {@code slot}.
    public static int slotLeft(int guiWidth, int slot) { return firstSlotLeft(guiWidth) + slot * SLOT_STRIDE; }

    /// @return the top y of the hotbar slot row.
    public static int slotTop(int guiHeight) { return guiHeight - SLOT_TOP_FROM_BOTTOM; }

    /// Reverse-map a rendered blit's top-left ({@code x}, {@code y}) to its hotbar slot index. The stride/row checks
    /// also exclude the offhand slot (which does not sit on the hotbar row at a multiple of the stride).
    ///
    /// @return the slot index in {@code [0, HOTBAR_SLOTS)}, or {@link Constants#NO_SLOT} if not a hotbar slot.
    public static int slotIndexAt(int guiWidth, int guiHeight, int x, int y)
    {
        if (y != slotTop(guiHeight)) return NO_SLOT;
        int rel = x - firstSlotLeft(guiWidth);
        if (rel < 0 || rel % SLOT_STRIDE != 0) return NO_SLOT;
        int slot = rel / SLOT_STRIDE;
        return slot < HOTBAR_SLOTS ? slot : NO_SLOT;
    }
}
