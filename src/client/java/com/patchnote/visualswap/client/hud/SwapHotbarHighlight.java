package com.patchnote.visualswap.client.hud;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.mixin.HudHotbarHighlightMixin;
import com.patchnote.visualswap.client.utils.ColorHelpers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/// Highlights the Hotbar Slots when Swapped
public final class SwapHotbarHighlight
{
    // singleton
    public static final SwapHotbarHighlight INSTANCE = new SwapHotbarHighlight();

    // from -> to color pair per preset. Single swap-hit (chain < 2) uses the endpoints directly; a longer chain
    // interpolates across them. Vanilla: cooldown-style gray, the two slots differ only by opacity.
    private static final int FROM_COLOR_VANILLA = 0x40FFFFFF;
    private static final int TO_COLOR_VANILLA = 0x95FFFFFF;
    private static final int FROM_COLOR_PRACTICE = 0xFFFF0000;
    private static final int TO_COLOR_PRACTICE = 0xFF00FF00;

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

    /* COLOR */

    private int colorFor(int idx)
    {
        ModConfig cfg = ModConfig.get();
        int fromColor;
        int toColor;
        switch (cfg.preset)
        {
            case PRACTICE -> { fromColor = FROM_COLOR_PRACTICE; toColor = TO_COLOR_PRACTICE; }
            case CUSTOM -> { fromColor = cfg.customColorFrom; toColor = cfg.customColorTo; }
            default -> { fromColor = FROM_COLOR_VANILLA; toColor = TO_COLOR_VANILLA; }
        }

        boolean to = idx == this.trailLen - 1;

        if (this.chainCount < 2) return to ? toColor : fromColor;

        float t = this.trailLen <= 1 ? 1.0f : (float) idx / (this.trailLen - 1);
        return ColorHelpers.lerpColorHSV(fromColor, toColor, t);
    }

    private static void fillSlot(GuiGraphicsExtractor graphics, int x, int y, int color)
    {
        graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 16, color);
    }
}
