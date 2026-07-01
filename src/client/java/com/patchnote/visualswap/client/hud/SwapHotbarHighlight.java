package com.patchnote.visualswap.client.hud;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.mixin.HudHotbarHighlightMixin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/// Highlights the Hotbar Slots when Swapped
public final class SwapHotbarHighlight
{
    // singleton
    public static final SwapHotbarHighlight INSTANCE = new SwapHotbarHighlight();

    // Single swap-hit (chain < 2). Vanilla: cooldown-style gray, the two slots differ only by opacity.
    private static final int FROM_COLOR_VANILLA = 0x7FFFFFFF;
    private static final int TO_COLOR_VANILLA = 0xB0FFFFFF;
    private static final int FROM_COLOR_PRACTICE = 0xFFFF0000;
    private static final int TO_COLOR_PRACTICE = 0xFF00FF00;

    private static final int CONSECUTIVE_START_VANILLA = 0x7FFFFFFF;
    private static final int CONSECUTIVE_END_VANILLA = 0xB0FFFFFF;
    private static final int CONSECUTIVE_START_PRACTICE = 0xFFFF0000;
    private static final int CONSECUTIVE_END_PRACTICE = 0xFF00FF00;

    private boolean active;
    /// Ordered hotbar slots touched by the current chain (origin first, latest hit last). Length {@link #trailLen}.
    private final int[] trail = new int[9];
    private int trailLen;
    private int chainCount;

    private SwapHotbarHighlight() { }

    /// Update Each Tick. {@code trail}/{@code trailLen} is the ordered chain of slots; {@code chainCount} drives the
    /// styling.
    public void update(boolean active, int[] trail, int trailLen, int chainCount)
    {
        this.active = active;
        this.trailLen = Math.min(trailLen, this.trail.length);
        System.arraycopy(trail, 0, this.trail, 0, this.trailLen);
        this.chainCount = chainCount;
    }

    public void clear()
    {
        this.active = false;
        this.trailLen = 0;
        this.chainCount = 0;
    }

    /// Called Via {@link HudHotbarHighlightMixin}
    public void highlightSlot(GuiGraphicsExtractor graphics, int slotX, int slotY)
    {
        if (!this.active || this.trailLen == 0) return;

        int hotbarLeft = graphics.guiWidth() / 2 - 90 + 2;
        // Last match wins so a slot revisited later in the chain renders at its brightest position.
        int idx = -1;
        for (int i = 0; i < this.trailLen; i++)
        {
            if (this.trail[i] >= 0 && slotX == hotbarLeft + this.trail[i] * 20) idx = i;
        }
        if (idx < 0) return;

        fillSlot(graphics, slotX, slotY, colorFor(idx));
    }

    /* HELPERS */

    private int colorFor(int idx)
    {
        boolean practice = ModConfig.get().indicatorType == ModConfig.IndicatorType.PRACTICE;
        boolean to = idx == this.trailLen - 1;

        if (this.chainCount < 2)
        {
            if (practice) return to ? TO_COLOR_PRACTICE : FROM_COLOR_PRACTICE;
            return to ? TO_COLOR_VANILLA : FROM_COLOR_VANILLA;
        }

        float t = this.trailLen <= 1 ? 1.0f : (float) idx / (this.trailLen - 1);
        return practice ? lerpArgb(CONSECUTIVE_START_PRACTICE, CONSECUTIVE_END_PRACTICE, t)
                        : lerpArgb(CONSECUTIVE_START_VANILLA, CONSECUTIVE_END_VANILLA, t);
    }

    private static int lerpArgb(int a, int b, float t)
    {
        int oa = lerpChannel(a, b, t, 24);
        int or = lerpChannel(a, b, t, 16);
        int og = lerpChannel(a, b, t, 8);
        int ob = lerpChannel(a, b, t, 0);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    private static int lerpChannel(int a, int b, float t, int shift)
    {
        int ca = (a >>> shift) & 0xFF;
        int cb = (b >>> shift) & 0xFF;
        return ca + Math.round((cb - ca) * t);
    }

    private static void fillSlot(GuiGraphicsExtractor graphics, int x, int y, int color)
    {
        graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 16, color);
    }
}
