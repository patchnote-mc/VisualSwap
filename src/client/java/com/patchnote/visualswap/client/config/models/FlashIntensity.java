package com.patchnote.visualswap.client.config.models;

import net.minecraft.network.chat.Component;

/// How strongly a {@link FlashRule}'s item is tinted when it flashes.
///
/// The value is a *gamma* applied to the item's own luminance (see `alpha_to_bw.py`): the shader reshapes each pixel's
/// brightness with `pow(luminance, 1 / gamma)` before painting it the tint colour. A higher gamma lifts the darks toward
/// the flat tint colour (a bright, punchy flash); a lower gamma preserves more of the item's own shading (subtler).
public enum FlashIntensity
{
    LOW(4.0),
    HIGH(8.0);

    private final double gamma;

    FlashIntensity(double gamma) { this.gamma = gamma; }

    /* GETTERS */

    public double getGamma() { return this.gamma; }

    /* HELPERS */

    public String getName()
    {
        return switch (this)
        {
            case LOW -> "Low";
            case HIGH -> "High";
        };
    }

    public Component getNameComponent()
    {
        return switch (this)
        {
            case LOW -> Component.literal("Low");
            case HIGH -> Component.literal("High");
        };
    }
}
