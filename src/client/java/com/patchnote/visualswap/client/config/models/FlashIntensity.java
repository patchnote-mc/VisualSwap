package com.patchnote.visualswap.client.config.models;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/// How strongly a {@link FlashRule}'s item is tinted when it flashes.
///
/// The value is the *shade exponent* the silhouette shader applies to each pixel's own luminance: it paints the tint
/// colour at `pow(luminance, exponent)` (the exponent is `1 / gamma`). {@link #HIGH} uses `0` — a flat fill where every
/// opaque pixel becomes the full tint colour (e.g. fully white), with no leftover grey. {@link #LOW} uses `1/4`,
/// keeping the item's own shading so its individual pixels still read (a subtler flash).
public enum FlashIntensity
{
    LOW(0.5),
    HIGH(0.0);

    private final double shadeExponent;

    FlashIntensity(double shadeExponent) { this.shadeExponent = shadeExponent; }

    /* GETTERS */

    public double getShadeExponent() { return this.shadeExponent; }

    /* HELPERS */

    public Component getNameComponent()
    {
        return Component.translatable("gui.visual-swap.intensity." + name().toLowerCase(Locale.ROOT));
    }
}
