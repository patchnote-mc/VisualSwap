package com.patchnote.visualswap.client.hud;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.mixin.HudHotbarHighlightMixin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

import static com.patchnote.visualswap.client.VisualSwapClient.NO_SLOT;

/// Highlights the Hotbar Slots when Swapped
public final class SwapHotbarHighlight
{
    // singleton
    public static final SwapHotbarHighlight INSTANCE = new SwapHotbarHighlight();

    // Vanilla: cooldown-style gray, the two slots differ only by opacity.
    private static final int FROM_COLOR_VANILLA = 0x7FFFFFFF;
    private static final int TO_COLOR_VANILLA = 0xB0FFFFFF;
    // Practice: scream the from->to direction with full-opacity hues instead of vanilla's faint->bold opacity ramp.
    // Red = the slot you swapped away from, green = the (emphasized) slot you swapped to.
    private static final int FROM_COLOR_PRACTICE = 0xFFFF0000;
    private static final int TO_COLOR_PRACTICE = 0xFF00FF00;

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

        boolean practice = ModConfig.get().indicatorType == ModConfig.IndicatorType.PRACTICE;
        int hotbarLeft = graphics.guiWidth() / 2 - 90 + 2;
        if (this.toSlot >= 0 && slotX == hotbarLeft + this.toSlot * 20)
        {
            fillSlot(graphics, slotX, slotY, practice ? TO_COLOR_PRACTICE : TO_COLOR_VANILLA);
        }
        else if (this.fromSlot >= 0 && slotX == hotbarLeft + this.fromSlot * 20)
        {
            fillSlot(graphics, slotX, slotY, practice ? FROM_COLOR_PRACTICE : FROM_COLOR_VANILLA);
        }
    }

    /* HELPERS */

    private static void fillSlot(GuiGraphicsExtractor graphics, int x, int y, int color)
    {
        graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 16, color);
    }
}
