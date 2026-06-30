package com.patchnote.visualswap.client.hud;

import com.patchnote.visualswap.client.mixin.HudHotbarHighlightMixin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

import static com.patchnote.visualswap.client.VisualSwapClient.NO_SLOT;

/// Highlights the Hotbar Slots when Swapped
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

        int hotbarLeft = graphics.guiWidth() / 2 - 90 + 2;
        if (this.toSlot >= 0 && slotX == hotbarLeft + this.toSlot * 20)
        {
            fillSlot(graphics, slotX, slotY, TO_COLOR);
        }
        else if (this.fromSlot >= 0 && slotX == hotbarLeft + this.fromSlot * 20)
        {
            fillSlot(graphics, slotX, slotY, FROM_COLOR);
        }
    }

    /* HELPERS */

    private static void fillSlot(GuiGraphicsExtractor graphics, int x, int y, int color)
    {
        graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 16, color);
    }
}
