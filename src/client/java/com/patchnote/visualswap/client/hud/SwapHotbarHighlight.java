package com.patchnote.visualswap.client.hud;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.mixin.HudHotbarHighlightMixin;
import com.patchnote.visualswap.client.utils.ColorHelpers;
import com.patchnote.visualswap.client.utils.HotbarGeometry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

/// Highlights the Hotbar Slots when Swapped
public final class SwapHotbarHighlight
{
    // singleton
    public static final SwapHotbarHighlight INSTANCE = new SwapHotbarHighlight();

    private boolean active;
    // trail of the current chain
    private final int[] trail = new int[9];
    private int trailLen;
    private int chainCount;

    private SwapHotbarHighlight() { }

    /* EVENTS */

    /// Update Each Tick
    public void eventUpdate(boolean active, int[] trail, int trailLen, int chainCount)
    {
        this.active = active;
        this.trailLen = Math.min(trailLen, this.trail.length);
        System.arraycopy(trail, 0, this.trail, 0, this.trailLen);
        this.chainCount = chainCount;
    }

    public void eventReset()
    {
        this.active = false;
        this.trailLen = 0;
        this.chainCount = 0;
    }

    /* MIXIN CALLS */

    /// Called Via {@link HudHotbarHighlightMixin}
    public void highlightSlot(GuiGraphics graphics, int slotX, int slotY)
    {
        if (!this.active || this.trailLen == 0 || !ModConfig.get().hotbarHighlightActive()) return;

        // Last match wins so a slot revisited later in the chain renders at its brightest position.
        int idx = -1;
        for (int i = 0; i < this.trailLen; i++)
        {
            if (this.trail[i] >= 0 && slotX == HotbarGeometry.slotLeft(graphics.guiWidth(), this.trail[i])) idx = i;
        }
        if (idx < 0) return;

        fillSlot(graphics, slotX, slotY, colorFor(idx));
    }

    /* COLOR */

    private int colorFor(int idx)
    {
        int fromColor = ModConfig.get().getFromColor();
        int toColor = ModConfig.get().getToColor();

        boolean to = idx == this.trailLen - 1;

        if (this.chainCount < 2) return to ? toColor : fromColor;

        float t = this.trailLen <= 1 ? 1.0f : (float) idx / (this.trailLen - 1);
        return ColorHelpers.lerpColorHSV(fromColor, toColor, t);
    }

    private static void fillSlot(GuiGraphics graphics, int x, int y, int color)
    {
        graphics.fill(RenderPipelines.GUI, x, y, x + HotbarGeometry.SLOT_SIZE, y + HotbarGeometry.SLOT_SIZE, color);
    }
}
