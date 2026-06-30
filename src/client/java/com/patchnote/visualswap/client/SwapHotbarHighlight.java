package com.patchnote.visualswap.client;

import com.patchnote.visualswap.client.mixin.HudHotbarHighlightMixin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

import static com.patchnote.visualswap.client.VisualSwapClient.NO_SLOT;

/**
 * Highlights the two hotbar slots involved in a swap-hit while the attacked flash runs. The boxes are drawn by
 * {@code HudHotbarHighlightMixin} at the head of each hotbar slot render — in front of the hotbar bar but behind the
 * item icon, so the item stays fully visible over a gray backdrop. Style mirrors the vanilla item-cooldown overlay; the
 * swapped-to slot is a little more opaque so it reads as the primary item.
 */
public final class SwapHotbarHighlight
{
    // singleton
    public static final SwapHotbarHighlight INSTANCE = new SwapHotbarHighlight();

    private static final int FROM_COLOR = 0x7FFFFFFF;
    private static final int TO_COLOR = 0xB0FFFFFF;

    private boolean active;
    private int fromSlot = NO_SLOT;
    private int toSlot = NO_SLOT;

    private SwapHotbarHighlight() { }

    /// Update Each Tick
    public void update(boolean active, int fromSlot, int toSlot)
    {
        this.active = active;
        this.fromSlot = fromSlot;
        this.toSlot = toSlot;
    }

    public void clear() { update(false, NO_SLOT, NO_SLOT); }

    /// Called Via {@link HudHotbarHighlightMixin}
    public void highlightSlot(GuiGraphicsExtractor graphics, int slotX, int slotY)
    {
        if (!this.active) return;

        int left = graphics.guiWidth() / 2 - 90 + 2;
        if (this.toSlot >= 0 && slotX == left + this.toSlot * 20)
        {
            fill(graphics, slotX, slotY, TO_COLOR);
        }
        else if (this.fromSlot >= 0 && slotX == left + this.fromSlot * 20)
        {
            fill(graphics, slotX, slotY, FROM_COLOR);
        }
    }

    private static void fill(GuiGraphicsExtractor graphics, int x, int y, int color)
    {
        graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 16, color);
    }
}
