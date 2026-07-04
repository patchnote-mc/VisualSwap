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
    private static final int FROM_COLOR_VANILLA = 0x40FFFFFF;
    private static final int TO_COLOR_VANILLA = 0x95FFFFFF;
    private static final int FROM_COLOR_PRACTICE = 0xFFFF0000;
    private static final int TO_COLOR_PRACTICE = 0xFF00FF00;

    private static final int CONSECUTIVE_START_VANILLA = FROM_COLOR_VANILLA;
    private static final int CONSECUTIVE_END_VANILLA = TO_COLOR_VANILLA;
    private static final int CONSECUTIVE_START_PRACTICE = 0xFFFF0000;
    private static final int CONSECUTIVE_END_PRACTICE = 0xFF00FF00;

    private boolean active;
    // trail of the current chain
    private final int[] trail = new int[9];
    private int trailLen;
    private int chainCount;

    private SwapHotbarHighlight() { }

    /// Update Each Tick
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

    /* COLOR */

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
        return practice ? lerpColor(CONSECUTIVE_START_PRACTICE, CONSECUTIVE_END_PRACTICE, t) //
                        : lerpColor(CONSECUTIVE_START_VANILLA, CONSECUTIVE_END_VANILLA, t);
    }

    /// Interpolates in HSV so hue sweeps around the wheel (red -> yellow -> green). Alpha stays a straight linear
    /// blend.
    private static int lerpColor(int a, int b, float t)
    {
        int aa = (a >>> 24) & 0xFF;
        int ab = (b >>> 24) & 0xFF;
        int oa = aa + Math.round((ab - aa) * t);

        float[] hsvA = rgbToHsv((a >>> 16) & 0xFF, (a >>> 8) & 0xFF, a & 0xFF);
        float[] hsvB = rgbToHsv((b >>> 16) & 0xFF, (b >>> 8) & 0xFF, b & 0xFF);

        // Desaturated ends have no meaningful hue; borrow the other end's so the sweep doesn't drift through it.
        if (hsvA[1] < 1e-4f) hsvA[0] = hsvB[0];
        if (hsvB[1] < 1e-4f) hsvB[0] = hsvA[0];

        float h = lerpHue(hsvA[0], hsvB[0], t);
        float s = hsvA[1] + (hsvB[1] - hsvA[1]) * t;
        float v = hsvA[2] + (hsvB[2] - hsvA[2]) * t;

        return (oa << 24) | hsvToRgb(h, s, v);
    }

    /// Shortest-arc hue interpolation. Hues in degrees; result wrapped to [0, 360).
    private static float lerpHue(float a, float b, float t)
    {
        float d = b - a;

        if (d > 180f) { d -= 360f; }
        else if (d < -180f) { d += 360f; }

        float h = a + d * t;

        if (h < 0f) { h += 360f; }
        else if (h >= 360f) { h -= 360f; }

        return h;
    }

    private static float[] rgbToHsv(int r, int g, int b)
    {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        float h = 0f;
        if (delta > 0f)
        {
            if (max == rf) h = ((gf - bf) / delta) % 6f;
            else if (max == gf) h = (bf - rf) / delta + 2f;
            else h = (rf - gf) / delta + 4f;
            h *= 60f;
            if (h < 0f) h += 360f;
        }
        float s = max == 0f ? 0f : delta / max;
        return new float[]{h, s, max};
    }

    /// Doesn't give Alpha
    ///
    /// @return packed int: 0x00RRGGBB
    private static int hsvToRgb(float h, float s, float v)
    {
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float rf, gf, bf;
        switch ((int) (h / 60f) % 6)
        {
            //@formatter:off
            case 0 -> { rf = c; gf = x; bf = 0f; }
            case 1 -> { rf = x; gf = c; bf = 0f; }
            case 2 -> { rf = 0f; gf = c; bf = x; }
            case 3 -> { rf = 0f; gf = x; bf = c; }
            case 4 -> { rf = x; gf = 0f; bf = c; }
            default -> { rf = c; gf = 0f; bf = x; }
            //@formatter:on
        }
        return (clamp8((rf + m) * 255f) << 16) | (clamp8((gf + m) * 255f) << 8) | clamp8((bf + m) * 255f);
    }

    private static int clamp8(float channel)
    {
        return Math.clamp(Math.round(channel), 0, 255);
    }

    private static void fillSlot(GuiGraphicsExtractor graphics, int x, int y, int color)
    {
        graphics.fill(RenderPipelines.GUI, x, y, x + 16, y + 16, color);
    }
}
