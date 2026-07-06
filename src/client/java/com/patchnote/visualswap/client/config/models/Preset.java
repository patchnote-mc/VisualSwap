package com.patchnote.visualswap.client.config.models;

/// A preset's mutable settings — the size multiplier and the From/To highlight colours. This is a plain data object
/// (not an enum) so its fields actually serialise; the identity and fixed defaults live on {@link PresetType}. In
/// practice only the Custom preset's settings are edited and persisted (see {@code ModConfig.customPreset}).
public final class Preset
{
    private double sizeMultiplier;
    private int fromColor;
    private int toColor;

    public Preset() { }  // for the (de)serializer

    public Preset(double sizeMultiplier, int fromColor, int toColor)
    {
        this.sizeMultiplier = sizeMultiplier;
        this.fromColor = fromColor;
        this.toColor = toColor;
    }

    public Preset(Preset other) { this(other.sizeMultiplier, other.fromColor, other.toColor); }

    /* GETTERS & SETTERS */

    public double getSizeMultiplier() { return sizeMultiplier; }

    public int getFromColor() { return fromColor; }

    public int getToColor() { return toColor; }

    public Preset setSizeMultiplier(double sizeMultiplier)
    {
        this.sizeMultiplier = sizeMultiplier;
        return this;
    }

    public Preset setFromColor(int fromColor)
    {
        this.fromColor = fromColor;
        return this;
    }

    public Preset setToColor(int toColor)
    {
        this.toColor = toColor;
        return this;
    }
}
